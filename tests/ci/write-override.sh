#!/usr/bin/env bash
# Generuje plik override dla docker compose: każdy zbudowany serwis
# działa z obrazu z rejestru zamiast budowania ze źródeł.
# Usage: tests/ci/write-override.sh <owner> <tag> [output]
set -euo pipefail

OWNER="${1:?podaj właściciela rejestru (małymi literami)}"
TAG="${2:?podaj tag obrazu, np. sha-abc123def456}"
OUT="${3:-ci-override.yml}"

# Musi odpowiadać macierzy budowania w .github/workflows/ci.yml
SERVICES=(
  service-registry
  config-server
  api-gateway
  auth-service
  incident-service
  data-ingestion-service
  ai-service
  decision-service
  reporting-service
  notification-service
  frontend
)

{
  echo "services:"
  for s in "${SERVICES[@]}"; do
    echo "  ${s}:"
    echo "    image: ghcr.io/${OWNER}/${s}:${TAG}"
  done
} > "$OUT"

echo "zapisano $OUT (${#SERVICES[@]} serwisów, tag $TAG)"
