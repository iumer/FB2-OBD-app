#!/usr/bin/env bash
# Build a head-unit-friendly sideload APK:
# - release classpath (no Compose ui-tooling bloat)
# - signed with the Android debug key (same as prior sideloads)
# - v1 + v2 signatures (many car HU installers hang on v2-only APKs)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BUILD_TOOLS="${ANDROID_HOME:-$HOME/android-sdk}/build-tools/34.0.0"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
KS="${HOME}/.android/debug.keystore"

./gradlew assembleRelease

SRC="$ROOT/app/build/outputs/apk/release/app-release.apk"
UNSIGNED="$(mktemp /tmp/fb2-unsigned-XXXXXX.apk)"
ALIGNED="$(mktemp /tmp/fb2-aligned-XXXXXX.apk)"
SIGNED="$(mktemp /tmp/fb2-signed-XXXXXX.apk)"
cp -f "$SRC" "$UNSIGNED"
zip -d "$UNSIGNED" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/MANIFEST.MF' >/dev/null 2>&1 || true
"$ZIPALIGN" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$APKSIGNER" sign \
  --ks "$KS" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --min-sdk-version 21 \
  --out "$SIGNED" \
  "$ALIGNED"

"$APKSIGNER" verify --min-sdk-version 21 "$SIGNED" >/dev/null
mkdir -p "$ROOT/dist"
cp -f "$SIGNED" "$ROOT/dist/FB2-Diag-debug.apk"
cp -f "$SIGNED" "$ROOT/dist/FB2-Diag-hu.apk"
rm -f "$UNSIGNED" "$ALIGNED" "$SIGNED"

echo "OK: dist/FB2-Diag-debug.apk ($(du -h "$ROOT/dist/FB2-Diag-debug.apk" | awk '{print $1}'))"
"$APKSIGNER" verify --verbose --min-sdk-version 21 "$ROOT/dist/FB2-Diag-debug.apk" | head -8
