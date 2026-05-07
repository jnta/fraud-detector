package com.jnta.vp;

import com.jnta.api.ReadinessProvider;
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
public class VpTreeService {
    private static final Logger LOG = LoggerFactory.getLogger(VpTreeService.class);

    @Value("${vptree.path:references.vpt}")
    String vptPath;

    @Inject
    ReadinessProvider readinessProvider;

    @Inject
    com.jnta.risk.MccRiskProvider riskProvider;

    @Value("${vptree.cache.size:1000000}")
    int cacheSize;

    @Value("${search.strategy:vptree}")
    String strategy;

    private SearchEngine engine;

    @PostConstruct
    void init() throws IOException {
        Path path = Paths.get(vptPath);
        if (!Files.exists(path)) {
            LOG.warn("VP-Tree file not found at {}. Skipping initialization.", vptPath);
            return;
        }
        LOG.info("Loading VP-Tree from {}...", vptPath);
        VpTree tree = VpTree.load(path);
        
        if ("linear".equalsIgnoreCase(strategy)) {
            LOG.info("Initializing LinearScanEngine strategy...");
            this.engine = tree.toLinearScan();
            // In linear strategy, we don't need the tree or cache anymore
            tree.close(); 
        } else {
            LOG.info("Initializing VpTree strategy...");
            this.engine = tree;
            if (cacheSize > 0) {
                LOG.info("Initializing Hot Node Cache (size={})...", cacheSize);
                HotNodeCache cache = new HotNodeCache(tree, cacheSize);
                tree.setHotNodeCache(cache);
                LOG.info("Hot Node Cache initialized with {} nodes.", cache.getCapacity());
            }
        }
        
        LOG.info("Search strategy '{}' initialized with {} nodes.", strategy, engine.size());
    }

    @EventListener
    void onStartup(ServerStartupEvent event) {
        LOG.info("Warming up providers...");
        if (engine != null) {
            if (engine instanceof VpTree) {
                ((VpTree) engine).warmup();
            }
        }
        riskProvider.warmup();
        LOG.info("Warm-up complete.");
        readinessProvider.setReady(true);
    }

    @PreDestroy
    void close() {
        if (engine != null) {
            engine.close();
        }
    }

    public SearchEngine getEngine() {
        return engine;
    }
}
