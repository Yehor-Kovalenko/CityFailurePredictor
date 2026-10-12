#!/usr/bin/env bash
# Smoke test całego systemu uruchomionego przez docker compose.
# Zakłada, że serwisy już wystartowały (docker compose up --wait).
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

# Usługi, które są klientami Eureka (zgodnie z docker-compose.yml)
EXPECTED_APPS=(
  CONFIG-SERVER API-GATEWAY AUTH-SERVICE INCIDENT-SERVICE
  DATA-INGESTION-SERVICE DECISION-SERVICE REPORTING-SERVICE NOTIFICATION-SERVICE
)

# 1. Rejestracja wszystkich usług w Eureka
deadline=$((SECONDS + TIMEOUT))
while true; do
  apps="$(curl -fsS "$EUREKA/eureka/apps" 2>/dev/null || true)"
  missing=()
  for app in "${EXPECTED_APPS[@]}"; do
    grep -q "<name>${app}</name>" <<< "$apps" || missing+=("$app")
  done
  (( ${#missing[@]} == 0 )) && break
  (( SECONDS < deadline )) || fail "nie zarejestrowano w Eureka: ${missing[*]}"
  sleep 5
done
ok "zarejestrowano w Eureka wszystkie ${#EXPECTED_APPS[@]} usługi klienckie"

# 2. Health bramy API
health="$(curl -fsS "$GATEWAY/actuator/health")" || fail "health bramy nie odpowiada"
grep -q '"status":"UP"' <<< "$health" || fail "health bramy nie jest UP"
ok "health bramy = UP"

# 3. Brama egzekwuje autoryzację: żądanie bez tokenu musi dostać 401
code="$(curl -s -o /dev/null -w '%{http_code}' "$GATEWAY/incidents/all")"
[[ "$code" == "401" ]] || fail "brama powinna zwrócić 401 bez tokenu, zwróciła $code"
ok "brama odrzuca żądanie bez tokenu (401)"

# 4. Frontend serwuje aplikację
code="$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND/")"
[[ "$code" == "200" ]] || fail "frontend zwrócił $code"
ok "frontend zwraca 200"

# 5. Serwis AI
ai_health="$(curl -fsS "$AI/health")" || fail "serwis AI nie odpowiada"
grep -q '"status":"UP"' <<< "$ai_health" || fail "serwis AI nie jest UP"
ok "serwis AI = UP"

# 6. Incident-service (health, endpoint z danymi) i config-server z repo konfiguracji
CONFIG_URL="$CONFIG" SMOKE_TIMEOUT="$TIMEOUT" "$(dirname "$0")/smoke.sh" http://localhost:8082

echo "SYSTEM SMOKE PASSED"
