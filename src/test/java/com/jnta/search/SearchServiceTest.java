package com.jnta.search;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@MicronautTest
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class SearchServiceTest implements io.micronaut.test.support.TestPropertyProvider {

    @Inject
    SearchService service;

    @Override
    public java.util.Map<String, String> getProperties() {
        try {
            Files.createDirectories(Path.of("build"));
            Path binPath = Path.of("build/test-search-service.bin").toAbsolutePath();
            
            int numVectors = 1;
            int dimsCount = 14;
            
            long fileSize = 0;
            for (int i = 0; i < dimsCount; i++) {
                long dimSize = numVectors * 4L;
                long padding = (64 - (dimSize % 64)) % 64;
                fileSize += dimSize + padding;
            }
            fileSize += numVectors;
            
            byte[] bytes = new byte[(int) fileSize];
            Files.write(binPath, bytes);
            
            return java.util.Map.of("vptree.path", binPath.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testServiceReturnsSearchEngine() throws IOException {
        SearchEngine engine = service.getEngine();
        Assertions.assertNotNull(engine);
    }
}
