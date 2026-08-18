package com.fb2.obd.obd

/**
 * Trip computer: integrates fuel economy from MAF + speed (when fuel-rate PID
 * is unavailable) or from PID 0x5E fuel rate when present.
 */
class TripComputer {
    var distanceKm: Double = 0.0
        private set
    var fuelLiters: Double = 0.0
        private set
    var idleSeconds: Double = 0.0
        private set
    var fuelPricePerLiter: Double = 280.0 // PKR default; editable in settings

    private var lastMs = 0L
    private var lastSpeed = 0.0

    fun reset() {
        distanceKm = 0.0
        fuelLiters = 0.0
        idleSeconds = 0.0
        lastMs = 0L
        lastSpeed = 0.0
    }

    /**
     * @param fuelRateLph optional PID 0x5E; if null, estimates from MAF (gasoline).
     */
    fun onSample(
        tMs: Long,
        speedKmh: Double?,
        mafGps: Double?,
        fuelRateLph: Double? = null,
    ) {
        if (lastMs == 0L) {
            lastMs = tMs
            lastSpeed = speedKmh ?: 0.0
            return
        }
        val dt = (tMs - lastMs) / 1000.0
        if (dt <= 0 || dt > 5) {
            lastMs = tMs
            lastSpeed = speedKmh ?: lastSpeed
            return
        }
        val speed = speedKmh ?: 0.0
        distanceKm += speed * dt / 3600.0
        if (speed < 1.0) idleSeconds += dt

        val litersPerHour = fuelRateLph ?: mafToLph(mafGps)
        if (litersPerHour != null) fuelLiters += litersPerHour * dt / 3600.0

        lastMs = tMs
        lastSpeed = speed
    }

    /** Rough gasoline estimate: AFR 14.7, density ~0.74 kg/L. */
    private fun mafToLph(mafGps: Double?): Double? {
        if (mafGps == null || mafGps <= 0) return null
        val kgPerHour = mafGps * 3.6 / 14.7
        return kgPerHour / 0.74
    }

    val kmPerLiter: Double?
        get() = if (fuelLiters > 0.05) distanceKm / fuelLiters else null

    val litersPer100Km: Double?
        get() = kmPerLiter?.let { if (it > 0) 100.0 / it else null }

    val tripCost: Double
        get() = fuelLiters * fuelPricePerLiter

    val idleFuelLiters: Double
        get() {
            // Approximate idle consumption ~0.8 L/h for 1.8L petrol.
            return idleSeconds / 3600.0 * 0.8
        }
}
