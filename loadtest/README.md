# FinanceCare load test (k6)

Local, reproducible load test of the Spring Boot API with and without the Redis cache.

## Setup

- Machine: Windows laptop, backend (`./gradlew bootRun`), k6, and Docker all on the same box (so absolute numbers are conservative; compare runs with each other, not with production)
- Postgres 16 and Redis 7.2 in Docker (`docker-compose.yml`, ports 55433 / 56379), isolated from the real DB
- Data: the built-in demo user (`POST /auth/demo-login`, `DEMO_SEED_ENABLED=true`), one seeded user with sample debts and transactions
- Load: 50 virtual users, 30 s, 0.1 s think time, Hikari pool 10 (default)
- Cache OFF = Redis `maxmemory 1` + `noeviction`, so every read misses and every write fails (the service code already falls back to the database). No source change needed.

```bash
cd backend/loadtest
docker compose up -d
./run-backend.sh                       # starts the API on :18080
k6 run -e LABEL=my-run -e ONLY=monthly_status,strategies k6/api.js
```

## Results

Cached endpoints only (`/debts/monthly-status`, `/repayment-plans/strategies`), 50 VUs, 30 s:

| | req/s | avg | p95 | p99 | errors |
|---|---|---|---|---|---|
| Redis cache OFF | 167 | 246 ms | 348 ms | 404 ms | 0% |
| Redis cache ON | 374 | 81 ms | 132 ms | 179 ms | 0% |
| Change | 2.2x | -67% | -62% | -56% | |

Full home-screen mix (5 read endpoints, 60 s, 50 VUs):

| | req/s | avg | p95 | p99 |
|---|---|---|---|---|
| Redis cache OFF | 101 | 468 ms | 730 ms | 876 ms |
| Redis cache ON | 116 | 406 ms | 672 ms | 850 ms |

The mix improved less because three of the five endpoints (`/debts`, `/debts/overview-graph`, `/debts/priorities`) are not cached and dominate the time.

## After fixing the N+1 queries

Batched the per-debt queries (see "Changes" below), same load (home-screen mix, 60 s, 50 VUs):

| | req/s | avg | p95 | p99 |
|---|---|---|---|---|
| Before, cache ON | 116 | 406 ms | 672 ms | 850 ms |
| After, cache ON | 252 | 177 ms | 284 ms | 347 ms |
| Before, cache OFF | 101 | 468 ms | 730 ms | 876 ms |
| After, cache OFF | 217 | 208 ms | 322 ms | 391 ms |

About 2.2x throughput and 58% lower p95 with the cache on. Responses of `/debts`, `/debts/{id}/summary`, `/debts/overview-graph`, `/debts/priorities`, `/debts/monthly-status` and `/repayment-plans` were compared before and after for the demo user and are byte-identical. The unit test suite has the same 22 failures before and after (they existed before this change; `test-baseline-failures.txt`), so no new failures.

### Changes

- `DebtTransactionRepository`: grouped queries `sumOutstandingByDebtIds` / `sumMonthByDebtIds` (one row per debt and type) plus `outstandingByDebt` / `monthByDebt` helpers
- `DebtService`: `getOwnDebt` and `getDebtSummaryInternal` build all summaries with 2 queries instead of 7 per debt
- `RepaymentPlanService`: the simulation and monthly allocation preload the outstanding sums once instead of 3 queries per debt
- `DebtRepository`: `findAllByUserId` and `findByActiveAndUserId` use `JOIN FETCH` for `debtType` and `repaymentType`, removing the `debt_type` / `repayment_type` lookups per debt
- Originals (also `UserService`, `BudgetService`, `BudgetServiceTest`) are in `loadtest/backup-orig/` (the project is not under git)

## Round 2: per-request user lookup and category N+1

| | req/s | avg | p95 | p99 |
|---|---|---|---|---|
| Original, cache ON | 116 | 406 ms | 672 ms | 850 ms |
| Round 1 (debt queries), cache ON | 252 | 177 ms | 284 ms | 347 ms |
| Round 2, cache ON | 327 | 131 ms | 238 ms | 308 ms |
| Round 2, cache OFF | 270 | 163 ms | 261 ms | 330 ms |

Overall about 2.8x throughput and 65% lower p95 versus the original (cache ON). Responses are still byte-identical for the same endpoints, and the test suite still has only the same 22 pre-existing failures.

- `UserService`: `loadUserByUsername` and `extractUserIdFromToken` remember the user for the current HTTP request only (request attribute), so the auth filter and the service call share one query. Nothing is cached across requests, so password / email-confirm changes are never stale. Outside a request it queries as before.
- `BudgetService.getAmountFromBudget`: one `findByUserId` for categories, matched per budget in memory, instead of one query per budget. `BudgetServiceTest` was updated to stub `findByUserId` accordingly.
- Remaining user lookups are now ~1 per request. The next top queries are the debt list (fetched once per endpoint) and the grouped transaction sums, which are expected.


## Findings

1. **N+1 queries (fixed, see above).** `pg_stat_statements` shows about 36 `select sum(amount) from debt_transactions where debt_id=? and txn_type=?` plus about 18 `debt_type` lookups per home-screen iteration. Each query is only ~0.04 ms in Postgres, so the cost is round trips and per-request connection time, not slow SQL. Adding an index would not help; batching (one grouped query / `JOIN FETCH` / `@EntityGraph`) would.
2. **Auth filter hits the DB on every request** (`loadUserByUsername`), so even a Redis hit costs a database round trip (~80 ms avg under load). Caching the user lookup would raise the cached-endpoint numbers further.
3. **Hikari pool 10 to 30 gave no gain** (cached endpoints 374 to 301 req/s, mixed p95 672 to 933 ms), so the pool is not the bottleneck. The machine is CPU-bound with k6, JVM and other containers sharing it. Keep the default.
4. `spring.data.redis.host` in `application.properties` has a trailing space (`127.0.0.1 `). Spring trims it, but remove it.
5. Jooble cache TTL is 6 hours in code (`JoobleService`), not 1 hour.

## Caveats

- Single run per configuration, single machine, background containers running. Treat as indicative; repeat 3 times on a quiet machine before quoting precise numbers.
- Jooble was not called (needs a real API key); `/jobs/suggest` is not measured.
