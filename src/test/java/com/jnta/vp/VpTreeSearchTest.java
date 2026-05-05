package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

class VpTreeSearchTest {

    @Test
    void searchShouldReturnNearestNeighbors() {
        int dims = 2;
        List<float[]> vectors = List.of(
            new float[]{1.0f, 1.0f},
            new float[]{2.0f, 2.0f},
            new float[]{10.0f, 10.0f},
            new float[]{1.1f, 1.1f}
        );
        boolean[] labels = new boolean[]{false, false, true, false};

        VpTree tree = VpTree.build(vectors, labels);
        KnnQueue queue = new KnnQueue(2);
        
        tree.search(new float[]{1.05f, 1.05f}, queue);
        
        Assertions.assertEquals(2, queue.size());
        float[] distances = queue.getDistances();
        // Distance from [1.05, 1.05] to [1.0, 1.0] is sqrt(0.05^2 + 0.05^2) = 0.0707
        // Distance from [1.05, 1.05] to [1.1, 1.1] is sqrt(0.05^2 + 0.05^2) = 0.0707
        Assertions.assertEquals(0.0707f, distances[0], 0.001f);
        Assertions.assertEquals(0.0707f, distances[1], 0.001f);
    }

    @Test
    void searchShouldHandleDeepTrees() {
        int dims = 2;
        int numVectors = 1000;
        List<float[]> vectors = new ArrayList<>();
        boolean[] labels = new boolean[numVectors];
        for (int i = 0; i < numVectors; i++) {
            vectors.add(new float[]{ (float)i, (float)i });
        }

        VpTree tree = VpTree.build(vectors, labels);
        KnnQueue queue = new KnnQueue(1);
        
        tree.search(new float[]{ 500.1f, 500.1f }, queue);
        
        Assertions.assertEquals(1, queue.size());
        Assertions.assertEquals(0.1414f, queue.getDistances()[0], 0.001f);
    }
}
