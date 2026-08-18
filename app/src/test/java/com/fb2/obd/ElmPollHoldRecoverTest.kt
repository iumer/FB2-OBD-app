package com.fb2.obd

import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.PidPollPlanner
import com.fb2.obd.obd.PollHold
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmPollHoldRecoverTest {

    private val catalog = listOf(
        ObdPid.ENGINE_RPM,
        ObdPid.SPEED,
        ObdPid.COOLANT_TEMP,
        ObdPid.MAF,
        ObdPid.STFT_B1,
        ObdPid.INTAKE_TEMP,
    )

    @Test
    fun recoverOnlyAfterFullPauseEnds() {
        assertTrue(
            Elm327BluetoothSource.shouldRecoverAfterResume(PollHold.FULL_PAUSE, PollHold.NONE),
        )
        assertFalse(
            Elm327BluetoothSource.shouldRecoverAfterResume(PollHold.HEROES_ONLY, PollHold.NONE),
        )
    }

    @Test
    fun heroesOnly_pollsAlwaysPidsOnly() {
        val chosen = PidPollPlanner.selectForCycle(
            activePids = catalog,
            failStreak = emptyMap(),
            cycle = 1,
            recovering = false,
            hold = PollHold.HEROES_ONLY,
        )
        assertTrue(ObdPid.ENGINE_RPM in chosen)
        assertTrue(ObdPid.SPEED in chosen)
        assertTrue(ObdPid.COOLANT_TEMP in chosen)
        assertFalse(ObdPid.STFT_B1 in chosen)
        assertFalse(ObdPid.INTAKE_TEMP in chosen)
    }
}
