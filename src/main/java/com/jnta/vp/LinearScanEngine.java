package com.jnta.vp;

import jdk.incubator.vector.*;
import java.util.Arrays;

/**
 * High-performance linear scan engine using Vertical SIMD.
 */
public class LinearScanEngine implements SearchEngine {
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;
    
    private final float[][] dims; // Transposed: [D][size]
    private final boolean[] fraud;
    private final int size;
    private final int dCount;

    public LinearScanEngine(float[][] data, boolean[] fraud) {
        this.size = data[0].length;
        this.dCount = data.length;
        this.fraud = fraud;
        this.dims = data;
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
                FloatVector v = FloatVector.fromArray(F_SPECIES, dims[d], i);
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
                float diff = query[d] - dims[d][i];
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
        return fraud[index];
    }

    @Override
    public void close() {
        // No resources to release
    }
}
