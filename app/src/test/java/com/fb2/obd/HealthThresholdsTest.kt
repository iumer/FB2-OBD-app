package com.fb2.obd

import com.fb2.obd.data.HealthThresholdStore
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.withField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HealthThresholdsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun customBatteryGreenBand_isHonoured() {
        val t = HealthThresholds.DEFAULT
            .withField("battRunGoodMin", 12.7)
            .withField("battRunGoodMax", 14.5)
            .withField("battRunWarnMin", 12.4)
        assertEquals(Health.GOOD, HealthEvaluator.battery(13.0, true, t).health)
        assertEquals(Health.GOOD, HealthEvaluator.battery(14.5, true, t).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(12.0, true, t).health)
    }

    @Test
    fun store_roundTrips() {
        val file = tmp.newFile("health_thresholds.json")
        val store = HealthThresholdStore(file)
        val custom = HealthThresholds.DEFAULT.withField("coolantGoodMax", 94.0)
        store.save(custom)
        val loaded = store.load()
        assertEquals(94.0, loaded.coolantGoodMax, 0.001)
        assertTrue(file.readText().contains("coolantGoodMax"))
    }
}
