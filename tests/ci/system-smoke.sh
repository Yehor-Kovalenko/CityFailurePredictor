#!/usr/bin/env bash
# Smoke test of the whole system started with docker compose.
# Assumes that the services are already running (docker compose up --wait).
# Usage: tests/ci/system-smoke.sh
set -euo pipefail
source "$(dirname "$0")/lib/in-network.sh"

TIMEOUT="${SMOKE_TIMEOUT:-300}"
GATEWAY="http://localhost:9191"
FRONTEND="http://localhost:3000"
AI="http://ai-service:8090"
EUREKA="http://service-registry:8761"
CONFIG="http://config-server:8888"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok()   { echo "OK: $*"; }

# Services that are Eureka clients (must match docker-compose.yml)
EXPECTED_APPS=(
  CONFIG-SERVER API-GATEWAY AUTH-SERVICE INCIDENT-SERVICE
  DATA-INGESTION-SERVICE DECISION-SERVICE REPORTING-SERVICE NOTIFICATION-SERVICE
)

INTERNAL_PORTS=(8082 8083 8084 8085 8086 8087 8761 8888 8090 9092 6379)

# 1. All services are registered in Eureka
deadline=$((SECONDS + TIMEOUT))
while true; do
  apps="$(net_curl -fsS "$EUREKA/eureka/apps" 2>/dev/null || true)"
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
ai_health="$(net_curl -fsS "$AI/health")" || fail "the AI service is not responding"
grep -q '"status":"UP"' <<< "$ai_health" || fail "the AI service is not UP"
ok "AI service = UP"

# 6. C3: the services cannot be called from outside the docker network, bypassing the gateway
for port in "${INTERNAL_PORTS[@]}"; do
  if (echo > "/dev/tcp/127.0.0.1/${port}") 2> /dev/null; then
    fail "port ${port} is reachable from the host, only the gateway may be published"
  fi
done
ok "internal ports are not published on the host (${INTERNAL_PORTS[*]})"

# 7. C5: the backend services speak only mutual TLS and register their https port in Eureka
grep -q '<securePort enabled="true">8082</securePort>' <<< "$(net_curl -fsS "$EUREKA/eureka/apps/INCIDENT-SERVICE")" \
  || fail "incident-service is not registered in Eureka with its secure port"
if net_curl -fsS -o /dev/null "http://incident-service:8082/incidents/all" 2> /dev/null; then
  fail "incident-service answers plain HTTP on its API port"
fi
if net_curl -fsS -o /dev/null --cacert "$CERTS_DIR/ca.crt" "https://incident-service:8082/incidents/all" 2> /dev/null; then
  fail "incident-service accepts a TLS client without a certificate"
fi
mtls_curl -fsS -o /dev/null "https://incident-service:8082/incidents/all" \
  || fail "incident-service rejects a client with a certificate from the internal CA"
ok "incident-service accepts only mTLS clients with a certificate from the internal CA"

# 8. Incident-service (health, data endpoint over mTLS) and config-server with the config repo
CONFIG_URL="$CONFIG" SMOKE_TIMEOUT="$TIMEOUT" bash "$(dirname "$0")/smoke.sh"

# 9. C4: the gateway throttles a burst from one client with 429 and records security events.
codes="$(curl -s -o /dev/null -w '%{http_code}\n' --parallel --parallel-max 50 "$GATEWAY/incidents/all?burst=[1-200]")"
grep -q '^429$' <<< "$codes" || fail "the gateway did not rate limit a burst of 200 requests"
metrics="$(curl -fsS "$GATEWAY/actuator/prometheus")" || fail "gateway metrics are not responding"
for type in AUTHENTICATION_FAILED RATE_LIMITED; do
  grep -q "^gateway_security_events_total{.*type=\"${type}\"" <<< "$metrics" \
    || fail "gateway_security_events_total{type=\"${type}\"} is missing"
done
ok "the gateway rate limits bursts (429) and counts security events"
sleep 5

echo "SYSTEM SMOKE PASSED"
