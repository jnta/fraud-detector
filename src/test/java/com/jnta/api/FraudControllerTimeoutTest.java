package com.jnta.api;

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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@MicronautTest
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class FraudControllerTimeoutTest implements io.micronaut.test.support.TestPropertyProvider {

    @Inject
    @Client("/")
    HttpClient client;

    @Override
    public java.util.Map<String, String> getProperties() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
            Path vptPath = java.nio.file.Path.of("build/test-fraud-timeout.bin").toAbsolutePath();
            
            // To ensure the timeout loop runs, we need size > 8192
            int numVectors = 10000;
            int dimsCount = 14;
            
            // Build float[14][10000]
            float[][] data = new float[dimsCount][numVectors];
            boolean[] labels = new boolean[numVectors];
            
            for (int i = 0; i < numVectors; i++) {
                data[0][i] = (float) i / 10.0f; // Dimension 0
                // Mark everything as fraud. If it doesn't timeout, it would return a high fraud score and reject.
                // If it times out, it should clear queue and approve.
                labels[i] = true;
            }
            
            // Write to file
            long fileSize = 0;
            for (int i = 0; i < dimsCount; i++) {
                long dimSize = numVectors * 4L;
                long padding = (64 - (dimSize % 64)) % 64;
                fileSize += dimSize + padding;
            }
            fileSize += numVectors;
            
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize).order(ByteOrder.LITTLE_ENDIAN);
            
            for (int i = 0; i < dimsCount; i++) {
                for (int j = 0; j < numVectors; j++) {
                    buffer.putFloat(data[i][j]);
                }
                long dimSize = numVectors * 4L;
                long padding = (64 - (dimSize % 64)) % 64;
                for (int p = 0; p < padding; p++) {
                    buffer.put((byte) 0);
                }
            }
            for (int j = 0; j < numVectors; j++) {
                buffer.put((byte) (labels[j] ? 1 : 0));
            }
            buffer.flip();
            
            try (FileChannel fc = FileChannel.open(vptPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                fc.write(buffer);
            }
            
            return java.util.Map.of(
                "vptree.path", vptPath.toString(),
                "search.timeout-ms", "-1" // Immediate timeout
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Transaction should be APPROVED with score 0.0 when search times out early")
    void testTimeoutResultsInApproval() {
        Map<String, Object> payload = createPayload(100.0f);
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
