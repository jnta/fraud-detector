package com.jnta.search.linear;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

public class SimdDistance {
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;

    /**
     * Optimized 7-dimensional squared distance for 16-bit quantized vectors.
     */
    public static long compute7D(short[] query, short[] array, int offset) {
        long d0 = (long) query[0] - array[offset];
        long d1 = (long) query[1] - array[offset + 1];
        long d2 = (long) query[2] - array[offset + 2];
        long d3 = (long) query[3] - array[offset + 3];
        long d4 = (long) query[4] - array[offset + 4];
        long d5 = (long) query[5] - array[offset + 5];
        long d6 = (long) query[6] - array[offset + 6];
        return (d0 * d0) + (d1 * d1) + (d2 * d2) + (d3 * d3) + (d4 * d4) + (d5 * d5) + (d6 * d6);
    }
    
    /**
     * Generic squared distance for any dimension (scalar).
     */
    public static long compute(short[] a, short[] b, int offset) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            long diff = (long) a[i] - b[offset + i];
            sum += diff * diff;
        }
        return sum;
    }
}
