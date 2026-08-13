package com.fb2.obd.obd

/**
 * Phone Dash presentation theme. [CLASSIC] is the existing tile grid + hero strip.
 * Opt A/B/C are presentation-only — same snapshot, health, alerts, MIN, AI, etc.
 */
enum class DashTheme(
    val id: String,
    val displayName: String,
    val subtitle: String,
) {
    CLASSIC(
        id = "classic",
        displayName = "Classic",
        subtitle = "Original multi-page Dash (Idle / Perf / Trip tabs).",
    ),
    OPT_A(
        id = "opt_a",
        displayName = "OptA",
        subtitle = "Red Orbit — immersive red cluster + scroll wheels.",
    ),
    OPT_B(
        id = "opt_b",
        displayName = "OptB",
        subtitle = "Twin Gauge — circular RPM/Speed instrument cluster.",
    ),
    OPT_C(
        id = "opt_c",
        displayName = "OptC",
        subtitle = "Pulse Deck — orange pulse cluster + metric cards.",
    ),
    ;

    companion object {
        val DEFAULT = CLASSIC

        fun fromId(id: String?): DashTheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
