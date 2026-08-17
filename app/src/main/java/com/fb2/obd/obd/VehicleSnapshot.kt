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
    /** Decoded PID 0103 text, e.g. CLOSED LOOP / OPEN LOOP. */
    val fuelSystemStatus: String? = null,
    val gear: Int? = null,
    val gearSource: GearSource = GearSource.NONE,
    /** 0–99 confidence when [gearSource] is ESTIMATED; null for ECU / none. */
    val gearConfidencePct: Int? = null,
    /** Actual transmission gear ratio from ECU PID 0xA4, if available. */
    val gearRatioActual: Double? = null,
    /** PID numbers the ECU reported as NOT supported (for "n/s" tiles). */
    val unsupportedPids: Set<Int> = emptySet(),
    /**
     * Wall-clock ms of the last successful decode per Dash field key
     * ([SnapshotFreshness] keys). Drives Torque-style green heartbeat LEDs.
     */
    val freshAtMs: Map<String, Long> = emptyMap(),
) {
    companion object {
        val EMPTY = VehicleSnapshot()
    }
}

/** True when a frame has no usable drive sensors (typical blank reconnect frame).
 *  ATRV/battery alone does NOT count — soft-recover often keeps volts while
 *  Mode 01 is dead; treating that as "content" wiped last-good heroes mid-drive.
 */
fun VehicleSnapshot.isEffectivelyBlank(): Boolean =
    rpm == null && speedKmh == null && coolantC == null && mafGps == null &&
        mapKpa == null && throttlePct == null && stftPct == null &&
        intakeC == null && engineLoadPct == null

/**
 * Per-field last-good merge for partial ELM frames (e.g. ATRV-only or one secondary
 * decoded while heroes are still in [prev]). Never overwrites a live hero with null.
 */
fun VehicleSnapshot.mergeLastGood(incoming: VehicleSnapshot): VehicleSnapshot =
    copy(
        rpm = incoming.rpm ?: rpm,
        speedKmh = incoming.speedKmh ?: speedKmh,
        coolantC = incoming.coolantC ?: coolantC,
        coolant2C = incoming.coolant2C ?: coolant2C,
        intakeC = incoming.intakeC ?: intakeC,
        ambientC = incoming.ambientC ?: ambientC,
        engineLoadPct = incoming.engineLoadPct ?: engineLoadPct,
        throttlePct = incoming.throttlePct ?: throttlePct,
        timingAdvance = incoming.timingAdvance ?: timingAdvance,
        mafGps = incoming.mafGps ?: mafGps,
        mapKpa = incoming.mapKpa ?: mapKpa,
        stftPct = incoming.stftPct ?: stftPct,
        ltftPct = incoming.ltftPct ?: ltftPct,
        batteryVolts = incoming.batteryVolts ?: batteryVolts,
        fuelSystemStatus = incoming.fuelSystemStatus ?: fuelSystemStatus,
        gear = incoming.gear ?: gear,
        gearSource = if (incoming.gearSource != GearSource.NONE) incoming.gearSource else gearSource,
        gearConfidencePct = incoming.gearConfidencePct ?: gearConfidencePct,
        gearRatioActual = incoming.gearRatioActual ?: gearRatioActual,
        unsupportedPids = if (incoming.unsupportedPids.isNotEmpty()) incoming.unsupportedPids else unsupportedPids,
        freshAtMs = incoming.freshAtMs + freshAtMs.filterKeys { it !in incoming.freshAtMs },
    )
