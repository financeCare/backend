#!/usr/bin/env bash
# Starts the backend against the isolated load-test Postgres/Redis (see docker-compose.yml)
# Usage: CACHE=on|off ./run-backend.sh   (off = every Redis read misses, see RedisReadDisabler note in README)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/financeCare_DB
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=56379
export SERVER_PORT=18080
export DEMO_SEED_ENABLED=true
# throwaway values for a local load test only (JWT_SECRET must be base64); never reuse real secrets here
export DB_PASSWORD=password123
export JWT_SECRET=bG9hZHRlc3Qtb25seS1zZWNyZXQta2V5LTAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm
export JOOBLE_API_KEY=loadtest
export MAIL_USERNAME=loadtest@example.com
export MAIL_PASSWORD=loadtest
export MINIO_SECRET_KEY=loadtest-minio-pw
export APP_MINIO_URL=http://127.0.0.1:59000
# async slip pipeline: RabbitMQ from docker-compose.yml, OCR answered by mock_ocr.py
export SLIP_ASYNC_ENABLED=true
export RABBITMQ_PORT=55672
export APP_OCR_URL=http://127.0.0.1:17860/slip/verify
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${POOL:-10}"
cd "$(dirname "$0")/.." && ./gradlew bootRun --no-daemon
