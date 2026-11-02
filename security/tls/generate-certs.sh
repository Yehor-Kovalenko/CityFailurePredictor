#!/bin/sh
set -eu

CA_DIR="${CA_DIR:-/ca}"
CERTS_DIR="${CERTS_DIR:-/certs}"
DAYS="${TLS_CERT_DAYS:-825}"
SERVICES="${TLS_SERVICES:?TLS_SERVICES is required}"
PASSWORD="${TLS_KEYSTORE_PASSWORD:?TLS_KEYSTORE_PASSWORD is required}"

mkdir -p "$CA_DIR" "$CERTS_DIR"
chmod 700 "$CA_DIR"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [ ! -f "$CA_DIR/ca.key" ] || [ ! -f "$CERTS_DIR/ca.crt" ]; then
  echo "Creating the internal certificate authority"
  find "$CERTS_DIR" -type f -delete
  openssl req -x509 -new -newkey rsa:3072 -noenc -sha256 -days 3650 \
    -subj "/O=CityFailurePredictor/CN=CityFailurePredictor Internal CA" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -keyout "$CA_DIR/ca.key" -out "$CERTS_DIR/ca.crt" 2> /dev/null
  chmod 600 "$CA_DIR/ca.key"
fi

for service in $SERVICES; do
  if [ -f "$CERTS_DIR/$service.crt" ] && [ -f "$CERTS_DIR/$service.key" ] && [ -f "$CERTS_DIR/$service.p12" ]; then
    echo "Certificate for $service already exists"
    continue
  fi

  echo "Issuing certificate for $service"
  cat > "$WORK_DIR/$service.ext" << EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth,clientAuth
subjectAltName=DNS:$service,DNS:localhost
EOF

  openssl req -new -newkey rsa:2048 -noenc \
    -subj "/O=CityFailurePredictor/CN=$service" \
    -keyout "$CERTS_DIR/$service.key" -out "$WORK_DIR/$service.csr" 2> /dev/null
  openssl x509 -req -sha256 -days "$DAYS" -in "$WORK_DIR/$service.csr" \
    -CA "$CERTS_DIR/ca.crt" -CAkey "$CA_DIR/ca.key" -CAcreateserial -CAserial "$CA_DIR/ca.srl" \
    -extfile "$WORK_DIR/$service.ext" -out "$CERTS_DIR/$service.crt" 2> /dev/null
  openssl pkcs12 -export -name "$service" \
    -inkey "$CERTS_DIR/$service.key" -in "$CERTS_DIR/$service.crt" -certfile "$CERTS_DIR/ca.crt" \
    -passout "pass:$PASSWORD" -out "$CERTS_DIR/$service.p12"
done

chmod 755 "$CERTS_DIR"
chmod 644 "$CERTS_DIR"/*.crt
chmod 600 "$CERTS_DIR"/*.key "$CERTS_DIR"/*.p12
echo "mTLS identities ready in $CERTS_DIR"
