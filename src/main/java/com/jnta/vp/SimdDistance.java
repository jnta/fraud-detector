package com.jnta.vp;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorMask;

public class SimdDistance {
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;

    public static float compute(float[] a, float[] b) {
        int i = 0;
        FloatVector sumVector = FloatVector.zero(F_SPECIES);
        int upperBound = F_SPECIES.loopBound(a.length);
        
        for (; i < upperBound; i += F_SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(F_SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(F_SPECIES, b, i);
            FloatVector diff = va.sub(vb);
            sumVector = diff.fma(diff, sumVector);
        }
        
        float sum = sumVector.reduceLanes(VectorOperators.ADD);
        
        for (; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        
        return sum;
    }

    public static float compute(float[] a, MemorySegment segment, long offset) {
        int upperBound = F_SPECIES.loopBound(a.length);
        int i = 0;
        ByteOrder le = ByteOrder.LITTLE_ENDIAN;
        FloatVector sumVector = FloatVector.zero(F_SPECIES);
        
        for (; i < upperBound; i += F_SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(F_SPECIES, a, i);
            FloatVector vb = FloatVector.fromMemorySegment(F_SPECIES, segment, offset + (long) i * 4, le);
            FloatVector diff = va.sub(vb);
            sumVector = diff.fma(diff, sumVector);
        }
        
        float res = sumVector.reduceLanes(VectorOperators.ADD);
        
        for (; i < a.length; i++) {
            float diff = a[i] - segment.get(ValueLayout.JAVA_FLOAT.withOrder(le), offset + (long) i * 4);
            res += diff * diff;
        }
        return res;
    }

    public static int computeQuantized(byte[] query, MemorySegment segment, long offset) {
        int i = 0;
        int upperBound = B_SPECIES.loopBound(query.length);
        IntVector sumVector = IntVector.zero(I_SPECIES);
        
        for (; i < upperBound; i += B_SPECIES.length()) {
            ByteVector va = ByteVector.fromArray(B_SPECIES, query, i);
            ByteVector vb = ByteVector.fromMemorySegment(B_SPECIES, segment, offset + i, ByteOrder.nativeOrder());
            
            // Expand each half to ShortVector to avoid overflow in subtraction
            // Max diff is 255, fits in short.
            int numShortParts = B_SPECIES.length() / S_SPECIES.length();
            for (int p = 0; p < numShortParts; p++) {
                ShortVector vaS = (ShortVector) va.convert(VectorOperators.B2S, p);
                ShortVector vbS = (ShortVector) vb.convert(VectorOperators.B2S, p);
                ShortVector diff = vaS.sub(vbS);
                
                // Expand each short part to int to avoid overflow in square
                // Max square is 65025, overflows signed short.
                int numIntParts = S_SPECIES.length() / I_SPECIES.length();
                for (int q = 0; q < numIntParts; q++) {
                    IntVector diffI = (IntVector) diff.convert(VectorOperators.S2I, q);
                    sumVector = sumVector.add(diffI.mul(diffI));
                }
            }
        }
        
        int sum = sumVector.reduceLanes(VectorOperators.ADD);
        for (; i < query.length; i++) {
            int q = query[i];
            int s = segment.get(ValueLayout.JAVA_BYTE, offset + i);
            int diff = q - s;
            sum += diff * diff;
        }
        return sum;
    }

    public static long compute16Bit(short[] query, MemorySegment segment, long offset) {
        int length = query.length;
        ByteOrder le = ByteOrder.LITTLE_ENDIAN;
        
        if (length <= S_SPECIES.length()) {
            VectorMask<Short> mask = S_SPECIES.indexInRange(0, length);
            ShortVector va = ShortVector.fromArray(S_SPECIES, query, 0, mask);
            ShortVector vb = ShortVector.fromMemorySegment(S_SPECIES, segment, offset, le, mask);
            
            long totalSum = 0;
            int numIntParts = S_SPECIES.length() / I_SPECIES.length();
            for (int p = 0; p < numIntParts; p++) {
                IntVector vaI = (IntVector) va.convert(VectorOperators.S2I, p);
                IntVector vbI = (IntVector) vb.convert(VectorOperators.S2I, p);
                IntVector diffI = vaI.sub(vbI);
                
                VectorSpecies<Long> lSpecies = LongVector.SPECIES_PREFERRED;
                int numLongParts = I_SPECIES.length() / lSpecies.length();
                for (int lp = 0; lp < numLongParts; lp++) {
                    LongVector diffL = (LongVector) diffI.convert(VectorOperators.I2L, lp);
                    totalSum += diffL.mul(diffL).reduceLanes(VectorOperators.ADD);
                }
            }
            return totalSum;
        }
        
        return computeUnrolledScalar(query, segment, offset);
    }

    public static long computeUnrolledScalar(short[] query, MemorySegment segment, long offset) {
        int length = query.length;
        ByteOrder le = ByteOrder.LITTLE_ENDIAN;
        long sum = 0;
        int i = 0;
        
        for (; i <= length - 4; i += 4) {
            long q0 = query[i];
            long s0 = segment.get(ValueLayout.JAVA_SHORT.withOrder(le), offset + (long) i * 2);
            long d0 = q0 - s0;
            
            long q1 = query[i + 1];
            long s1 = segment.get(ValueLayout.JAVA_SHORT.withOrder(le), offset + (long) (i + 1) * 2);
            long d1 = q1 - s1;
            
            long q2 = query[i + 2];
            long s2 = segment.get(ValueLayout.JAVA_SHORT.withOrder(le), offset + (long) (i + 2) * 2);
            long d2 = q2 - s2;
            
            long q3 = query[i + 3];
            long s3 = segment.get(ValueLayout.JAVA_SHORT.withOrder(le), offset + (long) (i + 3) * 2);
            long d3 = q3 - s3;
            
            sum += (d0 * d0) + (d1 * d1) + (d2 * d2) + (d3 * d3);
        }
        
        for (; i < length; i++) {
            long q = query[i];
            long s = segment.get(ValueLayout.JAVA_SHORT.withOrder(le), offset + (long) i * 2);
            long d = q - s;
            sum += d * d;
        }
        return sum;
    }

    public static long computeCached(short[] query, short[] cache, int offset) {
        long sum = 0;
        int i = 0;
        int length = query.length;
        
        for (; i <= length - 4; i += 4) {
            long q0 = query[i];
            long s0 = cache[offset + i];
            long d0 = q0 - s0;
            
            long q1 = query[i + 1];
            long s1 = cache[offset + i + 1];
            long d1 = q1 - s1;
            
            long q2 = query[i + 2];
            long s2 = cache[offset + i + 2];
            long d2 = q2 - s2;
            
            long q3 = query[i + 3];
            long s3 = cache[offset + i + 3];
            long d3 = q3 - s3;
            
            sum += (d0 * d0) + (d1 * d1) + (d2 * d2) + (d3 * d3);
        }
        
        for (; i < length; i++) {
            long q = query[i];
            long s = cache[offset + i];
            long d = q - s;
            sum += d * d;
        }
        return sum;
    }
}
