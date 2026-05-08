package com.jnta.search.linear;

import com.jnta.search.KnnQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

class MappedSearchEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void testBasicSearch() throws IOException {
        int size = 1000;
        int dims = 14;
        Random rnd = new Random(42);
        
        short[][] blockA = new short[6][size];
        short[][] blockB = new short[8][size];
        boolean[] labels = new boolean[size];
        
        for (int i = 0; i < size; i++) {
            for (int d = 0; d < 6; d++) blockA[d][i] = (short) (rnd.nextInt(65536) - 32768);
            for (int d = 0; d < 8; d++) blockB[d][i] = (short) (rnd.nextInt(65536) - 32768);
            labels[i] = rnd.nextBoolean();
        }
        
        Path binPath = tempDir.resolve("test.bin");
        FlatIndexIO.save(size, blockA, blockB, labels, -1.0f, 1.0f, binPath);
        
        try (MappedSearchEngine engine = new MappedSearchEngine(binPath)) {
            float[] query = new float[dims];
            for (int i = 0; i < dims; i++) query[i] = 0.0f; // Simplified query
            
            KnnQueue queue = new KnnQueue(5);
            engine.search(query, queue);
            
            Assertions.assertEquals(5, queue.size());
            // Verify that distances are increasing
            float[] dists = queue.getDistances();
            for (int i = 1; i < 5; i++) {
                Assertions.assertTrue(dists[i] >= dists[i-1]);
            }
        }
    }
}
