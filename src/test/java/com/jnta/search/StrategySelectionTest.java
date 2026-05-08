package com.jnta.search;

import com.jnta.search.linear.LinearScanEngine;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "search.strategy", value = "linear")
class StrategySelectionTest {

    @Inject
    SearchService service;

    @Test
    void testServiceUsesLinearStrategy() {
        SearchEngine engine = service.getEngine();
        Assertions.assertTrue(engine instanceof LinearScanEngine, 
            "Engine should be LinearScanEngine when strategy is 'linear', but was " + 
            (engine == null ? "null" : engine.getClass().getSimpleName()));
    }
}
