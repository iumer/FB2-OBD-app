# FB2-OBD agent regression gate (MANDATORY)

**Rule:** After *any* code change (UI/UX included), run `bash scripts/regression.sh`
and fix failures **before** asking the user to install/test an APK.

Do not tell the user “please test” until this gate is green.

Universal APK (only URL):  
`https://raw.githubusercontent.com/iumer/FB2-OBD-app/latest/dist/FB2-Diag-debug.apk`

---

## User-reported bugs / issues (catalog)

### Sideload / delivery process
1. Too many APK downloads because agent ships without self-testing.
2. Changing download URLs / dual APKs confused installs — keep **one** `latest` APK only.

### Vehicle profiles / deep search / honesty
3. Deep search must not claim success dishonestly; Generic must not thrash Honda Mode 22.
4. FB2 vs Generic OBD2 profile differences (catalog, Trans page, estimated-gear default).

### Performance / HU
5. Dash scroll lag on low-RAM head units.
6. Speed freeze / stale Speed; battery zone spam; poll planner issues.

### Gear / battery / coolant
7. Gear % / value clipping on Dash.
8. Battery voice alert threshold (≤11.8V) + ATRV median behavior.
9. FB2 coolant bands (green≤95 … red/voice ≥104).

### Themes (OptA / OptB / OptC)
10. Themes implemented without approval (process bug) — ask before large UI work unless user already authorized.
11. Settings listed everything → need dropdowns for profile + theme.
12. Themes only weakly restyled Classic tiles → need immersive Opt layouts; Classic-only Idle/Perf/Trip tabs.
13. Flat / low-fidelity vs design samples (bloom, needles, icons, headers).
14. Faults / AI / DIAG stayed classic cyan → app-wide `ThemePalette`.
15. **OptA side wheels:** tiny text, sticky not slider, focus box jumps, bottom-aligned, values clipped / not readable.
16. **OptC:** RPM gauge too small / weird needle; estimated gear appears missing.
17. OptB confirmed OK (do not regress Twin Gauge without cause).

### Gestures (all themes)
18. Double-tap remap / triple-tap deep search / hold deep-search (or thresholds) must work on **Classic + OptA + OptB + OptC**.

### Logging
19. Confusion: does LOG capture theme widgets or Classic tiles?  
   **Answer under test:** theme-independent canonical Dash CSV (hero + built-in tiles + extras); not Fuel/Trip/Trans/Perf pages.

### Drive-test UX / Coolant blanking (2026-08)
21. **Coolant 1 / MAF intermittent `--` while ELM LINKED** — poll planner now always requests Coolant+MAF; longer Coolant/MAF TTL (5s).
22. **Logging status invisible on Opt themes** — every Dash shows subtle `LOGGING` / `NOT LOGGING` + `NET`/`OFFLINE` + ELM link chips; menu shows STOP LOG when active.
23. **OptA side wheels sticky/sloppy** — continuous fractional dialer scroll + decay fling + spring snap (no per-delta coroutine queue / AnimatedContent).
24. **Deep search Tried 1/6 skipped 5** — simple Mode 01 forces run *before* bus abort; honest skip notes for advanced only.
25. **Dash hangs after deep search** — always restore + `resumePolling` + soft recover / clear fail streaks.
26. **OptB needles laggy** — `animateFloatAsState` on needle fraction.
27. **OptC digital RPM overlaps gauge** — digits sit beside the dial, not on top of the arc.
28. **Fuel loop truncated `CLOSED LOO`** — abbreviate to CLOSED/OPEN + ellipsis in wheels.
29. **Opt remaps ignored** — OptA/B/C now apply `tileOverrides` like Classic.
30. **Hold never opened thresholds on Opt** — hold prefers threshold editor; triple-tap = deep search (Classic aligned).
31. **Deep-search hit only Battery/Coolant1 into snapshot** — MAF/Ambient/LTFT/Coolant2/etc. now inject + freshness stamp.
32. **Estimated gear toggle not persisted** — saved in `DashThemeStore`.
33. **Opt showed `--` for unsupported ECU PIDs** — now shows `n/s` (honest) when bitmask says unsupported.
34. **Same-cycle Coolant TTL wipe** — freshness stamped at PID success time, not cycle start.
35. **Classic deepFound froze stale recovered values** — live wins; overlay only when blank; cleared when live returns.
36. **Classic could not deep-search Coolant when `--`** — deep search enabled whenever value is blank (not only n/s).
37. **Intake/Throttle sticky forever** — secondary TTL clear in `SnapshotFreshness` (2026-08-14 flat 60°C / 13.7% drive log).
38. **OptB/OptC km/h + EST clipped** — dial digits under gauges; height-scaled gear stacks; `includeFontPadding=false` tight labels.

### Must stay covered in code (additions)
| Coolant/MAF always-poll + TTL | `SpeedFreshnessAndPollPlannerTest` |
| Intake/Throttle secondary TTL | `SpeedFreshnessAndPollPlannerTest` |
| Deep search Mode 01 before bus skip | `DeepSearchKnowledgeBaseTest` |
| Theme status chips / Opt layouts | Paparazzi `ThemeDashboardSnapshotTest` |
| Opt tileOverrides + n/s | `DashThemeTest` |
| Hold prefers thresholds | `ThemeGestureLogicTest` |

---

## Automated gate (`scripts/regression.sh`)

Runs:
1. Unit tests (profiles, PIDs, logger, gear, gestures, themes, health, …)
2. Paparazzi snapshots including **Classic + OptA + OptB + OptC** Dash renders

### Must stay covered in code
| Area | Test entry points |
|------|-------------------|
| Themes enum + side metrics | `DashThemeTest`, `RegressionGateTest` |
| Gesture contract | `ThemeGestureLogicTest` |
| Logging lean CSV / Dash-only | `ObdLoggerTest`, `RegressionGateTest` |
| FB2 vs Generic PIDs / deep search | `VehicleProfileTest`, `RegressionGateTest` |
| Gear estimate floor | `GearEstimatorTest`, `RegressionGateTest` |
| Theme UI smoke (landscape) | `ThemeDashboardSnapshotTest` |
| Classic Dash smoke | `DashboardSnapshotTest` |
| Coolant/MAF always-poll | `SpeedFreshnessAndPollPlannerTest` |
| Deep search simple-force-first | `DeepSearchKnowledgeBaseTest` |

---

## Manual / visual checklist (agent, when UI changed)

Use Paparazzi PNGs under `app/build/reports/paparazzi/` first. If a computer-use / emulator path exists, also verify:

- [ ] OptA: side wheels readable, centered focus, swipe circulates, no jump
- [ ] OptB: twin gauges + bottom chips intact; needles interpolate
- [ ] OptC: RPM dial separate from digital digits; gear digit visible when speed ≥ 5
- [ ] Classic: Idle/Perf/Trip tabs still present; Opt themes hide them
- [ ] All themes: ELM / LOGGING / NET status visible
- [ ] Settings: Vehicle profile + Theme dropdowns
- [ ] LOG start/stop; CSV has Dash columns, no Transmission page dump
- [ ] Double-tap / hold / triple-tap still wired on themed metrics

---

## Delivery checklist (every iteration)

1. Implement change on `cursor/<name>-888a`
2. `bash scripts/regression.sh` → all green
3. If app binary changed: bump versionCode, copy APK to `dist/`, `bash scripts/publish-latest-apk.sh`
4. Commit + push + update PR
5. Only then tell the user the build is ready (include regression gate result)
