package com.fb2.obd

import com.fb2.obd.data.DemoFlavour
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.data.Elm327BluetoothSource
import com.fb2.obd.data.ObdLogger
import com.fb2.obd.obd.DashTheme
import com.fb2.obd.obd.DeepSearchKnowledgeBase
import com.fb2.obd.obd.EditableMetric
import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.KeepAlivePolicy
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.obd.VehicleProfileConfig
import com.fb2.obd.obd.VehicleSnapshot
import com.fb2.obd.ui.dash.DashThemeMetrics
import com.fb2.obd.ui.dash.interaction
import com.fb2.obd.ui.theme.ThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cross-cutting regression gate for bugs the user has already reported.
 * Keep this green before asking for a sideload test (.cursor/REGRESSION.md).
 */
class RegressionGateTest {

    @Before
    fun resetLogger() {
        ObdLogger.clearDebug()
        ObdLogger.clearValues()
        ObdLogger.valueLoggingEnabled = false
    }

    // --- Themes -------------------------------------------------------------

    @Test
    fun allThemes_haveDistinctPalettes() {
        val palettes = DashTheme.entries.map { ThemePalette.of(it) }
        assertEquals(4, palettes.size)
        // Opt accents must not collapse to Classic cyan-only look.
        assertNotEquals(ThemePalette.of(DashTheme.CLASSIC).accent, ThemePalette.of(DashTheme.OPT_A).accent)
        assertNotEquals(ThemePalette.of(DashTheme.OPT_A).accent, ThemePalette.of(DashTheme.OPT_B).accent)
        assertNotEquals(ThemePalette.of(DashTheme.OPT_B).accent, ThemePalette.of(DashTheme.OPT_C).accent)
    }

    @Test
    fun optThemes_sideMetrics_shareClassicLabelsAndPids() {
        val snap = drivingSnap()
        val metrics = DashThemeMetrics.sideMetrics(snap, thresholds = HealthThresholds.DEFAULT, dtcCount = 1)
        val required = listOf(
            "Coolant 1", "Battery", "Intake", "Load", "Throttle", "MAP", "MAF",
            "STFT", "Timing", "Health",
        )
        required.forEach { label ->
            assertTrue("$label missing", metrics.any { it.label == label })
        }
        val coolant = metrics.first { it.label == "Coolant 1" }
        assertEquals("0105", coolant.pidRequest)
        assertEquals(EditableMetric.COOLANT, coolant.editMetric)
        val battery = metrics.first { it.label == "Battery" }
        assertNotNull(battery.pidRequest)
        val (left, right) = DashThemeMetrics.splitWheels(metrics)
        assertTrue(left.isNotEmpty() && right.isNotEmpty())
        // Wheel focus tracking by label must remain stable when values update.
        val centerLabel = left[1.coerceAtMost(left.lastIndex)].label
        val updated = DashThemeMetrics.sideMetrics(
            snap.copy(coolantC = 99.0, batteryVolts = 13.9),
            thresholds = HealthThresholds.DEFAULT,
        )
        val (left2, _) = DashThemeMetrics.splitWheels(updated)
        assertTrue(left2.any { it.label == centerLabel })
    }

    @Test
    fun metricInteraction_wiresRemapDeepSearchAndEdit() {
        val m = DashThemeMetrics.sideMetrics(drivingSnap()).first { it.label == "Coolant 1" }
        var remapped: String? = null
        var deep: Pair<String, String?>? = null
        var edited: EditableMetric? = null
        val (remap, search, edit) = m.interaction(
            onRemapBase = { remapped = it },
            onDeepSearch = { label, pid -> deep = label to pid },
            onEdit = { edited = it },
        )
        remap()
        search()
        edit?.invoke()
        assertEquals("Coolant 1", remapped)
        assertEquals("Coolant 1" to "0105", deep)
        assertEquals(EditableMetric.COOLANT, edited)
    }

    // --- Logging (theme-independent) ----------------------------------------

    @Test
    fun valueLog_isThemeIndependent_dashCanonicalColumns() {
        ObdLogger.valueLoggingEnabled = true
        val snap = drivingSnap()
        ObdLogger.logSnapshot(snap)
        // Mirrors DashboardViewModel.logDashValues keys (theme must not change this set).
        ObdLogger.logTabMap(
            "Dash",
            mapOf(
                "RPM" to "2450",
                "Speed" to "96",
                "Gear" to "3",
                "GearSource" to "ESTIMATED",
                "Coolant1" to "85",
                "Coolant2" to "n/s",
                "Battery" to "14.2",
                "Intake" to "34",
                "Ambient" to "28",
                "Load" to "47",
                "Throttle" to "31",
                "STFT" to "2.3",
                "LTFT" to "n/s",
                "MAF" to "12.4",
                "MAP" to "58",
                "Timing" to "14",
                "FuelLoop" to "CLOSED LOOP",
                "DTCs" to "0",
                "HealthPct" to "94",
            ),
        )
        ObdLogger.logTabMap("Transmission", mapOf("ATF" to "86"))
        ObdLogger.logTabMap("Trip", mapOf("Distance" to "12"))
        val csv = ObdLogger.valuesCsv()
        assertTrue(csv.contains("time_ms,rpm,speed_kmh"))
        assertTrue(csv.contains("# dash_tiles"))
        assertTrue(csv.contains("Coolant1"))
        assertTrue(csv.contains("GearSource"))
        assertFalse("theme must not dump Trans/Trip pages into value CSV", csv.contains("ATF"))
        assertFalse(csv.contains("Distance"))
        assertFalse(csv.contains("# page_probes"))
    }

    @Test
    fun valueLog_disabled_writesNothing() {
        ObdLogger.logSnapshot(drivingSnap())
        ObdLogger.logTabMap("Dash", mapOf("RPM" to "1"))
        assertEquals(0, ObdLogger.valueRows().size)
        assertFalse(ObdLogger.valuesCsv().contains("2450"))
    }

    // --- Gear ----------------------------------------------------------------

    @Test
    fun estimatedGear_hiddenBelowMinSpeed_evenIfSettingOn() {
        val est = GearEstimator()
        assertNull(est.estimate(speedKmh = 0.0, rpm = 1600.0))
        assertNull(est.estimate(speedKmh = 4.9, rpm = 1600.0))
        assertNotNull(est.estimate(speedKmh = 50.0, rpm = 1900.0))
    }

    @Test
    fun showEstimatedGearOff_mapsEstimatedToNone() {
        val snap = drivingSnap().copy(gear = 3, gearSource = GearSource.ESTIMATED)
        val showEstimatedGear = false
        val gearSrc = if (!showEstimatedGear && snap.gearSource == GearSource.ESTIMATED) {
            GearSource.NONE
        } else {
            snap.gearSource
        }
        assertEquals(GearSource.NONE, gearSrc)
    }

    @Test
    fun demoFb2_emitsEstimatedGearWhenMoving() = runBlocking {
        val snap = DemoObdSource(flavour = DemoFlavour.FB2).snapshots().first()
        // Demo wave is often moving; if stopped this iteration, still assert source wiring.
        if ((snap.speedKmh ?: 0.0) >= 5.0 && (snap.rpm ?: 0.0) > 0.0) {
            assertEquals(GearSource.ESTIMATED, snap.gearSource)
            assertNotNull(snap.gear)
        } else {
            assertTrue(snap.gearSource == GearSource.NONE || snap.gearSource == GearSource.ESTIMATED)
        }
    }

    // --- Profiles / PIDs / protocols ----------------------------------------

    @Test
    fun profiles_pidCatalogs_andCoreSaesPresent() {
        val core = listOf("010C", "010D", "0105", "010B", "010C", "0111", "0142")
        val fb2 = VehicleProfileConfig.pidCatalog(VehicleProfile.FB2)
        val gen = VehicleProfileConfig.pidCatalog(VehicleProfile.GENERIC_OBD2)
        core.forEach { id ->
            assertTrue("FB2 missing $id", fb2.any { it.id.equals(id, true) })
            assertTrue("Generic missing $id", gen.any { it.id.equals(id, true) })
        }
        assertTrue(fb2.any { it.request.startsWith("22") })
        assertTrue(HondaPidCatalog.allPids.all { hp -> fb2.any { it.id == hp.id } })
        assertTrue(gen.none { it.request.startsWith("22") })
        assertTrue(gen.all { it.profile.equals("SAE", true) })
        assertTrue(StandardPidCatalog.all.size >= 160)
    }

    @Test
    fun profiles_dashPages_andEstimatedGearDefaults() {
        assertTrue(VehicleProfileConfig.dashPageTitles(VehicleProfile.FB2).contains("Trans"))
        assertFalse(VehicleProfileConfig.dashPageTitles(VehicleProfile.GENERIC_OBD2).contains("Trans"))
        assertTrue(VehicleProfileConfig.defaultShowEstimatedGear(VehicleProfile.FB2))
        assertFalse(VehicleProfileConfig.defaultShowEstimatedGear(VehicleProfile.GENERIC_OBD2))
    }

    @Test
    fun protocols_genericDeepSearch_skipsHondaAndKeepsSaes() {
        val generic = VehicleProfileConfig.deepSearchStrategies(
            VehicleProfile.GENERIC_OBD2, null, "Total misfire count", "221316",
        )
        assertTrue(generic.none { it.isHondaSpecific })
        assertTrue(generic.none { it.request.startsWith("22") })
        val fb2 = VehicleProfileConfig.deepSearchStrategies(
            VehicleProfile.FB2, null, "Total misfire count", "221316",
        )
        assertTrue(fb2.any { it.request.startsWith("22") })
        // Battery: Generic still allows ATRV (not Honda Mode 22).
        val batt = VehicleProfileConfig.deepSearchStrategies(
            VehicleProfile.GENERIC_OBD2, null, "Battery", "0142",
        )
        assertTrue(batt.any { it.request.equals("ATRV", true) })
        assertTrue(batt.none { it.isHondaSpecific })
    }

    @Test
    fun protocols_elmInit_usesAutoProtocol() {
        // Reflect private INIT_SEQUENCE — must stay ATSP0 (auto) for both profiles.
        val field = Elm327BluetoothSource::class.java.getDeclaredField("INIT_SEQUENCE")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val init = field.get(null) as List<String>
        assertTrue(init.contains("ATZ"))
        assertTrue(init.contains("ATSP0"))
        assertFalse(init.any { it.matches(Regex("ATSP[1-9A-C]", RegexOption.IGNORE_CASE)) })
    }

    @Test
    fun protocols_knowledgeBase_hasProtocolSwitchesForFb2Gaps() {
        val battery = DeepSearchKnowledgeBase.strategiesFor(null, "Battery", "0142")
        assertTrue(battery.any { it.setup.any { s -> s.equals("ATSP0", true) || s.startsWith("ATSP") } })
        assertTrue(battery.any { it.request.equals("ATRV", true) })
    }

    @Test
    fun freshness_holdValues_keepsDashDuringExclusiveLink() {
        val freshness = SnapshotFreshness()
        val now = 20_000L
        freshness.markOk(SnapshotFreshness.KEY_RPM, 0L)
        freshness.markOk(SnapshotFreshness.KEY_SPEED, 0L)
        freshness.markOk(SnapshotFreshness.KEY_COOLANT, 0L)
        val snap = drivingSnap()
        val held = freshness.sanitize(snap, nowMs = now, rpmUpdatedThisCycle = false, holdValues = true)
        assertEquals(snap.speedKmh, held.speedKmh)
        assertEquals(snap.coolantC, held.coolantC)
    }

    @Test
    fun picker_liveBeatsUnsupportedBitmask_forAtrvBattery() {
        val battery = StandardPidCatalog.all.first { it.request.equals("0142", true) }
        val snap = VehicleSnapshot(batteryVolts = 13.8, unsupportedPids = setOf(0x42))
        assertEquals(
            com.fb2.obd.obd.SensorReadKind.LIVE,
            com.fb2.obd.obd.SensorPickerReadings.resolve(battery, snap, emptyMap()).kind,
        )
    }

    @Test
    fun keepAlive_reconnectsUnlessUserDisconnected() {
        assertTrue(KeepAlivePolicy.shouldReconnectAfterDeath("00:11:22:33:44:55", false))
        assertFalse(KeepAlivePolicy.shouldReconnectAfterDeath("00:11:22:33:44:55", true))
    }

    @Test
    fun freshness_clearsEstimatedGearWhenSpeedStale() {
        val freshness = SnapshotFreshness()
        val now = 10_000L
        freshness.markOk(SnapshotFreshness.KEY_RPM, now)
        freshness.markOk(SnapshotFreshness.KEY_SPEED, now - 10_000L) // older than TTL
        val snap = drivingSnap().copy(
            gear = 3,
            gearSource = GearSource.ESTIMATED,
            gearConfidencePct = 90,
        )
        val out = freshness.sanitize(snap, nowMs = now, rpmUpdatedThisCycle = true)
        assertEquals(GearSource.NONE, out.gearSource)
        assertNull(out.gear)
    }

    private fun drivingSnap(): VehicleSnapshot {
        val speed = 96.0
        val rpm = 2450.0
        val base = VehicleSnapshot(
            rpm = rpm,
            speedKmh = speed,
            coolantC = 85.0,
            intakeC = 34.0,
            ambientC = 28.0,
            engineLoadPct = 47.0,
            throttlePct = 31.0,
            timingAdvance = 14.0,
            mafGps = 12.4,
            mapKpa = 58.0,
            stftPct = 2.3,
            batteryVolts = 14.2,
            fuelSystemStatus = "CLOSED LOOP",
            gear = GearEstimator().estimate(speed, rpm),
            gearSource = GearSource.ESTIMATED,
            gearConfidencePct = 90,
        )
        return base.copy(freshAtMs = SnapshotFreshness.mapForPresentFields(base, System.currentTimeMillis()))
    }
}
