package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class VpTreeWarmupTest {
    @Test
    void testWarmupDoesNotCrash() throws IOException {
        int dims = 2;
        List<float[]> vectors = new ArrayList<>();
        vectors.add(new float[]{0, 0});
        boolean[] labels = new boolean[]{false};

        VpTree tree = VpTree.build(vectors, labels);
        tree.warmup();
    }
}
