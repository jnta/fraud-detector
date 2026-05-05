package com.jnta.vp;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import java.nio.ByteBuffer;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class SimdDistance {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    public static float compute(float[] a, float[] b) {
        int i = 0;
        FloatVector sumVector = FloatVector.zero(SPECIES);
        int upperBound = SPECIES.loopBound(a.length);
        
        for (; i < upperBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            FloatVector diff = va.sub(vb);
            sumVector = diff.fma(diff, sumVector);
        }
        
        float sum = sumVector.reduceLanes(VectorOperators.ADD);
        
        for (; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        
        return (float) Math.sqrt(sum);
    }

    public static float compute(float[] a, ByteBuffer buffer, int offset) {
        MemorySegment segment = MemorySegment.ofBuffer(buffer);
        int i = 0;
        FloatVector sumVector = FloatVector.zero(SPECIES);
        int upperBound = SPECIES.loopBound(a.length);
        
        java.nio.ByteOrder le = java.nio.ByteOrder.LITTLE_ENDIAN;
        for (; i < upperBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromMemorySegment(SPECIES, segment, offset + i * 4, le);
            FloatVector diff = va.sub(vb);
            sumVector = diff.fma(diff, sumVector);
        }
        
        float sum = sumVector.reduceLanes(VectorOperators.ADD);
        
        for (; i < a.length; i++) {
            float diff = a[i] - segment.get(ValueLayout.JAVA_FLOAT.withOrder(le), offset + i * 4);
            sum += diff * diff;
        }
        
        return (float) Math.sqrt(sum);
    }
}
