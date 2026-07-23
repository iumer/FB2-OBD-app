package com.fb2.obd

import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.obd.DiagnosticParsers
import com.fb2.obd.obd.DtcCatalog
import com.fb2.obd.obd.HealthScoreCalculator
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.TripComputer
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExpansionTest {

    @Test
    fun saeCatalog_hasFullStandardCoverage() {
        assertTrue(
            "Expected ~150+ SAE Mode 01 entries, got ${StandardPidCatalog.all.size}",
            StandardPidCatalog.all.size >= 150,
        )
        assertNotNull(StandardPidCatalog.byId("010C"))
        assertTrue(StandardPidCatalog.fuelPageDefaults().size >= 10)
    }

    @Test
    fun hondaCatalog_coversEnhancedModules() {
        val ids = HondaPidCatalog.allPacks.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf(
            "honda_tcm", "honda_engine", "honda_abs", "honda_eps",
            "honda_srs", "honda_body", "honda_climate", "honda_tpms",
        )))
        assertTrue(HondaPidCatalog.allPids.size >= 40)
    }

    @Test
    fun dtcCatalog_isLargeMultiBrand() {
        // Spot-check Honda + transmission + ABS + body families exist.
        assertTrue(DtcCatalog.describe("P1259").contains("VTEC"))
        assertTrue(DtcCatalog.describe("P0741").contains("Torque converter"))
        assertTrue(DtcCatalog.explain("P0171").contains("Likely causes"))
        assertTrue(DtcCatalog.describe("C0035").contains("wheel speed"))
    }

    @Test
    fun readinessParser_decodesMilAndMonitors() {
        // MIL off, 0 DTCs, spark monitors — bytes A B C D after 41 01
        val status = DiagnosticParsers.parseReadiness("41 01 00 07 E5 E5")
        assertEquals(false, status.milOn)
        assertEquals(0, status.dtcCount)
        assertTrue(status.monitors.isNotEmpty())
    }

    @Test
    fun vinParser_extractsSeventeenChars() {
        val vin = DiagnosticParsers.parseMode09Vin(
            "49 02 01 4A 48 4D 46 42 32 31 32 33 34 35 36 37 38 39",
        )
        assertEquals("JHMFB2123456789", vin)
    }

    @Test
    fun tripComputer_accumulatesDistanceAndEconomy() {
        val trip = TripComputer()
        trip.fuelPricePerLiter = 280.0
        // 100 km/h for ~36s in 1s steps with MAF ~25 g/s
        var t = 1_000L
        trip.onSample(t, 100.0, 25.0, null)
        repeat(36) {
            t += 1_000L
            trip.onSample(t, 100.0, 25.0, null)
        }
        assertTrue(trip.distanceKm > 0.9)
        assertNotNull(trip.kmPerLiter)
        assertTrue(trip.tripCost > 0.0)
    }

    @Test
    fun healthScore_deductsForDtcsAndAtf() {
        val snap = VehicleSnapshot(rpm = 800.0, coolantC = 90.0, batteryVolts = 14.2, stftPct = 1.0, ltftPct = 2.0)
        val healthy = HealthScoreCalculator.compute(snap, 0, atfC = 85.0, tcSlipRpm = 20.0)
        assertEquals(100, healthy.enginePct)
        val sick = HealthScoreCalculator.compute(snap, 3, atfC = 120.0, tcSlipRpm = 400.0)
        assertTrue(sick.enginePct < 100)
        assertTrue(sick.transmissionPct < 90)
    }

    @Test
    fun demoSource_probesHondaAndFuelPacks() = runBlocking {
        val demo = DemoObdSource()
        val fuel = demo.probePids(StandardPidCatalog.fuelPageDefaults())
        assertTrue(fuel.any { it.supported })
        val modules = demo.probeHondaModules()
        assertEquals(HondaPidCatalog.allPacks.size, modules.size)
        assertTrue(modules.any { it.supportedCount > 0 })
        val info = demo.readVehicleInfo()
        assertEquals("JHMFB2123456789", info.vin)
        val deep = demo.readReadiness()
        assertTrue(deep.monitors.isNotEmpty())
    }
}
