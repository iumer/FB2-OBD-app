# FB2-OBD-app

A personal Android diagnostic dashboard for a Honda Civic **FB2** (1.8L, 5-speed
automatic), built around an ELM327 OBD-II adapter. The goal is a single-screen,
instrument-cluster style dashboard that goes beyond generic OBD apps (Torque
etc.) with Honda-focused diagnostics and a strong transmission dashboard.

> Status: **feature-rich diagnostic build.** Live dashboard + ELM327 Bluetooth,
> full SAE Mode 01 catalog (~150+), custom sensor picker, fuel/trip/transmission
> pages, Mode 02/05/06/09 diagnostics, Honda Mode 22 profile packs (probed against
> the ECU), health scores, maintenance template, G-force, DTC database with tips.
>
> Honda Mode 22 PIDs are **best-effort placeholders** — use **Honda modules /
> full-system probe** on the car and share the debug log so addresses can be
> refined for FB2 Pakistan ECUs.

## Tech stack

- Kotlin + Jetpack Compose (Material 3), single Gradle module `:app`
- Min SDK 26, target/compile SDK 34
- Landscape instrument-cluster UI (keeps screen on)

## Architecture

- `com.fb2.obd.obd` — pure-Kotlin core (no Android deps, unit-tested)
  - `ObdPid` / `ObdResponseParser` — Mode 01 PID definitions + ELM327 response decoding
  - `GearEstimator` — estimates gear from speed/RPM using FB2 gear ratios
  - `HealthEvaluator` — traffic-light status for coolant, battery, fuel trims, ATF
- `com.fb2.obd.data` — data sources
  - `DemoObdSource` — simulated driving feed (runs with no hardware)
  - `Elm327BluetoothSource` — real ELM327 over Bluetooth Classic (SPP)
- `com.fb2.obd.ui` — Compose dashboard (`DashboardScreen`, gauges) + `DashboardViewModel`

## Build & run

Requires JDK 17 and the Android SDK (see `AGENTS.md` for the cloud setup).

```bash
./gradlew testDebugUnitTest   # run JVM unit tests
./gradlew lintDebug           # static analysis
./gradlew assembleDebug       # build APK -> app/build/outputs/apk/debug/app-debug.apk
```

After code changes, follow [`REGRESSION.md`](REGRESSION.md) (full automated suite +
issue inventory + manual car checks).
Sideload `app-debug.apk` onto your phone (`adb install -r app-debug.apk` or copy
it over). The app launches straight into the dashboard on the **Demo** source;
wiring the paired ELM327 device into `Elm327BluetoothSource` switches it to live
data.
