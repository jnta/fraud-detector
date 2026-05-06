package com.jnta.api;

import com.jnta.vp.VpTree;
import com.jnta.vp.VpTreeService;
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
        VpTree tree = VpTree.build(List.of(new float[14], new float[14]), new boolean[]{false, false});
        tree.save(vptPath);

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
        float[] vec1 = new float[14];
        vec1[0] = 0.5f;
        float[] vec2 = new float[14];
        vec2[0] = 0.9f;
        
        VpTree tree = VpTree.build(List.of(vec1, vec2), new boolean[]{false, false});
        tree.save(vptPath);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("vptree.path", vptPath.toString()))) {
            
            VpTreeService service = server.getApplicationContext().getBean(VpTreeService.class);
            VpTree loadedTree = service.getTree();
            
            Assertions.assertNotNull(loadedTree);
            Assertions.assertEquals(2, loadedTree.size());
            
            float[] v1 = loadedTree.getVector(0);
            Assertions.assertEquals(0.5f, v1[0], 0.01f);
            
            float[] v2 = loadedTree.getVector(1);
            Assertions.assertEquals(0.9f, v2[0], 0.01f);
        }
    }
}
