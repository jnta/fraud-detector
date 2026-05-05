package com.jnta.vp;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

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
        
        // Tail
        for (; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        
        return (float) Math.sqrt(sum);
    }
}
