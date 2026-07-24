package com.fb2.obd.obd

import kotlin.math.abs

/**
 * Traffic-light status for a monitored metric (FB2 Civic–tuned).
 *
 * Color standard:
 * - [COLD] blue — warming up / cold fluid
 * - [GOOD] green — normal
 * - [WARN] yellow — caution
 * - [ELEVATED] orange — high risk
 * - [CRITICAL] red — critical
 * - [UNKNOWN] gray — n/s / inactive
 */
enum class Health { COLD, GOOD, WARN, ELEVATED, CRITICAL, UNKNOWN }

/** Health plus a short human status word for tiles (e.g. NORMAL, HOT, CHARGING OK). */
data class MetricStatus(
    val health: Health,
    val label: String,
) {
    companion object {
        val NS = MetricStatus(Health.UNKNOWN, "n/s")
    }
}

/**
 * Maps raw sensor values to health + status labels for the FB2 dashboard.
 * Pure Kotlin — fully unit-testable.
 */
object HealthEvaluator {

    fun coolant(celsius: Double?): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < 70 -> MetricStatus(Health.COLD, "COLD")
        celsius <= 97 -> MetricStatus(Health.GOOD, "NORMAL")
        celsius <= 103 -> MetricStatus(Health.WARN, "WARM")
        celsius <= 108 -> MetricStatus(Health.ELEVATED, "HOT")
        else -> MetricStatus(Health.CRITICAL, "OVERHEAT")
    }

    /**
     * Engine off: resting battery. Engine running: alternator charging band.
     */
    fun battery(volts: Double?, engineRunning: Boolean = true): MetricStatus = when {
        volts == null -> MetricStatus.NS
        engineRunning -> when {
            volts > 15.0 -> MetricStatus(Health.CRITICAL, "OVERCHARGE")
            volts >= 13.8 && volts <= 14.7 -> MetricStatus(Health.GOOD, "CHARGING OK")
            volts >= 13.2 && volts < 13.8 -> MetricStatus(Health.WARN, "LOW CHARGE")
            volts < 13.2 -> MetricStatus(Health.CRITICAL, "ALT WEAK")
            else -> MetricStatus(Health.WARN, "HIGH CHARGE") // 14.7–15.0
        }
        else -> when {
            volts > 12.6 -> MetricStatus(Health.GOOD, "RESTING OK")
            volts >= 12.3 -> MetricStatus(Health.WARN, "WEAK REST")
            else -> MetricStatus(Health.CRITICAL, "FLAT")
        }
    }

    fun fuelTrim(percent: Double?): MetricStatus {
        if (percent == null) return MetricStatus.NS
        val a = abs(percent)
        val leanRich = if (percent >= 0) "LEAN" else "RICH"
        return when {
            a <= 5 -> MetricStatus(Health.GOOD, "NORMAL")
            a <= 10 -> MetricStatus(Health.WARN, "SLIGHT $leanRich")
            a <= 20 -> MetricStatus(Health.ELEVATED, leanRich)
            else -> MetricStatus(Health.CRITICAL, "BAD $leanRich")
        }
    }

    fun engineLoad(pct: Double?): MetricStatus = when {
        pct == null -> MetricStatus.NS
        pct <= 60 -> MetricStatus(Health.GOOD, "NORMAL")
        pct <= 85 -> MetricStatus(Health.WARN, "HIGH")
        else -> MetricStatus(Health.CRITICAL, "MAX")
    }

    fun intakeAir(celsius: Double?): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < 10 -> MetricStatus(Health.COLD, "COLD AIR")
        celsius <= 45 -> MetricStatus(Health.GOOD, "NORMAL")
        celsius <= 60 -> MetricStatus(Health.WARN, "WARM")
        else -> MetricStatus(Health.CRITICAL, "HOT AIR")
    }

    fun ambient(celsius: Double?): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < 5 -> MetricStatus(Health.COLD, "COLD")
        celsius <= 45 -> MetricStatus(Health.GOOD, "NORMAL")
        else -> MetricStatus(Health.WARN, "HOT DAY")
    }

    /**
     * MAF at idle is the useful diagnostic band; under load we only flag absurd lows.
     */
    fun maf(gps: Double?, rpm: Double?, speedKmh: Double?): MetricStatus {
        if (gps == null) return MetricStatus.NS
        val idle = (speedKmh ?: 0.0) < 2.0 && (rpm ?: 0.0) in 500.0..1200.0
        return if (idle) {
            when {
                gps in 6.0..10.0 -> MetricStatus(Health.GOOD, "IDLE OK")
                gps in 4.0..5.99 -> MetricStatus(Health.WARN, "LOW IDLE")
                gps < 4.0 -> MetricStatus(Health.CRITICAL, "VERY LOW")
                gps <= 14.0 -> MetricStatus(Health.WARN, "HIGH IDLE")
                else -> MetricStatus(Health.ELEVATED, "HIGH")
            }
        } else {
            when {
                gps < 4.0 -> MetricStatus(Health.CRITICAL, "VERY LOW")
                gps < 8.0 && (speedKmh ?: 0.0) > 40 -> MetricStatus(Health.WARN, "LOW")
                gps <= 120 -> MetricStatus(Health.GOOD, "NORMAL")
                else -> MetricStatus(Health.WARN, "HIGH")
            }
        }
    }

    /**
     * MAP depends on throttle: >90 kPa is normal at high throttle / WOT.
     */
    fun map(kpa: Double?, throttlePct: Double?): MetricStatus {
        if (kpa == null) return MetricStatus.NS
        val thr = throttlePct ?: 0.0
        return when {
            kpa < 20 -> MetricStatus(Health.WARN, "LOW VAC?")
            kpa <= 60 -> MetricStatus(Health.GOOD, "NORMAL")
            kpa <= 90 -> MetricStatus(Health.WARN, "RISING")
            thr >= 70 -> MetricStatus(Health.GOOD, "WOT OK")
            else -> MetricStatus(Health.WARN, "HIGH MAP")
        }
    }

    fun throttle(pct: Double?): MetricStatus = when {
        pct == null -> MetricStatus.NS
        else -> MetricStatus(Health.GOOD, "NORMAL")
    }

    fun timing(degrees: Double?): MetricStatus = when {
        degrees == null -> MetricStatus.NS
        degrees < 0 -> MetricStatus(Health.CRITICAL, "RETARD")
        degrees < 5 -> MetricStatus(Health.WARN, "LOW ADV")
        else -> MetricStatus(Health.GOOD, "NORMAL")
    }

    fun rpm(rpm: Double?): MetricStatus = when {
        rpm == null -> MetricStatus.NS
        rpm <= 0 -> MetricStatus(Health.UNKNOWN, "OFF")
        rpm < 650 -> MetricStatus(Health.WARN, "LOW IDLE")
        rpm <= 750 -> MetricStatus(Health.GOOD, "IDLE")
        rpm <= 4500 -> MetricStatus(Health.GOOD, "NORMAL")
        rpm <= 6000 -> MetricStatus(Health.WARN, "HIGH")
        else -> MetricStatus(Health.CRITICAL, "REDLINE")
    }

    fun speed(kmh: Double?): MetricStatus = when {
        kmh == null -> MetricStatus.NS
        else -> MetricStatus(Health.GOOD, "NORMAL")
    }

    fun atfTemp(celsius: Double?): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < 20 -> MetricStatus(Health.COLD, "COLD")
        celsius <= 65 -> MetricStatus(Health.COLD, "WARMING")
        celsius <= 95 -> MetricStatus(Health.GOOD, "NORMAL")
        celsius <= 105 -> MetricStatus(Health.WARN, "WARM")
        celsius <= 115 -> MetricStatus(Health.ELEVATED, "HOT")
        else -> MetricStatus(Health.CRITICAL, "OVERHEAT")
    }

    fun tcSlip(rpm: Double?): MetricStatus = when {
        rpm == null -> MetricStatus.NS
        rpm <= 40 -> MetricStatus(Health.GOOD, "LOCKED OK")
        rpm <= 100 -> MetricStatus(Health.WARN, "SLIPPING")
        else -> MetricStatus(Health.CRITICAL, "HIGH SLIP")
    }

    fun lockUp(raw: String?): MetricStatus {
        if (raw.isNullOrBlank() || raw.startsWith("n/s")) return MetricStatus.NS
        val u = raw.uppercase()
        return when {
            u.contains("LOCK") && !u.contains("UN") -> MetricStatus(Health.GOOD, "LOCKED")
            u.contains("1") || u.equals("ON", true) || u.contains("TRUE") ->
                MetricStatus(Health.GOOD, "LOCKED")
            u.contains("0") || u.contains("UN") || u.contains("OFF") || u.contains("OPEN") ->
                MetricStatus(Health.UNKNOWN, "UNLOCKED")
            else -> MetricStatus(Health.UNKNOWN, raw.take(10))
        }
    }

    fun dtcCount(count: Int?): MetricStatus = when {
        count == null -> MetricStatus.NS
        count <= 0 -> MetricStatus(Health.GOOD, "CLEAR")
        else -> MetricStatus(Health.CRITICAL, "$count CODE(S)")
    }

    fun fuelSystem(status: String?, coolantC: Double?): MetricStatus {
        if (status.isNullOrBlank()) return MetricStatus.NS
        val closed = status.contains("CLOSED", ignoreCase = true)
        val warm = (coolantC ?: 0.0) >= 70.0
        return when {
            closed -> MetricStatus(Health.GOOD, "CLOSED LOOP")
            warm -> MetricStatus(Health.WARN, "OPEN LOOP")
            else -> MetricStatus(Health.COLD, "OPEN (COLD)")
        }
    }

    /** Best-effort match of Transmission-page labels to evaluators. */
    fun forTransmissionLabel(label: String, valueText: String): MetricStatus? {
        val v = valueText.substringBefore(" ").toDoubleOrNull()
        val l = label.lowercase()
        return when {
            l.contains("atf") || (l.contains("transmission") && l.contains("temp")) ||
                (l.contains("fluid") && l.contains("temp")) -> atfTemp(v)
            l.contains("slip") -> tcSlip(v)
            l.contains("lock") -> lockUp(valueText)
            else -> null
        }
    }

    // --- Back-compat helpers used by HealthScoreCalculator ---

    fun coolantHealth(c: Double?): Health = coolant(c).health
    fun batteryHealth(v: Double?, running: Boolean): Health = battery(v, running).health
    fun fuelTrimHealth(p: Double?): Health = fuelTrim(p).health
    fun atfTempHealth(c: Double?): Health = atfTemp(c).health
}
