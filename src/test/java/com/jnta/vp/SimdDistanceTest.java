package com.jnta.vp;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimdDistanceTest {

    @Test
    @DisplayName("SIMD distance should match scalar implementation for 14D vectors")
    void testSimdMatchesScalar() {
        int dims = 14;
        float[] a = new float[dims];
        float[] b = new float[dims];
        Random rnd = new Random(42);
        
        for (int i = 0; i < 500; i++) {
            for (int d = 0; d < dims; d++) {
                a[d] = rnd.nextFloat();
                b[d] = rnd.nextFloat();
            }
            
            float scalarDist = scalarDistance(a, b);
            float simdDist = SimdDistance.compute(a, b);
            
            Assertions.assertEquals(scalarDist, simdDist, 1e-5f);
        }
    }

    private float scalarDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }
}
