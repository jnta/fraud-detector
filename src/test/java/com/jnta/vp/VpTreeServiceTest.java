package com.jnta.vp;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@MicronautTest
class VpTreeServiceTest {

    @Inject
    VpTreeService service;

    @Test
    void testServiceReturnsSearchEngine() throws IOException {
        // The service should expose SearchEngine, not VpTree directly
        // This will fail to compile because getTree() returns VpTree
        SearchEngine engine = service.getEngine();
        Assertions.assertNotNull(engine);
    }
}
