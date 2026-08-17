package com.fb2.obd

import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.SensorPickerReadings
import com.fb2.obd.obd.SensorReadKind
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorPickerReadingsTest {

    private val coolant = StandardPidCatalog.all.first { it.request.equals("0105", true) }
    private val rpm = StandardPidCatalog.all.first { it.request.equals("010C", true) }
    private val ambient = StandardPidCatalog.all.first { it.request.equals("0146", true) }
    private val fuelLoop = StandardPidCatalog.all.first { it.request.equals("0103", true) }
    private val ltft = StandardPidCatalog.all.first { it.request.equals("0107", true) }

    @Test
    fun liveSnapshot_isGreenWithLatestValue() {
        val snap = VehicleSnapshot(rpm = 711.0, coolantC = 73.0, fuelSystemStatus = "CLOSED LOOP")
        val rpmReading = SensorPickerReadings.resolve(rpm, snap, emptyMap())
        assertEquals(SensorReadKind.LIVE, rpmReading.kind)
        assertTrue(rpmReading.subtitle.startsWith("Latest value:"))
        assertTrue(rpmReading.latest!!.contains("711"))

        val loop = SensorPickerReadings.resolve(fuelLoop, snap, emptyMap())
        assertEquals(SensorReadKind.LIVE, loop.kind)
        assertEquals("Latest value: CLOSED LOOP", loop.subtitle)
    }

    @Test
    fun atrvBattery_isLiveEvenWhen0142Unsupported() {
        val battery = StandardPidCatalog.all.first { it.request.equals("0142", true) }
        val snap = VehicleSnapshot(
            batteryVolts = 14.12,
            unsupportedPids = setOf(0x42, 0x46, 0x07, 0x67),
        )
        val reading = SensorPickerReadings.resolve(battery, snap, emptyMap())
        assertEquals(SensorReadKind.LIVE, reading.kind)
        assertTrue(reading.latest!!.contains("14.12"))
    }

    @Test
    fun july24Fb2DashPids_stayLiveFromSnapshot() {
        val snap = VehicleSnapshot(
            rpm = 848.0,
            speedKmh = 7.0,
            coolantC = 72.0,
            intakeC = 36.0,
            engineLoadPct = 43.1,
            throttlePct = 14.9,
            stftPct = -3.9,
            mafGps = 3.86,
            mapKpa = 46.0,
            timingAdvance = 6.5,
            fuelSystemStatus = "CLOSED LOOP",
            unsupportedPids = setOf(0x67, 0x46, 0x07),
        )
        val mustLive = listOf("010C", "010D", "0105", "010F", "0104", "0111", "0106", "0110", "010B", "010E", "0103")
        mustLive.forEach { req ->
            val pid = StandardPidCatalog.all.first { it.request.equals(req, true) }
            val reading = SensorPickerReadings.resolve(pid, snap, emptyMap())
            assertEquals("$req should be readable like the 2026-07-24 FB2 log", SensorReadKind.LIVE, reading.kind)
        }
        val ns = listOf("0167", "0146", "0107")
        ns.forEach { req ->
            val pid = StandardPidCatalog.all.first { it.request.equals(req, true) }
            assertEquals(SensorReadKind.NONE, SensorPickerReadings.resolve(pid, snap, emptyMap()).kind)
        }
    }

    @Test
    fun ecuUnsupported_isNoDataWithoutProbe() {
        val snap = VehicleSnapshot(unsupportedPids = setOf(0x46, 0x07, 0x67))
        val reading = SensorPickerReadings.resolve(ambient, snap, emptyMap())
        assertEquals(SensorReadKind.NONE, reading.kind)
        assertEquals("No data received", reading.subtitle)
        assertFalse(reading.isReadable)

        val ltftReading = SensorPickerReadings.resolve(ltft, snap, emptyMap())
        assertEquals(SensorReadKind.NONE, ltftReading.kind)
    }

    @Test
    fun waitingUntilProbed() {
        val reading = SensorPickerReadings.resolve(coolant, VehicleSnapshot.EMPTY, emptyMap())
        assertEquals(SensorReadKind.WAITING, reading.kind)
        assertEquals("Waiting for data", reading.subtitle)
    }

    @Test
    fun probeHit_becomesLive() {
        val probe = mapOf(
            coolant.id to PidProbeResult(coolant, supported = true, sample = 73.0, raw = "41 05 7B"),
        )
        val reading = SensorPickerReadings.resolve(coolant, VehicleSnapshot.EMPTY, probe)
        assertEquals(SensorReadKind.LIVE, reading.kind)
        assertTrue(reading.subtitle.contains("73"))
    }

    @Test
    fun probeMiss_isNoData() {
        val probe = mapOf(
            ambient.id to PidProbeResult(ambient, supported = false, sample = null, raw = "NO DATA"),
        )
        val reading = SensorPickerReadings.resolve(ambient, VehicleSnapshot.EMPTY, probe)
        assertEquals(SensorReadKind.NONE, reading.kind)
        assertEquals("No data received", reading.subtitle)
    }

    @Test
    fun searchMatchesLabelAndPid() {
        assertTrue(SensorPickerReadings.matchesQuery(rpm, "rpm"))
        assertTrue(SensorPickerReadings.matchesQuery(rpm, "010C"))
        assertTrue(SensorPickerReadings.matchesQuery(coolant, "temp"))
        assertFalse(SensorPickerReadings.matchesQuery(rpm, "banana"))
    }

    @Test
    fun parseSaeSupport_fromDemo0100() {
        val set = SensorPickerReadings.parseSaeSupport(0x00, "41 00 BE 3E B8 11")
        assertTrue("RPM 0x0C should be advertised", 0x0C in set)
        assertTrue("Coolant 0x05 should be advertised", 0x05 in set)
        assertFalse("Ambient 0x46 is not in the 01-20 block", 0x46 in set)
    }

    @Test
    fun pidInCoveredSupportBlock_onlyMarksDecodedRange() {
        assertTrue(SensorPickerReadings.pidInCoveredSupportBlock(0x0C, setOf(0x00)))
        assertFalse(SensorPickerReadings.pidInCoveredSupportBlock(0x42, setOf(0x00)))
        assertTrue(SensorPickerReadings.pidInCoveredSupportBlock(0x42, setOf(0x00, 0x40)))
    }
}
