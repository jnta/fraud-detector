package com.jnta.vp;

import java.io.IOException;
import java.nio.file.Paths;

public class DebugBenchmark {
    public static void main(String[] args) throws IOException {
        System.out.println("Loading VpTree...");
        VpTree tree = VpTree.load(Paths.get("references.vpt"));
        System.out.println("Loaded VpTree, size=" + tree.size() + ", dims=" + tree.dims);
        
        System.out.println("Converting to LinearScan...");
        LinearScanEngine engine = tree.toLinearScan();
        System.out.println("Converted.");
        
        float[] query = new float[tree.dims];
        KnnQueue queue = new KnnQueue(10);
        System.out.println("Searching...");
        engine.search(query, queue);
        System.out.println("Search complete. Size=" + queue.size());
    }
}
