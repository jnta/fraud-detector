package com.jnta.risk;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@MicronautTest
class MccRiskProviderTest {

    @Inject
    MccRiskProvider riskProvider;

    @Test
    @DisplayName("MccRiskProvider should load scores from JSON and provide O(1) lookup")
    void testMccRiskScores() {
        // Assuming mcc_risk.json has 5411 and 5812
        Assertions.assertTrue(riskProvider.getRiskScore(5411) > 0);
        Assertions.assertTrue(riskProvider.getRiskScore(5812) > 0);
        Assertions.assertEquals(0.0f, riskProvider.getRiskScore(9999), 0.001f);
    }
}
