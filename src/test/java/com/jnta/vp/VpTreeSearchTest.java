package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class VpTreeSearchTest {

    @Test
    void testSearchDoesNotThrowOOB() {
        int numVectors = 1000;
        int dims = 14; 
        List<float[]> vectors = new ArrayList<>();
        boolean[] labels = new boolean[numVectors];
        Random rand = new Random(42);

        for (int i = 0; i < numVectors; i++) {
            float[] v = new float[dims];
            for (int j = 0; j < dims; j++) v[j] = rand.nextFloat();
            vectors.add(v);
            labels[i] = rand.nextBoolean();
        }

        VpTree tree = VpTree.build(vectors, labels);
        
        float[] query = new float[dims];
        for (int j = 0; j < dims; j++) query[j] = rand.nextFloat();

        KnnQueue queue = new KnnQueue(5);
        // This should not throw IndexOutOfBoundsException
        Assertions.assertDoesNotThrow(() -> tree.search(query, queue));
    }

    @Test
    void testSearchWithEdgeCaseDimensions() {
        // Test with dimensions that might trigger SIMD alignment issues or tail loop issues
        int[] testDims = {1, 4, 7, 8, 9, 15, 16, 17, 31, 32, 33};
        for (int dims : testDims) {
            int numVectors = 50;
            List<float[]> vectors = new ArrayList<>();
            boolean[] labels = new boolean[numVectors];
            Random rand = new Random(dims);

            for (int i = 0; i < numVectors; i++) {
                float[] v = new float[dims];
                for (int j = 0; j < dims; j++) v[j] = rand.nextFloat();
                vectors.add(v);
                labels[i] = i % 2 == 0;
            }

            VpTree tree = VpTree.build(vectors, labels);
            float[] query = new float[dims];
            for (int j = 0; j < dims; j++) query[j] = rand.nextFloat();

            KnnQueue queue = new KnnQueue(3);
            Assertions.assertDoesNotThrow(() -> tree.search(query, queue), "Failed for dims=" + dims);
        }
    }

    @Test
    void testSearchWithDimensionMismatchThrows() {
        int numVectors = 10;
        int dims = 14;
        List<float[]> vectors = new ArrayList<>();
        boolean[] labels = new boolean[numVectors];
        for (int i = 0; i < numVectors; i++) {
            vectors.add(new float[dims]);
            labels[i] = false;
        }
        VpTree tree = VpTree.build(vectors, labels);

        float[] wrongQuery = new float[dims + 1];
        KnnQueue queue = new KnnQueue(1);
        
        // This is expected to throw either IllegalArgumentException (if we add the check)
        // or IndexOutOfBoundsException (current behavior)
        Assertions.assertThrows(Exception.class, () -> tree.search(wrongQuery, queue));
    }
}
