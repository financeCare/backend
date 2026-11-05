# Load test: FinanceCare (Java / Spring Boot) vs YourPace (Go / Echo)

Same k6 shape for both: 50 virtual users, one login, then a loop over 5 authenticated read endpoints with 0.1 s think
time. Everything runs on one laptop (API, Postgres 16, Redis, k6), each API against its own throwaway database.
`loadtest/k6/api.js` (Java) and `loadtest/yourpace/api.js` + `run.sh` (Go; the YourPace source tree is only read).

## Results

| | req/s | avg | p95 | p99 | errors | memory |
|---|---|---|---|---|---|---|
| FinanceCare, Java 21, Spring Boot 3.5, after the optimisations (60 s) | 327-341 | 131 ms | 223-238 ms | 308 ms | 0% | ~350-490 MiB (JVM, in Kubernetes) |
| YourPace, Go, 15-20 s runs on a fresh database | 437 | 93 ms | 249 ms | 376 ms | 0% | ~85 MB (working set after a run) |
| YourPace, Go, 60 s runs | 414-441 | 92-99 ms | 246-253 ms | 344-410 ms | **0%, 13%, 20%, 22%** in four runs | |

## How to read this

- **The workloads are not the same.** Different endpoints, different queries, different data volume. The comparison
  says "similar order of magnitude on this machine", not "Go is faster than Java" or the reverse. Throughput of the Go
  API with errors excluded is about 25-30% higher and its memory about 4-5x lower; I would not claim more than that.
- **The Go figures are not clean.** Runs of 15-20 s were error-free, but three of four 60 s runs returned errors
  (401 / 404 / 500 on endpoints that return 200 when called one at a time). Failed requests are fast, which inflates
  req/s, so the 60 s throughput is optimistic.
- What I could confirm in the Postgres log of those runs: **113 `duplicate key value violates unique constraint
  "idx_user_water_date"`**, a race when several requests create the same user's water-log row for the day at the same
  time (`water/today`), and a 500 on `health/weight/history` at the start of the failures. I did not find why valid
  requests also got 401/404 under load, so that part is unexplained. These are findings in the YourPace project, not
  fixed here.
- The YourPace handler test suite also fails on an empty database (`there is no unique constraint matching given
  keys for referenced table "workout_schedules"`, 42 occurrences in the same log): it passes (22.2% handler coverage)
  only on a database that the application itself migrated first.
- Single runs and short durations; no warm-up beyond the first seconds; the Java numbers come from a different
  day/state of the machine than the Go ones.

## Reproduce

```bash
cd loadtest && docker compose up -d postgres redis
bash yourpace/run.sh                 # builds the Go server from C:/yp-src/backend, runs k6, prints memory
```
