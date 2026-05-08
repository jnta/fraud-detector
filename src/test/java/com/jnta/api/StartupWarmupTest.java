package com.jnta.api;

import com.jnta.search.SearchEngine;
import com.jnta.search.SearchService;
import com.jnta.search.vpt.VpTree;
import com.jnta.search.vpt.VpTreeBuilder;
import com.jnta.search.vpt.VpTreeIO;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class StartupWarmupTest {

    @TempDir
    Path tempDir;

    @Test
    void testStartupSetsReadyFlag() throws IOException {
        Path vptPath = tempDir.resolve("test.vpt");
        
        // Create a small tree
        VpTree tree = VpTreeBuilder.build(List.of(new float[7], new float[7]), new boolean[]{false, false});
        VpTreeIO.save(tree, vptPath);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("vptree.path", vptPath.toString()))) {
            
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            
            HttpStatus status = client.toBlocking().retrieve(HttpRequest.GET("/ready"), HttpStatus.class);
            Assertions.assertEquals(HttpStatus.OK, status);
            
            ReadinessProvider provider = server.getApplicationContext().getBean(ReadinessProvider.class);
            Assertions.assertTrue(provider.isReady());
        }
    }

    @Test
    void testSearchUsingMappedTree() throws IOException {
        Path vptPath = tempDir.resolve("test_search.vpt");
        float[] vec1 = new float[7];
        vec1[0] = 0.5f;
        float[] vec2 = new float[7];
        vec2[0] = 0.9f;
        
        VpTree tree = VpTreeBuilder.build(List.of(vec1, vec2), new boolean[]{false, true});
        VpTreeIO.save(tree, vptPath);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("vptree.path", vptPath.toString()))) {
            
            SearchService service = server.getApplicationContext().getBean(SearchService.class);
            SearchEngine loadedEngine = service.getEngine();
            
            Assertions.assertNotNull(loadedEngine);
            Assertions.assertEquals(2, loadedEngine.size());
            Assertions.assertFalse(loadedEngine.isFraud(0));
            Assertions.assertTrue(loadedEngine.isFraud(1));
        }
    }
}
