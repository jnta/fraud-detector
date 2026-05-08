package com.jnta.search.vpt;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class VpTreeBuilder {

    public static VpTree build(List<float[]> vectors, boolean[] labels) {
        float[] bounds = Preprocessor.findGlobalBounds(vectors);
        float min = bounds[0];
        float max = bounds[1];
        List<short[]> quantized = new ArrayList<>(vectors.size());
        for (float[] v : vectors) {
            quantized.add(Preprocessor.quantize16Bit(v, min, max));
        }
        return buildFromQuantized(quantized, labels, min, max);
    }

    public static VpTree buildFromQuantized(List<short[]> vectors, boolean[] labels, float min, float max) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("No vectors");
        int dims = vectors.get(0).length;
        int size = vectors.size();
        
        float[] muQArr = new float[size];
        int[] leftArr = new int[size];
        int[] rightArr = new int[size];
        BitSet labelSet = new BitSet(size);
        short[] vectorArr = new short[size * dims];
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        Node root = buildRecursive(vectors, labels, indices, 0, size);
        writeBfs(root, muQArr, leftArr, rightArr, labelSet, vectorArr, dims);
        
        return new VpTree(dims, size, muQArr, leftArr, rightArr, labelSet, vectorArr, min, max);
    }

    private static class Node {
        final long muSq;
        final byte label;
        final short[] vp;
        Node left;
        Node right;
        int bfsIdx = -1;

        Node(long muSq, byte label, short[] vp) {
            this.muSq = muSq;
            this.label = label;
            this.vp = vp;
        }
    }

    private static Node buildRecursive(List<short[]> vectors, boolean[] labels, int[] indices, int start, int end) {
        if (start >= end) return null;
        
        int vpIdx = indices[start];
        short[] vp = vectors.get(vpIdx);
        byte label = (byte) (labels[vpIdx] ? 1 : 0);
        
        if (end - start == 1) {
            return new Node(0L, label, vp);
        }

        long[] distances = new long[end - start - 1];
        for (int i = start + 1; i < end; i++) {
            distances[i - (start + 1)] = distance(vp, vectors.get(indices[i]));
        }

        java.util.Arrays.sort(distances);
        long muSq = distances[distances.length / 2];

        int mid = partition(vectors, indices, start + 1, end, vp, muSq);
        
        Node node = new Node(muSq, label, vp);
        node.left = buildRecursive(vectors, labels, indices, start + 1, mid);
        node.right = buildRecursive(vectors, labels, indices, mid, end);
        
        return node;
    }

    private static void writeBfs(Node root, float[] muQ, int[] left, int[] right, BitSet labels, short[] vectors, int dims) {
        if (root == null) return;
        
        Queue<Node> queue = new LinkedList<>();
        List<Node> bfsNodes = new ArrayList<>();
        
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            node.bfsIdx = bfsNodes.size();
            bfsNodes.add(node);
            if (node.left != null) queue.add(node.left);
            if (node.right != null) queue.add(node.right);
        }
        
        for (int i = 0; i < bfsNodes.size(); i++) {
            Node node = bfsNodes.get(i);
            muQ[i] = (float) Math.sqrt(node.muSq);
            left[i] = (node.left != null) ? node.left.bfsIdx : -1;
            right[i] = (node.right != null) ? node.right.bfsIdx : -1;
            if (node.label == 1) labels.set(i);
            System.arraycopy(node.vp, 0, vectors, i * dims, dims);
        }
    }

    private static long distance(short[] a, short[] b) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            long diff = (long) a[i] - (long) b[i];
            sum += diff * diff;
        }
        return sum;
    }

    private static int partition(List<short[]> vectors, int[] indices, int start, int end, short[] vp, long muSq) {
        int i = start;
        int j = end - 1;
        while (i <= j) {
            while (i <= j && distance(vp, vectors.get(indices[i])) <= muSq) i++;
            while (i <= j && distance(vp, vectors.get(indices[j])) > muSq) j--;
            if (i < j) {
                int tmp = indices[i];
                indices[i] = indices[j];
                indices[j] = tmp;
            }
        }
        return i;
    }
}
