#!/usr/bin/env bash
# Mandatory pre-delivery regression gate for FB2-OBD cloud agents.
# See .cursor/REGRESSION.md — do not ask the user to sideload until this exits 0.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== FB2 regression gate ==="
echo "Running unit + Paparazzi theme/profile/logger/gesture suite…"

./gradlew :app:testDebugUnitTest \
  --tests 'com.fb2.obd.RegressionGateTest' \
  --tests 'com.fb2.obd.ThemeGestureLogicTest' \
  --tests 'com.fb2.obd.DashThemeTest' \
  --tests 'com.fb2.obd.ObdLoggerTest' \
  --tests 'com.fb2.obd.VehicleProfileTest' \
  --tests 'com.fb2.obd.GearEstimatorTest' \
  --tests 'com.fb2.obd.ThemeDashboardSnapshotTest' \
  --tests 'com.fb2.obd.DashboardSnapshotTest' \
  --tests 'com.fb2.obd.HealthEvaluatorTest' \
  --tests 'com.fb2.obd.HealthThresholdsTest' \
  --tests 'com.fb2.obd.SupportedPidsTest' \
  --tests 'com.fb2.obd.DeepSearchKnowledgeBaseTest' \
  --tests 'com.fb2.obd.SessionLogStoreTest' \
  --tests 'com.fb2.obd.SpeedFreshnessAndPollPlannerTest' \
  --tests 'com.fb2.obd.DashTileOverrideStoreTest' \
  --tests 'com.fb2.obd.DashExtraPidStoreTest' \
  --tests 'com.fb2.obd.SensorPickerReadingsTest' \
  --tests 'com.fb2.obd.SensorPickerSnapshotTest' \
  --tests 'com.fb2.obd.ChromeCollapseTest'

echo ""
echo "=== FULL unit suite (catch stray breakages) ==="
./gradlew :app:testDebugUnitTest

echo ""
echo "REGRESSION GATE GREEN"
echo "Paparazzi report: $ROOT/app/build/reports/paparazzi/debug/index.html"
