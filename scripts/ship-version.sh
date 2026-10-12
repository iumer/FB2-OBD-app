#!/usr/bin/env bash
# Bump dist catalog + build release APK for the version in app/build.gradle.kts.
# APKs are NOT committed — publish them as GitHub Release assets via publish-latest-apk.sh.
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
apk_url = f"https://github.com/iumer/FB2-OBD-app/releases/download/v{vn}/FB2-Diag-{vn}.apk"
version = json.loads((root / "dist/version.json").read_text())
version["versionCode"] = vc
version["versionName"] = vn
version["apkUrl"] = apk_url
(root / "dist/version.json").write_text(json.dumps(version, indent=2) + "\n")
catalog = json.loads((root / "dist/versions.json").read_text())
notes = {
    "0.1.24": "Persist Dash + extras across app restart",
    "0.1.25": "Settings simulation simple On/Off radio",
    "0.1.26": "Default GitHub + OpenAI tokens pre-filled",
    "0.1.27": "Full-screen Torque-style sensor picker with live green rows",
    "0.1.28": "Remove deep search (use picker instead)",
    "0.1.29": "Red Orbit side wheels smooth circular scroll",
    "0.1.30": "Picker keeps MAP/live PIDs readable (Torque-style, no Mode 01 pause)",
    "0.1.31": "Select sensor chrome scrolls with the list (not sticky)",
    "0.1.32": "Probe all Mode 01 PIDs Torque shows as readable",
    "0.1.33": "Red Orbit wheels continuous smooth scroll",
    "0.1.34": "Picker Readable latch: no MAP/Coolant/any PID flicker off green",
    "0.1.35": "Settings Simulation: short line + Off/On radios only",
    "0.1.36": "Picker Refresh + Torque-style one-PID scan; log upload shows GitHub error",
    "0.1.37": "Generic AI/logs labeling; open AI reports in-app; upload pending fixes",
    "0.1.38": "Credentials fill blank only; AI driver notes; MAF=PID 0110 honesty; no key overwrite",
    "0.1.39": "Retain logs after reinstall; keep update APK after install permission; AI reports in Settings",
    "0.1.40": "Settings cleanup: Private keys, less chrome, Simulation matches gear toggle",
    "0.1.41": "Settings categories: Vehicle / Appearance / Background-power (no options removed)",
    "0.1.42": "Security: encrypted keys, no packaged secrets, private logs repo, GitHub Release APKs",
}.get(vn, catalog["latest"].get("notes", ""))
catalog["latest"] = {
    "versionCode": vc,
    "versionName": vn,
    "apkUrl": apk_url,
    "notes": notes,
}
rel = catalog.setdefault("releases", [])
if not any(r.get("versionCode") == vc for r in rel):
    rel.append({
        "versionCode": vc,
        "versionName": vn,
        "apkUrl": apk_url,
        "notes": notes,
    })
else:
    for r in rel:
        if r.get("versionCode") == vc:
            r["versionName"] = vn
            r["apkUrl"] = apk_url
            r["notes"] = notes
# Rewrite historical archive raw URLs → GitHub Release asset URLs.
for r in rel:
    name = r.get("versionName") or ""
    if name and ("/dist/archive/" in r.get("apkUrl", "") or "FB2-Diag-debug.apk" in r.get("apkUrl", "")):
        r["apkUrl"] = f"https://github.com/iumer/FB2-OBD-app/releases/download/v{name}/FB2-Diag-{name}.apk"
rel.sort(key=lambda r: r["versionCode"])
(root / "dist/versions.json").write_text(json.dumps(catalog, indent=2) + "\n")
print(f"Catalog bumped to {vn} (code {vc}): {notes}")
print(f"APK URL: {apk_url}")
PY

bash scripts/package-hu-apk.sh
./gradlew testDebugUnitTest lintDebug checkNoPackagedSecrets
echo "Shipped ${VN} (code ${VC}) — APK is local under dist/ (gitignored). Publish with scripts/publish-latest-apk.sh"
