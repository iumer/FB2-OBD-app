package com.fb2.obd

import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthEvaluatorTest {

    @Test
    fun coolant_thresholds() {
        assertEquals(Health.GOOD, HealthEvaluator.coolant(90.0))
        assertEquals(Health.WARN, HealthEvaluator.coolant(103.0))
        assertEquals(Health.CRITICAL, HealthEvaluator.coolant(110.0))
        assertEquals(Health.UNKNOWN, HealthEvaluator.coolant(null))
    }

    @Test
    fun battery_thresholds() {
        assertEquals(Health.GOOD, HealthEvaluator.battery(14.2))
        assertEquals(Health.WARN, HealthEvaluator.battery(13.0))
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(12.1))
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(15.5)) // overcharge
    }

    @Test
    fun fuelTrim_thresholds() {
        assertEquals(Health.GOOD, HealthEvaluator.fuelTrim(3.0))
        assertEquals(Health.WARN, HealthEvaluator.fuelTrim(8.0))
        assertEquals(Health.CRITICAL, HealthEvaluator.fuelTrim(-12.0))
    }

    @Test
    fun atfTemp_thresholds() {
        assertEquals(Health.GOOD, HealthEvaluator.atfTemp(85.0))
        assertEquals(Health.WARN, HealthEvaluator.atfTemp(100.0))
        assertEquals(Health.CRITICAL, HealthEvaluator.atfTemp(115.0))
        assertEquals(Health.WARN, HealthEvaluator.atfTemp(60.0)) // cold
    }
}
