#!/usr/bin/env bash
# Smoke test of a running service: health check + a selected endpoint.
# Usage: tests/ci/smoke.sh [base_url]   (default: http://localhost:8082)
set -euo pipefail

BASE="${1:-http://localhost:8082}"
TIMEOUT="${SMOKE_TIMEOUT:-120}"

fail() { echo "FAIL: $*" >&2; exit 1; }

# 1. Health check: wait until /actuator/health returns UP
deadline=$((SECONDS + TIMEOUT))
until curl -fsS "$BASE/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  (( SECONDS < deadline )) || fail "health check did not return UP within ${TIMEOUT}s ($BASE/actuator/health)"
  sleep 3
done
echo "OK: health check = UP"

# 2. Business endpoint: list of incidents (data seeded into the database).
# No Python needed: the response is a JSON array and every record has an "id" field.
body="$(curl -fsS "$BASE/incidents/all")" || fail "GET /incidents/all returned an error"
[[ "$body" == \[* ]] || fail "GET /incidents/all: the response is not a JSON array"
count="$(grep -o '"id":' <<< "$body" | wc -l | tr -d ' ')"
(( count > 0 )) || fail "GET /incidents/all: the list is empty, the seed was not loaded"
echo "OK: GET /incidents/all returned $count records"

# 3. Optional: config-server serves the configuration from the real repo (via the deploy key).
# Runs only when CONFIG_URL is set.
if [[ -n "${CONFIG_URL:-}" ]]; then
  deadline=$((SECONDS + TIMEOUT))
  until curl -fsS "$CONFIG_URL/auth-service/main" 2>/dev/null | grep -q 'CityFailurePredictor-ConfigServer'; do
    (( SECONDS < deadline )) || fail "config-server did not return the configuration from the repo ($CONFIG_URL/auth-service/main)"
    sleep 3
  done
  echo "OK: config-server returns the configuration from the CityFailurePredictor-ConfigServer repo"
fi

echo "SMOKE PASSED"
