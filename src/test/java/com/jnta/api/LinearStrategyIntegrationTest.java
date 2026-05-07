package com.jnta.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jnta.vp.VpTree;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@MicronautTest
@Property(name = "search.strategy", value = "linear")
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class LinearStrategyIntegrationTest implements io.micronaut.test.support.TestPropertyProvider {

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public java.util.Map<String, String> getProperties() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
            Path vptPath = java.nio.file.Path.of("build/test-fraud-linear.vpt").toAbsolutePath();
            float[][] vectors = new float[10][7]; // 7 dims
            boolean[] labels = new boolean[10];
            for (int i = 0; i < 10; i++) {
                java.util.Arrays.fill(vectors[i], 0.0f);
                vectors[i][0] = (float) i / 10.0f; 
                labels[i] = (i >= 5);
            }
            VpTree tree = VpTree.build(java.util.Arrays.asList(vectors), labels);
            tree.save(vptPath);
            return java.util.Map.of(
                "vptree.path", vptPath.toString(),
                "search.strategy", "linear"
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Linear Strategy: Transaction should be REJECTED when fraud_score >= 0.6")
    void testRejection() {
        Map<String, Object> payload = createPayload(0.8f); 
        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertEquals(1.0f, score, 0.01f);
        Assertions.assertFalse((Boolean) body.get("approved"));
    }

    @Test
    @DisplayName("Linear Strategy: Transaction should be APPROVED when fraud_score < 0.6")
    void testApproval() {
        Map<String, Object> payload = createPayload(0.2f); 
        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertEquals(0.0f, score, 0.01f);
        Assertions.assertTrue((Boolean) body.get("approved"));
    }

    private Map<String, Object> createPayload(float amount) {
        return Map.of(
            "transaction", Map.of("amount", amount * 10000.0f, "installments", 1, "requested_at", OffsetDateTime.now().toString()),
            "customer", Map.of("avg_amount", 500.0f, "tx_count_24h", 2, "known_merchants", List.of("m-1")),
            "merchant", Map.of("id", "m-1", "mcc", "5411", "avg_amount", 100.0f),
            "terminal", Map.of("is_online", true, "card_present", true, "km_from_home", 5.0f)
        );
    }
}
