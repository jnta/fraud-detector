package com.jnta.search.linear;

import com.jnta.search.KnnQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

public class LinearScanEngineTest {

    @Test
    public void testAccuracyAgainstScalar() {
        int numVectors = 1000;
        int dimsCount = 14;
        float[][] data = new float[dimsCount][numVectors];
        boolean[] fraud = new boolean[numVectors];
        Random rnd = new Random(42);

        // Generate data
        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < dimsCount; j++) {
                data[j][i] = rnd.nextFloat() * 10000;
            }
            fraud[i] = rnd.nextBoolean();
        }

        // Calculate expected data size including padding
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

            // Populate segment
            for (int j = 0; j < dimsCount; j++) {
                for (int i = 0; i < numVectors; i++) {
                    segment.set(ValueLayout.JAVA_FLOAT, offsets[j] + i * 4L, data[j][i]);
                }
            }
            for (int i = 0; i < numVectors; i++) {
                segment.set(ValueLayout.JAVA_BYTE, fraudOffset + i, (byte) (fraud[i] ? 1 : 0));
            }

            LinearScanEngine engine = new LinearScanEngine(segment, numVectors, dimsCount);
            
            float[] query = new float[dimsCount];
            for (int i = 0; i < dimsCount; i++) {
                query[i] = rnd.nextFloat() * 10000;
            }

            KnnQueue queue = new KnnQueue(5);
            engine.search(query, queue, Long.MAX_VALUE);

            // Manual scalar search for verification
            KnnQueue expectedQueue = new KnnQueue(5);
            for (int i = 0; i < numVectors; i++) {
                float sumSq = 0;
                for (int j = 0; j < dimsCount; j++) {
                    float diff = query[j] - data[j][i];
                    sumSq += diff * diff;
                }
                expectedQueue.insert(i, sumSq); 
            }

            assertEquals(expectedQueue.size(), queue.size());
            for (int i = 0; i < expectedQueue.size(); i++) {
                assertEquals(expectedQueue.getIndices()[i], queue.getIndices()[i], "Mismatch at rank " + i);
                assertEquals(expectedQueue.getDistances()[i], queue.getDistances()[i], 50.0f, "Distance mismatch at rank " + i);
                assertEquals(fraud[expectedQueue.getIndices()[i]], engine.isFraud(expectedQueue.getIndices()[i]), "Fraud mismatch");
            }
        }
    }
}
