package com.fb2.obd

import com.fb2.obd.data.ObdSource
import com.fb2.obd.obd.PollHold
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rec 2 (2026-08-17): DIAG → Faults → Read blanked RPM/Speed/Coolant because
 * Mode 03/07/0A stole the ELM mutex and SnapshotFreshness TTL cleared heroes.
 * [ObdSource.withDashKeptAlive] must hold HEROES_ONLY for that window.
 */
class DashBusKeepAliveTest {

    private class FakeSource : ObdSource {
        override val name: String = "fake"
        override val isLive: Boolean = false
        override fun snapshots(): Flow<VehicleSnapshot> = emptyFlow()
        var hold: PollHold = PollHold.NONE
        override fun setPollHold(hold: PollHold) {
            this.hold = hold
        }
        override fun pollHold(): PollHold = hold
    }

    @Test
    fun heroesOnlyDuringBlock_thenRestoresNone() = runBlocking {
        val src = FakeSource()
        val seen = src.withDashKeptAlive {
            assertEquals(PollHold.HEROES_ONLY, src.pollHold())
            7
        }
        assertEquals(7, seen)
        assertEquals(PollHold.NONE, src.pollHold())
    }

    @Test
    fun nestedDoesNotClearExistingHeroesHold() = runBlocking {
        val src = FakeSource()
        src.setPollHold(PollHold.HEROES_ONLY)
        src.withDashKeptAlive {
            assertEquals(PollHold.HEROES_ONLY, src.pollHold())
        }
        assertEquals(PollHold.HEROES_ONLY, src.pollHold())
    }

    @Test
    fun restoresNoneAfterThrow() = runBlocking {
        val src = FakeSource()
        try {
            src.withDashKeptAlive<Unit> { error("boom") }
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(PollHold.NONE, src.pollHold())
    }
}
