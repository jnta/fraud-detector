package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

class HotNodeCacheTest {

    @Test
    void testCacheDataConsistency() {
        int numVectors = 1000;
        int dims = 7;
        List<float[]> vectors = new ArrayList<>();
        boolean[] labels = new boolean[numVectors];
        for (int i = 0; i < numVectors; i++) {
            float[] v = new float[dims];
            for (int d = 0; d < dims; d++) v[d] = (float) Math.random();
            vectors.add(v);
            labels[i] = (i % 2 == 0);
        }

        VpTree tree = VpTree.build(vectors, labels);
        int cacheSize = 100;
        HotNodeCache cache = new HotNodeCache(tree, cacheSize);
        tree.setHotNodeCache(cache);

        Assertions.assertEquals(cacheSize, cache.getCapacity());

        // Verify that search with cache gives same results as search without cache
        float[] query = vectors.get(500);
        KnnQueue q1 = new KnnQueue(10);
        tree.search(query, q1);

        // Disable cache
        tree.setHotNodeCache(null);
        KnnQueue q2 = new KnnQueue(10);
        tree.search(query, q2);

        Assertions.assertEquals(q1.size(), q2.size());
        Assertions.assertArrayEquals(q1.getDistances(), q2.getDistances(), 1e-6f);
        Assertions.assertArrayEquals(q1.getIndices(), q2.getIndices());
    }
}
