package com.jnta.api;

import com.jnta.search.SearchEngine;
import com.jnta.search.SearchService;
import com.jnta.search.vpt.Preprocessor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

class StartupWarmupTest {

    @TempDir
    Path tempDir;

    @Test
    void testStartupSetsReadyFlag() throws IOException {
        Path inputPath = tempDir.resolve("test.json.gz");
        Path binPath = tempDir.resolve("test.bin").toAbsolutePath();
        
        // Create 14D vector JSON
        String json = "[{\"id\": 1, \"vector\": [0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}]";
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(inputPath.toFile()))) {
            gzos.write(json.getBytes());
        }
        Preprocessor.main(new String[]{inputPath.toString(), binPath.toString()});

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("search.index.path", binPath.toString()))) {
            
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            
            HttpStatus status = client.toBlocking().retrieve(HttpRequest.GET("/ready"), HttpStatus.class);
            Assertions.assertEquals(HttpStatus.OK, status);
            
            ReadinessProvider provider = server.getApplicationContext().getBean(ReadinessProvider.class);
            Assertions.assertTrue(provider.isReady());
        }
    }

    @Test
    void testSearchUsingMappedTree() throws IOException {
        Path inputPath = tempDir.resolve("test_search.json.gz");
        Path binPath = tempDir.resolve("test_search.bin").toAbsolutePath();
        
        // Create two 14D vectors: vec1 (clean), vec2 (fraud)
        String json = "[" +
            "{\"id\": 0, \"is_fraud\": false, \"vector\": [0.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}," +
            "{\"id\": 1, \"is_fraud\": true, \"vector\": [0.9,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}" +
            "]";
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(inputPath.toFile()))) {
            gzos.write(json.getBytes());
        }
        Preprocessor.main(new String[]{inputPath.toString(), binPath.toString()});

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("search.index.path", binPath.toString()))) {
            
            SearchService service = server.getApplicationContext().getBean(SearchService.class);
            SearchEngine loadedEngine = service.getEngine();
            
            Assertions.assertNotNull(loadedEngine);
            Assertions.assertEquals(2, loadedEngine.size());
            Assertions.assertFalse(loadedEngine.isFraud(0));
            Assertions.assertTrue(loadedEngine.isFraud(1));
        }
    }
}
