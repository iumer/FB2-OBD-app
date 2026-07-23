package com.fb2.obd.obd

import kotlin.math.abs

/** Traffic-light status for a monitored metric. */
enum class Health { GOOD, WARN, CRITICAL, UNKNOWN }

/**
 * Maps raw sensor values to a GOOD / WARN / CRITICAL status, mirroring the
 * thresholds used while diagnosing the FB2 (coolant, charging voltage, fuel
 * trims, ATF temperature). Pure Kotlin, fully unit-testable.
 */
object HealthEvaluator {

    fun coolant(celsius: Double?): Health = when {
        celsius == null -> Health.UNKNOWN
        celsius >= 108 -> Health.CRITICAL
        celsius >= 100 -> Health.WARN
        else -> Health.GOOD
    }

    /**
     * Battery / charging-system health. Thresholds depend on whether the engine
     * is running: with the alternator charging we expect ~13.5–14.8 V, whereas a
     * healthy resting battery (engine off / key-on) sits around 12.4–12.8 V, so a
     * resting 12.5 V should not be flagged red.
     */
    fun battery(volts: Double?, engineRunning: Boolean = true): Health = when {
        volts == null -> Health.UNKNOWN
        engineRunning -> when {
            volts >= 15.0 -> Health.CRITICAL   // over-charging
            volts < 12.4 -> Health.CRITICAL
            volts < 13.2 -> Health.WARN
            else -> Health.GOOD
        }
        else -> when {                          // engine off / resting
            volts < 11.8 -> Health.CRITICAL
            volts < 12.4 -> Health.WARN
            else -> Health.GOOD
        }
    }

    fun fuelTrim(percent: Double?): Health = when {
        percent == null -> Health.UNKNOWN
        abs(percent) > 10 -> Health.CRITICAL
        abs(percent) > 5 -> Health.WARN
        else -> Health.GOOD
    }

    fun atfTemp(celsius: Double?): Health = when {
        celsius == null -> Health.UNKNOWN
        celsius > 110 -> Health.CRITICAL
        celsius > 95 -> Health.WARN
        celsius < 70 -> Health.WARN        // not yet warmed up
        else -> Health.GOOD
    }
}
