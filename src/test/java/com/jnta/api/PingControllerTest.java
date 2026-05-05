package com.jnta.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@MicronautTest
class PingControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    @DisplayName("GET /ping should return 200 OK")
    void testPing() {
        HttpStatus status = client.toBlocking().retrieve(HttpRequest.GET("/ping"), HttpStatus.class);
        Assertions.assertEquals(HttpStatus.OK, status);
    }
}
