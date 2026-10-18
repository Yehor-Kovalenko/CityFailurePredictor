#!/usr/bin/env bash
# Load test (k6) of the running system. Crossing the thresholds defined in
# tests/load_tests/k6/ci-load-test.js makes the script exit with a non-zero code.
# Assumes that the system is already running (docker compose up --wait).
#
# Usage: tests/ci/load-test.sh
# Env:   LOAD_REPORT_DIR (default: load-test-report),
#        GATEWAY_URL / INCIDENT_URL (default: localhost; on macOS: http://host.docker.internal:<port>),
#        K6_NETWORK (docker network for k6, default: host)
set -euo pipefail

K6_IMAGE="grafana/k6:1.8.2"
REPORT_DIR="${LOAD_REPORT_DIR:-load-test-report}"
SCRIPT_DIR="$(cd "$(dirname "$0")/../load_tests/k6" && pwd)"

mkdir -p "$REPORT_DIR"
REPORT_DIR="$(cd "$REPORT_DIR" && pwd)"

status=0
docker run --rm \
  --network "${K6_NETWORK:-host}" \
  --user "$(id -u):$(id -g)" \
  -v "$SCRIPT_DIR:/scripts:ro" \
  -v "$REPORT_DIR:/report" \
  -e GATEWAY_URL="${GATEWAY_URL:-http://localhost:9191}" \
  -e INCIDENT_URL="${INCIDENT_URL:-http://localhost:8082}" \
  -e SUMMARY_DIR=/report \
  "$K6_IMAGE" run --quiet /scripts/ci-load-test.js || status=$?

# The summary goes to the run's Summary tab even when thresholds were crossed
if [[ -n "${GITHUB_STEP_SUMMARY:-}" && -f "$REPORT_DIR/summary.md" ]]; then
  cat "$REPORT_DIR/summary.md" >> "$GITHUB_STEP_SUMMARY"
fi

(( status == 0 )) || { echo "FAIL: test obciążeniowy nie spełnił progów (k6 exit $status)" >&2; exit "$status"; }
echo "LOAD TEST PASSED"
