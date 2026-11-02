#!/usr/bin/env bash
# Smoke test of the incident-service inside the running compose system: health check,
# a selected endpoint over mutual TLS and, optionally, the config-server.
# The service is not published on the host and its API accepts only mTLS,
# so every request runs from a probe container in the compose network.
# Usage: tests/ci/smoke.sh
# Env:   SMOKE_TIMEOUT (seconds, default: 120), CONFIG_URL (optional, e.g. http://config-server:8888)
set -euo pipefail
source "$(dirname "$0")/lib/in-network.sh"

HEALTH_URL="http://incident-service:8081/actuator/health"
API_URL="https://incident-service:8082"
TIMEOUT="${SMOKE_TIMEOUT:-120}"

fail() { echo "FAIL: $*" >&2; exit 1; }

# 1. Health check (management port): wait until /actuator/health returns UP
deadline=$((SECONDS + TIMEOUT))
until net_curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; do
  (( SECONDS < deadline )) || fail "health check did not return UP within ${TIMEOUT}s ($HEALTH_URL)"
  sleep 3
done
echo "OK: health check = UP"

# 2. Business endpoint over mTLS: list of incidents (data seeded into the database).
# No Python needed: the response is a JSON array and every record has an "id" field.
body="$(mtls_curl -fsS "$API_URL/incidents/all")" || fail "GET /incidents/all over mTLS returned an error"
[[ "$body" == \[* ]] || fail "GET /incidents/all: the response is not a JSON array"
count="$(grep -o '"id":' <<< "$body" | wc -l | tr -d ' ')"
(( count > 0 )) || fail "GET /incidents/all: the list is empty, the seed was not loaded"
echo "OK: GET /incidents/all over mTLS returned $count records"

# 3. Optional: config-server serves the configuration from the real repo (via the deploy key).
# Runs only when CONFIG_URL is set.
if [[ -n "${CONFIG_URL:-}" ]]; then
  deadline=$((SECONDS + TIMEOUT))
  until net_curl -fsS "$CONFIG_URL/auth-service/main" 2>/dev/null | grep -q 'CityFailurePredictor-ConfigServer'; do
    (( SECONDS < deadline )) || fail "config-server did not return the configuration from the repo ($CONFIG_URL/auth-service/main)"
    sleep 3
  done
  echo "OK: config-server returns the configuration from the CityFailurePredictor-ConfigServer repo"
fi

echo "SMOKE PASSED"
