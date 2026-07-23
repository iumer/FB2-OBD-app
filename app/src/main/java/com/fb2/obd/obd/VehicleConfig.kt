package com.fb2.obd.obd

/**
 * Drivetrain constants for the target car (Honda Civic FB2, 1.8L, 5-speed
 * automatic). Values are used to estimate the current gear from wheel speed and
 * engine RPM when the TCM does not expose a direct gear PID.
 *
 * Ratios are editable in one place so the estimator can be tuned per car.
 */
object VehicleConfig {
    /** Forward gear ratios, index 0 == 1st gear. */
    val gearRatios = doubleArrayOf(2.995, 1.678, 1.066, 0.760, 0.512)

    /** Final drive ratio. */
    const val finalDrive = 4.438

    /** Rolling circumference in metres for 195/65 R15 (~0.634 m diameter). */
    const val tireCircumferenceMeters = 1.993

    /** Below this speed (km/h) gear estimation is unreliable (idle / stopped). */
    const val minSpeedForGearKmh = 5.0
}
