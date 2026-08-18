# What changed between 0.1.15 and 0.1.34

The app code in this branch is an **exact revert to the 0.1.15 tree** (commit
`d3790be`). This file is the record of everything that was added between 0.1.15
and 0.1.34 so nothing is lost and any of it can be brought back individually.

Nothing is deleted from git. 0.1.34 still exists on branch
`cursor/obd-connect-crash-c3be` and can be restored at any time.

- 44 commits
- 82 files changed, ~5,000 insertions, ~940 deletions
- unit tests went 197 → 274

---

## 1. Why the revert happened

0.1.27 broke live data (only battery/ATRV read). 0.1.28 through 0.1.34 crashed on
Connect. Four attempted fixes did not resolve it, so the user asked to return to
the last build they considered usable.

**0.1.15 is not known-good either — it is the last build before this run of
regressions.** The issues fixed between 0.1.16 and 0.1.26 (listed below) are also
reverted and will return.

---

## 2. Version-by-version

| Version | What it added |
|---|---|
| **0.1.16** | OptA orbit-wheel scroll; cleared stale Intake/Throttle values via TTL |
| **0.1.17** | Fixed OptB/OptC clipping of `km/h` and the `EST` gear badge; scaled gear/dial readouts for short landscape screens |
| **0.1.18** | Finished OptB/OptC clip work — dial digits moved below gauges, tighter labels, OptC hero bottom inset |
| **0.1.19** | Floating bubble blanks on ELM drop; Demo on/off toggle in Settings |
| **0.1.20** | `ELM_LINK` disconnect forensics (why the link dropped, logged to events); gentler soft-recover |
| **0.1.21** | **In-app update**: check / download / install from Settings |
| **0.1.22** | Trip link stability hardening for long drives |
| **0.1.23** | Analyze via AI follows the vehicle profile (Generic OBD2 vs FB2 Civic) instead of always assuming a Civic |
| **0.1.24** | **Full-screen live sensor picker**; Dash `+` extras persist across restart |
| **0.1.25** | Classic Dash chrome and picker search header stop sticking on scroll |
| **0.1.26** | Dash stays live during sensor search; session survives head-unit process death |
| **0.1.27** | Dash heroes stay live during Faults Read and other DIAG probes ⚠️ **also introduced the ATRV-only regression** |
| **0.1.28** | Bubble keeps values during RETRY; publishes every frame; FGS notifications grouped ⚠️ **crash on connect begins** |
| **0.1.29** | Attempted connect-crash fix (clear Demo snapshot, guard FGS starts); update-checker cache bust |
| **0.1.30** | Updater switched to the GitHub Contents API because the raw CDN served a stale `version.json` |
| **0.1.31** | Robolectric Android-runtime tests; APK switched to v1+v2 signed release classpath (7.3 MB, was 11 MB v2-only) |
| **0.1.32** | Guarded foreground-service restart in `onTaskRemoved` |
| **0.1.33** | Hardened `publishCarDash`, DIAG coroutines, bubble drag; corrected stale doc constants |
| **0.1.34** | `ForegroundServiceDidNotStartInTime` fix; on-device crash reporter |

---

## 3. Features reverted (present in 0.1.34, gone now)

These source files existed only after 0.1.15 and are removed by the revert:

| Capability | File |
|---|---|
| In-app update check/download/install | `AppUpdateManager.kt`, `AppUpdateChecker.kt` |
| On-device crash reporter | `CrashReporter.kt` |
| Process-scoped ViewModel (survives HU killing the activity) | `Fb2App.kt` |
| Full-screen live sensor picker | `SensorPickerScreen.kt`, `SensorPickerReadings.kt` |
| Dash `+` extras persistence | `DashExtraPidStore.kt` |
| Floating bubble enable/restore state | `FloatingDashPrefs.kt` |
| Auto-reconnect to last ELM after process death | `LastElmStore.kt`, `KeepAlivePolicy.kt` |
| ELM disconnect forensics | `ElmLinkForensics.kt` |
| Torque-style green freshness LEDs | `FreshnessLed.kt` |
| Heroes-only poll hold (keeps Dash live during probes) | `PollHold.kt` |
| Non-sticky Dash chrome on scroll | `ChromeCollapse.kt` |

**Practical consequences of running the reverted build:**

1. **No "Check for updates".** 0.1.15 predates it. Future builds are sideload-only
   unless the updater is added back.
2. **No crash reporter.** If it still crashes, there is again no stack trace.
3. Sensor picker returns to the old dialog; Dash `+` extras reset on restart.
4. No auto-reconnect after the head unit kills the app.
5. Faults/Fuel/Trans probes will blank the Dash again (the 0.1.26/0.1.27 fix is gone).

---

## 4. Issues fixed after 0.1.15 that will return

From `REGRESSION.md`, these were fixed in 0.1.16+ and are reverted with it:

| ID | Issue |
|---|---|
| I25 | Floating bubble ring too small / overflows on HU |
| I36 | No freshness indication that a value is actually updating |
| I37 | Long trips lost the whole log if the app died |
| I38 | UNABLE soft-recover looped forever with a sticky Dash |
| I48 | No way to update the app from inside the app |
| I49 | ATRV-only sticky values / recover heartbeat |
| I51 | Dash `+` extras did not survive a restart |
| I52 | Sensor picker was a cramped dialog with no live values |
| I53 | Classic Dash chrome and picker header stuck on scroll |
| I54 | Sensor picker blanked the Dash while scanning |
| I55 | Deep search paused hero polling |
| I56 | Head unit killing the app ended the session permanently |
| I57 | Freshness dots missing on Opt themes |
| I58 | Faults Read blanked the Dash |
| I59–I68 | Bubble RETRY stickiness, LIVE/RETRY/DEMO tag, grey n/s rings, bubble restore, notification grouping, tab auto-probe starvation, picker subtitle wording |
| I72 | Shipped APK was v2-only 11 MB debug instead of v1+v2 release |
| I73 | Entire test suite was pure-JVM and could not catch connect crashes |
| I74–I78 | Unguarded FGS restart, `publishCarDash` call sites, DIAG coroutines, bubble drag; stale doc constants |
| I79–I81 | `ForegroundServiceDidNotStartInTime`; no crash reporting; BT `SecurityException` on the UI thread |

Issues **I01–I24, I26–I35, I39–I47, I50** were fixed at or before 0.1.15 and are
still present in this build.

---

## 5. Test coverage difference

| | 0.1.15 (now) | 0.1.34 |
|---|---|---|
| Test classes | 38 | 54 |
| Tests | 197 | 274 |
| Android-runtime tests (Robolectric) | none | 14 |
| Paparazzi UI snapshots | yes | yes |

The 17 test files added after 0.1.15 are reverted, including the Robolectric
suites that exercise the real `DashboardViewModel` and the foreground services.

---

## 6. Restoring any of this

The full 0.1.34 tree is on `cursor/obd-connect-crash-c3be`. To bring back a single
capability:

```bash
git checkout cursor/obd-connect-crash-c3be -- app/src/main/java/com/fb2/obd/data/AppUpdateManager.kt
```

To return to 0.1.34 entirely:

```bash
git checkout cursor/obd-connect-crash-c3be
```
