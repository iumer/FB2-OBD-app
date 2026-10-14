package com.fb2.obd

import com.fb2.obd.data.ObdLogger
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObdLoggerTest {

    @Before
    fun reset() {
        ObdLogger.clearDebug()
        ObdLogger.clearValues()
        ObdLogger.valueLoggingEnabled = false
    }

    @Test
    fun debug_recordsTxRxLines() {
        ObdLogger.logDebug(ObdLogger.Dir.TX, "010C")
        ObdLogger.logDebug(ObdLogger.Dir.RX, "41 0C 1A F8")
        val lines = ObdLogger.debugLines()
        assertEquals(2, lines.size)
        assertEquals(ObdLogger.Dir.TX, lines[0].dir)
        assertTrue(ObdLogger.debugText().contains("010C"))
    }

    @Test
    fun valueLog_respectsToggle() {
        ObdLogger.logSnapshot(VehicleSnapshot(rpm = 800.0))
        assertEquals(0, ObdLogger.valueRows().size) // disabled

        ObdLogger.valueLoggingEnabled = true
        ObdLogger.logSnapshot(VehicleSnapshot(rpm = 900.0, speedKmh = 10.0))
        assertEquals(1, ObdLogger.valueRows().size)
    }

    @Test
    fun csv_isDashOnly_leanExport() {
        ObdLogger.valueLoggingEnabled = true
        ObdLogger.logSnapshot(VehicleSnapshot(rpm = 900.0, coolantC = 88.0, fuelSystemStatus = "CLOSED LOOP"))
        ObdLogger.logTabMap(
            "Dash",
            mapOf(
                "RPM" to "900",
                "Coolant1" to "88",
                "Extra sensor" to "12.3",
            ),
        )
        // Other tabs must not appear in the exported value CSV.
        ObdLogger.logTabMap(
            "Transmission",
            mapOf("ATF" to "86"),
        )
        val csv = ObdLogger.valuesCsv()
        assertTrue(csv.contains("time_ms,rpm,speed_kmh,coolant1_c"))
        assertTrue(csv.contains("900"))
        assertTrue(csv.contains("88"))
        assertTrue(csv.contains("# dash_tiles"))
        assertTrue(csv.contains("Extra sensor"))
        assertTrue(csv.contains("# events"))
        assertFalse(csv.contains("# page_probes"))
        assertFalse(csv.contains("# debug_log"))
        assertFalse(csv.contains("Transmission"))
        assertFalse(csv.contains("ATF"))
    }

    @Test
    fun csv_includesVehicleProfileHeader() {
        ObdLogger.valueLoggingEnabled = true
        ObdLogger.logSnapshot(VehicleSnapshot(rpm = 800.0))
        val csv = ObdLogger.valuesCsv(
            isDemo = false,
            vehicleProfileId = "generic_obd2",
            vehicleLabel = "Generic OBD2 (SAE Mode 01 / codes) — unidentified vehicle unless VIN/ECU given",
        )
        assertTrue(csv.contains("# vehicle_profile=generic_obd2"))
        assertTrue(csv.contains("# vehicle=Generic OBD2"))
        assertFalse(csv.contains("Honda Civic FB2"))
    }

    @Test
    fun probeLog_alwaysRecordsEvenWithoutValueToggle() {
        val pid = com.fb2.obd.obd.StandardPidCatalog.byId("010C")!!
        ObdLogger.logProbe(
            "Custom sensors",
            listOf(com.fb2.obd.obd.PidProbeResult(pid, true, 712.0, "41 0C 0B 20")),
        )
        assertTrue(ObdLogger.debugText().contains("PROBE [Custom sensors]"))
        assertTrue(ObdLogger.debugText().contains("010C"))
        assertEquals(1, ObdLogger.probeRows().size)
        // Probes stay out of the lean value CSV.
        assertFalse(ObdLogger.valuesCsv().contains("Custom sensors"))
    }
}
