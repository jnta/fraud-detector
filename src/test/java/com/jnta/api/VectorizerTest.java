package com.jnta.api;

import com.jnta.risk.MccRiskProvider;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;

@MicronautTest
class VectorizerTest {

    @Inject
    TransactionVectorizer vectorizer;

    @Test
    @DisplayName("Should vectorize a complete transaction payload correctly")
    void testVectorization() {
        // 2026-03-11 is Wednesday. 
        // If Monday=0, Tuesday=1, Wednesday=2.
        TransactionPayload payload = new TransactionPayload(
            "tx-1",
            new TransactionPayload.TransactionData(100.0f, 1, OffsetDateTime.parse("2026-03-11T18:45:53Z")),
            new TransactionPayload.CustomerData(100.0f, 1, List.of("MERC-1")),
            new TransactionPayload.MerchantData("MERC-1", "5411", 100.0f),
            new TransactionPayload.TerminalData(false, true, 10.0f),
            new TransactionPayload.LastTransactionData(OffsetDateTime.parse("2026-03-11T18:40:53Z"), 5.0f)
        );

        float[] vector = vectorizer.vectorize(payload);

        Assertions.assertEquals(14, vector.length);
        Assertions.assertEquals(0.01f, vector[0], 0.0001f); // amount
        Assertions.assertEquals(1.0f/12.0f, vector[1], 0.0001f); // installments
        Assertions.assertEquals(0.1f, vector[2], 0.0001f); // amount_vs_avg
        Assertions.assertEquals(18.0f/23.0f, vector[3], 0.0001f); // hour
        Assertions.assertEquals(2.0f/6.0f, vector[4], 0.0001f); // day_of_week (Wednesday=2)
        Assertions.assertEquals(5.0f/1440.0f, vector[5], 0.0001f); // minutes_since_last
        Assertions.assertEquals(5.0f/1000.0f, vector[6], 0.0001f); // km_from_last
        Assertions.assertEquals(10.0f/1000.0f, vector[7], 0.0001f); // km_from_home
        Assertions.assertEquals(1.0f/20.0f, vector[8], 0.0001f); // tx_count_24h
        Assertions.assertEquals(0.0f, vector[9], 0.0001f); // is_online
        Assertions.assertEquals(1.0f, vector[10], 0.0001f); // card_present
        Assertions.assertEquals(0.0f, vector[11], 0.0001f); // unknown_merchant (MERC-1 is known)
        Assertions.assertEquals(0.5f, vector[12], 0.0001f); // mcc_risk (5411 = 0.5)
        Assertions.assertEquals(0.01f, vector[13], 0.0001f); // merchant_avg_amount: 100/10000 = 0.01
    }

    @Test
    @DisplayName("Should handle null last_transaction with -1 sentinel")
    void testNullLastTransaction() {
        TransactionPayload payload = new TransactionPayload(
            "tx-1",
            new TransactionPayload.TransactionData(100.0f, 1, OffsetDateTime.parse("2026-03-11T18:45:53Z")),
            new TransactionPayload.CustomerData(100.0f, 1, List.of("MERC-1")),
            new TransactionPayload.MerchantData("MERC-1", "5411", 100.0f),
            new TransactionPayload.TerminalData(false, true, 10.0f),
            null
        );

        float[] vector = vectorizer.vectorize(payload);

        Assertions.assertEquals(-1.0f, vector[5], 0.0001f); // minutes_since_last
        Assertions.assertEquals(-1.0f, vector[6], 0.0001f); // km_from_last
    }

    @Test
    @DisplayName("Should clamp values to [0, 1]")
    void testClamping() {
        TransactionPayload payload = new TransactionPayload(
            "tx-1",
            new TransactionPayload.TransactionData(20000.0f, 20, OffsetDateTime.parse("2026-03-11T18:45:53Z")),
            new TransactionPayload.CustomerData(1.0f, 100, List.of()),
            new TransactionPayload.MerchantData("MERC-2", "5411", 20000.0f),
            new TransactionPayload.TerminalData(true, false, 2000.0f),
            null
        );

        float[] vector = vectorizer.vectorize(payload);

        Assertions.assertEquals(1.0f, vector[0], 0.0001f); // amount
        Assertions.assertEquals(1.0f, vector[1], 0.0001f); // installments
        Assertions.assertEquals(1.0f, vector[2], 0.0001f); // amount_vs_avg
        Assertions.assertEquals(1.0f, vector[7], 0.0001f); // km_from_home
        Assertions.assertEquals(1.0f, vector[8], 0.0001f); // tx_count_24h
        Assertions.assertEquals(1.0f, vector[11], 0.0001f); // unknown_merchant
        Assertions.assertEquals(1.0f, vector[13], 0.0001f); // merchant_avg_amount
    }
}
