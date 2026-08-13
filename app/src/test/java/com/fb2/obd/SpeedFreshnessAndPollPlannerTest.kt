package com.fb2.obd

import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.PidPollPlanner
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recreates the 2026-08-13 drive bug: Speed stuck (~63 km/h) for ~69s while
 * RPM/load/throttle kept updating — user saw ~65 vs real ~98 — then fixes.
 */
class SpeedFreshnessAndPollPlannerTest {

    private val catalog = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.SPEED,
        ObdPid.COOLANT_TEMP,
        ObdPid.ENGINE_LOAD,
        ObdPid.THROTTLE,
        ObdPid.TIMING_ADVANCE,
        ObdPid.MAF,
        ObdPid.INTAKE_MAP,
        ObdPid.STFT_B1,
        ObdPid.FUEL_SYSTEM_STATUS,
        ObdPid.CONTROL_MODULE_VOLTAGE,
        ObdPid.INTAKE_TEMP,
    )

    @Test
    fun legacyFailStreakWouldSkipSpeedForManyCycles() {
        // Documents the old bug: after 2 Speed failures, skip until cycle % 20 == 0.
        val streak = 2
        val skipped = (1..19).count { cycle ->
            streak >= 2 && cycle % 20 != 0
        }
        assertEquals(19, skipped)
    }

    @Test
    fun planner_alwaysIncludesHeroesEvenWithHighFailStreak() {
        val fail = mapOf(ObdPid.SPEED to 5, ObdPid.ENGINE_RPM to 3, ObdPid.MAF to 4)
        for (cycle in 1..25) {
            val chosen = PidPollPlanner.selectForCycle(catalog, fail, cycle, recovering = false)
            assertTrue("cycle $cycle missing RPM", ObdPid.ENGINE_RPM in chosen)
            assertTrue("cycle $cycle missing SPEED", ObdPid.SPEED in chosen)
            // Flaky secondary still skipped most cycles.
            if (cycle % 20 != 0) {
                assertFalse("cycle $cycle should skip MAF", ObdPid.MAF in chosen)
            }
        }
    }

    @Test
    fun planner_rotatesSecondariesSoCycleStaysShort() {
        val chosen = PidPollPlanner.selectForCycle(
            activePids = catalog,
            failStreak = emptyMap(),
            cycle = 1,
            recovering = false,
            secondaryBudget = 4,
        )
        assertEquals(2 /* heroes */ + 4, chosen.size)
        assertEquals(ObdPid.ENGINE_RPM, chosen[0])
        assertEquals(ObdPid.SPEED, chosen[1])

        val next = PidPollPlanner.selectForCycle(
            activePids = catalog,
            failStreak = emptyMap(),
            cycle = 2,
            recovering = false,
            secondaryBudget = 4,
        )
        // Rotation should advance the secondary window.
        assertTrue(chosen.drop(2) != next.drop(2))
    }

    @Test
    fun planner_recoveringUsesCoreSecondariesOnly() {
        val chosen = PidPollPlanner.selectForCycle(
            activePids = catalog,
            failStreak = emptyMap(),
            cycle = 3,
            recovering = true,
            secondaryBudget = 4,
        )
        assertTrue(ObdPid.SPEED in chosen)
        assertTrue(ObdPid.ENGINE_RPM in chosen)
        // Timing is not in CORE — excluded while recovering.
        assertFalse(ObdPid.TIMING_ADVANCE in chosen)
    }

    @Test
    fun freshness_clearsStaleSpeedWhileRpmStillLive() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_SPEED, nowMs = 1_000L)
        fresh.markOk(SnapshotFreshness.KEY_RPM, nowMs = 1_000L)

        // Simulate last-good Speed=63 while RPM keeps updating (drive log pattern).
        val sticky = VehicleSnapshot(
            rpm = 1900.0,
            speedKmh = 63.0,
            engineLoadPct = 100.0,
            throttlePct = 22.0,
            gear = 5,
            gearSource = GearSource.ESTIMATED,
            gearConfidencePct = 80,
        )
        val stillFresh = fresh.sanitize(sticky, nowMs = 2_000L, rpmUpdatedThisCycle = true)
        assertEquals(63.0, stillFresh.speedKmh!!, 0.01)

        // Past stale window — must not keep lying at 63 while "going 98".
        val cleared = fresh.sanitize(sticky, nowMs = 1_000L + 2_501L, rpmUpdatedThisCycle = true)
        assertNull(cleared.speedKmh)
        assertNull(cleared.gear)
        assertEquals(GearSource.NONE, cleared.gearSource)
        assertEquals(1900.0, cleared.rpm!!, 0.01)
        assertNull(cleared.freshAtMs[SnapshotFreshness.KEY_SPEED])
    }

    @Test
    fun freshness_mapForPresentFields_marksDemoSensors() {
        val snap = VehicleSnapshot(rpm = 2000.0, speedKmh = 90.0, batteryVolts = 14.0)
        val map = SnapshotFreshness.mapForPresentFields(snap, nowMs = 55L)
        assertEquals(55L, map[SnapshotFreshness.KEY_RPM])
        assertEquals(55L, map[SnapshotFreshness.KEY_SPEED])
        assertEquals(55L, map[SnapshotFreshness.KEY_BATTERY])
        assertNull(map[SnapshotFreshness.KEY_LTFT])
    }

    @Test
    fun keyForTileLabel_mapsDashLabels() {
        assertEquals(SnapshotFreshness.KEY_BATTERY, SnapshotFreshness.keyForTileLabel("Battery"))
        assertEquals(SnapshotFreshness.KEY_SPEED, SnapshotFreshness.keyForTileLabel("Speed"))
        assertEquals(SnapshotFreshness.KEY_COOLANT, SnapshotFreshness.keyForTileLabel("Coolant 1"))
        assertNull(SnapshotFreshness.keyForTileLabel("Health"))
    }

    @Test
    fun freshness_recreatedFreezeThenRecoveryUpdates() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        var snap = VehicleSnapshot(rpm = 1800.0, speedKmh = 63.0)
        fresh.markOk(SnapshotFreshness.KEY_SPEED, 0L)
        fresh.markOk(SnapshotFreshness.KEY_RPM, 0L)

        // ~69s of failed Speed polls (old behaviour kept 63). New: blank after 2.5s.
        snap = fresh.sanitize(snap.copy(rpm = 1850.0), nowMs = 3_000L, rpmUpdatedThisCycle = true)
        assertNull("stale speed must clear", snap.speedKmh)

        // ECU answers again at 92 km/h (log jump 63→92).
        fresh.markOk(SnapshotFreshness.KEY_SPEED, 70_000L)
        snap = snap.copy(speedKmh = 92.0, rpm = 1861.0)
        snap = fresh.sanitize(snap, nowMs = 70_000L, rpmUpdatedThisCycle = true)
        assertEquals(92.0, snap.speedKmh!!, 0.01)
    }
}
