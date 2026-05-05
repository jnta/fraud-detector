package com.jnta.vp;

import org.openjdk.jmh.annotations.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DistanceBenchmark {

    private float[] a;
    private float[] b;

    @Setup
    public void setup() {
        int dims = 14;
        a = new float[dims];
        b = new float[dims];
        Random rnd = new Random();
        for (int i = 0; i < dims; i++) {
            a[i] = rnd.nextFloat();
            b[i] = rnd.nextFloat();
        }
    }

    @Benchmark
    public float scalar() {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    @Benchmark
    public float simd() {
        return SimdDistance.compute(a, b);
    }
}
