package com.jnta.vp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimdDistanceQuantizedTest {

    @Test
    @DisplayName("Quantized SIMD distance should match scalar implementation")
    void testQuantizedSimdMatchesScalar() {
        int dims = 64;
        byte[] query = new byte[dims];
        byte[] data = new byte[dims];
        Random rnd = new Random(42);
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(dims);
            
            for (int i = 0; i < 100; i++) {
                for (int d = 0; d < dims; d++) {
                    query[d] = (byte) (rnd.nextInt(256) - 128);
                    data[d] = (byte) (rnd.nextInt(256) - 128);
                }
                segment.copyFrom(MemorySegment.ofArray(data));
                
                int scalarDist = scalarQuantizedDistance(query, data);
                int simdDist = SimdDistance.computeQuantized(query, segment, 0);
                
                Assertions.assertEquals(scalarDist, simdDist, "Distance mismatch at iteration " + i);
            }
        }
    }

    @Test
    @DisplayName("Quantized distance should be proportional to float distance")
    void testProportionality() {
        int dims = 64;
        float[] a = new float[dims];
        float[] b = new float[dims];
        byte[] qa = new byte[dims];
        byte[] qb = new byte[dims];
        Random rnd = new Random(42);
        
        // Global quantization params (mock)
        float min = -1.0f;
        float max = 1.0f;
        float range = max - min;
        float scale = range / 255.0f;

        for (int i = 0; i < 100; i++) {
            for (int d = 0; d < dims; d++) {
                a[d] = rnd.nextFloat() * 2.0f - 1.0f; // [-1, 1]
                b[d] = rnd.nextFloat() * 2.0f - 1.0f;
                
                qa[d] = (byte) Math.round((a[d] - min) / scale - 128);
                qb[d] = (byte) Math.round((b[d] - min) / scale - 128);
            }
            
            float floatDist = scalarFloatDistance(a, b);
            int intDist = scalarQuantizedDistance(qa, qb);
            
            // Proportionality check:
            // intDist is approx (floatDist / scale^2)
            double expectedIntDist = floatDist / (scale * scale);
            double error = Math.abs(intDist - expectedIntDist) / expectedIntDist;
            
            // With 8-bit quantization, error should be relatively low (e.g. < 5% for 64D random vectors)
            Assertions.assertTrue(error < 0.05, "Proportionality error too high: " + error + " at iteration " + i);
        }
    }

    private float scalarFloatDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    private int scalarQuantizedDistance(byte[] a, byte[] b) {
        int sum = 0;
        for (int i = 0; i < a.length; i++) {
            int diff = (int) a[i] - (int) b[i];
            sum += diff * diff;
        }
        return sum;
    }
}
