package com.jnta.search;

import com.jnta.api.ReadinessProvider;
import com.jnta.risk.MccRiskProvider;
import com.jnta.search.linear.LinearScanEngine;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Singleton
public class SearchService {
    private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

    @Value("${vptree.path:references.bin}")
    String vptPath;

    @Inject
    ReadinessProvider readinessProvider;

    @Inject
    MccRiskProvider riskProvider;

    @Value("${search.strategy:linear}")
    String strategy;

    private SearchEngine engine;
    private Arena arena;

    @PostConstruct
    void init() throws IOException {
        Path path = Paths.get(vptPath);
        if (!Files.exists(path)) {
            LOG.warn("Search index file not found at {}. Skipping initialization.", vptPath);
            return;
        }
        
        LOG.info("Loading Search Index from {}...", vptPath);
        
        long fileSize = Files.size(path);
        int dimsCount = 14;
        
        // Find N
        int size = -1;
        int approxN = (int) (fileSize / 57);
        for (int n = Math.max(0, approxN - 100); n <= approxN + 100; n++) {
            long expectedSize = 0;
            for (int i = 0; i < dimsCount; i++) {
                long dimSize = n * 4L;
                long padding = (64 - (dimSize % 64)) % 64;
                expectedSize += dimSize + padding;
            }
            expectedSize += n; // fraud flags
            if (expectedSize == fileSize) {
                size = n;
                break;
            }
        }
        if (size == -1) {
            throw new IOException("Cannot determine vector count from file size " + fileSize);
        }

        this.arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
            this.engine = new LinearScanEngine(segment, size, dimsCount);
        }
        
        LOG.info("Search strategy 'linear' initialized with {} nodes.", engine.size());
    }

    @EventListener
    void onStartup(ServerStartupEvent event) {
        LOG.info("Warming up providers...");
        riskProvider.warmup();
        LOG.info("Warm-up complete.");
        readinessProvider.setReady(true);
    }

    @PreDestroy
    void close() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                LOG.error("Error closing search engine", e);
            }
        }
        if (arena != null) {
            arena.close();
        }
    }

    public SearchEngine getEngine() {
        return engine;
    }
}
