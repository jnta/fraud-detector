package com.jnta.vp;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

public class HotNodeCache {
    private final int capacity;
    private final int dims;
    private final long[] muSq;
    private final int[] left;
    private final int[] right;
    private final byte[] label;
    private final short[] vectors;

    public HotNodeCache(VpTree tree, int maxNodes) {
        int size = Math.min(tree.size(), maxNodes);
        this.capacity = size;
        this.dims = getDims(tree);
        
        this.muSq = new long[size];
        this.left = new int[size];
        this.right = new int[size];
        this.label = new byte[size];
        this.vectors = new short[size * dims];
        
        load(tree);
    }

    private int getDims(VpTree tree) {
        try {
            java.lang.reflect.Field field = VpTree.class.getDeclaredField("dims");
            field.setAccessible(true);
            return (int) field.get(tree);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void load(VpTree tree) {
        try {
            java.lang.reflect.Field segmentField = VpTree.class.getDeclaredField("segment");
            segmentField.setAccessible(true);
            MemorySegment segment = (MemorySegment) segmentField.get(tree);
            
            java.lang.reflect.Field nodeSizeField = VpTree.class.getDeclaredField("nodeSize");
            nodeSizeField.setAccessible(true);
            long nodeSize = (long) nodeSizeField.get(tree);
            
            ByteOrder le = ByteOrder.LITTLE_ENDIAN;
            
            for (int i = 0; i < capacity; i++) {
                long offset = (long) i * nodeSize;
                muSq[i] = segment.get(ValueLayout.JAVA_LONG.withOrder(le), offset);
                left[i] = segment.get(ValueLayout.JAVA_INT.withOrder(le), offset + 8);
                right[i] = segment.get(ValueLayout.JAVA_INT.withOrder(le), offset + 12);
                label[i] = segment.get(ValueLayout.JAVA_BYTE, offset + 16);
                
                for (int d = 0; d < dims; d++) {
                    vectors[i * dims + d] = segment.get(ValueLayout.JAVA_SHORT.withOrder(le), offset + 20 + (long) d * 2);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public long getMuSq(int nodeIdx) {
        return muSq[nodeIdx];
    }

    public int getLeft(int nodeIdx) {
        return left[nodeIdx];
    }

    public int getRight(int nodeIdx) {
        return right[nodeIdx];
    }

    public byte getLabel(int nodeIdx) {
        return label[nodeIdx];
    }

    public short[] getVectors() {
        return vectors;
    }
    
    public int getDims() {
        return dims;
    }
}
