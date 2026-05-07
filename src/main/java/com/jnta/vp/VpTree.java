package com.jnta.vp;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class VpTree implements SearchEngine {
    final int dims;
    final int size;
    final MemorySegment segment;
    final long nodeSize;
    private final float globalMin;
    private final float globalMax;
    private HotNodeCache cache;

    private final Arena arena;

    private VpTree(int dims, int size, MemorySegment segment, Arena arena, float globalMin, float globalMax) {
        this.dims = dims;
        this.size = size;
        this.segment = segment;
        this.nodeSize = (20L + dims * 2L + 7) & ~7L;
        this.arena = arena;
        this.globalMin = globalMin;
        this.globalMax = globalMax;
    }

    public void setHotNodeCache(HotNodeCache cache) {
        this.cache = cache;
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
        long nodeSize = (20L + dims * 2L + 7) & ~7L;
        Arena arena = Arena.ofShared();
        MemorySegment segment = arena.allocate((long) size * nodeSize);
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        try {
            Node root = buildRecursive(vectors, labels, indices, 0, size);
            writeBfs(root, segment, dims);
        } catch (Exception e) {
            System.err.println("Build failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        
        return new VpTree(dims, size, segment, arena, min, max);
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

    private static void writeBfs(Node root, MemorySegment segment, int dims) {
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
            int leftIdx = (node.left != null) ? node.left.bfsIdx : -1;
            int rightIdx = (node.right != null) ? node.right.bfsIdx : -1;
            writeNode(segment, i, node.muSq, leftIdx, rightIdx, node.label, node.vp, dims);
        }
    }

    private static void writeNode(MemorySegment segment, int pos, long muSq, int left, int right, byte label, short[] vp, int dims) {
        long nodeSize = (20L + dims * 2L + 7) & ~7L;
        long offset = (long) pos * nodeSize;
        segment.set(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), offset, muSq);
        segment.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8, left);
        segment.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 12, right);
        segment.set(ValueLayout.JAVA_BYTE, offset + 16, label);
        for (int i = 0; i < dims; i++) {
            segment.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 20 + (long) i * 2, vp[i]);
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
                long offset = (long) index * nodeSize;
                buffer.putLong(segment.get(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), offset));
                buffer.putInt(segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8));
                buffer.putInt(segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 12));
                buffer.put(segment.get(ValueLayout.JAVA_BYTE, offset + 16));
                
                // Padding
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                
                for (int i = 0; i < dims; i++) {
                    buffer.putShort(segment.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 20 + (long) i * 2));
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
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();
            int dims = header.getInt();
            int size = header.getInt();
            float min = header.getFloat();
            float max = header.getFloat();
            
            long nodeSize = (20L + dims * 2L + 7) & ~7L;
            long expectedSize = 16L + (long) size * nodeSize;
            if (channel.size() < expectedSize) {
                throw new IOException("File too small");
            }

            Arena arena = Arena.ofShared();
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 16L, (long) size * nodeSize, arena);

            return new VpTree(dims, size, segment, arena, min, max);
        } finally {
            channel.close();
        }
    }

    public void warmup() {
        long bytes = segment.byteSize();
        for (long offset = 0; offset < bytes; offset += 4096) {
            segment.get(ValueLayout.JAVA_BYTE, offset);
        }
    }

    public void close() {
        arena.close();
    }

    public int size() {
        return size;
    }

    public float[] getVector(int index) {
        long offset = (long) index * nodeSize + 20;
        float[] v = new float[dims];
        float range = globalMax - globalMin;
        for (int i = 0; i < dims; i++) {
            short s = segment.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + (long) i * 2);
            float normalized = (s + 32768) / 65535.0f;
            v[i] = normalized * range + globalMin;
        }
        return v;
    }

    public boolean isFraud(int index) {
        return segment.get(ValueLayout.JAVA_BYTE, (long) index * nodeSize + 16) == 1;
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

            long muSqLong;
            int left;
            int right;
            long quantizedDSq;
            
            if (cache != null && nodeIdx < cache.getCapacity()) {
                muSqLong = cache.getMuSq(nodeIdx);
                left = cache.getLeft(nodeIdx);
                right = cache.getRight(nodeIdx);
                if (dims == 7) {
                    quantizedDSq = SimdDistance.compute7DCached(quantizedQuery, cache.getVectors(), nodeIdx * 7);
                } else {
                    quantizedDSq = SimdDistance.computeCached(quantizedQuery, cache.getVectors(), nodeIdx * dims);
                }
            } else {
                long offset = (long) nodeIdx * nodeSize;
                muSqLong = segment.get(ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), offset);
                left = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8);
                right = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 12);
                if (dims == 7) {
                    quantizedDSq = SimdDistance.compute7D(quantizedQuery, segment, offset + 20);
                } else {
                    quantizedDSq = SimdDistance.computeUnrolledScalar(quantizedQuery, segment, offset + 20);
                }
            }
            
            float dSq = quantizedDSq * scaleSq;
            queue.insert(nodeIdx, dSq);
            
            float worstSq = queue.worstDistance();
            if (worstSq != lastWorstSq) {
                lastWorstSq = worstSq;
                lastWorstQ = (worstSq == Float.MAX_VALUE) ? Float.MAX_VALUE : (float) Math.sqrt(worstSq) * invScale;
            }

            float dQ = SqrtLookupTable.get(quantizedDSq);
            float muQ = SqrtLookupTable.get(muSqLong);

            if (quantizedDSq <= muSqLong) {
                // Inside inner ball: Left is closer
                float distToBoundaryQ = muQ - dQ;
                
                if (distToBoundaryQ <= lastWorstQ) {
                    nodeStack[top] = right;
                    boundStackQ[top] = distToBoundaryQ;
                    top++;
                }

                nodeStack[top] = left;
                boundStackQ[top] = 0.0f;
                top++;
            } else {
                // Outside inner ball: Right is closer
                float distToBoundaryQ = dQ - muQ;
                
                if (distToBoundaryQ <= lastWorstQ) {
                    nodeStack[top] = left;
                    boundStackQ[top] = distToBoundaryQ;
                    top++;
                }

                nodeStack[top] = right;
                boundStackQ[top] = 0.0f;
                top++;
            }
        }
    }
}
