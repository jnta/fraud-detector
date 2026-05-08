package com.jnta.api;

import com.jnta.search.SearchEngine;
import com.jnta.search.SearchService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class StartupWarmupTest {

    @TempDir
    Path tempDir;

    private void createDummyBinary(Path path, int numVectors) throws IOException {
        int dimsCount = 14;
        long fileSize = 0;
        for (int i = 0; i < dimsCount; i++) {
            long dimSize = numVectors * 4L;
            long padding = (64 - (dimSize % 64)) % 64;
            fileSize += dimSize + padding;
        }
        fileSize += numVectors;
        
        byte[] bytes = new byte[(int) fileSize];
        if (numVectors > 1) {
            bytes[(int) (fileSize - numVectors + 1)] = 1; // 2nd element is fraud
        }
        Files.write(path, bytes);
    }

    @Test
    void testStartupSetsReadyFlag() throws IOException {
        Path binPath = tempDir.resolve("test.bin");
        createDummyBinary(binPath, 2);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("vptree.path", binPath.toString()))) {
            
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            
            HttpStatus status = client.toBlocking().retrieve(HttpRequest.GET("/ready"), HttpStatus.class);
            Assertions.assertEquals(HttpStatus.OK, status);
            
            ReadinessProvider provider = server.getApplicationContext().getBean(ReadinessProvider.class);
            Assertions.assertTrue(provider.isReady());
        }
    }

    @Test
    void testSearchUsingMappedTree() throws IOException {
        Path binPath = tempDir.resolve("test_search.bin");
        createDummyBinary(binPath, 2);

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, 
                Map.of("vptree.path", binPath.toString()))) {
            
            SearchService service = server.getApplicationContext().getBean(SearchService.class);
            SearchEngine loadedEngine = service.getEngine();
            
            Assertions.assertNotNull(loadedEngine);
            Assertions.assertEquals(2, loadedEngine.size());
            Assertions.assertFalse(loadedEngine.isFraud(0));
            Assertions.assertTrue(loadedEngine.isFraud(1));
        }
    }
}
