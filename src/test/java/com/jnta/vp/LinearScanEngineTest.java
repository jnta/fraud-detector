package com.jnta.vp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

public class LinearScanEngineTest {

    @Test
    public void testAccuracyAgainstScalar() {
        int numVectors = 1000;
        int dims = 7;
        short[][] data = new short[numVectors][dims];
        boolean[] fraud = new boolean[numVectors];
        Random rnd = new Random(42);

        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < dims; j++) {
                data[i][j] = (short) rnd.nextInt(10000);
            }
            fraud[i] = rnd.nextBoolean();
        }

        LinearScanEngine engine = new LinearScanEngine(data, fraud, 0.0f, 10000.0f);
        
        float[] query = new float[dims];
        for (int i = 0; i < dims; i++) {
            query[i] = (float) rnd.nextInt(10000);
        }

        KnnQueue queue = new KnnQueue(5);
        engine.search(query, queue);

        // Manual scalar search for verification
        short[] qShort = Preprocessor.quantize16Bit(query, 0.0f, 10000.0f);
        KnnQueue expectedQueue = new KnnQueue(5);
        for (int i = 0; i < numVectors; i++) {
            long sumSq = 0;
            for (int j = 0; j < dims; j++) {
                long diff = (long) qShort[j] - data[i][j];
                sumSq += diff * diff;
            }
            expectedQueue.insert(i, (float) sumSq); 
        }

        assertEquals(expectedQueue.size(), queue.size());
        for (int i = 0; i < expectedQueue.size(); i++) {
            assertEquals(expectedQueue.getIndices()[i], queue.getIndices()[i], "Mismatch at rank " + i);
            assertEquals(expectedQueue.getDistances()[i], queue.getDistances()[i], 0.01f, "Distance mismatch at rank " + i);
        }
    }
}
