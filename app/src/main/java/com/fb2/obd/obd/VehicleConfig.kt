package com.fb2.obd.obd

/**
 * Drivetrain constants for the target car (Honda Civic FB2, 1.8L, 5-speed
 * automatic). Values are used to estimate the current gear from wheel speed and
 * engine RPM when the TCM does not expose a direct gear PID.
 *
 * Ratios are editable in one place so the estimator can be tuned per car.
 */
object VehicleConfig {
    /**
     * Forward gear ratios (index 0 == 1st gear) for the FB2 "Compact 5-speed
     * automatic" (torque-converter auto with a D / D3 / 2 / 1 gate — not a CVT).
     * Source: Honda 2012/2013 Civic 1.8 sedan specs.
     */
    val gearRatios = doubleArrayOf(2.666, 1.534, 1.022, 0.721, 0.525)

    /** Final drive ratio for the 5-speed automatic. */
    const val finalDrive = 4.44

    /** Rolling circumference in metres for 195/65 R15 (~0.634 m diameter). */
    const val tireCircumferenceMeters = 1.993

    /** Below this speed (km/h) gear estimation is unreliable (idle / stopped). */
    const val minSpeedForGearKmh = 5.0
}
