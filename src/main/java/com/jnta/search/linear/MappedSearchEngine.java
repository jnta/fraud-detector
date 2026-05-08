package com.jnta.search.linear;

import com.jnta.search.SearchEngine;
import com.jnta.search.KnnQueue;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.PriorityQueue;

public class MappedSearchEngine implements SearchEngine {
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final int TOP_K_PRUNE = 100;

    private final Arena arena;
    private final int size;
    private final float min, max;
    private final MemorySegment[] blockA;
    private final MemorySegment[] blockB;
    private final MemorySegment labels;

    public MappedSearchEngine(Path path) throws IOException {
        this.arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(path)) {
            MemorySegment mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            
            long offset = 0;
            int magic = mapped.get(ValueLayout.JAVA_INT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), offset); offset += 4;
            if (magic != 0x52415A52) throw new IOException("Invalid magic");
            
            this.size = mapped.get(ValueLayout.JAVA_INT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), offset); offset += 4;
            this.min = mapped.get(ValueLayout.JAVA_FLOAT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), offset); offset += 4;
            this.max = mapped.get(ValueLayout.JAVA_FLOAT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), offset); offset += 4;
            
            blockA = new MemorySegment[6];
            for (int i = 0; i < 6; i++) {
                blockA[i] = mapped.asSlice(offset, (long) size * 2);
                offset += (long) size * 2;
            }
            
            blockB = new MemorySegment[8];
            for (int i = 0; i < 8; i++) {
                blockB[i] = mapped.asSlice(offset, (long) size * 2);
                offset += (long) size * 2;
            }
            
            long labelBytes = (size + 7) / 8;
            labels = mapped.asSlice(offset, labelBytes);
        }
    }

    @Override
    public void search(float[] query, KnnQueue queue) {
        short[] q = quantize(query);
        short[] qA = new short[6];
        short[] qB = new short[8];
        
        int[] indexA = {0, 6, 7, 9, 10, 12};
        int[] indexB = {1, 2, 3, 4, 5, 8, 11, 13};
        for (int i = 0; i < 6; i++) qA[i] = q[indexA[i]];
        for (int i = 0; i < 8; i++) qB[i] = q[indexB[i]];

        // Pass 1: SIMD Scan on Block A
        KnnQueue pruneQueue = new KnnQueue(TOP_K_PRUNE);
        scanBlockA(qA, pruneQueue);
        
        // Pass 2: Refine Top 100 with Block B
        int[] candidates = pruneQueue.getIndices();
        float[] partialDistA = pruneQueue.getDistances();
        int count = pruneQueue.size();
        
        for (int i = 0; i < count; i++) {
            int idx = candidates[i];
            float fullDist = partialDistA[i] + fullDistBlockB(qB, idx);
            queue.insert(idx, fullDist);
        }
    }

    private void scanBlockA(short[] queryA, KnnQueue queue) {
        int laneWidth = S_SPECIES.length();
        int upperBound = S_SPECIES.loopBound(size);
        
        FloatVector[] qVecs = new FloatVector[6];
        for (int d = 0; d < 6; d++) {
            qVecs[d] = FloatVector.broadcast(F_SPECIES, (float) queryA[d]);
        }

        for (int i = 0; i < upperBound; i += laneWidth) {
            FloatVector acc1 = FloatVector.zero(F_SPECIES);
            FloatVector acc2 = FloatVector.zero(F_SPECIES);
            
            for (int d = 0; d < 6; d++) {
                ShortVector sv = ShortVector.fromMemorySegment(S_SPECIES, blockA[d], (long) i * 2, java.nio.ByteOrder.nativeOrder());
                
                // Convert to two FloatVectors for fma
                FloatVector v1 = (FloatVector) sv.convert(VectorOperators.S2F, 0);
                FloatVector v2 = (FloatVector) sv.convert(VectorOperators.S2F, 1);
                
                FloatVector diff1 = v1.sub(qVecs[d]);
                acc1 = diff1.fma(diff1, acc1);
                
                FloatVector diff2 = v2.sub(qVecs[d]);
                acc2 = diff2.fma(diff2, acc2);
            }

            
            float worst = queue.worstDistance();
            if (acc1.reduceLanes(VectorOperators.MIN) < worst || acc2.reduceLanes(VectorOperators.MIN) < worst) {
                for (int k = 0; k < F_SPECIES.length(); k++) {
                    float d1 = acc1.lane(k);
                    if (d1 < worst) {
                        queue.insert(i + k, d1);
                        worst = queue.worstDistance();
                    }
                    float d2 = acc2.lane(k);
                    if (d2 < worst) {
                        queue.insert(i + F_SPECIES.length() + k, d2);
                        worst = queue.worstDistance();
                    }
                }
            }
        }
        
        // Tail
        for (int i = upperBound; i < size; i++) {
            float sum = 0;
            for (int d = 0; d < 6; d++) {
                int val = blockA[d].get(ValueLayout.JAVA_SHORT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), (long) i * 2);
                float diff = (float) val - (float) queryA[d];
                sum += diff * diff;
            }
            if (sum < queue.worstDistance()) {
                queue.insert(i, sum);
            }
        }
    }

    private float fullDistBlockB(short[] queryB, int index) {
        float sum = 0;
        for (int d = 0; d < 8; d++) {
            int val = blockB[d].get(ValueLayout.JAVA_SHORT.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), (long) index * 2);
            float diff = (float) val - (float) queryB[d];
            sum += diff * diff;
        }
        return sum;
    }

    @Override
    public int size() { return size; }

    @Override
    public boolean isFraud(int index) {
        int byteIdx = index / 8;
        int bitIdx = index % 8;
        byte b = labels.get(ValueLayout.JAVA_BYTE, byteIdx);
        return ((b >> bitIdx) & 1) == 1;
    }

    private short[] quantize(float[] v) {
        short[] q = new short[v.length];
        for (int i = 0; i < v.length; i++) {
            float norm = (v[i] - min) / (max - min);
            q[i] = (short) (norm * 65535 - 32768);
        }
        return q;
    }

    @Override
    public void close() { arena.close(); }
}

