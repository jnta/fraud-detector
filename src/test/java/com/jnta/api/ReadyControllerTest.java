package com.jnta.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@MicronautTest
class ReadyControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ReadinessProvider readinessProvider;

    @Test
    @DisplayName("GET /ready should return 503 Service Unavailable initially")
    void testReadyInitially() {
        readinessProvider.setReady(false);
        HttpClientResponseException exception = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.GET("/ready"))
        );
        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    @DisplayName("GET /ready should return 200 OK after warm-up")
    void testReadyAfterWarmup() {
        readinessProvider.setReady(true);
        HttpStatus status = client.toBlocking().retrieve(HttpRequest.GET("/ready"), HttpStatus.class);
        Assertions.assertEquals(HttpStatus.OK, status);
    }
}
