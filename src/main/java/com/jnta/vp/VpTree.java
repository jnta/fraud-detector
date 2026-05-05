package com.jnta.vp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;

public class VpTree {
    private final int dims;
    private final int size;
    private final ByteBuffer buffer;

    private VpTree(int dims, int size, ByteBuffer buffer) {
        this.dims = dims;
        this.size = size;
        this.buffer = buffer;
    }

    public static VpTree build(List<float[]> vectors) {
        if (vectors.isEmpty()) throw new IllegalArgumentException("No vectors");
        int dims = vectors.get(0).length;
        int size = vectors.size();
        int nodeSize = 12 + dims * 4;
        ByteBuffer buffer = ByteBuffer.allocateDirect(size * nodeSize).order(ByteOrder.LITTLE_ENDIAN);
        
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        
        buildRecursive(vectors, indices, 0, size, buffer, dims, new int[]{0});
        
        return new VpTree(dims, size, buffer);
    }

    private static int buildRecursive(List<float[]> vectors, int[] indices, int start, int end, ByteBuffer buffer, int dims, int[] nextNodeIdx) {
        if (start >= end) return -1;
        
        int nodeIdx = nextNodeIdx[0]++; 
        int vpIdx = indices[start];
        float[] vp = vectors.get(vpIdx);
        
        if (end - start == 1) {
            writeNode(buffer, nodeIdx, 0, -1, -1, vp, dims);
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
        
        int left = buildRecursive(vectors, indices, start + 1, mid, buffer, dims, nextNodeIdx);
        int right = buildRecursive(vectors, indices, mid, end, buffer, dims, nextNodeIdx);
        
        writeNode(buffer, nodeIdx, mu, left, right, vp, dims);
        return nodeIdx;
    }

    private static void writeNode(ByteBuffer buffer, int pos, float threshold, int left, int right, float[] vp, int dims) {
        int nodeSize = 12 + dims * 4;
        buffer.putFloat(pos * nodeSize, threshold);
        buffer.putInt(pos * nodeSize + 4, left);
        buffer.putInt(pos * nodeSize + 8, right);
        for (int i = 0; i < dims; i++) {
            buffer.putFloat(pos * nodeSize + 12 + i * 4, vp[i]);
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
            // Header: dims(4), size(4)
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(dims);
            header.putInt(size);
            header.flip();
            channel.write(header);
            buffer.rewind();
            channel.write(buffer);
        }
    }

    public static VpTree load(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();
            int dims = header.getInt();
            int size = header.getInt();
            
            // Map the rest of the file starting after the header
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 8, channel.size() - 8).order(ByteOrder.LITTLE_ENDIAN);
            return new VpTree(dims, size, buffer);
        }
    }

    public int size() {
        return size;
    }

    public float[] getVector(int index) {
        int nodeSize = 12 + dims * 4;
        int offset = index * nodeSize + 12;
        float[] v = new float[dims];
        for (int i = 0; i < dims; i++) {
            v[i] = buffer.getFloat(offset + i * 4);
        }
        return v;
    }

    public void warmup() {
        int sum = 0;
        for (int i = 0; i < buffer.capacity(); i += 4096) {
            sum += buffer.get(i);
        }
        // Touch the last byte too
        if (buffer.capacity() > 0) {
            sum += buffer.get(buffer.capacity() - 1);
        }
        // Minimal side effect to prevent JIT from optimizing it away
        if (sum == 1234567) System.out.print(""); 
    }
}
