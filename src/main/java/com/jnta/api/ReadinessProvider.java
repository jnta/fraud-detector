package com.jnta.api;

import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ReadinessProvider {
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    public void setReady(boolean value) {
        ready.set(value);
    }
}
