package com.jnta.search;

import com.jnta.api.ReadinessProvider;
import com.jnta.risk.MccRiskProvider;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
public class SearchService {
    private static final Logger LOG = LoggerFactory.getLogger(SearchService.class);

    @Value("${search.index.path:references.bin}")
    String indexPath;

    @Inject
    ReadinessProvider readinessProvider;

    @Inject
    MccRiskProvider riskProvider;

    private SearchEngine engine;

    @PostConstruct
    void init() throws IOException {
        Path path = Paths.get(indexPath);
        if (!Files.exists(path)) {
            LOG.warn("Search index file not found at {}. Skipping initialization.", indexPath);
            return;
        }
        
        LOG.info("Loading memory-mapped SIMD index (The Razor) from {}...", indexPath);
        this.engine = new com.jnta.search.linear.MappedSearchEngine(path);
        
        LOG.info("Search engine 'The Razor' initialized with {} nodes.", engine.size());
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
    }

    public SearchEngine getEngine() {
        return engine;
    }
}
