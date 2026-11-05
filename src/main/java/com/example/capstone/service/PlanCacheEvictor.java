package com.example.capstone.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Invalidates the per-user repayment-plan caches (strategies summary and monthly status). Both depend on the user's
 * debts, payments and plan, so every writer of those must call this. It lives in its own component so that
 * services which must not depend on {@link RepaymentPlanService} (payments) can use it too.
 *
 * <p>Inside a transaction the eviction runs after the commit: evicting earlier would let a concurrent read
 * repopulate the cache from the not yet committed (old) data.
 */
@Component
@RequiredArgsConstructor
public class PlanCacheEvictor {

    public static final String STRATEGIES_CACHE_PREFIX = "plans:strategies:";
    public static final String MONTHLY_STATUS_CACHE_PREFIX = "plans:monthly-status:";

    private final StringRedisTemplate redisTemplate;

    public void evictUserCache(UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(userId);
                }
            });
        } else {
            evictNow(userId);
        }
    }

    private void evictNow(UUID userId) {
        try {
            redisTemplate.delete(STRATEGIES_CACHE_PREFIX + userId);
            redisTemplate.delete(MONTHLY_STATUS_CACHE_PREFIX + userId);
        } catch (Exception e) {
            // the entries expire on their own (5 min / 7 days); a Redis outage must not fail the write
        }
    }
}
