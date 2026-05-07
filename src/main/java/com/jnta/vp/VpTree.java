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

public class VpTree {
    private final int dims;
    private final int size;
    private final MemorySegment segment;
    private final long nodeSize;
    private final float globalMin;
    private final float globalMax;

    private final Arena arena;

    private VpTree(int dims, int size, MemorySegment segment, Arena arena, float globalMin, float globalMax) {
        this.dims = dims;
        this.size = size;
        this.segment = segment;
        this.nodeSize = (16L + dims + 3) & ~3L;
        this.arena = arena;
        this.globalMin = globalMin;
        this.globalMax = globalMax;
    }

    public static VpTree build(List<float[]> vectors, boolean[] labels) {
        float[] bounds = Preprocessor.findGlobalBounds(vectors);
        float min = bounds[0];
        float max = bounds[1];
        List<byte[]> quantized = new ArrayList<>(vectors.size());
        for (float[] v : vectors) {
            quantized.add(Preprocessor.quantize(v, min, max));
        }
        return build(quantized, labels, min, max);
    }

    public static VpTree build(List<byte[]> vectors, boolean[] labels, float min, float max) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("No vectors");
        int dims = vectors.get(0).length;
        int size = vectors.size();
        long nodeSize = (16L + dims + 3) & ~3L;
        Arena arena = Arena.ofShared();
        MemorySegment segment = arena.allocate((long) size * nodeSize);
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        float scale = (max - min) / 255.0f;
        buildRecursive(vectors, labels, indices, 0, size, segment, dims, new int[]{0}, scale);
        
        return new VpTree(dims, size, segment, arena, min, max);
    }

    private static int buildRecursive(List<byte[]> vectors, boolean[] labels, int[] indices, int start, int end, MemorySegment segment, int dims, int[] nextNodeIdx, float scale) {
        if (start >= end) return -1;
        
        int nodeIdx = nextNodeIdx[0]++; 
        int vpIdx = indices[start];
        byte[] vp = vectors.get(vpIdx);
        byte label = (byte) (labels[vpIdx] ? 1 : 0);
        
        if (end - start == 1) {
            writeNode(segment, nodeIdx, 0, -1, -1, label, vp, dims);
            return nodeIdx;
        }

        int[] distances = new int[end - start - 1];
        for (int i = start + 1; i < end; i++) {
            distances[i - (start + 1)] = distance(vp, vectors.get(indices[i]));
        }

        java.util.Arrays.sort(distances);
        int muSq = distances[distances.length / 2];
        float mu = (float) Math.sqrt(muSq) * scale;

        int mid = partition(vectors, indices, start + 1, end, vp, muSq);
        
        int left = buildRecursive(vectors, labels, indices, start + 1, mid, segment, dims, nextNodeIdx, scale);
        int right = buildRecursive(vectors, labels, indices, mid, end, segment, dims, nextNodeIdx, scale);
        
        writeNode(segment, nodeIdx, mu, left, right, label, vp, dims);
        return nodeIdx;
    }

    private static void writeNode(MemorySegment segment, int pos, float mu, int left, int right, byte label, byte[] vp, int dims) {
        long nodeSize = (16L + dims + 3) & ~3L;
        long offset = (long) pos * nodeSize;
        segment.set(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset, mu);
        segment.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4, left);
        segment.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8, right);
        segment.set(ValueLayout.JAVA_BYTE, offset + 12, label);
        for (int i = 0; i < dims; i++) {
            segment.set(ValueLayout.JAVA_BYTE, offset + 16 + (long) i, vp[i]);
        }
    }

    private static int distance(byte[] a, byte[] b) {
        int sum = 0;
        for (int i = 0; i < a.length; i++) {
            int diff = (int) a[i] - (int) b[i];
            sum += diff * diff;
        }
        return sum;
    }

    private static int partition(List<byte[]> vectors, int[] indices, int start, int end, byte[] vp, int muSq) {
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
                buffer.putFloat(segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset));
                buffer.putInt(segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4));
                buffer.putInt(segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8));
                buffer.put(segment.get(ValueLayout.JAVA_BYTE, offset + 12));
                
                // Add 3 bytes of padding to align the vector at offset 16
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                
                for (int i = 0; i < dims; i++) {
                    buffer.put(segment.get(ValueLayout.JAVA_BYTE, offset + 16 + (long) i));
                }
                
                // If there's more padding at the end due to nodeSize being a multiple of 4
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
            
            long nodeSize = (16L + dims + 3) & ~3L;
            long expectedSize = 16L + (long) size * nodeSize;
            if (channel.size() < expectedSize) {
                throw new IOException("File too small");
            }

            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate((long) size * nodeSize);
            java.nio.ByteBuffer buffer = segment.asByteBuffer();
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) break;
            }

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
        long offset = (long) index * nodeSize + 16;
        float[] v = new float[dims];
        float range = globalMax - globalMin;
        for (int i = 0; i < dims; i++) {
            byte b = segment.get(ValueLayout.JAVA_BYTE, offset + (long) i);
            float normalized = (b + 128) / 255.0f;
            v[i] = normalized * range + globalMin;
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

        byte[] quantizedQuery = Preprocessor.quantize(query, globalMin, globalMax);
        float scale = (globalMax - globalMin) / 255.0f;
        float scaleSq = scale * scale;

        int[] nodeStack = NODE_STACK.get();
        float[] boundStack = BOUND_STACK.get();
        int top = 0;

        float lastWorstSq = -1.0f;
        float lastWorst = 0.0f;

        nodeStack[top] = 0;
        boundStack[top] = 0.0f;
        top++;

        while (top > 0) {
            top--;
            int nodeIdx = nodeStack[top];
            float boundSq = boundStack[top];

            if (nodeIdx == -1) continue;
            
            float worstSq = queue.worstDistance();
            if (boundSq > worstSq) continue;

            if (worstSq != lastWorstSq) {
                lastWorstSq = worstSq;
                lastWorst = (float) Math.sqrt(worstSq);
            }

            long offset = (long) nodeIdx * nodeSize;
            float mu = segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset);
            int left = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4);
            int right = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8);
            
            int quantizedDSq = SimdDistance.computeQuantized(quantizedQuery, segment, offset + 16);
            float dSq = quantizedDSq * scaleSq;
            
            queue.insert(nodeIdx, dSq);
            
            worstSq = queue.worstDistance();
            if (worstSq != lastWorstSq) {
                lastWorstSq = worstSq;
                lastWorst = (float) Math.sqrt(worstSq);
            }

            if (dSq < mu * mu) {
                nodeStack[top] = left;
                boundStack[top] = 0.0f;
                top++;

                float diff = mu - lastWorst;
                if (diff <= 0 || diff * diff <= dSq) {
                    nodeStack[top] = right;
                    boundStack[top] = diff > 0 ? diff * diff : 0.0f;
                    top++;
                }
            } else {
                nodeStack[top] = right;
                boundStack[top] = 0.0f;
                top++;

                float sum = mu + lastWorst;
                if (dSq <= sum * sum) {
                    nodeStack[top] = left;
                    float d = (float) Math.sqrt(dSq);
                    float diff = d - mu;
                    boundStack[top] = diff > 0 ? diff * diff : 0.0f;
                    top++;
                }
            }
        }
    }
}
