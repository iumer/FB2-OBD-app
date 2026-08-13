package com.fb2.obd.obd

/**
 * Tracks which live fields were freshly decoded this cycle vs sticky last-good.
 *
 * Drive logs showed Speed frozen for ~60–200s while RPM/load/throttle kept
 * moving — classic last-good reuse after Speed PID timeouts. When a hero
 * field goes longer than [staleAfterMs] without a successful decode while
 * RPM is still updating, clear it so the Dash shows `n/s` instead of a lie.
 */
class SnapshotFreshness(
    private val staleAfterMs: Long = STALE_AFTER_MS,
) {
    private val lastOkMs = mutableMapOf<String, Long>()

    fun markOk(key: String, nowMs: Long) {
        lastOkMs[key] = nowMs
    }

    fun lastOk(key: String): Long? = lastOkMs[key]

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
        val speedOkAt = lastOkMs[KEY_SPEED]
        if (speedOkAt != null && nowMs - speedOkAt > staleAfterMs) {
            // Only blank speed when the bus is otherwise alive (RPM still moving).
            if (rpmUpdatedThisCycle || snapshot.rpm != null) {
                out = out.copy(
                    speedKmh = null,
                    // Gear from stale speed is worse than showing no gear.
                    gear = null,
                    gearSource = GearSource.NONE,
                    gearConfidencePct = null,
                )
            }
        }
        return out
    }

    fun reset() = lastOkMs.clear()

    companion object {
        const val KEY_RPM = "rpm"
        const val KEY_SPEED = "speed"
        /** ~2–3 slow ELM cycles; short enough to avoid 65-vs-98 freezes. */
        const val STALE_AFTER_MS = 2_500L
    }
}
