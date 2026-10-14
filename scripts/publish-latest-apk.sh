#!/usr/bin/env bash
# Publish update catalog to branch `latest` (JSON only — no APKs in git) and
# upload the release APK as a GitHub Release asset.
#
# Catalog (in-app updater):
#   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/versions.json
# APK (sideload / in-app download):
#   https://github.com/iumer/FB2-OBD-app/releases/download/vX.Y.Z/FB2-Diag-X.Y.Z.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VN=$(grep 'versionName = ' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
APK_VERSIONED="dist/FB2-Diag-${VN}.apk"
APK_LATEST="dist/FB2-Diag.apk"

test -f "$APK_VERSIONED" || test -f dist/FB2-Diag-debug.apk || {
  echo "missing release APK — run scripts/package-hu-apk.sh first" >&2
  exit 1
}
if [[ ! -f "$APK_VERSIONED" && -f dist/FB2-Diag-debug.apk ]]; then
  cp -f dist/FB2-Diag-debug.apk "$APK_VERSIONED"
fi
if [[ ! -f "$APK_LATEST" ]]; then
  cp -f "$APK_VERSIONED" "$APK_LATEST"
fi

test -f dist/versions.json || {
  echo "missing dist/versions.json — add catalog before publishing" >&2
  exit 1
}
test -f dist/version.json || {
  echo "missing dist/version.json — add single-entry fallback before publishing" >&2
  exit 1
}

# 1) GitHub Release asset (APK binary — not committed to git).
if command -v gh >/dev/null 2>&1; then
  TAG="v${VN}"
  if gh release view "$TAG" >/dev/null 2>&1; then
    gh release upload "$TAG" "$APK_VERSIONED" "$APK_LATEST" --clobber
  else
    gh release create "$TAG" "$APK_VERSIONED" "$APK_LATEST" \
      --title "FB2 Diag ${VN}" \
      --notes "Release ${VN}. In-app updater reads dist/versions.json on branch latest."
  fi
  echo "Published Release assets for ${TAG}"
else
  echo "WARN: gh not available — skipped GitHub Release upload. Upload $APK_VERSIONED manually." >&2
fi

# 2) Catalog-only push to `latest` (never commit APKs).
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/dist"
cp dist/version.json "$TMP/dist/"
cp dist/versions.json "$TMP/dist/"
cat > "$TMP/README.md" <<EOF
# FB2 Diag — update catalog

In-app updater catalog: \`dist/versions.json\` (fallback: \`dist/version.json\`).

**APKs are GitHub Release assets** (not stored on this branch):

https://github.com/iumer/FB2-OBD-app/releases/download/v${VN}/FB2-Diag-${VN}.apk

https://github.com/iumer/FB2-OBD-app/releases/latest/download/FB2-Diag.apk
EOF
cd "$TMP"
git init -b latest >/dev/null
git add -A
git -c user.email='cursoragent@cursor.com' -c user.name='Cursor Agent' commit -m "Publish update catalog ${VN} (APKs via GitHub Releases)" >/dev/null
git remote add origin "$(cd "$ROOT" && git remote get-url origin)"
git push -f origin latest
echo "Published catalog: https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/versions.json"
bash "$ROOT/scripts/verify-latest-catalog.sh"
