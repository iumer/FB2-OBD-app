package com.fb2.obd

import com.fb2.obd.data.ObdLogger
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
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
    fun csv_hasHeaderAndRow() {
        ObdLogger.valueLoggingEnabled = true
        ObdLogger.logSnapshot(VehicleSnapshot(rpm = 900.0, coolantC = 88.0))
        ObdLogger.logTabMap(
            "Transmission",
            mapOf(
                "Transmission fluid temp" to "86",
                "Current gear" to "3",
                "Output shaft RPM" to "n/s",
            ),
        )
        val csv = ObdLogger.valuesCsv()
        assertTrue(csv.contains("time_ms,rpm,speed_kmh,coolant1_c"))
        assertTrue(csv.contains("900"))
        assertTrue(csv.contains("88"))
        assertTrue(csv.contains("# tab_values"))
        assertTrue(csv.contains("Transmission"))
        assertTrue(csv.contains("Output shaft RPM"))
        assertTrue(csv.contains("# page_probes"))
        assertTrue(csv.contains("# debug_log"))
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
        assertTrue(ObdLogger.valuesCsv().contains("Custom sensors"))
    }
}
