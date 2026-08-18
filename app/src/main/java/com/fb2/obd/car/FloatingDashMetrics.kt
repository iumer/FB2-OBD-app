package com.fb2.obd.car

import com.fb2.obd.data.ConnectionState
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
        // Hard offline only — RETRY keeps the full metric wheel with last-good numbers.
        if (!state.showingLiveValues) {
            val tag = when (state.connection) {
                ConnectionState.ERROR -> "OFF"
                else -> "OFF"
            }
            return listOf(Metric(tag, "--", "", null, state.statusLine))
        }
        // Coolant first — collapsed bubble + page 0 lead with temp while driving.
        // RPM stays early so high/redline colour is one swipe away on the ring.
        val preferredOrder = listOf(
            "Coolant 1", "RPM", "MAP", "Battery", "Intake",
            "Speed", "Load", "Throttle", "STFT", "Timing",
            "MAF", "Fuel loop", "Gear", "DTCs", "Health",
            "Coolant 2", "Ambient", "LTFT",
        )
        val hero = listOf(
            Metric("RPM", state.rpm, "", state.rpmHealth, state.rpmStatus),
            Metric("Speed", state.speedKmh, "km/h", null, null),
            Metric("Gear", state.gear, state.gearBadge, null, null),
        )
        val tiles = state.tiles.map {
            val displayOnly = it.label.equals("Load", ignoreCase = true) ||
                it.label.startsWith("Throttle", ignoreCase = true)
            Metric(
                label = it.label,
                value = it.value,
                unit = it.unit,
                health = if (displayOnly) null else it.health,
                status = it.status,
            )
        }
        // Prefer tile Coolant (has live health) over any duplicate hero entry.
        val combined = (tiles + hero).distinctBy { it.label.lowercase() }
        return combined.sortedBy { m ->
            val idx = preferredOrder.indexOfFirst { it.equals(m.label, true) }
            if (idx >= 0) idx else 1000 + m.label.hashCode().and(0x7fff)
        }
    }

    /**
     * Metric shown on the collapsed floating circle.
     * Prefers [preferredLabel] when the user pinned a satellite (e.g. RPM/MAF);
     * otherwise defaults to Coolant 1.
     */
    fun collapsedMetric(
        metrics: List<Metric>,
        preferredLabel: String? = null,
    ): Metric {
        if (metrics.isEmpty()) {
            return Metric("Dash", "--", "", null, "WAITING")
        }
        if (!preferredLabel.isNullOrBlank()) {
            metrics.firstOrNull { it.label.equals(preferredLabel, ignoreCase = true) }?.let {
                return it
            }
        }
        return metrics.firstOrNull { it.label.equals("Coolant 1", ignoreCase = true) }
            ?: metrics.firstOrNull { it.label.equals("Coolant 2", ignoreCase = true) }
            ?: metrics.firstOrNull { it.label.contains("Coolant", ignoreCase = true) }
            ?: metrics.first()
    }

    /** Page index that contains [label], or 0 if missing. */
    fun pageIndexOf(metrics: List<Metric>, label: String?, pageSize: Int = PAGE_SIZE): Int {
        if (label.isNullOrBlank() || metrics.isEmpty()) return 0
        val idx = metrics.indexOfFirst { it.label.equals(label, ignoreCase = true) }
        if (idx < 0) return 0
        return idx / pageSize
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
