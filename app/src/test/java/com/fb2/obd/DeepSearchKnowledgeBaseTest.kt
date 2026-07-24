package com.fb2.obd

import com.fb2.obd.data.DemoObdSource
import com.fb2.obd.obd.DeepSearchKnowledgeBase
import com.fb2.obd.obd.DeepSensorSearch
import com.fb2.obd.obd.StandardPidCatalog
import kotlinx.coroutines.runBlocking
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
    fun demoDeepSearch_recoversAmbient() = runBlocking {
        val source = DemoObdSource()
        val pid = StandardPidCatalog.all.find { it.request == "0146" }
        val report = DeepSensorSearch.run(source, "Ambient", pid, "0146")
        assertTrue(report.success)
        assertNotNull(report.hit)
        assertFalse(report.hit!!.value.isNaN())
    }
}
