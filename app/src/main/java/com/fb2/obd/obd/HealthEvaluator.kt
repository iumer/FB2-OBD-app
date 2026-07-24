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
 * Maps raw sensor values to health + status labels.
 * Pass [HealthThresholds] so the user can retune bands from a long-press editor.
 */
object HealthEvaluator {

    fun coolant(celsius: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < t.coolantColdBelow -> MetricStatus(Health.COLD, "COLD")
        celsius <= t.coolantGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
        celsius <= t.coolantWarnMax -> MetricStatus(Health.WARN, "WARM")
        celsius <= t.coolantElevatedMax -> MetricStatus(Health.ELEVATED, "HOT")
        else -> MetricStatus(Health.CRITICAL, "OVERHEAT")
    }

    fun battery(
        volts: Double?,
        engineRunning: Boolean = true,
        t: HealthThresholds = HealthThresholds.DEFAULT,
    ): MetricStatus = when {
        volts == null -> MetricStatus.NS
        engineRunning -> when {
            volts > t.battRunCriticalAbove -> MetricStatus(Health.CRITICAL, "OVERCHARGE")
            volts >= t.battRunGoodMin && volts <= t.battRunGoodMax ->
                MetricStatus(Health.GOOD, "CHARGING OK")
            volts >= t.battRunWarnMin && volts < t.battRunGoodMin ->
                MetricStatus(Health.WARN, "LOW CHARGE")
            volts < t.battRunWarnMin -> MetricStatus(Health.CRITICAL, "ALT WEAK")
            else -> MetricStatus(Health.WARN, "HIGH CHARGE")
        }
        else -> when {
            volts > t.battRestGoodAbove -> MetricStatus(Health.GOOD, "RESTING OK")
            volts >= t.battRestWarnAbove -> MetricStatus(Health.WARN, "WEAK REST")
            else -> MetricStatus(Health.CRITICAL, "FLAT")
        }
    }

    fun fuelTrim(percent: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus {
        if (percent == null) return MetricStatus.NS
        val a = abs(percent)
        val leanRich = if (percent >= 0) "LEAN" else "RICH"
        return when {
            a <= t.trimGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
            a <= t.trimWarnMax -> MetricStatus(Health.WARN, "SLIGHT $leanRich")
            a <= t.trimElevatedMax -> MetricStatus(Health.ELEVATED, leanRich)
            else -> MetricStatus(Health.CRITICAL, "BAD $leanRich")
        }
    }

    fun engineLoad(pct: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        pct == null -> MetricStatus.NS
        pct <= t.loadGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
        pct <= t.loadWarnMax -> MetricStatus(Health.WARN, "HIGH")
        else -> MetricStatus(Health.CRITICAL, "MAX")
    }

    fun intakeAir(celsius: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < t.intakeColdBelow -> MetricStatus(Health.COLD, "COLD AIR")
        celsius <= t.intakeGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
        celsius <= t.intakeWarnMax -> MetricStatus(Health.WARN, "WARM")
        else -> MetricStatus(Health.CRITICAL, "HOT AIR")
    }

    fun ambient(celsius: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < t.ambientColdBelow -> MetricStatus(Health.COLD, "COLD")
        celsius <= t.ambientGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
        else -> MetricStatus(Health.WARN, "HOT DAY")
    }

    fun maf(
        gps: Double?,
        rpm: Double?,
        speedKmh: Double?,
        t: HealthThresholds = HealthThresholds.DEFAULT,
    ): MetricStatus {
        if (gps == null) return MetricStatus.NS
        val idle = (speedKmh ?: 0.0) < 2.0 && (rpm ?: 0.0) in 500.0..1200.0
        return if (idle) {
            when {
                gps in t.mafIdleGoodMin..t.mafIdleGoodMax -> MetricStatus(Health.GOOD, "IDLE OK")
                gps >= t.mafIdleWarnMin && gps < t.mafIdleGoodMin -> MetricStatus(Health.WARN, "LOW IDLE")
                gps < t.mafIdleWarnMin -> MetricStatus(Health.CRITICAL, "VERY LOW")
                gps <= t.mafIdleGoodMax + 4 -> MetricStatus(Health.WARN, "HIGH IDLE")
                else -> MetricStatus(Health.ELEVATED, "HIGH")
            }
        } else {
            when {
                gps < t.mafIdleWarnMin -> MetricStatus(Health.CRITICAL, "VERY LOW")
                gps < t.mafIdleGoodMin && (speedKmh ?: 0.0) > 40 -> MetricStatus(Health.WARN, "LOW")
                gps <= 120 -> MetricStatus(Health.GOOD, "NORMAL")
                else -> MetricStatus(Health.WARN, "HIGH")
            }
        }
    }

    fun map(
        kpa: Double?,
        throttlePct: Double?,
        t: HealthThresholds = HealthThresholds.DEFAULT,
    ): MetricStatus {
        if (kpa == null) return MetricStatus.NS
        val thr = throttlePct ?: 0.0
        return when {
            kpa < 20 -> MetricStatus(Health.WARN, "LOW VAC?")
            kpa <= t.mapGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
            kpa <= t.mapWarnMax -> MetricStatus(Health.WARN, "RISING")
            thr >= t.mapWotThrottleMin -> MetricStatus(Health.GOOD, "WOT OK")
            else -> MetricStatus(Health.WARN, "HIGH MAP")
        }
    }

    fun throttle(pct: Double?): MetricStatus = when {
        pct == null -> MetricStatus.NS
        else -> MetricStatus(Health.GOOD, "NORMAL")
    }

    fun timing(degrees: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        degrees == null -> MetricStatus.NS
        degrees < t.timingRetardBelow -> MetricStatus(Health.CRITICAL, "RETARD")
        degrees < t.timingLowBelow -> MetricStatus(Health.WARN, "LOW ADV")
        else -> MetricStatus(Health.GOOD, "NORMAL")
    }

    fun rpm(rpm: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        rpm == null -> MetricStatus.NS
        rpm <= 0 -> MetricStatus(Health.UNKNOWN, "OFF")
        rpm < t.rpmIdleLow -> MetricStatus(Health.WARN, "LOW IDLE")
        rpm <= t.rpmIdleHigh -> MetricStatus(Health.GOOD, "IDLE")
        rpm <= t.rpmNormalMax -> MetricStatus(Health.GOOD, "NORMAL")
        rpm <= t.rpmHighMax -> MetricStatus(Health.WARN, "HIGH")
        else -> MetricStatus(Health.CRITICAL, "REDLINE")
    }

    fun speed(kmh: Double?): MetricStatus = when {
        kmh == null -> MetricStatus.NS
        else -> MetricStatus(Health.GOOD, "NORMAL")
    }

    fun atfTemp(celsius: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        celsius == null -> MetricStatus.NS
        celsius < 20 -> MetricStatus(Health.COLD, "COLD")
        celsius <= t.atfColdMax -> MetricStatus(Health.COLD, "WARMING")
        celsius <= t.atfGoodMax -> MetricStatus(Health.GOOD, "NORMAL")
        celsius <= t.atfWarnMax -> MetricStatus(Health.WARN, "WARM")
        celsius <= t.atfElevatedMax -> MetricStatus(Health.ELEVATED, "HOT")
        else -> MetricStatus(Health.CRITICAL, "OVERHEAT")
    }

    fun tcSlip(rpm: Double?, t: HealthThresholds = HealthThresholds.DEFAULT): MetricStatus = when {
        rpm == null -> MetricStatus.NS
        rpm <= t.slipGoodMax -> MetricStatus(Health.GOOD, "LOCKED OK")
        rpm <= t.slipWarnMax -> MetricStatus(Health.WARN, "SLIPPING")
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

    fun forTransmissionLabel(
        label: String,
        valueText: String,
        t: HealthThresholds = HealthThresholds.DEFAULT,
    ): MetricStatus? {
        val v = valueText.substringBefore(" ").toDoubleOrNull()
        val l = label.lowercase()
        return when {
            l.contains("atf") || (l.contains("transmission") && l.contains("temp")) ||
                (l.contains("fluid") && l.contains("temp")) -> atfTemp(v, t)
            l.contains("slip") -> tcSlip(v, t)
            l.contains("lock") -> lockUp(valueText)
            else -> null
        }
    }
}
