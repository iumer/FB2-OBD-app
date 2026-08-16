package com.fb2.obd.obd

/**
 * Tracks which live fields were freshly decoded this cycle vs sticky last-good.
 *
 * Drive logs showed Speed frozen for ~60–200s while RPM/load/throttle kept
 * moving — classic last-good reuse after Speed PID timeouts. Safety-critical
 * fields (RPM, Speed, Coolant, Battery, MAF, MAP) are cleared after their TTL
 * so the Dash shows `n/s` instead of a lie. Timestamps also drive Torque-style
 * green heartbeat LEDs.
 */
class SnapshotFreshness(
    private val staleAfterMs: Long = STALE_AFTER_MS,
) {
    private val lastOkMs = mutableMapOf<String, Long>()

    fun markOk(key: String, nowMs: Long) {
        lastOkMs[key] = nowMs
    }

    fun markPid(pid: ObdPid, nowMs: Long) {
        markOk(keyFor(pid), nowMs)
    }

    fun lastOk(key: String): Long? = lastOkMs[key]

    /** Copy of all last-success timestamps for UI heartbeat LEDs. */
    fun snapshotMap(): Map<String, Long> = lastOkMs.toMap()

    /**
     * Re-stamp keys that still have non-null values in [snapshot].
     * Used after deep-search pause so sanitize does not blank retained fields
     * before the first post-resume poll can refresh them.
     */
    fun remakePresent(snapshot: VehicleSnapshot, nowMs: Long) {
        fun touch(key: String, present: Boolean) {
            if (present) markOk(key, nowMs)
        }
        touch(KEY_RPM, snapshot.rpm != null)
        touch(KEY_SPEED, snapshot.speedKmh != null)
        touch(KEY_COOLANT, snapshot.coolantC != null)
        touch(KEY_COOLANT2, snapshot.coolant2C != null)
        touch(KEY_INTAKE, snapshot.intakeC != null)
        touch(KEY_AMBIENT, snapshot.ambientC != null)
        touch(KEY_LOAD, snapshot.engineLoadPct != null)
        touch(KEY_THROTTLE, snapshot.throttlePct != null)
        touch(KEY_TIMING, snapshot.timingAdvance != null)
        touch(KEY_MAF, snapshot.mafGps != null)
        touch(KEY_MAP, snapshot.mapKpa != null)
        touch(KEY_STFT, snapshot.stftPct != null)
        touch(KEY_LTFT, snapshot.ltftPct != null)
        touch(KEY_BATTERY, snapshot.batteryVolts != null)
        touch(KEY_FUEL_LOOP, !snapshot.fuelSystemStatus.isNullOrBlank())
    }

    /** Re-stamp only keys successfully decoded earlier in this same poll cycle. */
    fun restamp(keys: Set<String>, nowMs: Long) {
        keys.forEach { markOk(it, nowMs) }
    }
    /**
     * Apply stale rules to [snapshot] before UI/log emit.
     * [rpmUpdatedThisCycle] true when ENGINE_RPM decoded successfully this cycle.
     */
    fun sanitize(
        snapshot: VehicleSnapshot,
        nowMs: Long,
        rpmUpdatedThisCycle: Boolean,
    ): VehicleSnapshot {
        var out = snapshot

        fun isStale(key: String, ttl: Long = staleAfterMs): Boolean {
            val okAt = lastOkMs[key] ?: return false
            return nowMs - okAt > ttl
        }

        fun clearKey(key: String) {
            lastOkMs.remove(key)
        }

        // Speed: clear when stale even if the rest of the bus looks dead —
        // otherwise a lone frozen km/h can linger after a full Mode 01 drop.
        if (isStale(KEY_SPEED)) {
            clearKey(KEY_SPEED)
            out = out.copy(
                speedKmh = null,
                gear = null,
                gearSource = GearSource.NONE,
                gearConfidencePct = null,
            )
        }

        // RPM: sticky RPM + live Speed invents wrong gears on long hauls.
        if (isStale(KEY_RPM) && (out.speedKmh != null || out.coolantC != null || out.batteryVolts != null)) {
            clearKey(KEY_RPM)
            out = out.copy(
                rpm = null,
                gear = null,
                gearSource = GearSource.NONE,
                gearConfidencePct = null,
            )
        }

        // Coolant / Battery / MAF / MAP: never show hours-old "OK" numbers.
        // Coolant + MAF are always-polled; still allow a slightly longer TTL so a
        // single slow ELM cycle (ATRV + heroes + secondaries) cannot blank them.
        if (isStale(KEY_COOLANT, ttl = COOLANT_STALE_AFTER_MS)) {
            clearKey(KEY_COOLANT)
            out = out.copy(coolantC = null)
        }
        if (isStale(KEY_BATTERY, ttl = BATTERY_STALE_AFTER_MS)) {
            clearKey(KEY_BATTERY)
            out = out.copy(batteryVolts = null)
        }
        if (isStale(KEY_MAF, ttl = MAF_STALE_AFTER_MS)) {
            clearKey(KEY_MAF)
            out = out.copy(mafGps = null)
        }
        if (isStale(KEY_MAP)) {
            clearKey(KEY_MAP)
            out = out.copy(mapKpa = null)
        }

        // Intake / Throttle rotate as secondaries. Without TTL they freeze forever
        // after one decode (2026-08-14 drive: Intake=60°C and Throttle=13.7% flat
        // for ~56 min while RPM/Speed/MAF moved). Clear like MAP.
        if (isStale(KEY_INTAKE)) {
            clearKey(KEY_INTAKE)
            out = out.copy(intakeC = null)
        }
        if (isStale(KEY_THROTTLE)) {
            clearKey(KEY_THROTTLE)
            out = out.copy(throttlePct = null)
        }

        // Gear estimate needs both RPM and Speed fresh in this window.
        val rpmFresh = lastOkMs[KEY_RPM]?.let { nowMs - it <= staleAfterMs } == true
        val speedFresh = lastOkMs[KEY_SPEED]?.let { nowMs - it <= staleAfterMs } == true
        if (out.gearSource == GearSource.ESTIMATED && !(rpmFresh && speedFresh)) {
            out = out.copy(
                gear = null,
                gearSource = GearSource.NONE,
                gearConfidencePct = null,
            )
        }

        return out.copy(freshAtMs = snapshotMap())
    }

    fun reset() = lastOkMs.clear()

    companion object {
        const val KEY_RPM = "rpm"
        const val KEY_SPEED = "speed"
        const val KEY_COOLANT = "coolant"
        const val KEY_COOLANT2 = "coolant2"
        const val KEY_INTAKE = "intake"
        const val KEY_AMBIENT = "ambient"
        const val KEY_LOAD = "load"
        const val KEY_THROTTLE = "throttle"
        const val KEY_TIMING = "timing"
        const val KEY_MAF = "maf"
        const val KEY_MAP = "map"
        const val KEY_STFT = "stft"
        const val KEY_LTFT = "ltft"
        const val KEY_FUEL_LOOP = "fuel_loop"
        const val KEY_BATTERY = "battery"
        const val KEY_GEAR_RATIO = "gear_ratio"

        /** ~2–3 slow ELM cycles; short enough to avoid 65-vs-98 freezes. */
        const val STALE_AFTER_MS = 2_500L

        /** Coolant is always-polled but cycles can stretch on cheap clones. */
        const val COOLANT_STALE_AFTER_MS = 5_000L

        /** MAF is always-polled; same longer TTL as Coolant. */
        const val MAF_STALE_AFTER_MS = 5_000L

        /** ATRV may skip odd cycles — allow a bit longer before blanking volts. */
        const val BATTERY_STALE_AFTER_MS = 4_000L

        /** LED stays lit (dim) while fresher than this; then goes dark. */
        const val LED_ACTIVE_MS = 1_800L

        fun keyFor(pid: ObdPid): String = when (pid) {
            ObdPid.ENGINE_RPM -> KEY_RPM
            ObdPid.SPEED -> KEY_SPEED
            ObdPid.COOLANT_TEMP -> KEY_COOLANT
            ObdPid.COOLANT_TEMP_2 -> KEY_COOLANT2
            ObdPid.INTAKE_TEMP -> KEY_INTAKE
            ObdPid.AMBIENT_TEMP -> KEY_AMBIENT
            ObdPid.ENGINE_LOAD -> KEY_LOAD
            ObdPid.THROTTLE -> KEY_THROTTLE
            ObdPid.TIMING_ADVANCE -> KEY_TIMING
            ObdPid.MAF -> KEY_MAF
            ObdPid.INTAKE_MAP -> KEY_MAP
            ObdPid.STFT_B1 -> KEY_STFT
            ObdPid.LTFT_B1 -> KEY_LTFT
            ObdPid.FUEL_SYSTEM_STATUS -> KEY_FUEL_LOOP
            ObdPid.CONTROL_MODULE_VOLTAGE -> KEY_BATTERY
            ObdPid.TRANSMISSION_GEAR_RATIO -> KEY_GEAR_RATIO
        }

        /** Map a Dash tile label to the freshness key (null = no heartbeat). */
        fun keyForTileLabel(label: String): String? {
            val l = label.lowercase().trim()
            return when {
                l == "rpm" || l.startsWith("rpm") -> KEY_RPM
                l == "speed" || l.startsWith("speed") -> KEY_SPEED
                l.startsWith("coolant 1") || l == "coolant" -> KEY_COOLANT
                l.startsWith("coolant 2") -> KEY_COOLANT2
                l.startsWith("battery") || l.contains("ecu v") ||
                    l.contains("control module voltage") -> KEY_BATTERY
                l.startsWith("intake") -> KEY_INTAKE
                l.startsWith("ambient") -> KEY_AMBIENT
                l == "load" || l.contains("engine load") -> KEY_LOAD
                l.startsWith("throttle") -> KEY_THROTTLE
                l == "stft" || l.contains("short term") -> KEY_STFT
                l == "ltft" || l.contains("long term") -> KEY_LTFT
                l == "maf" -> KEY_MAF
                l == "map" -> KEY_MAP
                l == "timing" || l.contains("ignition") -> KEY_TIMING
                l.startsWith("fuel loop") || l.contains("fuel system") -> KEY_FUEL_LOOP
                else -> null
            }
        }

        /** Demo / synthetic frames: every non-null Dash field is "fresh" this tick. */
        fun mapForPresentFields(snapshot: VehicleSnapshot, nowMs: Long): Map<String, Long> =
            buildMap {
                if (snapshot.rpm != null) put(KEY_RPM, nowMs)
                if (snapshot.speedKmh != null) put(KEY_SPEED, nowMs)
                if (snapshot.coolantC != null) put(KEY_COOLANT, nowMs)
                if (snapshot.coolant2C != null) put(KEY_COOLANT2, nowMs)
                if (snapshot.intakeC != null) put(KEY_INTAKE, nowMs)
                if (snapshot.ambientC != null) put(KEY_AMBIENT, nowMs)
                if (snapshot.engineLoadPct != null) put(KEY_LOAD, nowMs)
                if (snapshot.throttlePct != null) put(KEY_THROTTLE, nowMs)
                if (snapshot.timingAdvance != null) put(KEY_TIMING, nowMs)
                if (snapshot.mafGps != null) put(KEY_MAF, nowMs)
                if (snapshot.mapKpa != null) put(KEY_MAP, nowMs)
                if (snapshot.stftPct != null) put(KEY_STFT, nowMs)
                if (snapshot.ltftPct != null) put(KEY_LTFT, nowMs)
                if (snapshot.batteryVolts != null) put(KEY_BATTERY, nowMs)
                if (snapshot.fuelSystemStatus != null) put(KEY_FUEL_LOOP, nowMs)
                if (snapshot.gearRatioActual != null) put(KEY_GEAR_RATIO, nowMs)
            }
    }
}
