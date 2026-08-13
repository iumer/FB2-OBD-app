package com.fb2.obd

import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.obd.DeepSearchKnowledgeBase
import com.fb2.obd.obd.DeepSensorSearch
import com.fb2.obd.obd.StandardPidCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSearchKnowledgeBaseTest {

    @Test
    fun coolant2_hasForceAndHeaderStrategies() {
        val strategies = DeepSearchKnowledgeBase.strategiesFor(null, "Coolant 2", "0167")
        assertTrue(strategies.size >= 3)
        assertTrue(strategies.any { it.request == "0167" })
        assertTrue(strategies.any { it.setup.any { c -> c.startsWith("ATSH") } })
    }

    @Test
    fun atf_includesTcmHeaders() {
        val strategies = DeepSearchKnowledgeBase.strategiesFor(null, "ATF temperature", "221101")
        assertTrue(strategies.any { it.setup.contains("ATSH7E1") || it.id.contains("7E1") })
        assertTrue(strategies.any { it.request.startsWith("22") })
    }

    @Test
    fun battery_prefersAtrvFirst() {
        val strategies = DeepSearchKnowledgeBase.strategiesFor(null, "Battery", "0142")
        assertTrue(strategies.isNotEmpty())
        assertTrue(strategies.first().request.equals("ATRV", ignoreCase = true))
        assertTrue(strategies.any { it.isAdapterLocal })
        assertTrue(strategies.any { it.isSimpleForce })
    }

    @Test
    fun demoDeepSearch_recoversBatteryViaAtrv() = runBlocking {
        val source = DemoObdSource()
        val report = DeepSensorSearch.run(source, "Battery", null, "0142")
        assertTrue(report.success)
        assertNotNull(report.hit)
        assertTrue(report.hit!!.value in 12.0..15.0)
        assertTrue(report.hit!!.strategy.request.equals("ATRV", true))
    }

    @Test
    fun misfire_strategies_areListed_notEmpty() {
        val strategies = DeepSearchKnowledgeBase.strategiesFor(null, "Total misfire count", "221316")
        // Specific Honda Mode 22 pack + generic header/protocol variants.
        assertTrue("expected ~10 strategies, got ${strategies.size}", strategies.size >= 6)
        assertTrue(strategies.any { it.request == "221316" })
        assertTrue(strategies.any { it.setup.any { c -> c.startsWith("ATSH") } })
    }

    @Test
    fun demoDeepSearch_recoversTotalMisfire_andWalksStrategies() = runBlocking {
        val source = DemoObdSource()
        val progress = mutableListOf<Pair<Int, Int>>()
        val report = DeepSensorSearch.run(
            source,
            "Total misfire count",
            null,
            "221316",
        ) { i, total, _ -> progress.add(i to total) }
        assertTrue(report.success)
        assertNotNull(report.hit)
        assertTrue(report.attempts >= 1)
        assertTrue(progress.isNotEmpty())
        // Progress total should match the library size (not fake 1-and-done).
        assertEquals(progress.last().second, DeepSearchKnowledgeBase.strategiesFor(null, "Total misfire count", "221316").size)
    }

    @Test
    fun deepSearch_busDown_reportsSkippedNotSilentOneOfTen() = runBlocking {
        val source = object : com.fb2.obd.data.ObdSource {
            override val name = "Fake UNABLE"
            override val isLive = true
            override fun snapshots() = kotlinx.coroutines.flow.flowOf(com.fb2.obd.obd.VehicleSnapshot.EMPTY)
            override suspend fun command(raw: String): String = "UNABLE TO CONNECT"
        }
        val report = DeepSensorSearch.run(source, "Total misfire count", null, "221316")
        assertFalse(report.success)
        val joined = report.notes.joinToString(" ")
        assertTrue(
            "expected honest skip note, got: $joined",
            joined.contains("Skipped", ignoreCase = true) || joined.contains("link is down", ignoreCase = true),
        )
    }
}
