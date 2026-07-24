package com.fb2.obd.car

import com.fb2.obd.obd.Health

/**
 * Pure builder for the floating Dash bubble metric wheel.
 * Kept Android-free so Paparazzi / JVM tests can validate ordering and health.
 */
object FloatingDashMetrics {

    const val PAGE_SIZE = 5

    data class Metric(
        val label: String,
        val value: String,
        val unit: String,
        val health: String?,
        val status: String?,
    )

    fun from(state: CarDashState): List<Metric> {
        // Prefer high-signal sensors first for the radial ring (matches user examples).
        val preferredOrder = listOf(
            "RPM", "Coolant 1", "MAP", "Battery", "Intake",
            "Speed", "Load", "Throttle", "STFT", "Timing",
            "MAF", "Fuel loop", "Gear", "DTCs", "Health",
            "Coolant 2", "Ambient", "LTFT",
        )
        val hero = listOf(
            Metric("RPM", state.rpm, "", null, null),
            Metric("Speed", state.speedKmh, "km/h", null, null),
            Metric("Gear", state.gear, state.gearBadge, null, null),
        )
        val tiles = state.tiles.map {
            Metric(it.label, it.value, it.unit, it.health, it.status)
        }
        val combined = (hero + tiles).distinctBy { it.label.lowercase() }
        return combined.sortedBy { m ->
            val idx = preferredOrder.indexOfFirst { it.equals(m.label, true) }
            if (idx >= 0) idx else 1000 + m.label.hashCode().and(0x7fff)
        }
    }

    fun pageCount(metrics: List<Metric>, pageSize: Int = PAGE_SIZE): Int =
        if (metrics.isEmpty()) 1 else (metrics.size + pageSize - 1) / pageSize

    /** Active group of up to [pageSize] metrics for the radial ring. */
    fun page(metrics: List<Metric>, pageIndex: Int, pageSize: Int = PAGE_SIZE): List<Metric> {
        if (metrics.isEmpty()) {
            return listOf(Metric("Dash", "--", "", null, "WAITING"))
        }
        val pages = pageCount(metrics, pageSize)
        val p = pageIndex.coerceIn(0, pages - 1)
        val start = p * pageSize
        return metrics.subList(start, minOf(start + pageSize, metrics.size))
    }

    fun worstHealth(healths: List<String>): String? {
        val order = listOf(
            Health.CRITICAL.name,
            Health.ELEVATED.name,
            Health.WARN.name,
            Health.COLD.name,
            Health.GOOD.name,
            Health.UNKNOWN.name,
        )
        return healths.minByOrNull { h ->
            order.indexOf(h).let { if (it < 0) 99 else it }
        }
    }
}
