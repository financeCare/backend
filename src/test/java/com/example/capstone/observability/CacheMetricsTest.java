package com.example.capstone.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CacheMetrics metrics = new CacheMetrics(registry);

    private double count(String cache, String result) {
        return registry.counter("app.cache.requests", "cache", cache, "result", result).count();
    }

    @Test
    void countsHitsAndMissesSeparatelyPerCache() {
        metrics.hit("monthly_status");
        metrics.hit("monthly_status");
        metrics.miss("monthly_status");
        metrics.miss("jooble_jobs");

        assertEquals(2.0, count("monthly_status", "hit"));
        assertEquals(1.0, count("monthly_status", "miss"));
        assertEquals(1.0, count("jooble_jobs", "miss"));
        assertEquals(0.0, count("jooble_jobs", "hit"));
    }
}
