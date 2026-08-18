#!/usr/bin/env bash
# Publish sideload APK + update catalog to branch `latest` (stable URL never changes):
#   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk
# In-app updater reads dist/versions.json (and version.json fallback) from the same branch.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
test -f dist/FB2-Diag-debug.apk || {
  echo "missing dist/FB2-Diag-debug.apk — run assembleDebug + copy first" >&2
  exit 1
}
test -f dist/versions.json || {
  echo "missing dist/versions.json — add catalog before publishing" >&2
  exit 1
}
test -f dist/version.json || {
  echo "missing dist/version.json — add single-entry fallback before publishing" >&2
  exit 1
}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/dist/archive"
cp dist/FB2-Diag-debug.apk "$TMP/dist/"
cp dist/version.json "$TMP/dist/"
cp dist/versions.json "$TMP/dist/"
if compgen -G "dist/archive/*.apk" > /dev/null; then
  cp dist/archive/*.apk "$TMP/dist/archive/"
fi
cat > "$TMP/README.md" <<'EOF'
# FB2 Diag — always-latest APK

**Only download link (does not change between updates):**

https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk

In-app updater catalog: `dist/versions.json` (fallback: `dist/version.json`).
EOF
cd "$TMP"
git init -b latest >/dev/null
git add -A
git -c user.email='cursoragent@cursor.com' -c user.name='Cursor Agent' commit -m "Publish latest sideload APK + update catalog" >/dev/null
git remote add origin "$(cd "$ROOT" && git remote get-url origin)"
git push -f origin latest
echo "Published APK: https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk"
echo "Published catalog: https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/versions.json"
bash "$ROOT/scripts/verify-latest-catalog.sh"
