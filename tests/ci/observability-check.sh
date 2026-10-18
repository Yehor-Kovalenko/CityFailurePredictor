#!/usr/bin/env bash
# Verify the observability of the running system (metrics + centralized logs).
# Assumes that the system and the observability stack (Prometheus, Loki, Alloy)
# are already running.
#
# Validates the entire observability pipeline, not just individual endpoints:
#   1. Each Spring service exposes /actuator/prometheus with metrics and an application tag.
#   2. Prometheus scrapes all services (target health = up).
#   3. Traffic generated through the gateway is visible in the http_server_requests metric in Prometheus.
#   4. Logs from all services are delivered to Loki (via Alloy).
#   5. A specific request can be found in Loki using its X-Correlation-ID.
#
# Usage: tests/ci/observability-check.sh
# Env:   OBS_TIMEOUT (seconds, default: 180),
#        OBS_REPORT_DIR (default: observability-report)
set -euo pipefail

TIMEOUT="${OBS_TIMEOUT:-180}"
REPORT_DIR="${OBS_REPORT_DIR:-observability-report}"
GATEWAY="http://localhost:9191"
PROMETHEUS="http://localhost:9090"
LOKI="http://localhost:3100"
REQUESTS=20

# usługa:port - musi odpowiadać jobom w monitoring/prometheus.yml
METRIC_TARGETS=(
  service-registry:8761
  config-server:8888
  api-gateway:9191
  auth-service:8083
  incident-service:8082
  data-ingestion-service:8084
  decision-service:8085
  reporting-service:8086
  notification-service:8087
)

# Usługi z LOG_FILE w docker-compose.yml, których logi zbiera Alloy
LOG_SERVICES=(
  api-gateway auth-service incident-service data-ingestion-service
  decision-service reporting-service notification-service
)

SUMMARY_ROWS=()

fail() {
  echo "FAIL: $*" >&2
  SUMMARY_ROWS+=("| ❌ | $* |")
  write_summary
  exit 1
}

ok() {
  echo "OK: $*"
  SUMMARY_ROWS+=("| ✅ | $* |")
}

write_summary() {
  [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] || return 0
  {
    echo "### Observability check"
    echo
    echo "| | Check |"
    echo "|---|---|"
    printf '%s\n' "${SUMMARY_ROWS[@]}"
  } >> "$GITHUB_STEP_SUMMARY"
}

# Ponawia polecenie aż do sukcesu albo upływu TIMEOUT.
# Usage: retry "<opis>" <polecenie...>
retry() {
  local desc="$1"; shift
  local deadline=$((SECONDS + TIMEOUT))
  until "$@"; do
    (( SECONDS < deadline )) || fail "$desc (timeout ${TIMEOUT}s)"
    sleep 5
  done
}

prom_query() {
  curl -fsS -G "$PROMETHEUS/api/v1/query" --data-urlencode "query=$1"
}

command -v jq > /dev/null || fail "brak jq"
mkdir -p "$REPORT_DIR/metrics"

# 1. Endpoint metryk każdej usługi
for target in "${METRIC_TARGETS[@]}"; do
  name="${target%%:*}"
  port="${target##*:}"
  out="$REPORT_DIR/metrics/${name}.prom"
  curl -fsS "http://localhost:${port}/actuator/prometheus" -o "$out" \
    || fail "$name: /actuator/prometheus nie odpowiada"
  grep -q '^jvm_memory_used_bytes{' "$out" || fail "$name: brak metryki jvm_memory_used_bytes"
  grep -q "application=\"${name}\"" "$out" || fail "$name: metryki bez tagu application=\"${name}\""
done
ok "wszystkie ${#METRIC_TARGETS[@]} usługi wystawiają /actuator/prometheus z tagiem application"

# 2. Prometheus scrapuje wszystkie usługi
retry "Prometheus nie jest gotowy" curl -fsS -o /dev/null "$PROMETHEUS/-/ready"

targets_up() {
  curl -fsS "$PROMETHEUS/api/v1/targets?state=active" > "$REPORT_DIR/prometheus-targets.json" || return 1
  local name health
  for target in "${METRIC_TARGETS[@]}"; do
    name="${target%%:*}"
    health="$(jq -r --arg job "$name" \
      '[.data.activeTargets[] | select(.labels.job == $job) | .health] | first // "missing"' \
      "$REPORT_DIR/prometheus-targets.json")"
    [[ "$health" == "up" ]] || { echo "  czekam na target $name (health=$health)"; return 1; }
  done
}
retry "Prometheus nie scrapuje wszystkich usług" targets_up
ok "Prometheus scrapuje wszystkie ${#METRIC_TARGETS[@]} usługi (health=up)"

# 3. Ruch na bramie z jednym correlation ID, potem weryfikacja metryki w Prometheusie
correlation_id="ci-obs-${GITHUB_RUN_ID:-local}-$(date +%s)-${RANDOM}"
baseline="$(prom_query 'sum(http_server_requests_seconds_count{application="api-gateway"}) or vector(0)' \
  | jq -r '.data.result[0].value[1] // "0"')"

for _ in $(seq "$REQUESTS"); do
  echoed="$(curl -s -o /dev/null -D - -H "X-Correlation-ID: ${correlation_id}" "$GATEWAY/incidents/all" \
    | tr -d '\r' | awk -F': ' 'tolower($1) == "x-correlation-id" { print $2 }')"
  [[ "$echoed" == "$correlation_id" ]] || fail "brama nie zwróciła nagłówka X-Correlation-ID"
done
ok "brama propaguje X-Correlation-ID ($REQUESTS żądań, id=${correlation_id})"

gateway_requests_counted() {
  local current
  current="$(prom_query 'sum(http_server_requests_seconds_count{application="api-gateway"})' \
    | jq -r '.data.result[0].value[1] // "0"')"
  echo "  http_server_requests api-gateway: baseline=${baseline}, teraz=${current}"
  awk -v c="$current" -v b="$baseline" -v n="$REQUESTS" 'BEGIN { exit !(c - b >= n) }'
}
retry "Prometheus nie zarejestrował ruchu na bramie" gateway_requests_counted
ok "Prometheus zarejestrował ruch na bramie (metryka http_server_requests_seconds_count wzrosła o >= $REQUESTS)"

# 4. Logi wszystkich usług trafiają do Loki
retry "Loki nie jest gotowe" bash -c "curl -fsS '$LOKI/ready' | grep -q ready"

services_logging() {
  curl -fsS "$LOKI/loki/api/v1/label/service/values" > "$REPORT_DIR/loki-services.json" || return 1
  local svc
  for svc in "${LOG_SERVICES[@]}"; do
    jq -e --arg s "$svc" '.data // [] | index($s) != null' "$REPORT_DIR/loki-services.json" > /dev/null \
      || { echo "  czekam na logi usługi $svc w Loki"; return 1; }
  done
}
retry "nie wszystkie usługi wysyłają logi do Loki" services_logging
ok "logi wszystkich ${#LOG_SERVICES[@]} usług docierają do Loki"

# 5. Konkretne żądanie odnalezione w Loki po correlation ID
correlated_log_found() {
  curl -fsS -G "$LOKI/loki/api/v1/query_range" \
    --data-urlencode "query={correlationId=\"${correlation_id}\"}" \
    --data-urlencode "limit=100" > "$REPORT_DIR/loki-correlation.json" || return 1
  jq -e '[.data.result[] | select(.stream.service == "api-gateway")] | length > 0' \
    "$REPORT_DIR/loki-correlation.json" > /dev/null
}
retry "w Loki brak logów żądania z correlationId=${correlation_id}" correlated_log_found
ok "logi żądania z correlationId=${correlation_id} odnalezione w Loki"

write_summary
echo "OBSERVABILITY CHECK PASSED"
