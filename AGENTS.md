# AGENTS.md

## Cursor Cloud specific instructions

This repo is a single-module Android app (Kotlin + Jetpack Compose), Gradle
module `:app`. Standard commands live in `README.md`; the notes below are the
non-obvious cloud specifics.

### Toolchain (already provisioned in the VM image)

- JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` and the Android SDK at
  `~/android-sdk` are pre-installed. `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`
  and `PATH` are exported from `~/.bashrc`, so interactive shells pick them up.
  If a non-login shell doesn't have them, export `JAVA_HOME` and `ANDROID_HOME`
  before invoking `./gradlew`.
- Gradle is pinned via the wrapper (`./gradlew`, 8.9). AGP 8.5.2 / Kotlin 1.9.24 /
  Compose compiler 1.5.14.

### Commands

- **After every code change, run the full checklist in [`REGRESSION.md`](REGRESSION.md)**
  (unit tests + lint + debug APK → `dist/FB2-Diag-debug.apk`). That file also
  lists every user-reported issue that must stay fixed.
- Unit tests (pure JVM, fast): `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Build APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (also copy to `dist/FB2-Diag-debug.apk` for sideload).

### Running / demoing the UI

- **There is no `/dev/kvm` in the cloud VM**, so a hardware-accelerated Android
  emulator will not run here. To see the actual Compose UI, use Paparazzi, which
  renders on the JVM (no device/emulator): `./gradlew recordPaparazziDebug`
  writes PNGs under `app/src/test/snapshots/images/`. On a real phone, install
  the debug APK instead.
- The app defaults to `DemoObdSource` (a simulated driving feed), so the full
  dashboard is usable with no adapter connected — handy for cloud UI work.
- New pages live under Settings: Custom sensors, Fuel, Trip, Transmission,
  Deep diagnostics, Vehicle info (Mode 09), Honda modules probe, Health,
  Maintenance, G-force, Faults, Performance, Debug/Value logs.

- **Android Auto:** Car App Library IOT template (`Fb2CarAppService`). Phone must
  keep FB2 Diag running (or recent) so `VehicleLiveStore` has live Dash data.
  **Important:** Android Auto “Unknown sources” does **not** apply to Car App
  Library apps. Sideloaded APKs usually **will not** appear in Customize launcher
  on a real car. Practical options:
  - **DHU** (Desktop Head Unit) — official, sideload OK, no Play needed for desk QA.
  - **Play Internal testing / Internal app sharing** — official path for a **real HU**
    without waiting for full car-quality review (still uses Play infra).
  - **Phone-only / mirroring (Fermata etc.)** — no true CAL projection; lab-only.
  - Root / AAWireless / AAAD — fragile unofficial paths; not a shippable CAL strategy.
  CONNECT from AA starts Demo if not live; pick real ELM on the phone.

### Notes / gotchas

- Core diagnostic logic is deliberately pure Kotlin under `com.fb2.obd.obd`
  (parser, gear estimator, health thresholds, PID catalogs, trip computer) so
  it stays JVM-unit-testable without Android. Prefer adding logic there and
  keeping `ui`/`data` thin.
- Honda enhanced packs in `HondaPidCatalog` use Mode 22 placeholders
  (`2211xx`…`2219xx`). Real FB2 / market-specific ECUs often need different IDs
  **and** CAN headers (`ATSH`). Until recently the app never sent headers — that
  is an app/protocol gap, not proof the ELM adapter is broken. **Triple-tap** any
  `n/s` tile to run **Deep research** (`DeepSearchKnowledgeBase` + `DeepSensorSearch`).
- Coolant2 (`0167`), Ambient (`0146`), and LTFT (`0107`) frequently return
  `n/s` on this Civic because the ECM support bitmask omits them — usually an
  ECU limitation. Deep search still forces the PID and tries ECM headers.
- **Demo simulation** intentionally marks Coolant2 / Ambient / LTFT as `n/s` so
  you can triple-tap → Yes → watch the library walk and recover a value without a car.
- **User adapter (Daraz):** OBD mini **ELM327 Bluetooth 5.1 / V2.1** clone. These
  are almost never genuine ELM chips. Typical limits vs our app:
  - Fine for basic SAE Mode 01 live data (RPM/speed/coolant/MAF) on CAN cars.
  - Often weak/broken on long commands, some AT cmds (`ATAL`/`ATPP`), multi-frame,
    and multi-ECU / Mode 22 with `ATSH` — deep search may still fail even with
    correct headers. That is adapter firmware, not necessarily app logic.
  - Coolant2/Ambient/LTFT `n/s` on FB2 remains primarily **ECU unsupported**.
  - Honda TCM/ABS/HVAC packs need real IDs + headers; a better adapter
    (OBDLink MX+/CX, ELS27-class) helps once IDs are known, but won't invent
    unsupported SAE PIDs.
- “AI explanations” on the Faults screen are curated text in `DtcCatalog.explain`,
  not a live LLM.
- `sdkmanager`/Gradle may print `SDK XML version 4 ... only understands up to 3`.
  It is harmless with the current command-line tools.

### Diagnostic spec (Dash behaviour)

- Main Dash tiles include **Fuel loop** (PID `0103` → OPEN/CLOSED LOOP text),
  **DTCs** (from readiness / Mode 01 PID 01, refreshed ~12s), and **Health**
  (`HealthScore.vehiclePct`). Load & Throttle are display-only (always green).
- Colour bands / voice thresholds live in `HealthThresholds` (long-press editor).
  Coolant **voice** alerts only above `coolantVoiceAbove` (default 110°C), even
  though the red tile starts earlier (`> coolantElevatedMax`, default 103°C).
- **Event logging** (`DiagnosticEventTracker` → `ObdLogger.logEvent`) always
  records zone/gear/ELM/DTC/fuel-loop transitions into the `# events` CSV
  section — independent of the continuous value LOG toggle.
- **Value LOG** captures **main Dash only** (hero RPM/Speed/Gear + Dash tiles
  including any `+` extras). It does **not** dump Fuel/Trip/Trans/Perf/etc.
  Saved CSVs are lean (`events` + `dashboard_snapshots` + `dash_tiles`).
  **Save logs:** always writes `Downloads/FB2-Diag/` + `Documents/exports/`
  (never auto-opens a Bluetooth-only share sheet on car HUs). Dialog offers
  Open file / optional Share… only when a useful non-BT app exists.
- **ELM idle drop:** cheap clones often hang mid-poll. The app uses short PID
  timeouts (~650 ms poll / ~450 ms probe), skips repeatedly-failing PIDs, keeps
  last-good Dash values, and retries RFCOMM forever with backoff (UI shows
  `RETRY`). A blank reconnect frame must not wipe the Dash or fake `Engine Stop`.
  **Battery** prefers `ATRV` (adapter rail voltage, Torque-style) every cycle even
  during `UNABLE` — do not gate ATRV on ECU bus health.
- **Screen off / background:** real ELM sessions start
  `ObdMonitorForegroundService` (`connectedDevice` FGS + sticky notification +
  `PARTIAL_WAKE_LOCK`). Demo mode must not start it. Voice alerts play a short
  beep + TTS. **Default is CarPlay/Z-Link safe:** no audio-focus duck (many HUs
  duck Z-Link and never restore volume). Optional Settings → **Lower CarPlay
  during alerts** re-enables MAY_DUCK. Never start BT SCO or rewrite STREAM_MUSIC
  while A2DP is present. Settings → **Check sound alert** plays the test alarm.
  Battery orange → “Battery low”; red CRITICAL → “Battery critical”.
  Voice/sound alerts require the same condition for ~2.5s
  (`VoiceAlertDebouncer`) before beeping — Dash tiles still update live.
  Settings **Check sound alert** bypasses the hold.
- **Floating Dash bubble:** Dash **MIN** chip starts `FloatingDashOverlayService`
  as a **foreground service** (`specialUse` + sticky notification) so the bubble
  survives going to Home / CarPlay. MainActivity waits for `ACTION_READY` (overlay
  attached) before `moveTaskToBack` — do not background first or OEMs may hide /
  defer the WindowManager view. Needs `SYSTEM_ALERT_WINDOW`.
  Collapsed = one draggable circle. Expanded = center + up to **5 satellites**.
  Vertical swipe pages metrics. Idle ~6s auto-collapses. **Long-press opens the
  app and dismisses the bubble.** Exit & disconnect also stops the overlay.
  Bubble position is clamped to the current display (landscape app → portrait
  Home must not push it off-screen).
- **Dash tile remap:** Double-tap any main-Dash tile to open the same sensor
  picker as `+`. Remaps persist in `dash_tile_overrides.json`. Remapped tiles
  show a `2× change` hint; picker can **Restore default**. Triple-tap still
  runs deep search on n/s tiles; long-press edits health thresholds.
- **Sensor picker search:** Type ≥2 chars in the dialog search box to filter
  by label / request / category (skip category drill-down).
- **Car HU readability:** Dash uses `DashType` (hero ~34sp, tile values ~22sp,
  88dp tiles, fewer/wider columns). Floating bubble sizes live in
  `FloatingDashLayout` (collapsed/center 92dp, sat 100dp, expanded max 400dp,
  auto-shrinks on short-edge HUs). Re-record HU Paparazzi after type-scale or
  bubble size changes. Prefer glanceability without filling the short edge.
- MAF/MAP health is context-aware (idle / coast / cruise / heavy); pass RPM,
  speed, and throttle into `HealthEvaluator.maf` / `map`. MAF threshold schema is
  currently **3** (`HealthThresholdStore`) — old harsh idle bands are force-migrated.
- **Deep analysis** (`DeepSensorSearch`): restore → ATRV/local first → bus ping →
  only then ATSP/ATSH strategies. Do not thrash protocols while the ECU link is down.
