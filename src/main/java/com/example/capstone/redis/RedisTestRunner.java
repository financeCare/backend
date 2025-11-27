package com.example.capstone.redis;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTestRunner implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;

    public RedisTestRunner(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        redisTemplate.opsForValue().set("testkey", "hello from spring");
        String value = redisTemplate.opsForValue().get("testkey");
        System.out.println("Redis test value: " + value);
    }
}
