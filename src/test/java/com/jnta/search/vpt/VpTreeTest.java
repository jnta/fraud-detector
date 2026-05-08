package com.jnta.search.vpt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class VpTreeTest {

    @Test
    @DisplayName("VpTree should support binary serialization and reconstruction")
    void testRoundTrip() throws IOException {
        int numVectors = 100;
        int dims = 7;
        List<float[]> vectors = new ArrayList<>();
        boolean[] labels = new boolean[numVectors];
        for (int i = 0; i < numVectors; i++) {
            float[] v = new float[dims];
            v[0] = i; // simple distinct values
            vectors.add(v);
            labels[i] = (i % 2 == 0);
        }

        VpTree tree = VpTreeBuilder.build(vectors, labels);
        Path tempFile = Files.createTempFile("vptree", ".vpt");
        VpTreeIO.save(tree, tempFile);

        VpTree loadedTree = VpTreeIO.load(tempFile);
        Assertions.assertEquals(numVectors, loadedTree.size());
        
        // Verify 16-bit quantization roundtrip error is small
        float[] original = vectors.get(0);
        float[] loaded = loadedTree.getVector(0);
        for (int i = 0; i < dims; i++) {
            Assertions.assertEquals(original[i], loaded[i], 0.5f); // Quantization error expected
        }
        
        Assertions.assertTrue(loadedTree.isFraud(0));
        Assertions.assertFalse(loadedTree.isFraud(1));
        
        Files.deleteIfExists(tempFile);
    }
}
