#!/usr/bin/env bash
# Publish ONE sideload APK to branch `latest` (stable URL never changes):
#   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk
# Also publishes dist/version.json for in-app update checks.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
test -f dist/FB2-Diag-debug.apk || {
  echo "missing dist/FB2-Diag-debug.apk — run assembleDebug + copy first" >&2
  exit 1
}
VERSION_CODE=$(sed -n 's/.*versionCode *= *\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)
VERSION_NAME=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
test -n "$VERSION_CODE" && test -n "$VERSION_NAME" || {
  echo "could not parse versionCode/versionName from app/build.gradle.kts" >&2
  exit 1
}
APK_URL="https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk"
cat > dist/version.json <<EOF
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "apkUrl": "${APK_URL}"
}
EOF
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/dist"
cp dist/FB2-Diag-debug.apk "$TMP/dist/"
cp dist/version.json "$TMP/dist/"
cat > "$TMP/README.md" <<'EOF'
# FB2 Diag — always-latest APK

**Only download link (does not change between updates):**

https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk

In-app update checks:

https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/version.json
EOF
cd "$TMP"
git init -b latest >/dev/null
git add -A
git -c user.email='cursoragent@cursor.com' -c user.name='Cursor Agent' commit -m "Publish latest sideload APK ${VERSION_NAME} (${VERSION_CODE})" >/dev/null
git remote add origin "$(cd "$ROOT" && git remote get-url origin)"
git push -f origin latest
echo "Published: $APK_URL"
echo "Version:   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/version.json ($VERSION_NAME / $VERSION_CODE)"
