package com.jnta.search.vpt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;

public class VpTreeIO {
    
    public static void save(VpTree tree, Path path) throws IOException {
        long nodeSize = (20L + tree.dims * 2L + 7) & ~7L;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(tree.dims);
            header.putInt(tree.size);
            header.putFloat(tree.globalMin);
            header.putFloat(tree.globalMax);
            header.flip();
            channel.write(header);
            
            ByteBuffer buffer = ByteBuffer.allocate((int) nodeSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < tree.size; index++) {
                buffer.clear();
                long muSqValue = (long) (tree.muQ[index] * tree.muQ[index]);
                buffer.putLong(muSqValue);
                buffer.putInt(tree.left[index]);
                buffer.putInt(tree.right[index]);
                buffer.put(tree.labels.get(index) ? (byte) 1 : (byte) 0);
                
                // Padding
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                
                for (int i = 0; i < tree.dims; i++) {
                    buffer.putShort(tree.vectors[index * tree.dims + i]);
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
            ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
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
            BitSet labels = new BitSet(size);
            short[] vectors = new short[size * dims];
            
            ByteBuffer buffer = ByteBuffer.allocate((int) nodeSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < size; i++) {
                buffer.clear();
                channel.read(buffer);
                buffer.flip();
                
                long muSqVal = buffer.getLong();
                muQ[i] = (float) Math.sqrt(muSqVal);
                left[i] = buffer.getInt();
                right[i] = buffer.getInt();
                if (buffer.get() == 1) labels.set(i);
                
                buffer.position(20);
                for (int d = 0; d < dims; d++) {
                    vectors[i * dims + d] = buffer.getShort();
                }
            }
            
            return new VpTree(dims, size, muQ, left, right, labels, vectors, min, max);
        }
    }
}
