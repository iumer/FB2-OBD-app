package com.fb2.obd.obd

/**
 * Selectable phone Dash presentations. [CLASSIC] is the existing tile grid + hero strip.
 * Other looks are presentation-only — same snapshot / health / alerts underneath.
 */
enum class DashboardLook(
    val id: String,
    val displayName: String,
    val subtitle: String,
) {
    CLASSIC(
        id = "classic",
        displayName = "Classic grid",
        subtitle = "Current layout — digital RPM/Gear/Speed strip + sensor tile grid.",
    ),
    RED_ORBIT(
        id = "red_orbit",
        displayName = "Red Orbit",
        subtitle = "Red rounded cluster: Gear centre, RPM/Speed bars, scrollable metric wheels.",
    ),
    TWIN_GAUGE(
        id = "twin_gauge",
        displayName = "Twin Gauge",
        subtitle = "Instrument-style circular RPM + Speed gauges with Gear between them.",
    ),
    PULSE_DECK(
        id = "pulse_deck",
        displayName = "Pulse Deck",
        subtitle = "Large speed focus, RPM arc, gear badge, rounded metric cards below.",
    ),
    ;

    companion object {
        val DEFAULT = CLASSIC

        fun fromId(id: String?): DashboardLook =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
