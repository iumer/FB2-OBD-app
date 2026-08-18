#!/usr/bin/env bash
# Publish ONE sideload APK to branch `latest` (stable URL never changes):
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
cat > "$TMP/README.md" <<'EOF'
# FB2 Diag — always-latest APK

**Only download link (does not change between updates):**

https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk
EOF
cd "$TMP"
git init -b latest >/dev/null
git add -A
git -c user.email='cursoragent@cursor.com' -c user.name='Cursor Agent' commit -m "Publish latest sideload APK" >/dev/null
git remote add origin "$(cd "$ROOT" && git remote get-url origin)"
git push -f origin latest
echo "Published: https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk"
