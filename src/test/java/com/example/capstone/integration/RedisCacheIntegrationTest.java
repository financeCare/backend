package com.example.capstone.integration;

import com.example.capstone.dto.JobSuggestionRequest;
import com.example.capstone.observability.CacheMetrics;
import com.example.capstone.service.JoobleService;
import com.example.capstone.service.PlanCacheEvictor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Redis cache behaviour against a real Redis (skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class RedisCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private JoobleService joobleService(RestTemplate restTemplate) {
        JoobleService service = new JoobleService(restTemplate, new ObjectMapper(), redisTemplate, new CacheMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "joobleUrl", "https://jooble.example/api/");
        return service;
    }

    private JobSuggestionRequest request(String keywords) {
        JobSuggestionRequest request = new JobSuggestionRequest();
        request.setKeywords(keywords);
        request.setLocation("Bangkok");
        request.setExtraIncomeNeeded(1000.0);
        return request;
    }

    @Test
    void stampede_concurrentRequestsForAnExpiredKeyCallTheApiOnce() throws Exception {
        AtomicInteger apiCalls = new AtomicInteger();
        RestTemplate slowApi = mock(RestTemplate.class);
        when(slowApi.postForObject(anyString(), any(), any())).thenAnswer(inv -> {
            apiCalls.incrementAndGet();
            Thread.sleep(800);   // a slow upstream is what makes the herd pile up
            return null;         // "no jobs": empty result, which is cached as well
        });
        JoobleService service = joobleService(slowApi);

        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.getSuggestedJobs(request("popular search"));
            }));
        }
        start.countDown();
        for (Future<?> f : results) {
            assertNotNull(f.get());
        }
        pool.shutdown();

        assertEquals(1, apiCalls.get(), "20 simultaneous misses must produce one upstream call");
    }

    @Test
    void emptyResultIsCachedBrieflyAndTheLockIsReleased() {
        AtomicInteger apiCalls = new AtomicInteger();
        RestTemplate api = mock(RestTemplate.class);
        when(api.postForObject(anyString(), any(), any())).thenAnswer(inv -> {
            apiCalls.incrementAndGet();
            return null;
        });
        JoobleService service = joobleService(api);

        service.getSuggestedJobs(request("nothing matches"));
        service.getSuggestedJobs(request("nothing matches"));

        assertEquals(1, apiCalls.get(), "the second identical empty search is answered from the cache");
        var keys = redisTemplate.keys("jooble:jobs:*");
        assertEquals(1, keys.size());
        Long ttlSeconds = redisTemplate.getExpire(keys.iterator().next());
        assertTrue(ttlSeconds > 0 && ttlSeconds <= 600, "empty results live 10 minutes at most, was " + ttlSeconds);
        assertTrue(redisTemplate.keys("lock:*").isEmpty(), "the loader lock must be released");
    }

    @Test
    void apiErrorsAreNotCached() {
        AtomicInteger apiCalls = new AtomicInteger();
        RestTemplate failing = mock(RestTemplate.class);
        when(failing.postForObject(anyString(), any(), any())).thenAnswer(inv -> {
            apiCalls.incrementAndGet();
            throw new IllegalStateException("upstream down");
        });
        JoobleService service = joobleService(failing);

        service.getSuggestedJobs(request("flaky"));
        service.getSuggestedJobs(request("flaky"));

        assertEquals(2, apiCalls.get(), "an error must not be remembered as an empty result");
        assertTrue(redisTemplate.keys("jooble:jobs:*").isEmpty());
        assertTrue(redisTemplate.keys("lock:*").isEmpty(), "the lock is released even when the API fails");
    }

    @Test
    void evictorDeletesBothPlanCachesForTheUserOnly() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        redisTemplate.opsForValue().set(PlanCacheEvictor.STRATEGIES_CACHE_PREFIX + user, "a");
        redisTemplate.opsForValue().set(PlanCacheEvictor.MONTHLY_STATUS_CACHE_PREFIX + user, "b");
        redisTemplate.opsForValue().set(PlanCacheEvictor.MONTHLY_STATUS_CACHE_PREFIX + other, "c");

        new PlanCacheEvictor(redisTemplate).evictUserCache(user);

        assertNull(redisTemplate.opsForValue().get(PlanCacheEvictor.STRATEGIES_CACHE_PREFIX + user));
        assertNull(redisTemplate.opsForValue().get(PlanCacheEvictor.MONTHLY_STATUS_CACHE_PREFIX + user));
        assertEquals("c", redisTemplate.opsForValue().get(PlanCacheEvictor.MONTHLY_STATUS_CACHE_PREFIX + other));
    }
}
