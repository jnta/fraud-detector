package com.jnta.vp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

public class LinearScanEngineTest {

    @Test
    public void testAccuracyAgainstScalar() {
        int numVectors = 1000;
        int dimsCount = 7;
        float[][] data = new float[dimsCount][numVectors];
        boolean[] fraud = new boolean[numVectors];
        Random rnd = new Random(42);

        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < dimsCount; j++) {
                data[j][i] = rnd.nextFloat() * 10000;
            }
            fraud[i] = rnd.nextBoolean();
        }

        LinearScanEngine engine = new LinearScanEngine(data, fraud);
        
        float[] query = new float[dimsCount];
        for (int i = 0; i < dimsCount; i++) {
            query[i] = rnd.nextFloat() * 10000;
        }

        KnnQueue queue = new KnnQueue(5);
        engine.search(query, queue);

        // Manual scalar search for verification
        KnnQueue expectedQueue = new KnnQueue(5);
        for (int i = 0; i < numVectors; i++) {
            float sumSq = 0;
            for (int j = 0; j < dimsCount; j++) {
                float diff = query[j] - data[j][i];
                sumSq += diff * diff;
            }
            expectedQueue.insert(i, sumSq); 
        }

        assertEquals(expectedQueue.size(), queue.size());
        for (int i = 0; i < expectedQueue.size(); i++) {
            assertEquals(expectedQueue.getIndices()[i], queue.getIndices()[i], "Mismatch at rank " + i);
            assertEquals(expectedQueue.getDistances()[i], queue.getDistances()[i], 0.1f, "Distance mismatch at rank " + i);
        }
    }
}
