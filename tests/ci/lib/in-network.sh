#!/usr/bin/env bash

PROBE_IMAGE="${PROBE_IMAGE:-eclipse-temurin:21-jre}"
CERTS_DIR="/etc/cfp/certs"

compose_network() {
  if [[ -z "${COMPOSE_NETWORK:-}" ]]; then
    COMPOSE_NETWORK="$(docker inspect api-gateway \
      --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' | awk '{ print $1 }')"
    [[ -n "$COMPOSE_NETWORK" ]] || { echo "FAIL: cannot find the network of the api-gateway container" >&2; return 1; }
  fi
  echo "$COMPOSE_NETWORK"
}

net_curl() {
  local network
  network="$(compose_network)" || return 1
  docker run --rm --user 0 --network "$network" --volumes-from api-gateway:ro --entrypoint curl "$PROBE_IMAGE" "$@"
}

mtls_curl() {
  net_curl --cacert "$CERTS_DIR/ca.crt" \
    --cert "$CERTS_DIR/api-gateway.crt" --key "$CERTS_DIR/api-gateway.key" "$@"
}
