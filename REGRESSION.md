# FB2 Diag — Regression checklist

**Mandatory for every code change.** After any non-trivial edit, run the full
automated suite below and tick the relevant manual items before calling the
work done. Update this file when the user reports a new issue or a new test is
added.

---

## 1. Issues reported by the user (fix inventory)

Keep these fixed. If a change might touch one of these areas, re-verify it.

| ID | Issue | Status | Where / how verified |
|----|--------|--------|----------------------|
| I01 | ELM drops after ~40s idle → blank Dash / false Engine Stop | Fixed | Sticky last-good + ignore blank frames; endless RFCOMM retry (`RETRY` chip) |
| I02 | Auto-reconnect must not require pressing a button | Fixed | Background backoff reconnect; RECONNECT only for fresh adapter pick |
| I03 | Battery shows n/s even though Torque shows volts | Fixed | ATRV every cycle even during `UNABLE`; sticky volts |
| I04 | Deep analysis said “nothing found” for Battery | Fixed | Deep search: restore → ATRV retries → bus gate → ECU strategies |
| I05 | MAF flagged CRITICAL while Torque shows normal | Fixed | Schema 3 R18 bands; coasting = `COAST OK`; PID `0110` confirmed correct |
| I06 | Suspicion wrong PID/protocol for other sensors | Partially | Compare vs Torque trackLog; LTFT missing in **both** apps (ECU). Re-check if new mismatches appear |
| I07 | Rough Idle page stuck on “Probing…” | Fixed | Live Dash prefill; Mode 01 first; skip Mode 22 if bus unhealthy; shorter probe timeout |
| I08 | App very laggy / slow | Fixed | Timeouts ~650/450 ms; core-PID-only while recovering; less ATSP thrash |
| I09 | Debug log Share does nothing | Fixed | FileProvider + URI grants to resolvers + toast |
| I10 | Value LOG Share broken / truncated (large CSV as EXTRA_TEXT) | Fixed | FileProvider CSV share for session logs |
| I11 | Voice alerts must keep working with screen off (real ELM) | Fixed | `ObdMonitorForegroundService` + wake lock + AudioFocus |
| I12 | Android Auto sideload not showing on real HU | Documented | Needs Play Internal testing/sharing; DHU for desk — see `AGENTS.md` |
| I13 | LOG should be main Dash only (not Fuel/Trip/Trans dumps) | Fixed | Lean CSV: events + dashboard_snapshots + dash_tiles |
| I14 | Pakistan units (km, km/h, km/L) | Fixed | Product default |
| I15 | Health colours / long-press threshold editor | Fixed | `HealthThresholds` + store |
| I16 | Fuel Open/Closed Loop on Dash | Fixed | PID `0103` |
| I17 | Always-on event logging (`# events`) | Fixed | `DiagnosticEventTracker` |

When the user reports a **new** bug, add a new `Ixx` row here (Status: Open → Fixed)
and add a matching automated or manual check in sections 2–3.

---

## 2. Automated suite (run after every code change)

Run from repo root (`/workspace`):

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/FB2-Diag-debug.apk
```

All three Gradle tasks must pass. Copy the APK into `dist/` so sideload artifacts stay current.

### Unit-test map (what “green” covers)

| Test class | Guards |
|------------|--------|
| `ObdResponseParserTest` | Mode 01 decode, ATRV parse, NO DATA, gear ratio |
| `SupportedPidsTest` | Support bitmask mapping |
| `GearEstimatorTest` | FB2 speed/RPM → gear |
| `HealthEvaluatorTest` | Coolant/battery/trims/MAF/coast/ATF/DTC bands |
| `HealthThresholdsTest` | Custom bands + **MAF schema migration** |
| `VoiceAlertRulesTest` | Voice critical thresholds |
| `DiagnosticEventTrackerTest` | Zone / fuel-loop events |
| `DtcDecoderTest` | Mode 03/07 DTC frames |
| `DeepSearchKnowledgeBaseTest` | Strategy order + demo ATRV/ambient recovery |
| `FeatureExpansionTest` | Catalogs, readiness, VIN, trip, `n/s` overlay, probes |
| `AccelerationTimerTest` | 0–100 perf timer |
| `ObdLoggerTest` | Debug buffer, lean CSV, LOG toggle |
| `SessionLogStoreTest` | Saved session naming |
| `CarDashBuilderTest` | Android Auto dash model |
| `DashboardSnapshotTest` / `ScreensSnapshotTest` / `ConnectSheetSnapshotTest` | Paparazzi UI snapshots |

---

## 3. Manual / car checks (when ELM or UI behaviour changed)

Do these on a phone with the new `dist/FB2-Diag-debug.apk` whenever the change
touches ELM, Dash health, deep search, logging, or share:

1. **Demo smoke** — open app → Demo feed moves; swipe Dash → Custom → Idle → Fuel → Trip → Trans → Perf → G-force → Health.
2. **Connect live ELM** — chip goes `LIVE`; Battery shows volts (ATRV); MAF at idle ~3–5 g/s and **not** CRITICAL.
3. **Idle stability** — leave idling several minutes; Dash stays populated; on drop chip shows `RETRY` and auto-recovers without tapping.
4. **Deep analysis (Battery)** — triple-tap Battery when n/s (or after a glitch) → should recover via ATRV when adapter is powered.
5. **Rough Idle page** — opens with live values quickly; does not hang on Probing if bus is unhealthy.
6. **Debug log Share** — Settings → Debug log → Share → system chooser appears (toast “Opening share sheet…”).
7. **Value LOG Share** — stop a LOG session → share CSV via chooser.
8. **Screen off alerts** — real ELM connected → sticky notification present; with voice alerts on, a critical condition still speaks after screen off.
9. **Android Auto** — phone UI / DHU only unless installed via Play Internal testing.

---

## 4. Agent rule

Future cloud agents: after code changes, run **section 2** fully before commit.
If the change affects ELM/Dash/deep-search/share/alerts, note which **section 3**
items were verified (or explicitly blocked, e.g. no car in VM). Never delete
issue rows from section 1 — mark them Fixed/Open/Won't fix.
