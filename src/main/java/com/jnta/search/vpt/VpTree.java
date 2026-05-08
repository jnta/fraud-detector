package com.jnta.search.vpt;

import com.jnta.search.linear.LinearScanEngine;
import java.util.BitSet;

/**
 * Value object representing a VP-Tree structure stored on the heap.
 */
public class VpTree {
    public final int dims;
    public final int size;
    public final float[] muQ;
    public final int[] left;
    public final int[] right;
    public final BitSet labels;
    public final short[] vectors;
    public final float globalMin;
    public final float globalMax;

    public VpTree(int dims, int size, float[] muQ, int[] left, int[] right, BitSet labels, short[] vectors, float globalMin, float globalMax) {
        this.dims = dims;
        this.size = size;
        this.muQ = muQ;
        this.left = left;
        this.right = right;
        this.labels = labels;
        this.vectors = vectors;
        this.globalMin = globalMin;
        this.globalMax = globalMax;
    }

    public int size() {
        return size;
    }

    public boolean isFraud(int index) {
        return labels.get(index);
    }

    public float[] getVector(int index) {
        float[] v = new float[dims];
        int offset = index * dims;
        float range = globalMax - globalMin;
        for (int d = 0; d < dims; d++) {
            short s = vectors[offset + d];
            float normalized = (s + 32768) / 65535.0f;
            v[d] = normalized * range + globalMin;
        }
        return v;
    }

    public LinearScanEngine toLinearScan() {
        float[][] data = new float[dims][size];
        boolean[] fraud = new boolean[size];
        float range = globalMax - globalMin;
        for (int i = 0; i < size; i++) {
            fraud[i] = labels.get(i);
            int offset = i * dims;
            for (int d = 0; d < dims; d++) {
                short s = vectors[offset + d];
                float normalized = (s + 32768) / 65535.0f;
                data[d][i] = normalized * range + globalMin;
            }
        }
        return new LinearScanEngine(data, fraud);
    }
}
