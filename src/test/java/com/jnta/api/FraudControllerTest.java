package com.jnta.api;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jnta.vp.VpTree;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@MicronautTest
@Property(name = "vptree.path", value = "build/test-fraud.vpt")
class FraudControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @BeforeAll
    static void setup(@TempDir Path tempDir) throws IOException {
        // Create a small tree with 10 vectors.
        // Neighbors 0-4 are LEGIT (false), 5-9 are FRAUD (true).
        float[][] vectors = new float[10][14];
        boolean[] labels = new boolean[10];
        for (int i = 0; i < 10; i++) {
            java.util.Arrays.fill(vectors[i], 0.0f);
            vectors[i][0] = (float) i / 10.0f; // 0.0, 0.1, ..., 0.9
            labels[i] = (i >= 5);
        }
        VpTree tree = VpTree.build(java.util.Arrays.asList(vectors), labels);
        tree.save(Path.of("build/test-fraud.vpt"));
    }

    @Test
    @DisplayName("POST /fraud-score should return 200 OK and correct JSON structure")
    void testFraudScoreEndpoint() {
        TransactionPayload payload = createPayload(100.0f); // distance logic is complex, but let's test structure first
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST("/fraud-score", payload), Map.class);
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("Transaction should be REJECTED when fraud_score >= 0.6")
    void testRejection() {
        // Close to 0.8 (Vector 8) -> Neighbors should be 6, 7, 8, 9, 5 (all fraud)
        TransactionPayload payload = createPayload(0.8f); 
        
        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertEquals(1.0f, score, 0.01f);
        Assertions.assertFalse((Boolean) body.get("approved"));
    }

    @Test
    @DisplayName("Transaction should be APPROVED when fraud_score < 0.6")
    void testApproval() {
        // Close to 0.2 (Vector 2) -> Neighbors should be 0, 1, 2, 3, 4 (all legit)
        TransactionPayload payload = createPayload(0.2f); 

        Map body = client.toBlocking().retrieve(HttpRequest.POST("/fraud-score", payload), Map.class);
        
        float score = ((Double) body.get("fraud_score")).floatValue();
        Assertions.assertEquals(0.0f, score, 0.01f);
        Assertions.assertTrue((Boolean) body.get("approved"));
    }

    private TransactionPayload createPayload(float amount) {
        return new TransactionPayload(
            "tx-123",
            new TransactionPayload.TransactionData(amount * 10000.0f, 1, OffsetDateTime.now()), // normalized to amount
            new TransactionPayload.CustomerData(500.0f, 2, List.of("m-1")),
            new TransactionPayload.MerchantData("m-1", "5411", 100.0f),
            new TransactionPayload.TerminalData(true, true, 5.0f),
            null
        );
    }
}
