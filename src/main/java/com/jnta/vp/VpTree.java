package com.jnta.vp;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class VpTree implements SearchEngine {
    final int dims;
    final int size;
    final float[] muQ;
    final int[] left;
    final int[] right;
    final java.util.BitSet labels;
    final short[] vectors;
    private final float globalMin;
    private final float globalMax;

    private VpTree(int dims, int size, float[] muQ, int[] left, int[] right, java.util.BitSet labels, short[] vectors, float globalMin, float globalMax) {
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



    public static VpTree build(List<float[]> vectors, boolean[] labels) {
        float[] bounds = Preprocessor.findGlobalBounds(vectors);
        float min = bounds[0];
        float max = bounds[1];
        List<short[]> quantized = new ArrayList<>(vectors.size());
        for (float[] v : vectors) {
            quantized.add(Preprocessor.quantize16Bit(v, min, max));
        }
        return build(quantized, labels, min, max);
    }

    public static VpTree build(List<short[]> vectors, boolean[] labels, float min, float max) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("No vectors");
        int dims = vectors.get(0).length;
        int size = vectors.size();
        
        float[] muQArr = new float[size];
        int[] leftArr = new int[size];
        int[] rightArr = new int[size];
        java.util.BitSet labelSet = new java.util.BitSet(size);
        short[] vectorArr = new short[size * dims];
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        try {
            Node root = buildRecursive(vectors, labels, indices, 0, size);
            writeBfs(root, muQArr, leftArr, rightArr, labelSet, vectorArr, dims);
        } catch (Exception e) {
            System.err.println("Build failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
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

    private static void writeBfs(Node root, float[] muQ, int[] left, int[] right, java.util.BitSet labels, short[] vectors, int dims) {
        if (root == null) return;
        
        java.util.Queue<Node> queue = new java.util.LinkedList<>();
        List<Node> bfsNodes = new java.util.ArrayList<>();
        
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

    public void save(Path path) throws IOException {
        long nodeSize = (20L + dims * 2L + 7) & ~7L;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(dims);
            header.putInt(size);
            header.putFloat(globalMin);
            header.putFloat(globalMax);
            header.flip();
            channel.write(header);
            
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) nodeSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < size; index++) {
                buffer.clear();
                // Convert muQ back to muSq for saving (to maintain file compatibility)
                long muSqValue = (long) (muQ[index] * muQ[index]);
                buffer.putLong(muSqValue);
                buffer.putInt(left[index]);
                buffer.putInt(right[index]);
                buffer.put(labels.get(index) ? (byte) 1 : (byte) 0);
                
                // Padding
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                
                for (int i = 0; i < dims; i++) {
                    buffer.putShort(vectors[index * dims + i]);
                }
                
                while (buffer.position() < nodeSize) {
                    buffer.put((byte) 0);
                }
                buffer.flip();
                channel.write(buffer);
            }
        }
    }

    public static VpTree load(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();
            int dims = header.getInt();
            int size = header.getInt();
            float min = header.getFloat();
            float max = header.getFloat();
            
            long nodeSize = (20L + dims * 2L + 7) & ~7L;
            
            float[] muQ = new float[size];
            int[] left = new int[size];
            int[] right = new int[size];
            java.util.BitSet labels = new java.util.BitSet(size);
            short[] vectors = new short[size * dims];
            
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) nodeSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < size; i++) {
                buffer.clear();
                channel.read(buffer);
                buffer.flip();
                
                long muSqVal = buffer.getLong();
                muQ[i] = (float) Math.sqrt(muSqVal);
                left[i] = buffer.getInt();
                right[i] = buffer.getInt();
                if (buffer.get() == 1) labels.set(i);
                
                // Skip padding (3 bytes + possibly more if dims are small)
                buffer.position(20);
                
                for (int d = 0; d < dims; d++) {
                    vectors[i * dims + d] = buffer.getShort();
                }
            }
            
            return new VpTree(dims, size, muQ, left, right, labels, vectors, min, max);
        }
    }

    public void warmup() {
        // No-op for heap arrays
    }
    
    public void close() {
        // No-op for heap arrays
    }

    public int size() {
        return size;
    }

    public float[] getVector(int index) {
        float[] v = new float[dims];
        float range = globalMax - globalMin;
        int offset = index * dims;
        for (int i = 0; i < dims; i++) {
            short s = vectors[offset + i];
            float normalized = (s + 32768) / 65535.0f;
            v[i] = normalized * range + globalMin;
        }
        return v;
    }

    public boolean isFraud(int index) {
        return labels.get(index);
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

    private static final ThreadLocal<int[]> NODE_STACK = ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<float[]> BOUND_STACK_Q = ThreadLocal.withInitial(() -> new float[1024]);

    public void search(float[] query, KnnQueue queue) {
        if (query.length != dims) {
            throw new IllegalArgumentException("Query dimension mismatch");
        }

        short[] quantizedQuery = Preprocessor.quantize16Bit(query, globalMin, globalMax);
        float scale = (globalMax - globalMin) / 65535.0f;
        float invScale = 1.0f / scale;
        float scaleSq = scale * scale;

        int[] nodeStack = NODE_STACK.get();
        float[] boundStackQ = BOUND_STACK_Q.get();
        int top = 0;

        float lastWorstSq = -1.0f;
        float lastWorstQ = 0.0f;

        nodeStack[top] = 0;
        boundStackQ[top] = 0.0f;
        top++;

        while (top > 0) {
            top--;
            int nodeIdx = nodeStack[top];
            float boundQ = boundStackQ[top];

            if (nodeIdx == -1) continue;
            
            // Fast prune using quantized bound
            if (boundQ > lastWorstQ) continue;

            int leftIdx = left[nodeIdx];
            int rightIdx = right[nodeIdx];
            long quantizedDSq;
            
            if (dims == 7) {
                quantizedDSq = SimdDistance.compute7D(quantizedQuery, vectors, nodeIdx * 7);
            } else {
                quantizedDSq = SimdDistance.compute(quantizedQuery, vectors, nodeIdx * dims);
            }
            
            float dSq = quantizedDSq * scaleSq;
            queue.insert(nodeIdx, dSq);
            
            float worstSq = queue.worstDistance();
            if (worstSq != lastWorstSq) {
                lastWorstSq = worstSq;
                lastWorstQ = (worstSq == Float.MAX_VALUE) ? Float.MAX_VALUE : (float) Math.sqrt(worstSq) * invScale;
            }

            float dQ = SqrtLookupTable.get(quantizedDSq);
            float muQVal = muQ[nodeIdx];

            if (dQ <= muQVal) {
                // Inside inner ball: Left is closer
                float distToBoundaryQ = muQVal - dQ;
                
                if (distToBoundaryQ <= lastWorstQ) {
                    nodeStack[top] = rightIdx;
                    boundStackQ[top] = distToBoundaryQ;
                    top++;
                }

                nodeStack[top] = leftIdx;
                boundStackQ[top] = 0.0f;
                top++;
            } else {
                // Outside inner ball: Right is closer
                float distToBoundaryQ = dQ - muQVal;
                
                if (distToBoundaryQ <= lastWorstQ) {
                    nodeStack[top] = leftIdx;
                    boundStackQ[top] = distToBoundaryQ;
                    top++;
                }

                nodeStack[top] = rightIdx;
                boundStackQ[top] = 0.0f;
                top++;
            }
        }
    }
}
