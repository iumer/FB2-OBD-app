package com.fb2.obd

import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthEvaluatorTest {

    @Test
    fun coolant_fb2Bands() {
        assertEquals(Health.COLD, HealthEvaluator.coolant(55.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.coolant(85.0).health)
        assertEquals("NORMAL", HealthEvaluator.coolant(85.0).label)
        assertEquals(Health.GOOD, HealthEvaluator.coolant(95.0).health)
        assertEquals(Health.WARN, HealthEvaluator.coolant(98.0).health)
        assertEquals(Health.ELEVATED, HealthEvaluator.coolant(102.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.coolant(104.0).health)
        assertEquals("OVERHEAT", HealthEvaluator.coolant(104.0).label)
        assertEquals(Health.UNKNOWN, HealthEvaluator.coolant(null).health)
    }

    @Test
    fun battery_engineRunning_eldAware() {
        // Full charge band
        assertEquals(Health.GOOD, HealthEvaluator.battery(14.2, true, rpm = 2000.0).health)
        assertEquals("CHARGING OK", HealthEvaluator.battery(14.2, true, rpm = 2000.0).label)
        // 13.4V is inside green (good min 13.2)
        assertEquals(Health.GOOD, HealthEvaluator.battery(13.4, true, rpm = 2000.0).health)
        // Soft ELD at idle — low volts are not ALT WEAK
        assertEquals(Health.GOOD, HealthEvaluator.battery(13.0, true, rpm = 800.0).health)
        assertEquals("ELD IDLE", HealthEvaluator.battery(13.0, true, rpm = 800.0).label)
        assertEquals(Health.ELEVATED, HealthEvaluator.battery(12.5, true, rpm = 800.0).health)
        assertEquals("IDLE LOW", HealthEvaluator.battery(12.5, true, rpm = 800.0).label)
        // Above idle: same volts escalate
        assertEquals(Health.WARN, HealthEvaluator.battery(13.0, true, rpm = 2000.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(12.5, true, rpm = 2000.0).health)
        assertEquals("ALT WEAK", HealthEvaluator.battery(12.5, true, rpm = 2000.0).label)
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(15.5, true, rpm = 2000.0).health)
    }

    @Test
    fun battery_engineOff() {
        assertEquals(Health.GOOD, HealthEvaluator.battery(12.7, false).health)
        assertEquals(Health.WARN, HealthEvaluator.battery(12.4, false).health)
        assertEquals(Health.ELEVATED, HealthEvaluator.battery(12.1, false).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.battery(11.8, false).health)
    }

    @Test
    fun fuelTrim_bands() {
        assertEquals(Health.GOOD, HealthEvaluator.fuelTrim(3.0).health)
        assertEquals(Health.WARN, HealthEvaluator.fuelTrim(8.0).health)
        assertTrue(HealthEvaluator.fuelTrim(8.0).label.contains("LEAN"))
        assertEquals(Health.ELEVATED, HealthEvaluator.fuelTrim(-12.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.fuelTrim(22.0).health)
    }

    @Test
    fun load_displayOnly_intake_map_timing() {
        // Load / throttle: display only — always green when present
        assertEquals(Health.GOOD, HealthEvaluator.engineLoad(40.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.engineLoad(90.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.throttle(80.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.intakeAir(32.0).health)
        assertEquals(Health.COLD, HealthEvaluator.intakeAir(10.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.intakeAir(65.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.map(35.0, 18.0, 700.0, 0.0).health) // idle
        assertEquals("IDLE", HealthEvaluator.map(35.0, 18.0, 700.0, 0.0).label)
        assertEquals(Health.GOOD, HealthEvaluator.map(98.0, 95.0, 3000.0, 80.0).health) // WOT
        // Tip-in / rising MAP under light throttle must stay green (not WARN "RISING")
        assertEquals(Health.GOOD, HealthEvaluator.map(98.0, 20.0, 2500.0, 60.0).health)
        assertEquals("LIGHT LOAD", HealthEvaluator.map(98.0, 20.0, 2500.0, 60.0).label)
        assertEquals(Health.GOOD, HealthEvaluator.map(70.0, 40.0, 2500.0, 60.0).health)
        assertEquals("MED LOAD", HealthEvaluator.map(70.0, 40.0, 2500.0, 60.0).label)
        assertEquals(Health.WARN, HealthEvaluator.timing(-2.0).health) // 0 to -5 yellow
        assertEquals(Health.CRITICAL, HealthEvaluator.timing(-6.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.timing(15.0).health)
        assertEquals(Health.WARN, HealthEvaluator.timing(0.0).health)
    }

    @Test
    fun maf_idleBand_r18Realistic() {
        // FB2 R18 idle MAF is typically ~3–5 g/s (Torque-normal); not critical.
        assertEquals(Health.GOOD, HealthEvaluator.maf(3.8, 850.0, 0.0).health)
        assertEquals("IDLE OK", HealthEvaluator.maf(3.8, 850.0, 0.0).label)
        assertEquals(Health.GOOD, HealthEvaluator.maf(5.0, 700.0, 0.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.maf(2.0, 700.0, 0.0).health)
        assertEquals(Health.WARN, HealthEvaluator.maf(1.2, 700.0, 0.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.maf(0.5, 700.0, 0.0).health)
        // Light city cruise ~8 g/s should be green, not "LOW".
        assertEquals(Health.GOOD, HealthEvaluator.maf(8.0, 1400.0, 25.0, 17.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.maf(22.0, 2200.0, 70.0, 20.0).health)
        // Coast / overrun at city speed with closed throttle — low MAF is normal.
        assertEquals(Health.GOOD, HealthEvaluator.maf(3.7, 1300.0, 24.0, 14.0).health)
        assertEquals("COAST OK", HealthEvaluator.maf(3.7, 1300.0, 24.0, 14.0).label)
    }

    @Test
    fun fuelSystem_and_dtc() {
        assertEquals(Health.GOOD, HealthEvaluator.fuelSystem("CLOSED LOOP", 90.0).health)
        // Warm OPEN LOOP is normal (accel / fuel cut) — never WARN
        assertEquals(Health.GOOD, HealthEvaluator.fuelSystem("OPEN LOOP", 90.0).health)
        assertEquals(Health.COLD, HealthEvaluator.fuelSystem("OPEN LOOP", 40.0).health)
        assertEquals(Health.ELEVATED, HealthEvaluator.fuelSystem("OPEN (FAULT)", 90.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.dtcCount(0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.dtcCount(2).health)
    }

    @Test
    fun rpm_and_atf_and_slip() {
        assertEquals(Health.GOOD, HealthEvaluator.rpm(2200.0).health)
        assertEquals(Health.WARN, HealthEvaluator.rpm(5200.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.rpm(6500.0).health)
        assertEquals(Health.COLD, HealthEvaluator.atfTemp(40.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.atfTemp(85.0).health)
        assertEquals(Health.ELEVATED, HealthEvaluator.atfTemp(110.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.atfTemp(120.0).health)
        assertEquals(Health.GOOD, HealthEvaluator.tcSlip(20.0).health)
        assertEquals(Health.CRITICAL, HealthEvaluator.tcSlip(150.0).health)
    }

    @Test
    fun transmissionLabel_matchesAtf() {
        val s = HealthEvaluator.forTransmissionLabel("ATF temperature", "86.00 °C")
        assertEquals(Health.GOOD, s!!.health)
        assertEquals("NORMAL", s.label)
    }
}
