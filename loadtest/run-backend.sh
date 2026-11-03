#!/usr/bin/env bash
# Starts the backend against the isolated load-test Postgres/Redis (see docker-compose.yml)
# Usage: CACHE=on|off ./run-backend.sh   (off = every Redis read misses, see RedisReadDisabler note in README)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/financeCare_DB
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=56379
export SERVER_PORT=18080
export DEMO_SEED_ENABLED=true
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${POOL:-10}"
cd "$(dirname "$0")/.." && ./gradlew bootRun --no-daemon
