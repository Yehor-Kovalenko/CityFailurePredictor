#!/usr/bin/env bash
# Load test of the running system with the team's JMeter plan
# (tests/load_tests/stress_test_invcidents_v1.jmx), scaled down for CI.
# The plan runs non-GUI and generates the HTML dashboard; the thresholds below
# are checked against its statistics.json. Crossing them fails the script.
# Assumes that the system is already running (docker compose up --wait).
#
# Usage: tests/ci/load-test.sh
# Env:   LOAD_REPORT_DIR (default: load-test-report),
#        LOAD_HOST / LOAD_PORT / LOAD_INCIDENTS_PATH (default: incident-service directly,
#          localhost:8082/incidents; the gateway needs a JWT the plan does not send),
#        LOAD_THREADS (20), LOAD_RAMP_UP (s, 10), LOAD_LOOPS (10),
#        MAX_ERROR_PCT (1), MAX_P95_MS (1000), MAX_P99_MS (3000),
#        JMETER_NETWORK (docker network for JMeter, default: host)
set -euo pipefail
# Decimal point in printf regardless of the runner locale
export LC_NUMERIC=C

JMETER_IMAGE="alpine/jmeter:5.6.3"
PLAN="stress_test_invcidents_v1.jmx"
REPORT_DIR="${LOAD_REPORT_DIR:-load-test-report}"
PLAN_DIR="$(cd "$(dirname "$0")/../load_tests" && pwd)"

MAX_ERROR_PCT="${MAX_ERROR_PCT:-1}"
MAX_P95_MS="${MAX_P95_MS:-1000}"
MAX_P99_MS="${MAX_P99_MS:-3000}"

# JMeter refuses to write the dashboard into a non-empty directory
rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"
REPORT_DIR="$(cd "$REPORT_DIR" && pwd)"

docker run --rm \
  --network "${JMETER_NETWORK:-host}" \
  --user "$(id -u):$(id -g)" \
  -v "$PLAN_DIR:/plan:ro" \
  -v "$REPORT_DIR:/report" \
  -w /report \
  "$JMETER_IMAGE" -n \
  -t "/plan/$PLAN" \
  -l /report/results.jtl \
  -j /report/jmeter.log \
  -e -o /report/html \
  -Jhost="${LOAD_HOST:-localhost}" \
  -Jport="${LOAD_PORT:-8082}" \
  -JincidentsPath="${LOAD_INCIDENTS_PATH:-/incidents}" \
  -Jthreads="${LOAD_THREADS:-20}" \
  -JrampUp="${LOAD_RAMP_UP:-10}" \
  -Jloops="${LOAD_LOOPS:-10}"

STATS="$REPORT_DIR/html/statistics.json"
[[ -f "$STATS" ]] || { echo "FAIL: JMeter did not generate $STATS" >&2; exit 1; }

# One row per transaction: label, samples, error %, median, p95, p99 (pct1/2/3 = 90/95/99)
rows="$(jq -r 'to_entries[] | [.key, .value.sampleCount, .value.errorPct,
  .value.medianResTime, .value.pct2ResTime, .value.pct3ResTime] | @tsv' "$STATS")"

failures=()
summary=$'### Load test (JMeter)\n\n| | Transaction | samples | errors | median | p95 | p99 |\n|---|---|---|---|---|---|---|\n'
while IFS=$'\t' read -r label samples err med p95 p99; do
  ok=1
  if [[ "$label" == "Total" ]]; then
    awk -v e="$err" -v max="$MAX_ERROR_PCT" 'BEGIN { exit !(e < max) }' \
      || { ok=0; failures+=("errors ${err}% >= ${MAX_ERROR_PCT}%"); }
  else
    awk -v a="$p95" -v b="$p99" -v ma="$MAX_P95_MS" -v mb="$MAX_P99_MS" 'BEGIN { exit !(a < ma && b < mb) }' \
      || { ok=0; failures+=("${label}: p95=${p95} ms, p99=${p99} ms"); }
  fi
  summary+="$(printf '| %s | %s | %s | %.2f%% | %.0f ms | %.0f ms | %.0f ms |' \
    "$( ((ok)) && echo '✅' || echo '❌')" "$label" "$samples" "$err" "$med" "$p95" "$p99")"$'\n'
done <<< "$rows"
summary+=$'\n'"Thresholds: errors < ${MAX_ERROR_PCT}%, p95 < ${MAX_P95_MS} ms, p99 < ${MAX_P99_MS} ms"$'\n'

echo "$summary"
echo "$summary" > "$REPORT_DIR/summary.md"
# The summary goes to the run's Summary tab even when thresholds were crossed
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "$summary" >> "$GITHUB_STEP_SUMMARY"
fi

if (( ${#failures[@]} > 0 )); then
  printf 'FAIL: %s\n' "${failures[@]}" >&2
  echo "FAIL: the load test did not meet the thresholds" >&2
  exit 1
fi
echo "LOAD TEST PASSED"
