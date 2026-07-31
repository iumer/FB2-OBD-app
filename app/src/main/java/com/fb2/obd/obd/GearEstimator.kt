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
    fun estimate(speedKmh: Double, rpm: Double): Int? = estimateDetailed(speedKmh, rpm)?.gear

    data class GearEstimate(val gear: Int, val confidencePct: Int)

    /**
     * Same as [estimate] plus a rough confidence (0–99) from how uniquely the
     * speed/RPM match one FB2 gear ratio.
     */
    fun estimateDetailed(speedKmh: Double, rpm: Double): GearEstimate? {
        if (speedKmh < config.minSpeedForGearKmh || rpm <= 0.0) return null

        val wheelRevPerSec = (speedKmh * 1000.0 / 3600.0) / config.tireCircumferenceMeters

        var bestGear = 1
        var bestError = Double.MAX_VALUE
        var secondError = Double.MAX_VALUE
        config.gearRatios.forEachIndexed { index, ratio ->
            val predictedRpm = wheelRevPerSec * ratio * config.finalDrive * 60.0
            val error = abs(predictedRpm - rpm)
            if (error < bestError) {
                secondError = bestError
                bestError = error
                bestGear = index + 1
            } else if (error < secondError) {
                secondError = error
            }
        }
        val confidence = when {
            bestError < 40 -> 98
            bestError < 80 -> 92
            bestError < 150 -> 80
            bestError < 250 -> 65
            else -> 50
        }.let { base ->
            if (secondError.isFinite() && secondError > 0) {
                val separation = ((secondError - bestError) / secondError * 20).toInt()
                (base + separation).coerceIn(40, 99)
            } else {
                base
            }
        }
        return GearEstimate(bestGear, confidence)
    }

    /**
     * Maps an actual transmission gear ratio (from ECU PID 0xA4) to the nearest
     * forward gear number. More reliable than [estimate] because it uses the
     * ratio the TCM reports rather than inferring from speed/RPM.
     *
     * @return gear number (1-based) or null if the ratio is implausible.
     */
    fun gearFromRatio(ratio: Double): Int? {
        if (ratio <= 0.0) return null
        var bestGear = 1
        var bestError = Double.MAX_VALUE
        config.gearRatios.forEachIndexed { index, gearRatio ->
            val error = abs(gearRatio - ratio)
            if (error < bestError) {
                bestError = error
                bestGear = index + 1
            }
        }
        // Reject values that don't resemble any forward gear (e.g. neutral/park).
        return if (bestError <= 0.6) bestGear else null
    }
}
