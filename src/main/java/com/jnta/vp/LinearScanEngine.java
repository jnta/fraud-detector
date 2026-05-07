package com.jnta.vp;

import jdk.incubator.vector.*;
import java.util.Arrays;

/**
 * High-performance linear scan engine using Vertical SIMD.
 */
public class LinearScanEngine implements SearchEngine {
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;
    
    private final short[][] dims; // Transposed: [7][size]
    private final boolean[] fraud;
    private final int size;
    private final float min;
    private final float max;

    public LinearScanEngine(short[][] data, boolean[] fraud, float min, float max) {
        this.size = data.length;
        this.fraud = fraud;
        this.min = min;
        this.max = max;
        
        // Transpose data for Vertical SIMD
        this.dims = new short[7][size];
        for (int i = 0; i < size; i++) {
            for (int d = 0; d < 7; d++) {
                dims[d][i] = data[i][d];
            }
        }
    }

    @Override
    public void search(float[] query, KnnQueue queue) {
        short[] qShort = Preprocessor.quantize16Bit(query, min, max);
        
        // Pre-broadcast query dimensions
        LongVector[] qVecs = new LongVector[7];
        for (int d = 0; d < 7; d++) {
            qVecs[d] = LongVector.broadcast(L_SPECIES, qShort[d]);
        }
        
        int laneWidth = S_SPECIES.length();
        int numLongs = laneWidth / L_SPECIES.length();
        int upperBound = S_SPECIES.loopBound(size);
        
        LongVector[] acc = new LongVector[numLongs];
        
        for (int i = 0; i < upperBound; i += laneWidth) {
            for (int j = 0; j < numLongs; j++) acc[j] = LongVector.zero(L_SPECIES);
            
            // Explicitly unroll the 7 dimensions for max performance
            for (int d = 0; d < 7; d++) {
                ShortVector v = ShortVector.fromArray(S_SPECIES, dims[d], i);
                LongVector qv = qVecs[d];
                for (int j = 0; j < numLongs; j++) {
                    LongVector lv = (LongVector) v.convert(VectorOperators.S2L, j);
                    LongVector diff = lv.sub(qv);
                    acc[j] = acc[j].add(diff.mul(diff));
                }
            }
            
            // Only insert into queue if at least one vector in the lane is potentially a candidate
            float worst = queue.worstDistance();
            for (int j = 0; j < numLongs; j++) {
                // Potential optimization: check if any lane in acc[j] < worst
                // LongVector.compare(LT, worst) returns a mask
                if (acc[j].reduceLanes(VectorOperators.MIN) < (long) worst) {
                    for (int k = 0; k < L_SPECIES.length(); k++) {
                        long dist = acc[j].lane(k);
                        if (dist < worst) {
                            queue.insert(i + j * L_SPECIES.length() + k, (float) dist);
                            worst = queue.worstDistance();
                        }
                    }
                }
            }
        }
        
        // Tail
        for (int i = upperBound; i < size; i++) {
            long sumSq = 0;
            for (int d = 0; d < 7; d++) {
                long diff = (long) qShort[d] - dims[d][i];
                sumSq += diff * diff;
            }
            if (sumSq < queue.worstDistance()) {
                queue.insert(i, (float) sumSq);
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
