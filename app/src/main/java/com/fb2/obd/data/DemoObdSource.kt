package com.fb2.obd.data

import com.fb2.obd.obd.GearEstimator
import com.fb2.obd.obd.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simulated ELM327 feed. Produces a believable driving cycle (accelerate, cruise,
 * decelerate) so the full dashboard can be exercised without a car or adapter —
 * useful for development, demos and UI review in the cloud.
 */
class DemoObdSource(
    private val gearEstimator: GearEstimator = GearEstimator(),
) : ObdSource {

    override val name: String = "Demo (simulated)"

    override fun snapshots(): Flow<VehicleSnapshot> = flow {
        var t = 0.0
        var coolant = 40.0 // cold start, warms toward ~92C
        while (true) {
            // Speed follows a smooth accelerate/cruise/brake wave 0..120 km/h.
            val phase = sin(t / 12.0)
            val speed = (60.0 + 60.0 * phase).coerceIn(0.0, 120.0)

            // RPM tracks speed with some load-based variation.
            val accelerating = sin(t / 12.0 + 0.3) - phase > 0
            val baseRpm = 900.0 + speed * 22.0
            val rpm = (baseRpm + (if (accelerating) 700.0 else 0.0)).coerceIn(750.0, 6500.0)

            coolant = (coolant + 0.6).coerceAtMost(92.0)
            // Post-thermostat sensor stays cool until the thermostat opens (~82C),
            // then tracks a few degrees below the main sensor.
            val coolant2 = if (coolant < 82.0) 30.0 + (coolant - 40.0).coerceAtLeast(0.0) * 0.25
            else coolant - 3.0

            val throttle = (10.0 + 40.0 * (0.5 + 0.5 * phase)).coerceIn(0.0, 100.0)
            val load = (15.0 + 55.0 * (0.5 + 0.5 * phase)).coerceIn(0.0, 100.0)

            val snapshot = VehicleSnapshot(
                rpm = rpm.roundToInt().toDouble(),
                speedKmh = speed.roundToInt().toDouble(),
                coolantC = coolant.roundToInt().toDouble(),
                coolant2C = coolant2.roundToInt().toDouble(),
                intakeC = 32.0,
                ambientC = 28.0,
                engineLoadPct = load,
                throttlePct = throttle,
                timingAdvance = 12.0,
                mafGps = 4.0 + load / 5.0,
                mapKpa = 30.0 + load,
                stftPct = 2.0 * sin(t / 5.0),
                ltftPct = 3.5,
                batteryVolts = 14.2 + 0.1 * sin(t / 7.0),
                gear = gearEstimator.estimate(speed, rpm),
            )
            emit(snapshot)
            t += 1.0
            delay(250L)
        }
    }
}
