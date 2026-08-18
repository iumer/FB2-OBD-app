#!/usr/bin/env bash
# Post-publish guard: in-app updater must never 404 on the catalog again.
# Run after scripts/publish-latest-apk.sh (also invoked automatically from that script).
set -euo pipefail

REPO="${REPO:-iumer/FB2-OBD-app}"
BRANCH="${BRANCH:-latest}"
RAW_BASE="https://raw.githubusercontent.com/${REPO}/${BRANCH}/dist"
API_BASE="https://api.github.com/repos/${REPO}/contents/dist"

fetch_http_code() {
  local url="$1"
  curl -fsSIL --max-time 20 "$url" 2>/dev/null | awk '/^HTTP/{code=$2} END{print code+0}'
}

fetch_ok() {
  local url="$1"
  local code
  code="$(fetch_http_code "$url")"
  if [[ "$code" != "200" ]]; then
    echo "FAIL HTTP $code — $url" >&2
    return 1
  fi
  echo "OK HTTP 200 — $url"
}

fetch_body() {
  local url="$1"
  curl -fsSL --max-time 25 "$url"
}

echo "Verifying latest-branch update catalog (${REPO}@${BRANCH})…"

# GitHub raw CDN can lag seconds after a force-push — retry briefly.
attempt=1
max_attempts=8
while true; do
  versions_code="$(fetch_http_code "${RAW_BASE}/versions.json")"
  version_code="$(fetch_http_code "${RAW_BASE}/version.json")"
  apk_code="$(fetch_http_code "${RAW_BASE}/FB2-Diag-debug.apk")"
  if [[ "$versions_code" == "200" && "$version_code" == "200" && "$apk_code" == "200" ]]; then
    break
  fi
  if (( attempt >= max_attempts )); then
    echo "FAIL after $max_attempts attempts:" >&2
    echo "  versions.json → HTTP $versions_code" >&2
    echo "  version.json  → HTTP $version_code" >&2
    echo "  APK           → HTTP $apk_code" >&2
    exit 1
  fi
  echo "Waiting for CDN (attempt $attempt/$max_attempts)… versions=$versions_code version=$version_code apk=$apk_code"
  sleep 4
  attempt=$((attempt + 1))
done

fetch_ok "${RAW_BASE}/versions.json"
fetch_ok "${RAW_BASE}/version.json"
fetch_ok "${RAW_BASE}/FB2-Diag-debug.apk"

# GitHub Contents API — same path the app tries first.
api_code="$(fetch_http_code "${API_BASE}/versions.json?ref=${BRANCH}")"
if [[ "$api_code" != "200" ]]; then
  echo "FAIL HTTP $api_code — ${API_BASE}/versions.json?ref=${BRANCH}" >&2
  exit 1
fi
echo "OK HTTP 200 — GitHub Contents API versions.json"

versions_json="$(fetch_body "${RAW_BASE}/versions.json")"
version_json="$(fetch_body "${RAW_BASE}/version.json")"

python3 - <<'PY' "$versions_json" "$version_json"
import json, sys
versions = json.loads(sys.argv[1])
version = json.loads(sys.argv[2])
latest = versions.get("latest") or {}
if not latest.get("versionCode") or not latest.get("versionName"):
    raise SystemExit("versions.json missing latest.versionCode/versionName")
releases = versions.get("releases") or []
if not releases:
    raise SystemExit("versions.json releases[] is empty")
codes = {r["versionCode"] for r in releases}
if latest["versionCode"] not in codes:
    raise SystemExit("latest.versionCode not present in releases[]")
if version.get("versionCode") != latest.get("versionCode"):
    raise SystemExit(
        f"version.json code {version.get('versionCode')} != latest {latest.get('versionCode')}"
    )
if version.get("versionName") != latest.get("versionName"):
    raise SystemExit(
        f"version.json name {version.get('versionName')} != latest {latest.get('versionName')}"
    )
print(f"Catalog OK — latest v{latest['versionName']} (code {latest['versionCode']}), {len(releases)} releases")
PY

echo "All update-catalog checks passed."
