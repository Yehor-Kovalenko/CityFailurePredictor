#!/usr/bin/env bash
# Generates a docker compose override file: every built service
# runs from the registry image instead of being built from source.
# Usage: tests/ci/write-override.sh <owner> <tag> [output]
set -euo pipefail

OWNER="${1:?registry owner (lowercase) is required}"
TAG="${2:?image tag is required, e.g. sha-abc123def456}"
OUT="${3:-ci-override.yml}"

# Must match the build matrix in .github/workflows/ci.yml
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

echo "wrote $OUT (${#SERVICES[@]} services, tag $TAG)"
