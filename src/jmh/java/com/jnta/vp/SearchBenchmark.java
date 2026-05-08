package com.jnta.vp;

import org.openjdk.jmh.annotations.*;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class SearchBenchmark {

    private VpTree vptree;
    private LinearScanEngine linear;
    private float[][] queries;
    private Random random = new Random(42);
    private int queryIdx = 0;

    @Setup
    public void setup() throws IOException {
        System.out.println("DEBUG: Setup SearchBenchmark...");
        String path = "references.vpt";
        vptree = VpTree.load(Paths.get(path));
        linear = vptree.toLinearScan();
        System.out.println("DEBUG: Loaded data, size=" + vptree.size());
        
        // Use 1M hot node cache for VPTree to be fair
        HotNodeCache cache = new HotNodeCache(vptree, 1000000);
        vptree.setHotNodeCache(cache);

        int d = vptree.dims;
        queries = new float[1000][d];
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j < d; j++) {
                queries[i][j] = random.nextFloat();
            }
        }
    }

    @Benchmark
    public Object testVpTree() {
        KnnQueue queue = new KnnQueue(10);
        vptree.search(queries[queryIdx++ % 1000], queue);
        return queue;
    }

    @Benchmark
    public Object testLinearScan() {
        KnnQueue queue = new KnnQueue(10);
        linear.search(queries[queryIdx++ % 1000], queue);
        return queue;
    }
}
