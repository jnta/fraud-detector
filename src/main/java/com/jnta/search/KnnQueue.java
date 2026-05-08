package com.jnta.search;

import java.util.Arrays;

public class KnnQueue {
    private final float[] distances;
    private final int[] indices;
    private final int k;
    private int count;

    public KnnQueue(int k) {
        this.k = k;
        this.distances = new float[k];
        this.indices = new int[k];
        this.count = 0;
        Arrays.fill(distances, Float.MAX_VALUE);
    }

    public void insert(int index, float distance) {
        if (count < k) {
            insertAt(count, index, distance);
            count++;
        } else if (distance < distances[k - 1]) {
            insertAt(k - 1, index, distance);
        }
    }

    private void insertAt(int startPos, int index, float distance) {
        int i = startPos;
        while (i > 0 && distances[i - 1] > distance) {
            distances[i] = distances[i - 1];
            indices[i] = indices[i - 1];
            i--;
        }
        distances[i] = distance;
        indices[i] = index;
    }

    public float worstDistance() {
        return count < k ? Float.MAX_VALUE : distances[k - 1];
    }

    public float[] getDistances() {
        return distances;
    }

    public int[] getIndices() {
        return indices;
    }
    
    public int size() {
        return count;
    }

    public void clear() {
        count = 0;
        Arrays.fill(distances, Float.MAX_VALUE);
    }
}
