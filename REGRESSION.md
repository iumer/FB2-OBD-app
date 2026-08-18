# FB2 Diag — Regression checklist

> ## ⚠️ Base tree is 0.1.15; updater is 0.1.16; Nakamichi keep-alive is 0.1.17
>
> `app/` started as an exact copy of commit `d3790be` (0.1.15). Restored so far:
> in-app updater (0.1.16), Nakamichi keep-alive (0.1.17), battery ALLOWED row
> (0.1.18), Settings stop-simulation (0.1.19), live-ELM Disconnect chip (0.1.21: all themes),
> deep-search heroes-live + blinking freshness LEDs (0.1.22), Classic hero scrolls with Dash (0.1.23).
> Crash reporter, full-screen sensor picker and Robolectric tests are still **not** in this tree.

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
| I08 | App very laggy / slow | Fixed | Timeouts **900 ms poll / 450 ms probe+recover**; core-PID-only while recovering; less ATSP thrash; **PID rotate** (heroes every cycle, ≤4 secondaries) |
| I34 | Speed stuck / under-reads (e.g. 65 vs ~98 km/h) while RPM moves | Fixed | `PidPollPlanner` never fail-streak-skips RPM/Speed; `SnapshotFreshness` clears Speed after 2.5s stale; unit test recreates freeze |
| I36 | Want Torque-style green blink when a value is freshly fetched | Fixed | Shared blink clock (`FreshnessLed` + `FreshnessBlinkHost`) on Classic + OptA/B/C; bright pulse while `freshAtMs` is live, dim when stale |
| I37 | Long-trip: LOG only in RAM until STOP (crash loses hours) | Fixed | Checkpoint CSV to disk every ~60s from LOG start; finalize on STOP |
| I38 | Long-trip: UNABLE soft-recover loops forever (sticky Dash) | Fixed | ATRV-only ≠ healthy cycle; hard reconnect after **5** soft recovers (**8** while ATRV still answers) |
| I39 | Long-trip: Coolant/Battery/MAF/RPM sticky last-good | Fixed | Sanitize clears safety fields after TTL; smoother clears on null; EST gear needs fresh RPM+Speed |
| I40 | Deep search shows 1/N then fails; Dash goes laggy / **pauses fetching** during search | Fixed | Heroes-only poll during deep search; each ATSH strategy is `withLinkExclusive`; TTL `holdValues` so Dash does not blank |
| I41 | Need FB2 vs Generic OBD2 profiles (no Honda junk on other cars) | Fixed | Settings → Vehicle profile; SAE-only catalog/pages/DIAG/deep search for Generic; Mode 0A permanent DTCs |
| I42 | Dash swipe/scroll laggy in Demo (and weak car HUs) | Fixed | **One** shared LED clock (not per-tile Animatable); Demo 800 ms; pager beyondBounds=0; accel UI 2 Hz; throttle trip/health/AA; fewer columns/slots |
| I43 | Gear confidence % clipped on phone hero | Fixed | Taller hero; badge text without Trim.Both; wider gear column |
| I44 | Battery voice too aggressive; ELM under-reads vs multimeter | Fixed | Voice only ≤11.8V; prefer ATRV over 0142; 3-sample ATRV median |
| I45 | FB2 coolant bands/voice retune (green≤95 … alarm≥104) | Fixed | Defaults + schema 4 migration: 95/100/103 colours, voice ≥104 |
| I46 | Want selectable Dash themes Classic/OptA/OptB/OptC | Fixed | Immersive Opt themes + app-wide ThemePalette/MaterialTheme colour language |
| I47 | Theme must recolour entire app menus (Faults/AI/DIAG/…) | Fixed | FB2Theme(palette) + CompositionLocal; screens use MaterialTheme.colorScheme |
| I09 | Debug log Share does nothing | Fixed | FileProvider share; HU fallback → Downloads/FB2-Diag + path dialog |
| I10 | Value LOG Share broken / truncated (large CSV as EXTRA_TEXT) | Fixed | FileProvider CSV share; same HU save fallback |
| I11 | Voice alerts must keep working with screen off (real ELM) | Fixed | `ObdMonitorForegroundService` + wake lock + AudioFocus |
| I27 | Battery red/orange on Dash but no audible alarm | Fixed | CRITICAL (above-idle ELD) → “Battery critical”; orange ELD dips silent; Settings Check sound alert |
| I28 | Alerts duck CarPlay/Z-Link and volume never restores | Fixed | Default no duck; Settings “CarPlay / Android Auto connected” Yes↔no-duck (inverted); no SCO/MUSIC while A2DP |
| I29 | Split-second threshold spikes trigger false alarms | Fixed | Per-key `AlertPolicy` holds (coolant ~4s, battery ~25s) + EMA + hysteresis latch |
| I30 | Want satellite tap to set collapsed blob metric | Fixed | Tap satellite pins primary (persisted); default Coolant until changed |
| I31 | Want LOG on by default for real ELM | Fixed | Auto-start value logging on live ELM connect |
| I32 | Hard to get logs off HU | Fixed | GitHub `logs/car-uploads/` + AI `logs/ai-reports/` sync + Upload + Downloads |
| I33 | Want OpenAI analyze of drive data | Fixed | DIAG → Analyze via AI; live window / saved log; `.txt` reports |
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
| I24 | Dash text too small to read while driving on HU | Fixed | `DashType` HU scale (hero **30**sp / tile **22**sp, 80dp tiles, 92dp hero); ellipsis; fewer wider columns |
| I25 | Floating bubble ring too small / overflow on HU | Fixed | `FloatingDashLayout`: 92/100/max **340**dp (tightened by I64); shrinks on short-edge HUs |
| I26 | Log Share shows “No apps…” / opens Bluetooth search on HU | Fixed | Always save to Downloads/FB2-Diag; ignore BT as share target |
| I48 | Want in-app Update button (check / download / install or “up to date”) | Fixed | Settings → App update; catalog `versions.json` lists every newer release; pick one to download |
| I49 | Soft-recover / ATRV-only frames wipe Dash mid-drive; UI RETRY blanks bubble | Fixed | ATRV-only = blank sticky; recover heartbeat + 450ms AT timeout; stale UI 12s; deep-found TTL/clear; deep search gentle restore |
| I50 | Analyze via AI on Generic OBD2 still wrote Honda Civic FB2 | Fixed | Profile-aware system/user prompt + VIN if Mode 09 available; otherwise “generic SAE data from a car” |
| I51 | Extra Dash sensors added via **+** vanished after closing the app | Fixed | Persist `filesDir/dash_extra_pids.json`; reload on start (Honda extras stay on disk when switching Generic) |
| I52 | Sensor picker was a tiny dialog — hard to read, no live values | Fixed | Full-screen Torque-style Select sensor: category list, green = ECU answered with Latest value, dark = No data received |
| I53 | Classic Dash RPM/Speed bar and Select sensor search chrome stayed pinned while scrolling | Fixed | Classic TopBar+hero collapse on scroll; picker title/search/chips scroll with the list |
| I54 | Opening Select sensor / search blanks Dash values (appear then vanish) | Fixed | Picker no longer pauses Mode 01; 1 extra PID per poll cycle; live snapshot beats support bitmask |
| I55 | Deep search still pauses live fetching | Fixed | Same as I40: heroes keep polling between exclusive ATSH strategies |
| I56 | Nakamichi / phone kills app mid-drive so LOG cannot be trusted vs Torque | Fixed (restored 0.1.17) | Process-scoped ViewModel (`Fb2App`); FGS `stopWithTask=false` + sticky reconnect; battery unrestricted prompt |
| I57 | Green freshness dots static / missing on some themes | Fixed | Shared blink on Classic + OptA/B/C; dim when that field is not freshly fetched |
| I58 | DIAG Faults Read blanks Dash heroes (`--`) while CONNECTED | Fixed | Mode 03/07/0A/09/probes use `withLinkExclusive` + `withDashKeptAlive` (heroes keep polling between commands; TTL `holdValues` mid-cycle) |
| I59 | Floating bubble blanks on RETRY while phone Dash keeps values | Fixed | `showingLiveValues` sticky during CONNECTING; amber RETRY rim; `publishCarDash` every snapshot frame |
| I60 | Bubble collapsed chip missing LIVE / RETRY / DEMO tag | Fixed | `bubbleLinkTag` on center text (LIVE · COOL · 91°C) |
| I61 | Bubble n/s tiles showed cyan accent ring | Fixed | Unknown health → grey rim; Load/Throttle display-only (null health) |
| I62 | Bubble not restored after HU process death / ELM reconnect | Fixed | `FloatingDashPrefs`; `stopWithTask=false`; START_STICKY + onTaskRemoved restart; `maybeRestoreFloatingBubble()` |
| I63 | Bubble ~1 Hz updates felt frozen vs phone Dash | Fixed | `publishCarDash()` on every live snapshot, not only 1 Hz heavy UI tick |
| I64 | Expanded bubble ring too large on phone / short-edge HU | Fixed | `FloatingDashLayout`: max 340dp, 72dp edge margin |
| I65 | MIN 900 ms fallback backgrounded before overlay READY | Fixed | Removed timeout — only `ACTION_READY` triggers `moveTaskToBack` |
| I66 | Swiping Fuel/Trip/Trans auto-probe starved ELM and blanked Dash | Fixed | Tab `LaunchedEffect` no-op; Probe/Refresh buttons only |
| I67 | Dual FGS notifications felt noisy | Fixed | Shared group `fb2_diag_session`; bubble channel IMPORTANCE_MIN |
| I68 | Sensor picker “N readable” confused vs main Dash heroes | Fixed | Subtitle clarifies catalog scan vs heroes-always-live |
| I69 | 0.1.27 only battery/ATRV live — heroes n/s everywhere (FB2 + Generic) | Fixed | `shouldRecoverAfterResume` only after `FULL_PAUSE`; field `mergeLastGood`; batch Mode 01 picker scan; Demo reconnect guard |
| I70 | 0.1.28 crash on ELM connect (Demo snapshot + mergeLastGood + FGS) | Fixed | Clear snapshot on fresh live `useSource`; runCatching FGS/bubble restore; `attachOverlay` guard; `ElmConnectTransitionTest` |
| I71 | Check for updates stuck on stale 0.1.27/0.1.28 (raw CDN cache) | Fixed | GitHub Contents API primary fetch; raw CDN fallback with cache-bust |
| I72 | Shipped APK was v2-only debug (11 MB) — violates documented HU requirement | Fixed | Ship via `scripts/package-hu-apk.sh`: v1+v2 signed, release classpath, 7.3 MB |
| I73 | Whole suite was pure-JVM, so I69/I70 shipped green while the app crashed | Fixed | Robolectric `ElmConnectRuntimeTest` drives the real `DashboardViewModel` on a real Android context |
| I74 | Unguarded FGS restart in both services' `onTaskRemoved` — swiping app from recents could crash on API 31+ | Fixed | `runCatching` around both restarts (matching the already-guarded call sites); `ForegroundServiceRuntimeTest` |
| I75 | 15 of 16 `publishCarDash()` / 2 of 3 `ensureElmMonitor()` call sites unguarded | Fixed | Both made safe **inside the function** so no caller can reintroduce the 0.1.28 crash |
| I76 | One-shot DIAG jobs (Faults/Fuel/Trans/Honda/Mode 09/deep scan) had no exception handling — `viewModelScope` has no handler, so a throw killed the process | Fixed | `launchDiag()` catches, logs, and clears the page spinner; `throwingFaultsProbe_doesNotCrash_andClearsSpinner` |
| I77 | Bubble drag called `updateViewLayout` unguarded — `BadTokenException` if dragged during teardown | Fixed | `runCatching` in `applyDrag`; detaches and stops the service on failure |
| I78 | REGRESSION/AGENTS quoted stale constants (650 ms, cap 3, 34sp, 400dp) | Fixed | Corrected to 900/450 ms, cap 5 (8 on ATRV), hero 30sp, bubble 340dp |
| I79 | **Still crashing on connect at 0.1.33.** `startForeground()` failure was swallowed, so the service never reached foreground and Android killed the process with `ForegroundServiceDidNotStartInTime` ~5 s later — thrown on the main looper where no `runCatching` can reach it | Fixed | Both services now `stopSelf()` when promotion fails |
| I80 | No stack trace available for on-car crashes (no logcat in the car) | Fixed | `CrashReporter` persists trace + device + **recent ELM log**; prompts to save on next launch; `CrashReporterTest` |
| I81 | `loadBondedDevices` / `connectTo` could throw `SecurityException` on the UI thread if BT permission is revoked after the gate | Fixed | Both wrapped; connect surfaces a toast instead of dying |
| I82 | Show every newer version in a list, not only “latest”; after installing 0.1.18 the next check shows 0.1.19+ | Fixed | `AppUpdateChecker.newerThan`; Settings list GET/INSTALL per row; `versions.json` catalog |
| I83 | Settings → App update HTTP 404 on `version.json` / `versions.json` | Fixed | `publish-latest-apk.sh` ships catalog + archive; `verify-latest-catalog.sh` post-push; `PublishCatalogGuardTest` |
| I84 | Classic Dash hero (RPM/Speed/Gear) sticky while tiles scroll | Fixed | Hero is first row inside Dash `LazyVerticalGrid` — scrolls off with tiles |
| I83 | No way to stop Demo values from Settings | Fixed | Settings → Simulation: **STOP** while Demo is running; **Demo / simulated data** toggle persists `allowDemo` |
| I84 | After live OBD connect, chip still said CONNECT and opened the picker | Fixed | Live ELM → red **DISCONNECT** on Classic + OptA/B/C (visible chip + ☰ menu). Demo stays CONNECT |

When the user reports a **new** bug, add a new `Ixx` row here (Status: Open → Fixed)
and add a matching automated or manual check in sections 2–3.

---

## 2. Automated suite (run after every code change)

Run from repo root (`/workspace`):

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
# Ship the sideload APK with this script — NOT a plain `assembleDebug` copy.
# It produces a v1+v2 signed release-classpath APK (~7 MB). A plain AGP debug
# APK is v2-only and ~11 MB, which hangs some car HU installers (I72).
bash scripts/package-hu-apk.sh   # writes dist/FB2-Diag-debug.apk
# Also copy to dist/archive/FB2-Diag-<versionName>.apk and add a row to
# dist/versions.json so older installs can pick this build from the list.
# Then publish to branch `latest` (script copies APK + version.json + versions.json + archive):
bash scripts/publish-latest-apk.sh   # auto-runs verify-latest-catalog.sh (must pass)
#   https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk
# Or re-check catalog any time:
bash scripts/verify-latest-catalog.sh
```

All tasks must pass. Verify the shipped APK before publishing:

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose --min-sdk-version 21 dist/FB2-Diag-debug.apk
# must print: v1 scheme ... true  AND  v2 scheme ... true
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump badging dist/FB2-Diag-debug.apk | grep '^package:'
# versionCode must match dist/version.json AND dist/versions.json latest
```

After publishing, confirm the catalog the app actually reads:

```bash
curl -fsSL "https://api.github.com/repos/iumer/FB2-OBD-app/contents/dist/versions.json?ref=latest" \
  | python3 -c "import sys,json,base64;print(base64.b64decode(json.load(sys.stdin)['content']).decode())"
```

### Unit-test map (what “green” covers)

| Test class | Guards |
|------------|--------|
| `ObdResponseParserTest` | Mode 01 decode, ATRV parse, NO DATA, gear ratio |
| `SupportedPidsTest` | Support bitmask mapping |
| `GearEstimatorTest` | FB2 speed/RPM → gear |
| `HealthEvaluatorTest` | Coolant/battery/trims/MAF/coast/ATF/DTC bands |
| `HealthThresholdsTest` | Custom bands + **MAF schema migration** |
| `VoiceAlertRulesTest` | Voice critical thresholds |
| `VoiceAlertDebouncerTest` | Per-key hold (coolant ~4s / battery ~25s) before sound |
| `DiagnosticBrainTest` | EMA smoothing + zone hysteresis latch |
| `SpeedFreshnessAndPollPlannerTest` | Hero RPM/Speed always polled; stale Speed cleared (65-vs-98 freeze) |
| `AiAnalysisPayloadBuilderTest` | FB2 prompt + live/saved window truncation |
| `DiagnosticEventTrackerTest` | Zone / fuel-loop events; battery ZONE hysteresis (no ELD flap spam) |
| `DtcDecoderTest` | Mode 03/07 DTC frames |
| `DeepSearchKnowledgeBaseTest` | Strategy order + demo ATRV/ambient recovery |
| `VehicleProfileTest` | FB2 vs Generic catalogs/pages/deep-search/Mode 0A demo |
| `FeatureExpansionTest` | Catalogs, readiness, VIN, trip, `n/s` overlay, probes |
| `AccelerationTimerTest` | 0–100 perf timer |
| `ObdLoggerTest` | Debug buffer, lean CSV, LOG toggle |
| `SessionLogStoreTest` | Saved session naming |
| `CarDashBuilderTest` | Android Auto dash model |
| `FloatingDashMetricsTest` | Radial order, RPM redline, RETRY sticky values, OFF on ERROR, n/s grey health |
| `ElmPollHoldRecoverTest` | HEROES_ONLY→NONE must not arm recover; `mergeLastGood` keeps heroes on partial frames |
| `ElmConnectTransitionTest` | Fresh ELM connect clears Demo prev; mid-session partial merge keeps heroes |
| `ElmConnectRuntimeTest` | **Android runtime (Robolectric).** Real `DashboardViewModel` on a real `Application`: construct, Demo→live ELM connect without crashing, no Demo leak into first live frame, ATRV-only frame keeps heroes, disconnect clears state |
| `ForegroundServiceRuntimeTest` | **Android runtime (Robolectric).** Neither service propagates an exception out of `onTaskRemoved` (app swiped from recents) or `startOverlay` |
| `DashboardSnapshotTest` / `ScreensSnapshotTest` / `ConnectSheetSnapshotTest` | Paparazzi UI snapshots |
| `DemoAllowPolicyTest` | Settings stop-simulation: off while Demo disconnects; on while idle starts Demo; live ELM left running |
| `ConnectActionPolicyTest` | Live ELM → DISCONNECT; Demo/idle → CONNECT; RETRY/ERROR not disconnect |
| `AppUpdateCheckerTest` | versions.json catalog parse; newerThan lists 0.1.16–0.1.20 then only 0.1.19+ after installing 0.1.18 |
| `PublishCatalogGuardTest` | publish script ships both JSON catalogs + archive; dist matches BuildConfig; no `lumer` typo; archive APKs on disk |
| `DashExtraPidStoreTest` | **+** extras survive process death (`dash_extra_pids.json`) |
| `SensorPickerReadingsTest` | Green/live vs waiting vs ECU-unsupported; SAE support bitmask parse; **ATRV battery live even if 0142 unsupported**; 2026-07-24 FB2 Dash PIDs stay LIVE |
| `ChromeCollapseTest` | Classic Dash chrome hides on scroll-up and returns at list top |
| `FreshnessLedTest` | Shared blink: bright on fetch, dim when stale |
| `KeepAlivePolicyTest` / `LastElmStoreTest` | Reconnect after HU process death unless user disconnected |
| `DashBusKeepAliveTest` | Faults/VIN/probes set heroes-only hold then restore; nested hold stays |
| `SpeedFreshnessAndPollPlannerTest` | Heroes-only hold; `holdValues` does not TTL-blank Dash during exclusive ATSH |

---

## 3. Manual / car checks (when ELM or UI behaviour changed)

Do these on a phone with the new `dist/FB2-Diag-debug.apk` whenever the change
touches ELM, Dash health, deep search, logging, or share:

1. **Demo smoke** — open app → Demo feed moves; swipe all Dash pages for the active profile (Generic has no Trans).
1a. **Stop simulation** — Settings → Simulation → **STOP** (or turn off Demo / simulated data). Dash goes `--` / disconnected. Toggle off survives process death; Connect → Demo or toggle on starts it again.
1b. **Vehicle profile** — Settings → select Generic OBD2 → Trans + Honda DIAG gone; picker SAE-only; badge `OBD2`. Switch back to FB2 → Trans + Honda modules return.
1c. **Faults** — Read shows Stored / Pending / Permanent (Mode 0A); Clear refreshes lists.
2. **Connect live ELM** — chip goes red **DISCONNECT**; Battery shows volts (ATRV); MAF at idle ~3–5 g/s and **not** CRITICAL. Tap DISCONNECT → adapter drops, chip returns to CONNECT.
3. **Idle stability** — leave idling several minutes; Dash stays populated; on drop chip shows `RETRY` and auto-recovers without tapping.
4. **Deep analysis (Battery)** — triple-tap Battery when n/s (or after a glitch) → should recover via ATRV when adapter is powered.
5. **Rough Idle page** — opens with live values quickly; does not hang on Probing if bus is unhealthy.
6. **Debug log Save** — Settings → Debug log → **Save**. Writes `Downloads/FB2-Diag/` (never opens Bluetooth-only share). Dialog can Open file / optional Share… if a real app exists.
7. **Value LOG Save** — same Save flow for current buffer or listed sessions.
8. **Screen off alerts** — real ELM connected → sticky notification present; with voice alerts on, a critical condition still beeps + speaks after screen off.
9. **Check sound alert** — Settings → **Check sound alert** must play beep + “Battery critical” on phone and (when BT audio is up) in the car.
9. **Floating bubble (MIN)** — grant overlay permission → MIN → collapsed circle shows **LIVE** tag; drag works; tap expands radial ring; on ELM drop bubble keeps last-good with **RETRY** amber rim (matches phone); vertical swipe pages; tap satellite pins; idle ~6s auto-collapses; hold opens app; Exit removes bubble. After HU kill + ELM reconnect, bubble restores if MIN was active.
10. **Car HU layout (automated)** — Paparazzi at 1024×600, 1280×720, 1920×720 in `CarHuSnapshotTest` / `CarHuBubbleSnapshotTest` (collapsed + radial expanded). Adaptive column counts for Dash/dense pages.
11. **Android Auto** — phone UI / DHU only unless installed via Play Internal testing.
12. **Morning regression trio** — Battery volts via ATRV (not n/s); Idle page shows values (not stuck Probing); MAF idle ~3–5 g/s = IDLE OK not CRITICAL.

---

## 4. Agent rule

Future cloud agents: after code changes, run **section 2** fully before commit.
If the change affects ELM/Dash/deep-search/share/alerts, note which **section 3**
items were verified (or explicitly blocked, e.g. no car in VM). Never delete
issue rows from section 1 — mark them Fixed/Open/Won't fix.

**Mutation rule (added after I69/I70 shipped green).** A green suite is not
evidence. When you fix a crash or a regression, prove the new test can fail:
revert the fix, confirm the test goes red, restore the fix, confirm green again.
Record that in the commit message. Three consecutive releases (0.1.27, 0.1.28)
passed every test while broken on the user's car — a test that has never failed
has not been shown to test anything.

**Runtime rule.** Pure-Kotlin tests cannot instantiate `DashboardViewModel`,
services, or anything touching `Context`, which is exactly where the connect
crash lived. Anything on the connect / foreground-service / permission path
needs a Robolectric test (`ElmConnectRuntimeTest`), not just a logic test.
Do not call `advanceUntilIdle()` there — Demo polling and the upload/voice jobs
loop forever, so it never returns; step virtual time with `advanceTimeBy`.
