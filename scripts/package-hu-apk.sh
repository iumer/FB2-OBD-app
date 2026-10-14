#!/usr/bin/env bash
# Build a head-unit-friendly release APK:
# - release classpath (non-debuggable, no Compose ui-tooling bloat)
# - signed with the release keystore from local.properties / env
# - v1 + v2 signatures (many car HU installers hang on v2-only APKs)
#
# APK is written to dist/ (gitignored). Publish as a GitHub Release asset —
# never commit APKs to the repo.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VN=$(grep 'versionName = ' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
BUILD_TOOLS="${ANDROID_HOME:-$HOME/android-sdk}/build-tools/34.0.0"
APKSIGNER="$BUILD_TOOLS/apksigner"

./gradlew assembleRelease

SRC="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$SRC" ]]; then
  echo "missing $SRC — assembleRelease failed?" >&2
  exit 1
fi

mkdir -p "$ROOT/dist"
OUT_VERSIONED="$ROOT/dist/FB2-Diag-${VN}.apk"
OUT_LATEST="$ROOT/dist/FB2-Diag.apk"
cp -f "$SRC" "$OUT_VERSIONED"
cp -f "$SRC" "$OUT_LATEST"
# Keep legacy filename for older scripts/docs that still look for it locally.
cp -f "$SRC" "$ROOT/dist/FB2-Diag-debug.apk"

"$APKSIGNER" verify --min-sdk-version 21 "$OUT_VERSIONED" >/dev/null
echo "OK: $OUT_VERSIONED ($(du -h "$OUT_VERSIONED" | awk '{print $1}'))"
"$APKSIGNER" verify --verbose --min-sdk-version 21 "$OUT_VERSIONED" | head -8

# Refuse to package if secrets leaked into the APK.
./gradlew checkNoPackagedSecrets
