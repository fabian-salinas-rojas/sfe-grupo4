#!/usr/bin/env bash
# Genera un certificado AUTOFIRMADO de desarrollo (BETA de SUNAT no exige certificado registrado, manual 2.3).
# El RUC va en el campo OU del Subject (manual 3.1). NUNCA usar este certificado en produccion.
set -euo pipefail
cd "$(dirname "$0")"
set -a; [ -f .env ] && . ./.env; set +a
: "${SFE_KEYSTORE_PASSWORD:?define SFE_KEYSTORE_PASSWORD en .env}"
mkdir -p secrets
keytool -genkeypair -alias sfe -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 365 \
  -dname "CN=EMISOR DESARROLLO, OU=20608945123, O=COMERCIAL ANDINA SAC, C=PE" \
  -storetype PKCS12 -keystore secrets/sfe-dev.p12 -storepass "$SFE_KEYSTORE_PASSWORD" -keypass "$SFE_KEYSTORE_PASSWORD"
echo "OK: deploy/secrets/sfe-dev.p12 (ignorado por git)"
