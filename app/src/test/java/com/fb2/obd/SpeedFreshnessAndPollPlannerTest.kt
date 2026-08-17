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
        val fail = mapOf(ObdPid.SPEED to 5, ObdPid.ENGINE_RPM to 3, ObdPid.INTAKE_TEMP to 4)
        for (cycle in 1..25) {
            val chosen = PidPollPlanner.selectForCycle(catalog, fail, cycle, recovering = false)
            assertTrue("cycle $cycle missing RPM", ObdPid.ENGINE_RPM in chosen)
            assertTrue("cycle $cycle missing SPEED", ObdPid.SPEED in chosen)
            assertTrue("cycle $cycle missing Coolant", ObdPid.COOLANT_TEMP in chosen)
            assertTrue("cycle $cycle missing MAF", ObdPid.MAF in chosen)
            // Flaky rotating secondary still skipped most cycles.
            if (cycle % 20 != 0) {
                assertFalse("cycle $cycle should skip Intake", ObdPid.INTAKE_TEMP in chosen)
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
        // RPM + Speed + Coolant + MAF always, plus up to 4 rotating secondaries.
        assertEquals(4 /* always */ + 4, chosen.size)
        assertEquals(ObdPid.ENGINE_RPM, chosen[0])
        assertEquals(ObdPid.SPEED, chosen[1])
        assertEquals(ObdPid.COOLANT_TEMP, chosen[2])
        assertEquals(ObdPid.MAF, chosen[3])

        val next = PidPollPlanner.selectForCycle(
            activePids = catalog,
            failStreak = emptyMap(),
            cycle = 2,
            recovering = false,
            secondaryBudget = 4,
        )
        // Rotation should advance the secondary window.
        assertTrue(chosen.drop(4) != next.drop(4))
    }

    @Test
    fun planner_heroesOnly_skipsRotatingSecondaries() {
        val chosen = PidPollPlanner.selectForCycle(
            activePids = catalog,
            failStreak = emptyMap(),
            cycle = 1,
            recovering = false,
            hold = com.fb2.obd.obd.PollHold.HEROES_ONLY,
        )
        assertTrue(ObdPid.ENGINE_RPM in chosen)
        assertTrue(ObdPid.SPEED in chosen)
        assertTrue(ObdPid.COOLANT_TEMP in chosen)
        assertTrue(ObdPid.MAF in chosen)
        assertFalse(ObdPid.INTAKE_TEMP in chosen)
        assertFalse(ObdPid.THROTTLE in chosen)
    }

    @Test
    fun freshness_holdValues_doesNotBlankDuringDeepSearchPause() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_SPEED, 0L)
        fresh.markOk(SnapshotFreshness.KEY_RPM, 0L)
        fresh.markOk(SnapshotFreshness.KEY_COOLANT, 0L)
        val snap = VehicleSnapshot(rpm = 1800.0, speedKmh = 72.0, coolantC = 88.0)
        val held = fresh.sanitize(
            snap,
            nowMs = 8_000L,
            rpmUpdatedThisCycle = false,
            holdValues = true,
        )
        // Rec 2: Faults Read stole the mutex for Mode 03/07/0A (~seconds) and
        // SnapshotFreshness TTL blanked RPM/Speed/Coolant. Exclusive + holdValues
        // keeps last-good on screen; HEROES_ONLY between commands refreshes them.
        assertEquals(72.0, held.speedKmh!!, 0.01)
        assertEquals(1800.0, held.rpm!!, 0.01)
        assertEquals(88.0, held.coolantC!!, 0.01)
        val cleared = fresh.sanitize(snap, nowMs = 8_000L, rpmUpdatedThisCycle = false, holdValues = false)
        assertNull(cleared.speedKmh)
    }

    @Test
    fun planner_isAlways_coversCoolantAndMaf() {
        assertTrue(PidPollPlanner.isAlways(ObdPid.COOLANT_TEMP))
        assertTrue(PidPollPlanner.isAlways(ObdPid.MAF))
        assertTrue(PidPollPlanner.isHero(ObdPid.ENGINE_RPM))
        assertFalse(PidPollPlanner.isHero(ObdPid.COOLANT_TEMP))
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
    fun freshness_clearsStaleIntakeAndThrottle() {
        // 2026-08-14 drive: Intake + Throttle stuck flat for ~56 min (no TTL clear).
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_INTAKE, 0L)
        fresh.markOk(SnapshotFreshness.KEY_THROTTLE, 0L)
        fresh.markOk(SnapshotFreshness.KEY_RPM, 3_000L)
        val sticky = VehicleSnapshot(
            rpm = 1800.0,
            intakeC = 60.0,
            throttlePct = 13.72549019607843,
        )
        val stillFresh = fresh.sanitize(sticky, nowMs = 2_000L, rpmUpdatedThisCycle = true)
        assertEquals(60.0, stillFresh.intakeC!!, 0.01)
        assertEquals(13.72549019607843, stillFresh.throttlePct!!, 0.01)

        val cleared = fresh.sanitize(sticky, nowMs = 2_501L, rpmUpdatedThisCycle = true)
        assertNull(cleared.intakeC)
        assertNull(cleared.throttlePct)
        assertEquals(1800.0, cleared.rpm!!, 0.01)
    }

    @Test
    fun freshness_clearsStaleCoolantAndBattery() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_COOLANT, 0L)
        fresh.markOk(SnapshotFreshness.KEY_BATTERY, 0L)
        fresh.markOk(SnapshotFreshness.KEY_RPM, 0L)
        val snap = VehicleSnapshot(rpm = 1800.0, coolantC = 90.0, batteryVolts = 12.5)
        // Coolant TTL is 5s; battery TTL is 4s — past both.
        val out = fresh.sanitize(snap, nowMs = 5_001L, rpmUpdatedThisCycle = true)
        assertNull(out.coolantC)
        assertNull(out.batteryVolts)
        assertNull(out.rpm) // RPM also past TTL
    }

    @Test
    fun freshness_restampProtectsSameCycleDecodes() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        // Speed decoded at T=0; rest of cycle takes 4s of timeouts.
        fresh.markOk(SnapshotFreshness.KEY_SPEED, 0L)
        fresh.restamp(setOf(SnapshotFreshness.KEY_SPEED), nowMs = 4_000L)
        val snap = VehicleSnapshot(speedKmh = 90.0, rpm = 2000.0)
        val out = fresh.sanitize(snap, nowMs = 4_000L, rpmUpdatedThisCycle = true)
        assertEquals(90.0, out.speedKmh!!, 0.01)
    }

    @Test
    fun freshness_staleSpeedClearsEvenWhenBusLooksDead() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_SPEED, 0L)
        val sticky = VehicleSnapshot(speedKmh = 65.0)
        val cleared = fresh.sanitize(sticky, nowMs = 2_501L, rpmUpdatedThisCycle = false)
        assertNull("stale speed must clear without busAlive gate", cleared.speedKmh)
    }

    @Test
    fun freshness_remakePresentAfterPause() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_COOLANT, 0L)
        val snap = VehicleSnapshot(coolantC = 88.0)
        // Simulate deep-search pause aging the mark past TTL, then remake.
        fresh.remakePresent(snap, nowMs = 10_000L)
        val out = fresh.sanitize(snap, nowMs = 10_100L, rpmUpdatedThisCycle = false)
        assertEquals(88.0, out.coolantC!!, 0.01)
    }

    @Test
    fun freshness_estimatedGearRequiresFreshRpmAndSpeed() {
        val fresh = SnapshotFreshness(staleAfterMs = 2_500L)
        fresh.markOk(SnapshotFreshness.KEY_SPEED, 4_000L)
        // RPM never marked / stale
        val snap = VehicleSnapshot(
            rpm = 2000.0,
            speedKmh = 90.0,
            gear = 5,
            gearSource = GearSource.ESTIMATED,
            gearConfidencePct = 80,
        )
        val out = fresh.sanitize(snap, nowMs = 4_100L, rpmUpdatedThisCycle = false)
        assertEquals(GearSource.NONE, out.gearSource)
        assertNull(out.gear)
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
        fresh.markOk(SnapshotFreshness.KEY_RPM, 70_000L)
        snap = snap.copy(speedKmh = 92.0, rpm = 1861.0)
        snap = fresh.sanitize(snap, nowMs = 70_000L, rpmUpdatedThisCycle = true)
        assertEquals(92.0, snap.speedKmh!!, 0.01)
        assertEquals(1861.0, snap.rpm!!, 0.01)
    }
}
