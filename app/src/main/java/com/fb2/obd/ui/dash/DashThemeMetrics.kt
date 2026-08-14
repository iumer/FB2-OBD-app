package com.fb2.obd.ui.dash

import com.fb2.obd.obd.EditableMetric
import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.SnapshotFreshness
import com.fb2.obd.obd.VehicleSnapshot
import kotlin.math.roundToInt

/** One interactive metric for OptA/B/C themes. */
data class DashThemeMetric(
    val label: String,
    val value: String,
    val unit: String,
    val health: Health = Health.UNKNOWN,
    val freshAtMs: Long? = null,
    val pidRequest: String? = null,
    val editMetric: EditableMetric? = null,
)

object DashThemeMetrics {

    private fun Double?.fmt(digits: Int = 0): String = this?.let {
        if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
    } ?: "--"

    /**
     * Labels match Classic Dash tiles so remap / deep-search / thresholds
     * share the same override + PID keys.
     */
    fun sideMetrics(
        snapshot: VehicleSnapshot,
        healthSnapshot: VehicleSnapshot = snapshot,
        thresholds: HealthThresholds = HealthThresholds.DEFAULT,
        dtcCount: Int? = null,
        healthScore: HealthScore? = null,
        latchHealth: (String, MetricStatus) -> MetricStatus = { _, s -> s },
        deepFoundValues: Map<String, String> = emptyMap(),
    ): List<DashThemeMetric> {
        val hs = healthSnapshot
        val engineRunning = (hs.rpm ?: snapshot.rpm ?: 0.0) > 0.0
        val t = thresholds
        val fresh = snapshot.freshAtMs
        fun L(key: String, status: MetricStatus) = latchHealth(key, status)

        fun row(
            label: String,
            value: String,
            unit: String,
            status: MetricStatus,
            freshKey: String?,
            pid: ObdPid?,
            edit: EditableMetric? = null,
        ) = DashThemeMetric(
            label = label,
            value = value,
            unit = unit,
            health = status.health,
            freshAtMs = freshKey?.let { fresh[it] },
            pidRequest = pid?.request,
            editMetric = edit,
        )

        return listOf(
            row(
                "Coolant 1", snapshot.coolantC.fmt(), "°C",
                L("coolant1", HealthEvaluator.coolant(hs.coolantC, t)),
                SnapshotFreshness.KEY_COOLANT, ObdPid.COOLANT_TEMP, EditableMetric.COOLANT,
            ),
            row(
                "Battery", snapshot.batteryVolts.fmt(1), "V",
                L(
                    "battery",
                    HealthEvaluator.battery(hs.batteryVolts, engineRunning, t, rpm = hs.rpm ?: snapshot.rpm),
                ),
                SnapshotFreshness.KEY_BATTERY, ObdPid.CONTROL_MODULE_VOLTAGE, EditableMetric.BATTERY,
            ),
            row(
                "Intake", snapshot.intakeC.fmt(), "°C",
                L("intake", HealthEvaluator.intakeAir(hs.intakeC, t)),
                SnapshotFreshness.KEY_INTAKE, ObdPid.INTAKE_TEMP, EditableMetric.INTAKE,
            ),
            row(
                "Load", snapshot.engineLoadPct.fmt(), "%",
                HealthEvaluator.engineLoad(snapshot.engineLoadPct, t),
                SnapshotFreshness.KEY_LOAD, ObdPid.ENGINE_LOAD,
            ),
            row(
                "Throttle", snapshot.throttlePct.fmt(), "%",
                HealthEvaluator.throttle(snapshot.throttlePct),
                SnapshotFreshness.KEY_THROTTLE, ObdPid.THROTTLE,
            ),
            row(
                "MAP", snapshot.mapKpa.fmt(), "kPa",
                L(
                    "map",
                    HealthEvaluator.map(
                        hs.mapKpa, hs.throttlePct ?: snapshot.throttlePct,
                        hs.rpm ?: snapshot.rpm, hs.speedKmh ?: snapshot.speedKmh, t,
                    ),
                ),
                SnapshotFreshness.KEY_MAP, ObdPid.INTAKE_MAP, EditableMetric.MAP,
            ),
            row(
                "MAF", snapshot.mafGps.fmt(1), "g/s",
                L(
                    "maf",
                    HealthEvaluator.maf(
                        hs.mafGps, hs.rpm ?: snapshot.rpm, hs.speedKmh ?: snapshot.speedKmh,
                        hs.throttlePct ?: snapshot.throttlePct, t,
                    ),
                ),
                SnapshotFreshness.KEY_MAF, ObdPid.MAF, EditableMetric.MAF,
            ),
            row(
                "STFT", snapshot.stftPct.fmt(1), "%",
                L("stft", HealthEvaluator.fuelTrim(hs.stftPct, t)),
                SnapshotFreshness.KEY_STFT, ObdPid.STFT_B1, EditableMetric.FUEL_TRIM,
            ),
            row(
                "LTFT", snapshot.ltftPct.fmt(1), "%",
                L("ltft", HealthEvaluator.fuelTrim(hs.ltftPct, t)),
                SnapshotFreshness.KEY_LTFT, ObdPid.LTFT_B1, EditableMetric.FUEL_TRIM,
            ),
            row(
                "Timing", snapshot.timingAdvance.fmt(), "°",
                L("timing", HealthEvaluator.timing(hs.timingAdvance, t)),
                SnapshotFreshness.KEY_TIMING, ObdPid.TIMING_ADVANCE, EditableMetric.TIMING,
            ),
            row(
                "Ambient", snapshot.ambientC.fmt(), "°C",
                L("ambient", HealthEvaluator.ambient(hs.ambientC, t)),
                SnapshotFreshness.KEY_AMBIENT, ObdPid.AMBIENT_TEMP,
            ),
            row(
                "Coolant 2", snapshot.coolant2C.fmt(), "°C",
                L("coolant2", HealthEvaluator.coolant(hs.coolant2C, t)),
                SnapshotFreshness.KEY_COOLANT2, ObdPid.COOLANT_TEMP_2, EditableMetric.COOLANT,
            ),
            row(
                "Fuel loop",
                abbreviateFuelLoop(snapshot.fuelSystemStatus),
                "",
                HealthEvaluator.fuelSystem(snapshot.fuelSystemStatus, snapshot.coolantC),
                SnapshotFreshness.KEY_FUEL_LOOP, ObdPid.FUEL_SYSTEM_STATUS,
            ),
            row("DTCs", dtcCount?.toString() ?: "--", "", HealthEvaluator.dtcCount(dtcCount), null, null),
            row(
                "Health",
                healthScore?.vehiclePct?.toString() ?: "--",
                if (healthScore?.vehiclePct != null) "%" else "",
                HealthEvaluator.vehicleHealth(healthScore?.vehiclePct),
                null, null,
            ),
        ).map { applyDeepFound(it, deepFoundValues) }
    }

    /** Keep Fuel loop readable in narrow OptA wheel slots (avoid "CLOSED LOO"). */
    fun abbreviateFuelLoop(raw: String?): String {
        if (raw.isNullOrBlank()) return "--"
        val u = raw.trim().uppercase()
        return when {
            u.contains("CLOSED") -> "CLOSED"
            u.contains("OPEN") -> "OPEN"
            else -> raw.take(8)
        }
    }

    private fun applyDeepFound(
        metric: DashThemeMetric,
        deepFound: Map<String, String>,
    ): DashThemeMetric {
        if (deepFound.isEmpty()) return metric
        if (metric.value != "--" && metric.value.isNotBlank()) return metric
        val recovered = deepFound[metric.label]
            ?: metric.pidRequest?.let { deepFound[it] }
            ?: return metric
        val valuePart = recovered.substringBefore(" ").ifBlank { recovered }
        val unitPart = recovered.substringAfter(" ", "").ifBlank { metric.unit }
        return metric.copy(value = valuePart, unit = unitPart)
    }

    fun splitWheels(all: List<DashThemeMetric>): Pair<List<DashThemeMetric>, List<DashThemeMetric>> {
        if (all.isEmpty()) return emptyList<DashThemeMetric>() to emptyList()
        val mid = (all.size + 1) / 2
        return all.subList(0, mid) to all.subList(mid, all.size)
    }

    fun freshnessKeyForHero(label: String): String? = when (label.lowercase()) {
        "rpm" -> SnapshotFreshness.KEY_RPM
        "speed" -> SnapshotFreshness.KEY_SPEED
        else -> null
    }
}
