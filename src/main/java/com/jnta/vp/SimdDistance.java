package com.jnta.vp;

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



    public static long compute(short[] query, short[] array, int offset) {
        long sum = 0;
        int i = 0;
        int length = query.length;
        
        for (; i <= length - 4; i += 4) {
            long q0 = query[i];
            long s0 = array[offset + i];
            long d0 = q0 - s0;
            
            long q1 = query[i + 1];
            long s1 = array[offset + i + 1];
            long d1 = q1 - s1;
            
            long q2 = query[i + 2];
            long s2 = array[offset + i + 2];
            long d2 = q2 - s2;
            
            long q3 = query[i + 3];
            long s3 = array[offset + i + 3];
            long d3 = q3 - s3;
            
            sum += (d0 * d0) + (d1 * d1) + (d2 * d2) + (d3 * d3);
        }
        
        for (; i < length; i++) {
            long q = query[i];
            long s = array[offset + i];
            long d = q - s;
            sum += d * d;
        }
        return sum;
    }



    public static long compute7D(short[] query, short[] array, int offset) {
        long d0 = (long) query[0] - array[offset];
        long d1 = (long) query[1] - array[offset + 1];
        long d2 = (long) query[2] - array[offset + 2];
        long d3 = (long) query[3] - array[offset + 3];
        long d4 = (long) query[4] - array[offset + 4];
        long d5 = (long) query[5] - array[offset + 5];
        long d6 = (long) query[6] - array[offset + 6];
        return (d0 * d0) + (d1 * d1) + (d2 * d2) + (d3 * d3) + (d4 * d4) + (d5 * d5) + (d6 * d6);
    }
}
