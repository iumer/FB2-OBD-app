package com.fb2.obd.car

import com.fb2.obd.obd.Health

/**
 * Pure builder for the floating Dash bubble metric wheel.
 * Kept Android-free so Paparazzi / JVM tests can validate ordering and health.
 */
object FloatingDashMetrics {

    data class Metric(
        val label: String,
        val value: String,
        val unit: String,
        val health: String?,
        val status: String?,
    )

    fun from(state: CarDashState): List<Metric> {
        val worst = worstHealth(state.tiles.mapNotNull { it.health })
        val hero = listOf(
            Metric("RPM", state.rpm, "", worst, null),
            Metric("Speed", state.speedKmh, "km/h", worst, null),
            Metric("Gear", state.gear, state.gearBadge, worst, null),
        )
        val tiles = state.tiles.map {
            Metric(it.label, it.value, it.unit, it.health, it.status)
        }
        return hero + tiles
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
