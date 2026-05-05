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

    private VpTree(int dims, int size, MemorySegment segment) {
        this.dims = dims;
        this.size = size;
        this.segment = segment;
        this.nodeSize = 16L + (long) dims * 4;
    }

    public static VpTree build(List<float[]> vectors, boolean[] labels) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("No vectors");
        int dims = vectors.get(0).length;
        int size = vectors.size();
        long nodeSize = 16L + (long) dims * 4;
        MemorySegment segment = Arena.ofAuto().allocate((long) size * nodeSize);
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        buildRecursive(vectors, labels, indices, 0, size, segment, dims, new int[]{0});
        
        return new VpTree(dims, size, segment);
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

        // Calculate distances to others to find median
        float[] distances = new float[end - start - 1];
        for (int i = start + 1; i < end; i++) {
            distances[i - (start + 1)] = distance(vp, vectors.get(indices[i]));
        }

        // Find median
        java.util.Arrays.sort(distances);
        float mu = distances[distances.length / 2];

        // Partition indices based on mu
        int mid = partition(vectors, indices, start + 1, end, vp, mu);
        
        int left = buildRecursive(vectors, labels, indices, start + 1, mid, segment, dims, nextNodeIdx);
        int right = buildRecursive(vectors, labels, indices, mid, end, segment, dims, nextNodeIdx);
        
        writeNode(segment, nodeIdx, mu, left, right, label, vp, dims);
        return nodeIdx;
    }

    private static void writeNode(MemorySegment segment, int pos, float threshold, int left, int right, byte label, float[] vp, int dims) {
        long nodeSize = 16L + (long) dims * 4;
        long offset = (long) pos * nodeSize;
        segment.set(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset, threshold);
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
        return (float) Math.sqrt(sum);
    }

    private static int partition(List<float[]> vectors, int[] indices, int start, int end, float[] vp, float mu) {
        int i = start;
        int j = end - 1;
        while (i <= j) {
            while (i <= j && distance(vp, vectors.get(indices[i])) <= mu) i++;
            while (i <= j && distance(vp, vectors.get(indices[j])) > mu) j--;
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
            
            // Write memory segment to channel
            channel.write(segment.asByteBuffer());
        }
    }

    public static VpTree load(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();
            int dims = header.getInt();
            int size = header.getInt();
            
            long nodeSize = 16L + (long) dims * 4;
            long expectedSize = 8L + (long) size * nodeSize;
            if (channel.size() < expectedSize) {
                throw new IOException("File too small: expected " + expectedSize + " bytes but got " + channel.size());
            }

            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 8, (long) size * nodeSize, Arena.ofAuto());
            return new VpTree(dims, size, segment);
        }
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

    public void search(float[] query, KnnQueue queue) {
        if (query.length != dims) {
            throw new IllegalArgumentException("Query dimension mismatch: expected " + dims + " but got " + query.length);
        }
        searchRecursive(0, query, queue);
    }

    private void searchRecursive(int nodeIdx, float[] query, KnnQueue queue) {
        if (nodeIdx == -1) return;

        long offset = (long) nodeIdx * nodeSize;
        float mu = segment.get(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), offset);
        int left = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 4);
        int right = segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), offset + 8);
        
        float d = SimdDistance.compute(query, segment, offset + 16);
        queue.insert(nodeIdx, d);

        if (d < mu) {
            searchRecursive(left, query, queue);
            if (d + queue.worstDistance() >= mu) {
                searchRecursive(right, query, queue);
            }
        } else {
            searchRecursive(right, query, queue);
            if (d - queue.worstDistance() <= mu) {
                searchRecursive(left, query, queue);
            }
        }
    }

    public void warmup() {
        long sum = 0;
        long cap = segment.byteSize();
        for (long i = 0; i < cap; i += 1024) {
            sum += segment.get(ValueLayout.JAVA_BYTE, i);
        }
        if (cap > 0) {
            sum += segment.get(ValueLayout.JAVA_BYTE, cap - 1);
        }
        // Use sum to prevent optimization
        if (sum == System.nanoTime()) {
            System.out.print("");
        }
    }
}
