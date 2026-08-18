package com.fb2.obd.obd

/**
 * How the ELM Mode 01 poll loop should behave while another job needs the
 * RFCOMM socket (deep search, exclusive ATSH).
 *
 * A full pause plus freshness TTL blanks the Dash. [HEROES_ONLY] keeps
 * RPM/Speed/Coolant/MAF/ATRV moving while deep search walks header strategies.
 */
enum class PollHold {
    /** Full Dash cycle (heroes + rotating secondaries). */
    NONE,

    /** Heroes only — deep search borrows the link between exclusive strategies. */
    HEROES_ONLY,

    /** Do not send Mode 01; emit last-good with [SnapshotFreshness.sanitize] holdValues. */
    FULL_PAUSE,
}
