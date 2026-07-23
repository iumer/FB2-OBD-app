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

    fun battery(volts: Double?): Health = when {
        volts == null -> Health.UNKNOWN
        volts >= 15.0 -> Health.CRITICAL   // over-charging
        volts < 12.4 -> Health.CRITICAL
        volts < 13.2 -> Health.WARN
        else -> Health.GOOD
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
