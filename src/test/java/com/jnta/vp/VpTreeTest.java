package com.jnta.vp;

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
        int dims = 384; // standard for all-MiniLM-L6-v2
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < numVectors; i++) {
            float[] v = new float[dims];
            v[0] = i; // simple distinct values
            vectors.add(v);
        }

        VpTree tree = VpTree.build(vectors);
        Path tempFile = Files.createTempFile("vptree", ".vpt");
        tree.save(tempFile);

        VpTree loadedTree = VpTree.load(tempFile);
        Assertions.assertEquals(numVectors, loadedTree.size());
        // Verify a sample
        Assertions.assertArrayEquals(vectors.get(0), loadedTree.getVector(0), 0.001f);
        
        Files.deleteIfExists(tempFile);
    }
}
