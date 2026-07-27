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
        assertEquals(Health.GOOD, HealthEvaluator.battery(13.0, true, t, rpm = 2000.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.battery(14.5, true, t, rpm = 2000.0).health)
        // Below elevated band + above idle → CRITICAL ALT WEAK
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(12.0, true, t, rpm = 2000.0).health)
    }

    @Test
    fun store_migratesHarshMafBands_onSchemaBump() {
        val file = tmp.newFile("health_thresholds.json")
        // Simulate an older install that saved schema 2 with the harsh 6–10 idle band.
        file.writeText(
            """
            {"schemaVersion":2,"mafIdleGoodMin":6.0,"mafIdleGoodMax":10.0,"mafIdleWarnMin":4.0,
             "mafCruiseGoodMin":15.0,"mafCruiseGoodMax":35.0,"coolantGoodMax":90.0}
            """.trimIndent(),
        )
        val loaded = HealthThresholdStore(file).load()
        assertEquals(2.0, loaded.mafIdleGoodMin, 0.001)
        assertEquals(8.0, loaded.mafIdleGoodMax, 0.001)
        assertEquals(1.0, loaded.mafIdleWarnMin, 0.001)
        assertEquals(2.5, loaded.mafCruiseGoodMin, 0.001)
        // Non-MAF fields still round-trip from the old file when present.
        assertEquals(90.0, loaded.coolantGoodMax, 0.001)
        assertTrue(file.readText().contains("\"schemaVersion\": 3") || file.readText().contains("\"schemaVersion\":3"))
    }
}
