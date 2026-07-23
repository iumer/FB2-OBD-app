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
        val csv = ObdLogger.valuesCsv()
        assertTrue(csv.startsWith("time_ms,rpm,speed_kmh,coolant1_c"))
        assertTrue(csv.contains("900"))
        assertTrue(csv.contains("88"))
    }
}
