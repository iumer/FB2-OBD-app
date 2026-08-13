#!/usr/bin/env bash
# Publish dist/*.apk to branch `latest` so the stable sideload URL stays current:
#   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
test -f dist/FB2-Diag-debug.apk || {
  echo "missing dist/FB2-Diag-debug.apk — run assembleDebug + copy first" >&2
  exit 1
}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/dist"
cp dist/FB2-Diag-debug.apk "$TMP/dist/"
if [[ -f dist/FB2-Diag-hu.apk ]]; then
  cp dist/FB2-Diag-hu.apk "$TMP/dist/"
else
  cp dist/FB2-Diag-debug.apk "$TMP/dist/FB2-Diag-hu.apk"
fi
cat > "$TMP/README.md" <<'EOF'
# FB2 Diag — always-latest APK

**One download link (does not change between updates):**

https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk

HU copy:

https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-hu.apk
EOF
cd "$TMP"
git init -b latest >/dev/null
git add -A
git -c user.email='cursoragent@cursor.com' -c user.name='Cursor Agent' commit -m "Publish latest sideload APK" >/dev/null
git remote add origin "$(cd "$ROOT" && git remote get-url origin)"
git push -f origin latest
echo "Published: https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk"
