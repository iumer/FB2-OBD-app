package com.fb2.obd.obd

/**
 * A single instant of decoded vehicle data driving the dashboard. All fields are
 * nullable because an ECU may not support (or may not have answered for) a given
 * PID yet.
 */
data class VehicleSnapshot(
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantC: Double? = null,
    val coolant2C: Double? = null,
    val intakeC: Double? = null,
    val ambientC: Double? = null,
    val engineLoadPct: Double? = null,
    val throttlePct: Double? = null,
    val timingAdvance: Double? = null,
    val mafGps: Double? = null,
    val mapKpa: Double? = null,
    val stftPct: Double? = null,
    val ltftPct: Double? = null,
    val batteryVolts: Double? = null,
    val gear: Int? = null,
    val gearSource: GearSource = GearSource.NONE,
    /** 0–99 confidence when [gearSource] is ESTIMATED; null for ECU / none. */
    val gearConfidencePct: Int? = null,
    /** Actual transmission gear ratio from ECU PID 0xA4, if available. */
    val gearRatioActual: Double? = null,
    /** PID numbers the ECU reported as NOT supported (for "n/s" tiles). */
    val unsupportedPids: Set<Int> = emptySet(),
) {
    companion object {
        val EMPTY = VehicleSnapshot()
    }
}
