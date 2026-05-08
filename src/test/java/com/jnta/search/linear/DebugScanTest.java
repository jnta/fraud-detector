package com.jnta.search.linear;

import com.jnta.search.KnnQueue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Random;

public class DebugScanTest {
    public static void main(String[] args) {
        int numVectors = 1000;
        int dimsCount = 14;
        float[][] data = new float[dimsCount][numVectors];
        Random rnd = new Random(42);

        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < dimsCount; j++) {
                data[j][i] = rnd.nextFloat() * 10000;
            }
        }

        long[] offsets = new long[dimsCount];
        long currentOffset = 0;
        for (int i = 0; i < dimsCount; i++) {
            offsets[i] = currentOffset;
            long dimSize = numVectors * 4L;
            long padding = (64 - (dimSize % 64)) % 64;
            currentOffset += dimSize + padding;
        }
        long fraudOffset = currentOffset;
        long totalSize = fraudOffset + numVectors;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(totalSize);
            for (int j = 0; j < dimsCount; j++) {
                for (int i = 0; i < numVectors; i++) {
                    segment.set(ValueLayout.JAVA_FLOAT, offsets[j] + i * 4L, data[j][i]);
                }
            }

            LinearScanEngine engine = new LinearScanEngine(segment, numVectors, dimsCount);
            
            float[] query = new float[dimsCount];
            for (int i = 0; i < dimsCount; i++) {
                query[i] = rnd.nextFloat() * 10000;
            }

            KnnQueue queue = new KnnQueue(5);
            engine.search(query, queue, Long.MAX_VALUE);

            KnnQueue expectedQueue = new KnnQueue(5);
            for (int i = 0; i < numVectors; i++) {
                float sumSq = 0;
                for (int j = 0; j < dimsCount; j++) {
                    float diff = query[j] - data[j][i];
                    sumSq += diff * diff;
                }
                expectedQueue.insert(i, sumSq); 
            }

            for (int i = 0; i < 5; i++) {
                System.out.println("Expected: idx " + expectedQueue.getIndices()[i] + " dist " + expectedQueue.getDistances()[i]);
                System.out.println("Actual:   idx " + queue.getIndices()[i] + " dist " + queue.getDistances()[i]);
            }
        }
    }
}
