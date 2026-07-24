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

- Unit tests (pure JVM, fast): `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Build APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

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
- “AI explanations” on the Faults screen are curated text in `DtcCatalog.explain`,
  not a live LLM.
- `sdkmanager`/Gradle may print `SDK XML version 4 ... only understands up to 3`.
  It is harmless with the current command-line tools.
