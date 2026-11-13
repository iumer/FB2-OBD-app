#!/usr/bin/env bash
# Refuse an APK signed with the Android debug key, or with any key other than the
# expected release certificate. Usage: scripts/check-apk-signer.sh path/to.apk
#
# Optional: set RELEASE_CERT_SHA256 (env or local.properties) to the release
# certificate's SHA-256 digest (from `apksigner verify --print-certs`) to pin it.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:?usage: check-apk-signer.sh path/to.apk}"
BUILD_TOOLS="${ANDROID_HOME:-$HOME/android-sdk}/build-tools/34.0.0"
APKSIGNER="${APKSIGNER:-$BUILD_TOOLS/apksigner}"

EXPECTED="${RELEASE_CERT_SHA256:-}"
if [[ -z "$EXPECTED" && -f "$ROOT/local.properties" ]]; then
  EXPECTED=$(grep -E '^RELEASE_CERT_SHA256=' "$ROOT/local.properties" | head -1 | cut -d= -f2- | tr -d ' :' || true)
fi

CERTS=$("$APKSIGNER" verify --print-certs "$APK")
if grep -qi "CN=Android Debug" <<<"$CERTS"; then
  echo "REFUSED: $APK is signed with the Android debug key. Configure RELEASE_STORE_* and rebuild." >&2
  exit 1
fi
if [[ -n "$EXPECTED" ]]; then
  ACTUAL=$(grep -i "certificate SHA-256 digest" <<<"$CERTS" | head -1 | awk -F': ' '{print $2}' | tr -d ' :')
  lower() { tr '[:upper:]' '[:lower:]' <<<"$1"; }
  if [[ "$(lower "$ACTUAL")" != "$(lower "$EXPECTED")" ]]; then
    echo "REFUSED: $APK signer SHA-256 does not match RELEASE_CERT_SHA256." >&2
    exit 1
  fi
else
  echo "WARN: RELEASE_CERT_SHA256 not set; only checked that the APK is not debug-signed." >&2
fi
echo "Signer OK: $APK"
