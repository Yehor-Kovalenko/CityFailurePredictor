#!/usr/bin/env bash
# Smoke test uruchomionej usługi: health check + wybrany endpoint.
# Usage: tests/ci/smoke.sh [base_url]   (domyślnie http://localhost:8082)
set -euo pipefail

BASE="${1:-http://localhost:8082}"
TIMEOUT="${SMOKE_TIMEOUT:-120}"

fail() { echo "FAIL: $*" >&2; exit 1; }

# 1. Health check: czekaj, aż /actuator/health zwróci UP
deadline=$((SECONDS + TIMEOUT))
until curl -fsS "$BASE/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  (( SECONDS < deadline )) || fail "health check nie zwrócił UP w ${TIMEOUT}s ($BASE/actuator/health)"
  sleep 3
done
echo "OK: health check = UP"

# 2. Endpoint biznesowy: lista incydentów (dane z seeda w bazie).
# Bez Pythona: odpowiedź to JSON-tablica, każdy rekord ma pole "id".
body="$(curl -fsS "$BASE/incidents/all")" || fail "GET /incidents/all zwrócił błąd"
[[ "$body" == \[* ]] || fail "GET /incidents/all: odpowiedź nie jest tablicą JSON"
count="$(grep -o '"id":' <<< "$body" | wc -l | tr -d ' ')"
(( count > 0 )) || fail "GET /incidents/all: lista jest pusta, seed nie został wczytany"
echo "OK: GET /incidents/all zwrócił $count rekordów"

# 3. Opcjonalnie: config-server serwuje konfigurację z prawdziwego repo (przez deploy key).
# Uruchamiane tylko, gdy podano CONFIG_URL.
if [[ -n "${CONFIG_URL:-}" ]]; then
  deadline=$((SECONDS + TIMEOUT))
  until curl -fsS "$CONFIG_URL/auth-service/main" 2>/dev/null | grep -q 'CityFailurePredictor-ConfigServer'; do
    (( SECONDS < deadline )) || fail "config-server nie zwrócił konfiguracji z repo ($CONFIG_URL/auth-service/main)"
    sleep 3
  done
  echo "OK: config-server zwraca konfigurację z repo CityFailurePredictor-ConfigServer"
fi

echo "SMOKE PASSED"
