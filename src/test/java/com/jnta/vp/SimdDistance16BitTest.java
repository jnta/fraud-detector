package com.jnta.vp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimdDistance16BitTest {

    @Test
    @DisplayName("16-bit distance should match scalar implementation")
    void test16BitMatchesScalar() {
        int dims = 14;
        short[] query = new short[dims];
        Random rnd = new Random(42);
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64, 8); 
            
            for (int i = 0; i < 1000; i++) {
                for (int d = 0; d < dims; d++) {
                    query[d] = (short) rnd.nextInt(10000);
                    segment.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), (long) d * 2, (short) rnd.nextInt(10000));
                }
                
                long expected = 0;
                for (int d = 0; d < dims; d++) {
                    long diff = (long) query[d] - (long) segment.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), (long) d * 2);
                    expected += diff * diff;
                }
                
                long actual = SimdDistance.compute16Bit(query, segment, 0);
                
                Assertions.assertEquals(expected, actual, "Distance mismatch at iteration " + i);
            }
        }
    }
}
