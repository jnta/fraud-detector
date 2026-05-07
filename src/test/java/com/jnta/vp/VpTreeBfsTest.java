package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;

class VpTreeBfsTest {

    @Test
    void testBfsOrdering() {
        int numVectors = 100;
        int dims = 7;
        List<float[]> vectors = new ArrayList<>();
        boolean[] labels = new boolean[numVectors];
        for (int i = 0; i < numVectors; i++) {
            float[] v = new float[dims];
            for (int d = 0; d < dims; d++) v[d] = (float) Math.random();
            vectors.add(v);
            labels[i] = (i % 2 == 0);
        }

        VpTree tree = VpTree.build(vectors, labels);
        
        // Check that nodes are in BFS order
        // 1. Root must be at index 0
        // 2. We can traverse the tree and collect depths
        int[] depths = new int[numVectors];
        for (int i = 0; i < numVectors; i++) depths[i] = -1;
        
        Queue<Integer> queue = new LinkedList<>();
        queue.add(0);
        depths[0] = 0;
        
        int visitedCount = 0;
        int maxIndex = -1;
        
        while (!queue.isEmpty()) {
            int nodeIdx = queue.poll();
            visitedCount++;
            maxIndex = Math.max(maxIndex, nodeIdx);
            
            int left = getLeftChild(tree, nodeIdx);
            int right = getRightChild(tree, nodeIdx);
            
            if (left != -1) {
                Assertions.assertTrue(left > nodeIdx, "Left child index " + left + " must be > parent index " + nodeIdx);
                depths[left] = depths[nodeIdx] + 1;
                queue.add(left);
            }
            if (right != -1) {
                Assertions.assertTrue(right > nodeIdx, "Right child index " + right + " must be > parent index " + nodeIdx);
                depths[right] = depths[nodeIdx] + 1;
                queue.add(right);
            }
        }
        
        Assertions.assertEquals(numVectors, visitedCount, "All nodes should be reachable from root");
        
        // Verify BFS level property: depths[i] must be non-decreasing
        for (int i = 1; i < numVectors; i++) {
            Assertions.assertTrue(depths[i] >= depths[i - 1], 
                "Depth at index " + i + " (" + depths[i] + ") must be >= depth at index " + (i-1) + " (" + depths[i-1] + ")");
        }
    }

    private int getLeftChild(VpTree tree, int nodeIdx) {
        try {
            java.lang.reflect.Field segmentField = VpTree.class.getDeclaredField("segment");
            segmentField.setAccessible(true);
            MemorySegment segment = (MemorySegment) segmentField.get(tree);
            
            java.lang.reflect.Field nodeSizeField = VpTree.class.getDeclaredField("nodeSize");
            nodeSizeField.setAccessible(true);
            long nodeSize = (long) nodeSizeField.get(tree);
            
            return segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), nodeIdx * nodeSize + 8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int getRightChild(VpTree tree, int nodeIdx) {
        try {
            java.lang.reflect.Field segmentField = VpTree.class.getDeclaredField("segment");
            segmentField.setAccessible(true);
            MemorySegment segment = (MemorySegment) segmentField.get(tree);
            
            java.lang.reflect.Field nodeSizeField = VpTree.class.getDeclaredField("nodeSize");
            nodeSizeField.setAccessible(true);
            long nodeSize = (long) nodeSizeField.get(tree);
            
            return segment.get(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), nodeIdx * nodeSize + 12);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
