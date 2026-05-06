package com.jnta.vp;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class VpTree {
    private final int dims;
    private final int size;
    private final MemorySegment segment;
    private final long nodeSize;

    private final Arena arena;

    private VpTree(int dims, int size, MemorySegment segment, Arena arena) {
        this.dims = dims;
        this.size = size;
        this.segment = segment;
        this.nodeSize = 16L + (long) dims * 4;
        this.arena = arena;
    }

    public static VpTree build(List<float[]> vectors, boolean[] labels) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("No vectors");
        int dims = vectors.get(0).length;
        int size = vectors.size();
        long nodeSize = 16L + (long) dims * 4;
        Arena arena = Arena.ofShared();
        MemorySegment segment = arena.allocate((long) size * nodeSize);
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        buildRecursive(vectors, labels, indices, 0, size, segment, dims, new int[]{0});
        
        return new VpTree(dims, size, segment, arena);
    }

    private static int buildRecursive(List<float[]> vectors, boolean[] labels, int[] indices, int start, int end, MemorySegment segment, int dims, int[] nextNodeIdx) {
        if (start >= end) return -1;
        
        int nodeIdx = nextNodeIdx[0]++; 
        int vpIdx = indices[start];
        float[] vp = vectors.get(vpIdx);
        byte label = (byte) (labels[vpIdx] ? 1 : 0);
        
        if (end - start == 1) {
            writeNode(segment, nodeIdx, 0, -1, -1, label, vp, dims);
            return nodeIdx;
        }

        float[] distances = new float[end - start - 1];
        for (int i = start + 1; i < end; i++) {
            distances[i - (start + 1)] = distance(vp, vectors.get(indices[i]));
        }

        java.util.Arrays.sort(distances);
        float muSq = distances[distances.length / 2];
        float mu = (float) Math.sqrt(muSq);

        int mid = partition(vectors, indices, start + 1, end, vp, muSq);
        
        int left = buildRecursive(vectors, labels, indices, start + 1, mid, segment, dims, nextNodeIdx);
        int right = buildRecursive(vectors, labels, indices, mid, end, segment, dims, nextNodeIdx);
        
        writeNode(segment, nodeIdx, mu, left, right, label, vp, dims);
        return nodeIdx;
    }

    private static void writeNode(MemorySegment segment, int pos, float mu, int left, int right, byte label, float[] vp, int dims) {
        long nodeSize = 16L + (long) dims * 4;
        long offset = (long) pos * nodeSize;
        // Segment stores Euclidean mu for easier pruning logic
        segment.set(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset, mu);
        segment.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4, left);
        segment.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8, right);
        segment.set(ValueLayout.JAVA_BYTE, offset + 12, label);
        for (int i = 0; i < dims; i++) {
            segment.set(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 16 + (long) i * 4, vp[i]);
        }
    }

    private static float distance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    private static int partition(List<float[]> vectors, int[] indices, int start, int end, float[] vp, float muSq) {
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
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(dims);
            header.putInt(size);
            header.flip();
            channel.write(header);
            
            // Segment already has Euclidean mu, so we can write it directly (with padding)
            long nodeSize = 16L + (long) dims * 4;
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int)nodeSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < size; i++) {
                buffer.clear();
                long offset = (long) i * nodeSize;
                buffer.putFloat(segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset));
                buffer.putInt(segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4));
                buffer.putInt(segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8));
                buffer.put(segment.get(ValueLayout.JAVA_BYTE, offset + 12));
                buffer.put((byte)0); buffer.put((byte)0); buffer.put((byte)0); // padding
                for (int d = 0; d < dims; d++) {
                    buffer.putFloat(segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 16 + (long) d * 4));
                }
                buffer.flip();
                channel.write(buffer);
            }
        }
    }

    public static VpTree load(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();
            int dims = header.getInt();
            int size = header.getInt();
            
            long nodeSize = 16L + (long) dims * 4;
            long expectedSize = 8L + (long) size * nodeSize;
            if (channel.size() < expectedSize) {
                throw new IOException("File too small");
            }

            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate((long) size * nodeSize);
            java.nio.ByteBuffer buffer = segment.asByteBuffer();
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) break;
            }

            // No conversion needed, disk already stores Euclidean mu
            return new VpTree(dims, size, segment, arena);
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
        long offset = (long) index * nodeSize + 16;
        float[] v = new float[dims];
        for (int i = 0; i < dims; i++) {
            v[i] = segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + (long) i * 4);
        }
        return v;
    }

    public boolean isFraud(int index) {
        return segment.get(ValueLayout.JAVA_BYTE, (long) index * nodeSize + 12) == 1;
    }

    private static final ThreadLocal<int[]> NODE_STACK = ThreadLocal.withInitial(() -> new int[1024]);
    private static final ThreadLocal<float[]> BOUND_STACK = ThreadLocal.withInitial(() -> new float[1024]);

    public void search(float[] query, KnnQueue queue) {
        if (query.length != dims) {
            throw new IllegalArgumentException("Query dimension mismatch");
        }

        int[] nodeStack = NODE_STACK.get();
        float[] boundStack = BOUND_STACK.get();
        int top = 0;

        nodeStack[top] = 0;
        boundStack[top] = 0.0f;
        top++;

        while (top > 0) {
            top--;
            int nodeIdx = nodeStack[top];
            float bound = boundStack[top];

            if (nodeIdx == -1) continue;
            if (bound > queue.worstDistance()) continue;

            long offset = (long) nodeIdx * nodeSize;
            float mu = segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset);
            int left = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4);
            int right = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8);
            
            float dSq = SimdDistance.compute(query, segment, offset + 16);
            queue.insert(nodeIdx, dSq);

            float worstSq = queue.worstDistance();
            float muSq = mu * mu;

            if (dSq < muSq) {
                float d = (float) Math.sqrt(dSq);
                float worst = (float) Math.sqrt(worstSq);
                if (mu - d <= worst) {
                    nodeStack[top] = right;
                    boundStack[top] = (mu - d) * (mu - d);
                    top++;
                }
                nodeStack[top] = left;
                boundStack[top] = 0.0f;
                top++;
            } else {
                float d = (float) Math.sqrt(dSq);
                float worst = (float) Math.sqrt(worstSq);
                if (d - mu <= worst) {
                    nodeStack[top] = left;
                    boundStack[top] = (d - mu) * (d - mu);
                    top++;
                }
                nodeStack[top] = right;
                boundStack[top] = 0.0f;
                top++;
            }
        }
    }
}
