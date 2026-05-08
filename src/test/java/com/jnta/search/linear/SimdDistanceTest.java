package com.jnta.search.linear;

import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimdDistanceTest {

    @Test
    @DisplayName("SIMD distance should match scalar implementation for short[] vectors")
    void testShortSimdMatchesScalar() {
        int dims = 14;
        short[] query = new short[dims];
        short[] vectors = new short[dims * 10];
        Random rnd = new Random(42);
        
        for (int i = 0; i < 10; i++) {
            for (int d = 0; d < dims; d++) {
                query[d] = (short) rnd.nextInt(1000);
                vectors[i * dims + d] = (short) rnd.nextInt(1000);
            }
            
            long scalarDist = scalarDistanceShort(query, vectors, i * dims, dims);
            long simdDist = SimdDistance.compute(query, vectors, i * dims);
            
            Assertions.assertEquals(scalarDist, simdDist);
        }
    }

    @Test
    @DisplayName("SIMD distance should match scalar implementation for 7D short[] vectors")
    void testShortSimdMatchesScalar7D() {
        int dims = 7;
        short[] query = new short[dims];
        short[] vectors = new short[dims * 10];
        Random rnd = new Random(42);
        
        for (int i = 0; i < 10; i++) {
            for (int d = 0; d < dims; d++) {
                query[d] = (short) rnd.nextInt(1000);
                vectors[i * dims + d] = (short) rnd.nextInt(1000);
            }
            
            long scalarDist = scalarDistanceShort(query, vectors, i * dims, dims);
            long simdDist = SimdDistance.compute7D(query, vectors, i * dims);
            
            Assertions.assertEquals(scalarDist, simdDist);
        }
    }

    private long scalarDistanceShort(short[] a, short[] b, int offset, int dims) {
        long sum = 0;
        for (int i = 0; i < dims; i++) {
            long diff = (long) a[i] - (long) b[offset + i];
            sum += diff * diff;
        }
        return sum;
    }
}
