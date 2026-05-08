package com.jnta.search.linear;

import com.jnta.search.KnnQueue;
import com.jnta.search.SearchEngine;
import jdk.incubator.vector.*;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * High-performance linear scan engine using Vertical SIMD.
 */
public class LinearScanEngine implements SearchEngine {
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;
    
    private final MemorySegment segment;
    private final int size;
    private final int dCount;
    private final long[] offsets;
    private final long fraudOffset;

    public LinearScanEngine(MemorySegment segment, int size, int dCount) {
        this.segment = segment;
        this.size = size;
        this.dCount = dCount;
        this.offsets = new long[dCount];
        
        long currentOffset = 0;
        for (int i = 0; i < dCount; i++) {
            offsets[i] = currentOffset;
            long dimSize = size * 4L;
            long padding = (64 - (dimSize % 64)) % 64;
            currentOffset += dimSize + padding;
        }
        this.fraudOffset = currentOffset;
    }

    @Override
    public void search(float[] query, KnnQueue queue) {
        FloatVector[] qVecs = new FloatVector[dCount];
        for (int d = 0; d < dCount; d++) {
            qVecs[d] = FloatVector.broadcast(F_SPECIES, query[d]);
        }
        
        int laneWidth = F_SPECIES.length();
        int upperBound = F_SPECIES.loopBound(size);
        
        for (int i = 0; i < upperBound; i += laneWidth) {
            FloatVector acc = FloatVector.zero(F_SPECIES);
            
            for (int d = 0; d < dCount; d++) {
                FloatVector v = FloatVector.fromMemorySegment(F_SPECIES, segment, offsets[d] + i * 4L, ByteOrder.nativeOrder());
                FloatVector diff = v.sub(qVecs[d]);
                acc = diff.fma(diff, acc);
            }
            
            float worst = queue.worstDistance();
            if (acc.reduceLanes(VectorOperators.MIN) < worst) {
                for (int k = 0; k < laneWidth; k++) {
                    float dist = acc.lane(k);
                    if (dist < worst) {
                        queue.insert(i + k, dist);
                        worst = queue.worstDistance();
                    }
                }
            }
        }
        
        // Tail
        for (int i = upperBound; i < size; i++) {
            float sumSq = 0;
            for (int d = 0; d < dCount; d++) {
                float diff = query[d] - segment.get(ValueLayout.JAVA_FLOAT, offsets[d] + i * 4L);
                sumSq += diff * diff;
            }
            if (sumSq < queue.worstDistance()) {
                queue.insert(i, sumSq);
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isFraud(int index) {
        return segment.get(ValueLayout.JAVA_BYTE, fraudOffset + index) != 0;
    }

    @Override
    public void close() {
        // Nothing to close since Arena manages the MemorySegment
    }
}
