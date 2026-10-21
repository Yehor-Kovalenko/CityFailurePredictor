#!/usr/bin/env bash
# Smoke test of the whole system started with docker compose.
# Assumes that the services are already running (docker compose up --wait).
# Usage: tests/ci/system-smoke.sh
set -euo pipefail

TIMEOUT="${SMOKE_TIMEOUT:-300}"
GATEWAY="http://localhost:9191"
FRONTEND="http://localhost:3000"
AI="http://localhost:8090"
EUREKA="http://localhost:8761"
CONFIG="http://localhost:8888"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok()   { echo "OK: $*"; }

# Services that are Eureka clients (must match docker-compose.yml)
EXPECTED_APPS=(
  CONFIG-SERVER API-GATEWAY AUTH-SERVICE INCIDENT-SERVICE
  DATA-INGESTION-SERVICE DECISION-SERVICE REPORTING-SERVICE NOTIFICATION-SERVICE
)

# 1. All services are registered in Eureka
deadline=$((SECONDS + TIMEOUT))
while true; do
  apps="$(curl -fsS "$EUREKA/eureka/apps" 2>/dev/null || true)"
  missing=()
  for app in "${EXPECTED_APPS[@]}"; do
    grep -q "<name>${app}</name>" <<< "$apps" || missing+=("$app")
  done
  (( ${#missing[@]} == 0 )) && break
  (( SECONDS < deadline )) || fail "not registered in Eureka: ${missing[*]}"
  sleep 5
done
ok "all ${#EXPECTED_APPS[@]} client services are registered in Eureka"

# 2. API gateway health
health="$(curl -fsS "$GATEWAY/actuator/health")" || fail "gateway health is not responding"
grep -q '"status":"UP"' <<< "$health" || fail "gateway health is not UP"
ok "gateway health = UP"

# 3. The gateway enforces authorization: a request without a token must get 401
code="$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/incidents/all")"
[[ "$code" == "401" ]] || fail "the gateway should return 401 without a token, it returned $code"
ok "the gateway rejects a request without a token (401)"

# 4. The frontend serves the application
code="$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND/")"
[[ "$code" == "200" ]] || fail "the frontend returned $code"
ok "the frontend returns 200"

# 5. AI service
ai_health="$(curl -fsS "$AI/health")" || fail "the AI service is not responding"
grep -q '"status":"UP"' <<< "$ai_health" || fail "the AI service is not UP"
ok "AI service = UP"

# 6. Incident-service (health, data endpoint) and config-server with the config repo
CONFIG_URL="$CONFIG" SMOKE_TIMEOUT="$TIMEOUT" bash "$(dirname "$0")/smoke.sh" http://localhost:8082

echo "SYSTEM SMOKE PASSED"
