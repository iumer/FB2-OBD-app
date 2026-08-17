package com.fb2.obd.obd

/**
 * How the ELM Mode 01 poll loop should behave while another job needs the
 * RFCOMM socket (deep search, Faults Read, VIN, page probes).
 *
 * Cheap clones are single-threaded: a full pause plus SnapshotFreshness TTL
 * blanks the Dash (values appear, vanish, then come back). Heroes-only keeps
 * RPM/Speed/Coolant/MAF/ATRV moving; exclusive is only for one ATSH strategy
 * or one Mode 03/07/0A/09 command.
 */
enum class PollHold {
    /** Full Dash cycle (heroes + rotating secondaries + 1 background probe). */
    NONE,

    /**
     * Keep the driving heroes alive (ATRV + RPM + Speed + Coolant + MAF) while
     * deep search walks header strategies. Secondaries and picker probes wait.
     */
    HEROES_ONLY,

    /**
     * Do not send Mode 01 at all. Emit the last snapshot with [holdValues] so
     * TTL cannot blank the Dash. Prefer [HEROES_ONLY] for user-visible work.
     */
    FULL_PAUSE,
}
