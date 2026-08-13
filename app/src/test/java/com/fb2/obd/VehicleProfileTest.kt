package com.fb2.obd

import com.fb2.obd.data.DemoFlavour
import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.obd.DeepSearchKnowledgeBase
import com.fb2.obd.obd.DeepSensorSearch
import com.fb2.obd.obd.HondaPidCatalog
import com.fb2.obd.obd.StandardPidCatalog
import com.fb2.obd.obd.VehicleProfile
import com.fb2.obd.obd.VehicleProfileConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileTest {

    @Test
    fun genericCatalog_excludesHondaMode22() {
        val cat = VehicleProfileConfig.pidCatalog(VehicleProfile.GENERIC_OBD2)
        assertTrue(cat.all { it.profile.equals("SAE", true) })
        assertFalse(cat.any { it.request.startsWith("22") })
        assertTrue(cat.size >= StandardPidCatalog.all.size)
    }

    @Test
    fun fb2Catalog_includesHondaPacks() {
        val cat = VehicleProfileConfig.pidCatalog(VehicleProfile.FB2)
        assertTrue(cat.size > StandardPidCatalog.all.size)
        assertTrue(cat.any { it.request.startsWith("22") })
        assertTrue(HondaPidCatalog.allPids.all { hp -> cat.any { it.id == hp.id } })
    }

    @Test
    fun genericDashPages_dropTrans() {
        val pages = VehicleProfileConfig.dashPageTitles(VehicleProfile.GENERIC_OBD2)
        assertFalse(pages.contains("Trans"))
        assertTrue(pages.contains("Dash"))
        assertTrue(pages.contains("Fuel"))
        assertTrue(VehicleProfileConfig.dashPageTitles(VehicleProfile.FB2).contains("Trans"))
    }

    @Test
    fun genericDeepSearch_skipsHondaMode22() {
        val strategies = VehicleProfileConfig.deepSearchStrategies(
            VehicleProfile.GENERIC_OBD2,
            null,
            "Total misfire count",
            "221316",
        )
        assertTrue(strategies.none { it.isHondaSpecific })
        assertTrue(strategies.none { it.request.startsWith("22") })
        val fb2 = VehicleProfileConfig.deepSearchStrategies(
            VehicleProfile.FB2,
            null,
            "Total misfire count",
            "221316",
        )
        assertTrue(fb2.any { it.request.startsWith("22") })
    }

    @Test
    fun genericIdle_hasNoMode22() {
        val idle = VehicleProfileConfig.idleSections(VehicleProfile.GENERIC_OBD2)
        assertTrue(idle.isNotEmpty())
        assertTrue(idle.flatMap { it.pids }.none { it.request.startsWith("22") })
    }

    @Test
    fun standardCatalog_coversCoreAndExtendedSaes() {
        val ids = StandardPidCatalog.all.map { it.id }.toSet()
        listOf("010C", "010D", "0105", "0142", "015C", "015E", "01A6", "0136", "0169").forEach {
            assertTrue("missing $it", ids.contains(it))
        }
        assertTrue(StandardPidCatalog.all.size >= 160)
    }

    @Test
    fun demoGeneric_readsPermanentAndShowsAmbient() = runBlocking {
        val source = DemoObdSource(flavour = DemoFlavour.GENERIC)
        val s = source.snapshots().first()
        assertTrue(s.ambientC != null)
        assertTrue(s.ltftPct != null)
        assertTrue(s.coolant2C != null)
        val permanent = source.readPermanentDtcs()
        assertTrue(permanent.isNotEmpty())
    }

    @Test
    fun deepSearch_genericBatteryStillUsesAtrv() = runBlocking {
        val source = DemoObdSource(flavour = DemoFlavour.GENERIC)
        val report = DeepSensorSearch.run(
            source,
            "Battery",
            null,
            "0142",
            profile = VehicleProfile.GENERIC_OBD2,
        )
        assertTrue(report.success)
        assertTrue(report.hit!!.strategy.request.equals("ATRV", true))
        assertTrue(
            DeepSearchKnowledgeBase.strategiesFor(null, "Battery", "0142")
                .any { it.isHondaSpecific },
        )
        assertTrue(
            VehicleProfileConfig.deepSearchStrategies(VehicleProfile.GENERIC_OBD2, null, "Battery", "0142")
                .none { it.isHondaSpecific },
        )
    }

    @Test
    fun estimatedGearDefault_offForGeneric() {
        assertFalse(VehicleProfileConfig.defaultShowEstimatedGear(VehicleProfile.GENERIC_OBD2))
        assertTrue(VehicleProfileConfig.defaultShowEstimatedGear(VehicleProfile.FB2))
    }
}
