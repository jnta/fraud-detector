package com.jnta.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;

@Controller("/ready")
public class ReadyController {

    @Inject
    ReadinessProvider readinessProvider;

    @Get
    public HttpResponse<String> ready() {
        if (readinessProvider.isReady()) {
            return HttpResponse.ok("OK");
        } else {
            return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("NOT READY");
        }
    }
}
