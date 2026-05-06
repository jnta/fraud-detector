package com.jnta.vp;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

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

    private static int computeQuantizedScalar(byte[] query, MemorySegment segment, long offset) {
        int sum = 0;
        for (int i = 0; i < query.length; i++) {
            int q = query[i];
            int s = segment.get(ValueLayout.JAVA_BYTE, offset + i);
            int diff = q - s;
            sum += diff * diff;
        }
        return sum;
    }
}
