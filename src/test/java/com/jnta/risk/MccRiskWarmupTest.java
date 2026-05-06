package com.jnta.risk;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class MccRiskWarmupTest {
    @Inject
    MccRiskProvider provider;

    @Test
    void testWarmupDoesNotCrash() {
        provider.warmup();
    }
}
