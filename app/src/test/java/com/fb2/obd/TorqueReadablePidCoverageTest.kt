package com.fb2.obd

import com.fb2.obd.obd.StandardPidCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECU PIDs Torque listed as readable in the 2026-08-18 HU recording.
 * GPS / phone / calculated MPG are out of scope — only Mode 01 from the adapter.
 */
class TorqueReadablePidCoverageTest {

    @Test
    fun catalogHasEveryTorqueMode01PidFromRecording() {
        val byRequest = StandardPidCatalog.all.associateBy { it.request.uppercase() }
        val required = listOf(
            "0103", // Fuel system status
            "0104", // Engine load
            "0105", // Coolant
            "0106", // STFT
            "010B", // MAP — must stay readable once live (I85)
            "010C", // RPM
            "010D", // Speed
            "010E", // Timing
            "010F", // Intake temp
            "0110", // MAF
            "0111", // Throttle
            "0114", // O2 B1S1 voltage
            "0115", // O2 B1S2 voltage
            "011F", // Run time
            "0124", // O2 S1 lambda (Torque also shows current from same PID)
            "012F", // Fuel level
            "0133", // Barometric pressure
            "0142", // Control module voltage
            "0143", // Absolute load
            "0145", // Relative throttle
            "0149", // Accelerator pedal D
            "014A", // Accelerator pedal E
        )
        required.forEach { req ->
            assertTrue("missing Torque Mode 01 PID $req", byRequest.containsKey(req))
        }
        assertTrue(
            "Torque shows O2 S1 wide-range current from the same 0124 frame",
            StandardPidCatalog.all.any { it.id.equals("0124I", true) && it.request.equals("0124", true) },
        )
    }
}
