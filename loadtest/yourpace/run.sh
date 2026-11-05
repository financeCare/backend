#!/usr/bin/env bash
# Load test of the YourPace Go API (read-only use of its source tree) for the Java vs Go comparison.
#   ./loadtest/yourpace/run.sh [path-to-yourpace-backend]     default: C:/yp-src/backend
# Uses its own throwaway database (yp_load) in the loadtest Postgres/Redis, never the YourPace dev stack.
# Needs: docker compose up -d postgres redis (loadtest/), go, k6.
set -euo pipefail
YP=${1:-C:/yp-src/backend}
HERE="$(cd "$(dirname "$0")" && pwd)"
export TEST_PASSWORD=loadtest-password

cleanup() { taskkill //IM yourpace-server.exe //F >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup
docker exec lt-postgres psql -U root -d postgres -qc "DROP DATABASE IF EXISTS yp_load WITH (FORCE);" -c "CREATE DATABASE yp_load;"
docker exec lt-redis redis-cli FLUSHALL >/dev/null

# build once so startup and the first requests are not measured against `go run` compilation
(cd "$YP" && go build -o "$HERE/yourpace-server.exe" .)
(
  cd "$YP"
  DB_HOST=localhost DB_PORT=55433 DB_USER=root DB_PASSWORD=password123 DB_NAME=yp_load DB_SSLMODE=disable \
  REDIS_HOST=localhost REDIS_PORT=56379 JWT_SECRET=loadtest-only-secret-loadtest-only-secret PORT=18099 \
  nohup "$HERE/yourpace-server.exe" > "$HERE/server.log" 2>&1 &
)
# the server answers /health only after AutoMigrate and the seed ran; wait for the users table as well
for i in $(seq 1 60); do
  curl -s -o /dev/null localhost:18099/health     && [ "$(docker exec lt-postgres psql -U root -d yp_load -tAc "select to_regclass('public.users') is not null")" = "t" ] && break
  sleep 1
done

curl -s -o /dev/null -X POST localhost:18099/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"loadtest","email":"loadtest@example.com","password":"'"$TEST_PASSWORD"'","dob":"1995-01-01"}'
docker exec lt-postgres psql -U root -d yp_load -qc "UPDATE users SET email_confirm = true WHERE email = 'loadtest@example.com';"

cd "$HERE/.."
mkdir -p results
k6 run -q -e LABEL=yourpace-go-50vu -e VUS=${VUS:-50} -e DURATION=${DURATION:-60s} yourpace/api.js

echo "memory after the run (Windows working set):"
tasklist //FI "IMAGENAME eq yourpace-server.exe" //FO CSV //NH | cut -d, -f1,5
taskkill //IM yourpace-server.exe //F >/dev/null 2>&1 || true
