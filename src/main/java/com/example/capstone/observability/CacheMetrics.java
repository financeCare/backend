package com.example.capstone.observability;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Counts Redis cache lookups so the hit ratio shows up in Prometheus:
 * {@code app_cache_requests_total{cache="...", result="hit|miss"}}.
 */
@Component
@RequiredArgsConstructor
public class CacheMetrics {

    private final MeterRegistry registry;

    public void hit(String cache) {
        registry.counter("app.cache.requests", "cache", cache, "result", "hit").increment();
    }

    public void miss(String cache) {
        registry.counter("app.cache.requests", "cache", cache, "result", "miss").increment();
    }
}
