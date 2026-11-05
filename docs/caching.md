# Caching (Redis)

All caches are **cache-aside**: the service reads Redis first, on a miss it loads from the database / upstream API and
writes the result back. Redis is never required: every read and write is wrapped so a Redis outage falls back to the
source (and `CacheMetrics` counts it as a miss).

| Key | What | TTL | Invalidated by |
|---|---|---|---|
| `plans:monthly-status:{userId}` | amount to pay / paid / remaining this month | 5 min | debt add/update/delete, priority change, plan change, **payment** |
| `plans:strategies:{userId}` | minimum-payment sums and the strategy list | 7 days | same as above |
| `jooble:jobs:{keywords}:{location}` | job suggestions from the Jooble API | 6 h ±10% (10 min when empty) | TTL only |
| `occupations:recommended` | static occupation list | 30 days | TTL only |

OTP codes (`otp:{email}`, 5 min) are plain Redis TTL data, not a cache.

## Invalidation

Both plan caches depend on a user's debts, payments and plan, so every writer calls `PlanCacheEvictor.evictUserCache`.
Payments were missing from that list: after paying, "paid this month" stayed stale for up to 5 minutes and the safe
minimum payment (derived from the outstanding principal) for up to 7 days. Fixed in `RepaymentService.processDebtPayment`.

Inside a transaction the eviction runs **after commit**. Evicting earlier would let a concurrent read repopulate the
cache from the old, not yet committed data.

## Protection against stampede, penetration and synchronized expiry

Applied to the Jooble cache, the only one backed by a slow, rate-limited upstream:

- **Stampede (single flight):** on a miss one request takes a short Redis lock (`SET lock:{key} NX`, 20 s) and calls
  the API; concurrent requests for the same key wait up to 5 s for its result instead of calling the API too. If the
  loader is slow or fails, waiters fall back to calling the API themselves, and with Redis down every request loads
  directly. Verified against a real Redis: 20 simultaneous misses cause 1 upstream call
  (`RedisCacheIntegrationTest`).
- **Penetration:** a search with no results is cached for 10 minutes, so repeating it does not hit the API each time.
  API *errors* are not cached.
- **Synchronized expiry:** the TTL is 6 h ±10% random jitter, so keys written together do not expire together.

The per-user plan caches need none of this: keys are scoped to one user and recomputing them is a cheap database read.

## Measured effect

k6, 50 users, endpoints behind the plan caches: 167 → 374 req/s, p95 348 → 132 ms (details in `loadtest/README.md`).
Hit ratio is exported as `app_cache_requests_total{cache,result}` and shown in the Grafana dashboard
(`monitoring/`): close to 100% under that load.

## Not done

- No lock on the occupations cache (computed in-process in milliseconds, so a stampede is harmless).
- The Jooble TTL is not configurable; the 6 h / 10 min values are constants in `JoobleService`.
- No cache warm-up, no Redis Sentinel/Cluster (single instance).
