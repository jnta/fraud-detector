package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

class SearchEngineTest {

    @Test
    void testSearchEngineInterface() {
        int dims = 2;
        List<float[]> vectors = new ArrayList<>();
        vectors.add(new float[]{0, 0});
        vectors.add(new float[]{1, 1});
        boolean[] labels = new boolean[]{false, true};

        // This will fail to compile initially because VpTree doesn't implement SearchEngine
        // and SearchEngine doesn't exist yet.
        SearchEngine engine = VpTree.build(vectors, labels);
        
        KnnQueue queue = new KnnQueue(1);
        engine.search(new float[]{0.9f, 0.9f}, queue);

        Assertions.assertEquals(1, queue.getIndices()[0]);
        Assertions.assertTrue(engine.isFraud(1));
        Assertions.assertFalse(engine.isFraud(0));
        
        engine.close();
    }
}
