package com.fb2.obd.obd

import kotlin.math.abs

/**
 * Estimates the current forward gear from vehicle speed and engine RPM using the
 * known gear/final-drive ratios and tyre circumference.
 *
 * For each candidate gear it predicts the engine RPM that speed would produce,
 * then picks the gear whose prediction is closest to the measured RPM. Pure
 * Kotlin so it can be unit-tested without hardware.
 */
class GearEstimator(private val config: VehicleConfig = VehicleConfig) {

    /**
     * @return gear number (1-based) or null when stopped / too slow to tell.
     */
    fun estimate(speedKmh: Double, rpm: Double): Int? {
        if (speedKmh < config.minSpeedForGearKmh || rpm <= 0.0) return null

        val wheelRevPerSec = (speedKmh * 1000.0 / 3600.0) / config.tireCircumferenceMeters

        var bestGear = 1
        var bestError = Double.MAX_VALUE
        config.gearRatios.forEachIndexed { index, ratio ->
            val predictedRpm = wheelRevPerSec * ratio * config.finalDrive * 60.0
            val error = abs(predictedRpm - rpm)
            if (error < bestError) {
                bestError = error
                bestGear = index + 1
            }
        }
        return bestGear
    }
}
