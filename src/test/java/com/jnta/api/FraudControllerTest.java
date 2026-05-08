package com.jnta.api;

import com.jnta.search.vpt.VpTree;
import com.jnta.search.vpt.VpTreeBuilder;
import com.jnta.search.vpt.VpTreeIO;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@MicronautTest
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class FraudControllerTest implements io.micronaut.test.support.TestPropertyProvider {

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public java.util.Map<String, String> getProperties() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
            Path vptPath = java.nio.file.Path.of("build/test-fraud.vpt").toAbsolutePath();
            List<float[]> vectors = new java.util.ArrayList<>();
            boolean[] labels = new boolean[10];
            for (int i = 0; i < 10; i++) {
                float[] v = new float[7]; // 7D vector
                java.util.Arrays.fill(v, 0.0f);
                v[0] = (float) i / 10.0f; // 0.0, 0.1, ..., 0.9
                vectors.add(v);
                labels[i] = (i >= 5);
            }
            VpTree tree = VpTreeBuilder.build(vectors, labels);
            VpTreeIO.save(tree, vptPath);
            return java.util.Map.of("vptree.path", vptPath.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("POST /fraud-score should return 200 OK and correct JSON structure")
    void testFraudScoreEndpoint() {
        Map<String, Object> payload = createPayload(100.0f);
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST("/fraud-score", payload), Map.class);
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("Transaction should be REJECTED when fraud_score >= 0.6")
    void testRejection() {
        Map<String, Object> payload = createPayload(0.8f); 
        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertEquals(1.0f, score, 0.01f);
        Assertions.assertFalse((Boolean) body.get("approved"));
    }

    @Test
    @DisplayName("Transaction should be APPROVED when fraud_score < 0.6")
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
