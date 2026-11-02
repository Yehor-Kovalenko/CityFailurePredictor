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
#   6. Security events (requirement C4) reach Loki with the securityEvent label.
#
# Prometheus, Loki and the services are not published on the host (requirement C3),
# so they are queried from a probe container in the compose network.
#
# Usage: tests/ci/observability-check.sh
# Env:   OBS_TIMEOUT (seconds, default: 180),
#        OBS_REPORT_DIR (default: observability-report)
set -euo pipefail
source "$(dirname "$0")/lib/in-network.sh"

TIMEOUT="${OBS_TIMEOUT:-180}"
REPORT_DIR="${OBS_REPORT_DIR:-observability-report}"
GATEWAY="http://localhost:9191"
PROMETHEUS="http://prometheus:9090"
LOKI="http://loki:3100"
REQUESTS=20

# service:port - must match the jobs in monitoring/prometheus.yml
# (the mTLS services expose metrics on their management port 8081)
METRIC_TARGETS=(
  service-registry:8761
  config-server:8888
  api-gateway:9191
  auth-service:8081
  incident-service:8081
  data-ingestion-service:8081
  decision-service:8081
  reporting-service:8081
  notification-service:8081
)

# Services with LOG_FILE in docker-compose.yml whose logs are collected by Alloy
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

# Retries the command until it succeeds or TIMEOUT expires.
# Usage: retry "<description>" <command...>
retry() {
  local desc="$1"; shift
  local deadline=$((SECONDS + TIMEOUT))
  until "$@"; do
    (( SECONDS < deadline )) || fail "$desc (timeout ${TIMEOUT}s)"
    sleep 5
  done
}

prom_query() {
  net_curl -fsS -G "$PROMETHEUS/api/v1/query" --data-urlencode "query=$1"
}

command -v jq > /dev/null || fail "jq is not installed"
mkdir -p "$REPORT_DIR/metrics"

# 1. Metrics endpoint of every service
for target in "${METRIC_TARGETS[@]}"; do
  name="${target%%:*}"
  port="${target##*:}"
  out="$REPORT_DIR/metrics/${name}.prom"
  net_curl -fsS "http://${name}:${port}/actuator/prometheus" > "$out" \
    || fail "$name: /actuator/prometheus is not responding"
  grep -q '^jvm_memory_used_bytes{' "$out" || fail "$name: jvm_memory_used_bytes metric is missing"
  grep -q "application=\"${name}\"" "$out" || fail "$name: metrics are missing the tag application=\"${name}\""
done
ok "all ${#METRIC_TARGETS[@]} services expose /actuator/prometheus with the application tag"

# 2. Prometheus scrapes all services
retry "Prometheus is not ready" net_curl -fsS -o /dev/null "$PROMETHEUS/-/ready"

targets_up() {
  net_curl -fsS "$PROMETHEUS/api/v1/targets?state=active" > "$REPORT_DIR/prometheus-targets.json" || return 1
  local name health
  for target in "${METRIC_TARGETS[@]}"; do
    name="${target%%:*}"
    health="$(jq -r --arg job "$name" \
      '[.data.activeTargets[] | select(.labels.job == $job) | .health] | first // "missing"' \
      "$REPORT_DIR/prometheus-targets.json")"
    [[ "$health" == "up" ]] || { echo "  waiting for target $name (health=$health)"; return 1; }
  done
}
retry "Prometheus is not scraping all services" targets_up
ok "Prometheus scrapes all ${#METRIC_TARGETS[@]} services (health=up)"

# 3. Gateway traffic with a single correlation ID, then verify the metric in Prometheus
correlation_id="ci-obs-${GITHUB_RUN_ID:-local}-$(date +%s)-${RANDOM}"
baseline="$(prom_query 'sum(http_server_requests_seconds_count{application="api-gateway"}) or vector(0)' \
  | jq -r '.data.result[0].value[1] // "0"')"

for _ in $(seq "$REQUESTS"); do
  echoed="$(curl -s -o /dev/null -D - -H "X-Correlation-ID: ${correlation_id}" "$GATEWAY/incidents/all" \
    | tr -d '\r' | awk -F': ' 'tolower($1) == "x-correlation-id" { print $2 }')"
  [[ "$echoed" == "$correlation_id" ]] || fail "the gateway did not return the X-Correlation-ID header"
done
ok "the gateway propagates X-Correlation-ID ($REQUESTS requests, id=${correlation_id})"

gateway_requests_counted() {
  local current
  current="$(prom_query 'sum(http_server_requests_seconds_count{application="api-gateway"})' \
    | jq -r '.data.result[0].value[1] // "0"')"
  echo "  http_server_requests api-gateway: baseline=${baseline}, now=${current}"
  awk -v c="$current" -v b="$baseline" -v n="$REQUESTS" 'BEGIN { exit !(c - b >= n) }'
}
retry "Prometheus did not record the gateway traffic" gateway_requests_counted
ok "Prometheus recorded the gateway traffic (http_server_requests_seconds_count increased by >= $REQUESTS)"

# 4. Logs from all services reach Loki
loki_ready() {
  net_curl -fsS "$LOKI/ready" | grep -q ready
}
retry "Loki is not ready" loki_ready

services_logging() {
  net_curl -fsS "$LOKI/loki/api/v1/label/service/values" > "$REPORT_DIR/loki-services.json" || return 1
  local svc
  for svc in "${LOG_SERVICES[@]}"; do
    jq -e --arg s "$svc" '.data // [] | index($s) != null' "$REPORT_DIR/loki-services.json" > /dev/null \
      || { echo "  waiting for logs of $svc in Loki"; return 1; }
  done
}
retry "not all services are sending logs to Loki" services_logging
ok "logs from all ${#LOG_SERVICES[@]} services reach Loki"

# 5. A specific request found in Loki by its correlation ID
correlated_log_found() {
  net_curl -fsS -G "$LOKI/loki/api/v1/query_range" \
    --data-urlencode "query={correlationId=\"${correlation_id}\"}" \
    --data-urlencode "limit=100" > "$REPORT_DIR/loki-correlation.json" || return 1
  jq -e '[.data.result[] | select(.stream.service == "api-gateway")] | length > 0' \
    "$REPORT_DIR/loki-correlation.json" > /dev/null
}
retry "no logs in Loki for the request with correlationId=${correlation_id}" correlated_log_found
ok "logs of the request with correlationId=${correlation_id} found in Loki"

# 6. The rejected requests of step 3 (no token) are audited as security events
security_event_logged() {
  net_curl -fsS -G "$LOKI/loki/api/v1/query_range" \
    --data-urlencode "query={securityEvent=\"AUTHENTICATION_FAILED\", correlationId=\"${correlation_id}\"}" \
    --data-urlencode "limit=100" > "$REPORT_DIR/loki-security-events.json" || return 1
  jq -e '[.data.result[] | select(.stream.service == "api-gateway")] | length > 0' \
    "$REPORT_DIR/loki-security-events.json" > /dev/null
}
retry "no security event in Loki for the rejected request with correlationId=${correlation_id}" security_event_logged
ok "security events of rejected requests are searchable in Loki (securityEvent label)"

write_summary
echo "OBSERVABILITY CHECK PASSED"
