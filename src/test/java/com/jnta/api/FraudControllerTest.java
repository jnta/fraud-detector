package com.jnta.api;

import com.jnta.search.vpt.Preprocessor;
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

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

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
            Path inputPath = java.nio.file.Path.of("build/test-refs.json.gz");
            Path binPath = java.nio.file.Path.of("build/test-fraud.bin").toAbsolutePath();
            
            // Generate 10 transactions: 0..4 are clean, 5..9 are fraud
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 10; i++) {
                float amountVal = (float) i / 10.0f; // This will be vectorized
                // We mock the vectorization by providing the expected vector in JSON
                // However, Preprocessor reads the "vector" field from JSON
                sb.append("{\"id\": ").append(i).append(", \"is_fraud\": ").append(i >= 5).append(", \"vector\": [");
                for (int d = 0; d < 14; d++) {
                    sb.append(d == 0 ? amountVal : 0.0f); // Put amount in dimension 0
                    if (d < 13) sb.append(",");
                }
                sb.append("]}");
                if (i < 9) sb.append(",");
            }
            sb.append("]");
            
            try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(inputPath.toFile()))) {
                gzos.write(sb.toString().getBytes());
            }
            
            Preprocessor.main(new String[]{inputPath.toString(), binPath.toString()});
            
            return java.util.Map.of("search.index.path", binPath.toString());
        } catch (Exception e) {
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
        // Query with amount ~0.8 should be close to fraud vectors (5..9)
        Map<String, Object> payload = createPayload(8000.0f); 
        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertTrue(score >= 0.6f, "Score should be >= 0.6 for fraud, was " + score);
        Assertions.assertFalse((Boolean) body.get("approved"));
    }

    @Test
    @DisplayName("Transaction should be APPROVED when fraud_score < 0.6")
    void testApproval() {
        // Query with amount ~0.2 should be close to clean vectors (0..4)
        Map<String, Object> payload = createPayload(2000.0f); 
        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertTrue(score < 0.6f, "Score should be < 0.6 for clean tx, was " + score);
        Assertions.assertTrue((Boolean) body.get("approved"));
    }

    private Map<String, Object> createPayload(float amount) {
        return Map.of(
            "transaction", Map.of("amount", amount, "installments", 1, "requested_at", OffsetDateTime.now().toString()),
            "customer", Map.of("avg_amount", 500.0f, "tx_count_24h", 2, "known_merchants", List.of("m-1")),
            "merchant", Map.of("id", "m-1", "mcc", "5411", "avg_amount", 100.0f),
            "terminal", Map.of("is_online", true, "card_present", true, "km_from_home", 5.0f)
        );
    }
}
