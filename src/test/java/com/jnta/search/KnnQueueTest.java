package com.jnta.search;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnnQueueTest {

    @Test
    @DisplayName("KnnQueue should maintain top 5 smallest distances")
    void testKnnQueue() {
        KnnQueue queue = new KnnQueue(5);
        queue.insert(0, 10.0f);
        queue.insert(1, 5.0f);
        queue.insert(2, 2.0f);
        queue.insert(3, 8.0f);
        queue.insert(4, 1.0f);
        queue.insert(5, 3.0f); // Should evict 10.0f

        float[] distances = queue.getDistances();
        int[] indices = queue.getIndices();

        // Should be sorted: 1.0, 2.0, 3.0, 5.0, 8.0
        Assertions.assertEquals(1.0f, distances[0]);
        Assertions.assertEquals(4, indices[0]);
        
        Assertions.assertEquals(2.0f, distances[1]);
        Assertions.assertEquals(2, indices[1]);
        
        Assertions.assertEquals(3.0f, distances[2]);
        Assertions.assertEquals(5, indices[2]);
        
        Assertions.assertEquals(5.0f, distances[3]);
        Assertions.assertEquals(1, indices[3]);
        
        Assertions.assertEquals(8.0f, distances[4]);
        Assertions.assertEquals(3, indices[4]);
        
        Assertions.assertEquals(8.0f, queue.worstDistance());
    }
}
