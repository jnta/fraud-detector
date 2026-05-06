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

    private VpTree tree;

    @PostConstruct
    void init() throws IOException {
        Path path = Paths.get(vptPath);
        if (!Files.exists(path)) {
            LOG.warn("VP-Tree file not found at {}. Skipping initialization.", vptPath);
            return;
        }
        LOG.info("Loading VP-Tree from {}...", vptPath);
        this.tree = VpTree.load(path);
        LOG.info("Loaded VP-Tree with {} nodes.", tree.size());
    }

    @EventListener
    void onStartup(ServerStartupEvent event) {
        LOG.info("Warming up providers...");
        if (tree != null) {
            tree.warmup();
        }
        riskProvider.warmup();
        LOG.info("Warm-up complete.");
        readinessProvider.setReady(true);
    }

    @PreDestroy
    void close() {
        if (tree != null) {
            tree.close();
        }
    }

    public VpTree getTree() {
        return tree;
    }
}
