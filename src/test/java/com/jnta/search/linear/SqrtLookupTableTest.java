package com.jnta.search.linear;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SqrtLookupTableTest {
    @Test
    public void testBasicValues() {
        assertEquals(0.0f, SqrtLookupTable.get(0), 1e-6);
        assertEquals(1.0f, SqrtLookupTable.get(1), 1e-6);
        assertEquals(10.0f, SqrtLookupTable.get(100), 1e-6);
    }

    @Test
    public void testAccuracy() {
        for (int i = 0; i <= 512000; i += 1000) {
            assertEquals((float) Math.sqrt(i), SqrtLookupTable.get(i), 1e-6);
        }
    }

    @Test
    public void testOutOfBounds() {
        assertEquals((float) Math.sqrt(1000000), SqrtLookupTable.get(1000000), 1e-6);
        assertEquals(0.0f, SqrtLookupTable.get(-1), 1e-6);
    }
}
