package com.fb2.obd

import com.fb2.obd.obd.PidProbeResult
import com.fb2.obd.obd.SensorPickerReading
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
    fun liveMap_beatsEcuUnsupportedBitmask() {
        val map = StandardPidCatalog.all.first { it.request.equals("010B", true) }
        val snap = VehicleSnapshot(mapKpa = 97.0, mafGps = 0.33, unsupportedPids = setOf(0x0B))
        val reading = SensorPickerReadings.resolve(map, snap, emptyMap())
        assertEquals(SensorReadKind.LIVE, reading.kind)
        assertTrue(reading.latest!!.contains("97"))
    }

    @Test
    fun latch_keepsLiveWhenSnapshotClears() {
        val live = SensorPickerReading(SensorReadKind.LIVE, "97.00 kPa")
        val gone = SensorPickerReading(SensorReadKind.NONE)
        val kept = SensorPickerReadings.latch(live, gone)
        assertEquals(SensorReadKind.LIVE, kept.kind)
        assertEquals("97.00 kPa", kept.latest)
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
