package com.fb2.obd.ui.dash

import com.fb2.obd.obd.Health
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.MetricStatus
import com.fb2.obd.obd.VehicleSnapshot
import kotlin.math.roundToInt

/** One row for alternative Dash looks (wheels / cards). */
data class DashLookMetric(
    val label: String,
    val value: String,
    val unit: String,
    val health: Health = Health.UNKNOWN,
    val statusLabel: String = "",
)

object DashLookMetrics {

    private fun Double?.fmt(digits: Int = 0): String = this?.let {
        if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
    } ?: "--"

    /**
     * Live sensor rows for side wheels / decks — excludes RPM / Speed / Gear
     * (those live in the centre cluster of alternative looks).
     */
    fun sideMetrics(
        snapshot: VehicleSnapshot,
        healthSnapshot: VehicleSnapshot = snapshot,
        thresholds: HealthThresholds = HealthThresholds.DEFAULT,
        dtcCount: Int? = null,
        healthScore: HealthScore? = null,
        latchHealth: (String, MetricStatus) -> MetricStatus = { _, s -> s },
    ): List<DashLookMetric> {
        val hs = healthSnapshot
        val engineRunning = (hs.rpm ?: snapshot.rpm ?: 0.0) > 0.0
        val t = thresholds
        fun L(key: String, status: MetricStatus) = latchHealth(key, status)

        fun row(
            label: String,
            value: String,
            unit: String,
            status: MetricStatus,
        ) = DashLookMetric(label, value, unit, status.health, status.label)

        return listOf(
            row("Coolant", snapshot.coolantC.fmt(), "°C", L("coolant1", HealthEvaluator.coolant(hs.coolantC, t))),
            row(
                "Battery",
                snapshot.batteryVolts.fmt(1),
                "V",
                L(
                    "battery",
                    HealthEvaluator.battery(hs.batteryVolts, engineRunning, t, rpm = hs.rpm ?: snapshot.rpm),
                ),
            ),
            row("Intake", snapshot.intakeC.fmt(), "°C", L("intake", HealthEvaluator.intakeAir(hs.intakeC, t))),
            row("Load", snapshot.engineLoadPct.fmt(), "%", HealthEvaluator.engineLoad(snapshot.engineLoadPct, t)),
            row("Throttle", snapshot.throttlePct.fmt(), "%", HealthEvaluator.throttle(snapshot.throttlePct)),
            row("MAP", snapshot.mapKpa.fmt(), "kPa", L(
                "map",
                HealthEvaluator.map(
                    hs.mapKpa, hs.throttlePct ?: snapshot.throttlePct,
                    hs.rpm ?: snapshot.rpm, hs.speedKmh ?: snapshot.speedKmh, t,
                ),
            )),
            row("MAF", snapshot.mafGps.fmt(1), "g/s", L(
                "maf",
                HealthEvaluator.maf(
                    hs.mafGps, hs.rpm ?: snapshot.rpm, hs.speedKmh ?: snapshot.speedKmh,
                    hs.throttlePct ?: snapshot.throttlePct, t,
                ),
            )),
            row("STFT", snapshot.stftPct.fmt(1), "%", L("stft", HealthEvaluator.fuelTrim(hs.stftPct, t))),
            row("LTFT", snapshot.ltftPct.fmt(1), "%", L("ltft", HealthEvaluator.fuelTrim(hs.ltftPct, t))),
            row("Timing", snapshot.timingAdvance.fmt(), "°", L("timing", HealthEvaluator.timing(hs.timingAdvance, t))),
            row("Ambient", snapshot.ambientC.fmt(), "°C", L("ambient", HealthEvaluator.ambient(hs.ambientC, t))),
            row(
                "Coolant 2",
                snapshot.coolant2C.fmt(),
                "°C",
                L("coolant2", HealthEvaluator.coolant(hs.coolant2C, t)),
            ),
            row(
                "Fuel",
                snapshot.fuelSystemStatus?.take(10) ?: "--",
                "",
                HealthEvaluator.fuelSystem(snapshot.fuelSystemStatus, snapshot.coolantC),
            ),
            row("DTCs", dtcCount?.toString() ?: "--", "", HealthEvaluator.dtcCount(dtcCount)),
            row(
                "Health",
                healthScore?.vehiclePct?.toString() ?: "--",
                if (healthScore?.vehiclePct != null) "%" else "",
                HealthEvaluator.vehicleHealth(healthScore?.vehiclePct),
            ),
        )
    }

    fun splitWheels(all: List<DashLookMetric>): Pair<List<DashLookMetric>, List<DashLookMetric>> {
        if (all.isEmpty()) return emptyList<DashLookMetric>() to emptyList()
        val mid = (all.size + 1) / 2
        return all.subList(0, mid) to all.subList(mid, all.size)
    }
}
