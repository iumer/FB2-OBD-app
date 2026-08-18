#!/usr/bin/env bash
# Bump dist catalog + build HU APK for the version in app/build.gradle.kts
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
VC=$(grep 'versionCode = ' app/build.gradle.kts | head -1 | sed 's/.*= //')
VN=$(grep 'versionName = ' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

python3 <<PY
import json
from pathlib import Path
vn = "$VN"
vc = int("$VC")
root = Path("$ROOT")
version = json.loads((root / "dist/version.json").read_text())
version["versionCode"] = vc
version["versionName"] = vn
(root / "dist/version.json").write_text(json.dumps(version, indent=2) + "\n")
catalog = json.loads((root / "dist/versions.json").read_text())
notes = {
    "0.1.24": "Persist Dash + extras across app restart",
    "0.1.25": "Settings simulation simple On/Off radio",
    "0.1.26": "Default GitHub + OpenAI tokens pre-filled",
    "0.1.27": "Full-screen Torque-style sensor picker with live green rows",
    "0.1.28": "Remove deep search (use picker instead)",
    "0.1.29": "Red Orbit side wheels smooth circular scroll",
}.get(vn, catalog["latest"].get("notes", ""))
catalog["latest"] = {
    "versionCode": vc,
    "versionName": vn,
    "apkUrl": "https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk",
    "notes": notes,
}
rel = catalog.setdefault("releases", [])
if not any(r.get("versionCode") == vc for r in rel):
    rel.append({
        "versionCode": vc,
        "versionName": vn,
        "apkUrl": f"https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/archive/FB2-Diag-{vn}.apk",
        "notes": notes,
    })
rel.sort(key=lambda r: r["versionCode"])
(root / "dist/versions.json").write_text(json.dumps(catalog, indent=2) + "\n")
print(f"Catalog bumped to {vn} (code {vc}): {notes}")
PY

bash scripts/package-hu-apk.sh
cp dist/FB2-Diag-debug.apk "dist/archive/FB2-Diag-${VN}.apk"
./gradlew testDebugUnitTest lintDebug
echo "Shipped ${VN} (code ${VC})"
