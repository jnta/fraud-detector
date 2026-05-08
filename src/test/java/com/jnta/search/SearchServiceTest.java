package com.jnta.search;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@MicronautTest
class SearchServiceTest {

    @Inject
    SearchService service;

    @Test
    void testServiceReturnsSearchEngine() throws IOException {
        SearchEngine engine = service.getEngine();
        Assertions.assertNotNull(engine);
    }
}
