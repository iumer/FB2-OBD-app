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
| I05 | MAF flagged CRITICAL while Torque shows normal | Fixed | Schema 3 R18 bands; coasting = `COAST OK`; PID `0110` SAE decode unit-tested |
| I06 | Suspicion wrong PID/protocol for other sensors | Partially | Compare vs Torque trackLog; LTFT missing in **both** apps (ECU). Re-check if new mismatches appear |
| I07 | Rough Idle page stuck on “Probing…” | Fixed | Live Dash prefill; Mode 01 first; skip Mode 22 if bus unhealthy; shorter probe timeout |
| I08 | App very laggy / slow | Fixed | Timeouts ~650/450 ms; core-PID-only while recovering; less ATSP thrash |
| I09 | Debug log Share does nothing | Fixed | FileProvider share; HU fallback → Downloads/FB2-Diag + path dialog |
| I10 | Value LOG Share broken / truncated (large CSV as EXTRA_TEXT) | Fixed | FileProvider CSV share; same HU save fallback |
| I11 | Voice alerts must keep working with screen off (real ELM) | Fixed | `ObdMonitorForegroundService` + wake lock + AudioFocus |
| I27 | Battery red/orange on Dash but no audible alarm | Fixed | Alarm tone + TTS; battery ELEVATED → “Battery low”; Settings Check sound alert |
| I28 | Alerts duck CarPlay/Z-Link and volume never restores | Fixed | Default no audio-focus duck; optional “Lower CarPlay during alerts”; no SCO/MUSIC while A2DP |
| I12 | Android Auto sideload not showing on real HU | Documented | Needs Play Internal testing/sharing; DHU for desk — see `AGENTS.md` |
| I13 | LOG should be main Dash only (not Fuel/Trip/Trans dumps) | Fixed | Lean CSV: events + dashboard_snapshots + dash_tiles |
| I14 | Pakistan units (km, km/h, km/L) | Fixed | Product default |
| I15 | Health colours / long-press threshold editor | Fixed | `HealthThresholds` + store |
| I16 | Fuel Open/Closed Loop on Dash | Fixed | PID `0103` |
| I17 | Always-on event logging (`# events`) | Fixed | `DiagnosticEventTracker` |
| I18 | Floating minimize bubble over CarPlay / other apps | Fixed | FGS + READY-before-minimize; radial ring; Exit/long-press dismiss |
| I19 | Exit left floating bubble on screen | Fixed | Exit confirm → `FloatingDashOverlayService.stop` + `finishAndRemoveTask` |
| I20 | Long-press bubble reopened app but left overlay | Fixed | Long-press → open app + `stopSelf()` |
| I21 | Cannot remap built-in Dash tiles | Fixed | Double-tap tile → same sensor picker as `+`; persisted overrides |
| I22 | Sensor picker has no text search | Fixed | Search box in `SensorPickerDialog` (label / PID / category) |
| I23 | MIN toast shows but bubble missing on Home | Fixed | FGS + attach/READY before `moveTaskToBack`; clamp on-screen |
| I24 | Dash text too small to read while driving on HU | Fixed | `DashType` HU scale (~34/22sp, 88dp tiles); ellipsis; fewer wider columns |
| I25 | Floating bubble ring too small / overflow on HU | Fixed | `FloatingDashLayout`: 92/100/max400dp; shrinks on short-edge HUs |
| I26 | Log Share shows “No apps…” / opens Bluetooth search on HU | Fixed | Always save to Downloads/FB2-Diag; ignore BT as share target |

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
6. **Debug log Save** — Settings → Debug log → **Save**. Writes `Downloads/FB2-Diag/` (never opens Bluetooth-only share). Dialog can Open file / optional Share… if a real app exists.
7. **Value LOG Save** — same Save flow for current buffer or listed sessions.
8. **Screen off alerts** — real ELM connected → sticky notification present; with voice alerts on, a critical condition still beeps + speaks after screen off.
9. **Check sound alert** — Settings → **Check sound alert** must play beep + “Battery critical” on phone and (when BT audio is up) in the car.
9. **Floating bubble (MIN)** — grant overlay permission → MIN → collapsed circle appears; drag works; tap expands **radial ring** (up to 5 live values around center); vertical swipe pages next/previous groups; idle ~6s auto-collapses to circle; tap center collapses; hold opens app. **Back → Exit & disconnect** must remove the bubble entirely. (On Dellson: verify over CarPlay if used.)
10. **Car HU layout (automated)** — Paparazzi at 1024×600, 1280×720, 1920×720 in `CarHuSnapshotTest` / `CarHuBubbleSnapshotTest` (collapsed + radial expanded). Adaptive column counts for Dash/dense pages.
11. **Android Auto** — phone UI / DHU only unless installed via Play Internal testing.
12. **Morning regression trio** — Battery volts via ATRV (not n/s); Idle page shows values (not stuck Probing); MAF idle ~3–5 g/s = IDLE OK not CRITICAL.

---

## 4. Agent rule

Future cloud agents: after code changes, run **section 2** fully before commit.
If the change affects ELM/Dash/deep-search/share/alerts, note which **section 3**
items were verified (or explicitly blocked, e.g. no car in VM). Never delete
issue rows from section 1 — mark them Fixed/Open/Won't fix.
