package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

class VpTreeSearchTest {

    @Test
    void testSearchReturnsCorrectResults() {
        int dims = 2;
        List<float[]> vectors = new ArrayList<>();
        vectors.add(new float[]{0, 0});
        vectors.add(new float[]{1, 1});
        vectors.add(new float[]{2, 2});
        vectors.add(new float[]{3, 3});
        boolean[] labels = new boolean[]{false, true, false, true};

        VpTree tree = VpTree.build(vectors, labels);
        KnnQueue queue = new KnnQueue(1);
        tree.search(new float[]{0.1f, 0.1f}, queue);

        Assertions.assertEquals(0, queue.getIndices()[0]);
        // Distance is (0.1-0)^2 + (0.1-0)^2 = 0.01 + 0.01 = 0.02
        Assertions.assertEquals(0.02f, queue.getDistances()[0], 0.005f);
    }
}
