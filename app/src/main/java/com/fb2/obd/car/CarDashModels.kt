package com.fb2.obd.car

import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.GearSource
import com.fb2.obd.obd.HealthEvaluator
import com.fb2.obd.obd.HealthThresholds
import com.fb2.obd.obd.LiveSnapshotOverlay
import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.PidDefinition
import com.fb2.obd.obd.VehicleSnapshot
import kotlin.math.roundToInt

/** One dashboard tile mirrored to Android Auto / floating bubble. */
data class CarDashTile(
    val label: String,
    val value: String,
    val unit: String,
    val status: String?,
    /** [com.fb2.obd.obd.Health] name, or null when unknown / n/s. */
    val health: String? = null,
)

/** Snapshot of the phone main Dash for the car screen. */
data class CarDashState(
    val rpm: String = "--",
    val speedKmh: String = "--",
    val gear: String = "–",
    val gearBadge: String = "",
    val tiles: List<CarDashTile> = emptyList(),
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val sourceIsLive: Boolean = false,
    val sourceName: String = "",
    val logging: Boolean = false,
) {
    val connectLabel: String
        get() = when {
            connection == ConnectionState.CONNECTED && sourceIsLive -> "CONNECTED"
            connection == ConnectionState.CONNECTING -> "…"
            else -> "CONNECT"
        }

    val statusLine: String
        get() = when {
            connection == ConnectionState.CONNECTED && sourceIsLive -> "LIVE · $sourceName"
            connection == ConnectionState.CONNECTED && !sourceIsLive -> "DEMO"
            connection == ConnectionState.CONNECTING -> "Connecting…"
            connection == ConnectionState.ERROR -> "Connection error"
            else -> "Not connected"
        }
}

/**
 * Builds the same main-Dash tile set the phone shows (base sensors + configured extras).
 */
object CarDashBuilder {

    fun build(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds,
        extraPidIds: List<String>,
        extraValues: Map<String, String>,
        deepFoundValues: Map<String, String>,
        catalog: List<PidDefinition>,
        connection: ConnectionState,
        sourceIsLive: Boolean,
        sourceName: String,
        logging: Boolean,
        showEstimatedGear: Boolean,
        dtcCount: Int? = null,
        healthScore: com.fb2.obd.obd.HealthScore? = null,
    ): CarDashState {
        val gearSource = if (!showEstimatedGear && snapshot.gearSource == GearSource.ESTIMATED) {
            GearSource.NONE
        } else {
            snapshot.gearSource
        }
        val gearText = if (gearSource == GearSource.NONE) "–" else (snapshot.gear?.toString() ?: "–")
        val badge = when (gearSource) {
            GearSource.ECU -> "ECU"
            GearSource.ESTIMATED -> snapshot.gearConfidencePct?.let { "$it%" } ?: "EST"
            GearSource.NONE -> ""
        }

        val engineRunning = (snapshot.rpm ?: 0.0) > 0.0
        val t = thresholds
        val vehiclePct = healthScore?.vehiclePct
        val base = listOf(
            Triple("Coolant 1", snapshot.coolantC.fmt(), "\u00B0C") to
                HealthEvaluator.coolant(snapshot.coolantC, t),
            Triple("Coolant 2", snapshot.coolant2C.fmt(), "\u00B0C") to
                HealthEvaluator.coolant(snapshot.coolant2C, t),
            Triple("Battery", snapshot.batteryVolts.fmt(1), "V") to
                HealthEvaluator.battery(snapshot.batteryVolts, engineRunning, t),
            Triple("Intake", snapshot.intakeC.fmt(), "\u00B0C") to
                HealthEvaluator.intakeAir(snapshot.intakeC, t),
            Triple("Ambient", snapshot.ambientC.fmt(), "\u00B0C") to
                HealthEvaluator.ambient(snapshot.ambientC, t),
            Triple("Load", snapshot.engineLoadPct.fmt(), "%") to
                HealthEvaluator.engineLoad(snapshot.engineLoadPct, t),
            Triple("Throttle", snapshot.throttlePct.fmt(), "%") to
                HealthEvaluator.throttle(snapshot.throttlePct),
            Triple("STFT", snapshot.stftPct.fmt(1), "%") to
                HealthEvaluator.fuelTrim(snapshot.stftPct, t),
            Triple("LTFT", snapshot.ltftPct.fmt(1), "%") to
                HealthEvaluator.fuelTrim(snapshot.ltftPct, t),
            Triple("MAF", snapshot.mafGps.fmt(1), "g/s") to
                HealthEvaluator.maf(
                    snapshot.mafGps, snapshot.rpm, snapshot.speedKmh, snapshot.throttlePct, t,
                ),
            Triple("MAP", snapshot.mapKpa.fmt(), "kPa") to
                HealthEvaluator.map(
                    snapshot.mapKpa, snapshot.throttlePct, snapshot.rpm, snapshot.speedKmh, t,
                ),
            Triple("Timing", snapshot.timingAdvance.fmt(), "\u00B0") to
                HealthEvaluator.timing(snapshot.timingAdvance, t),
            Triple("Fuel loop", snapshot.fuelSystemStatus?.take(12) ?: "--", "") to
                HealthEvaluator.fuelSystem(snapshot.fuelSystemStatus, snapshot.coolantC),
            Triple("DTCs", dtcCount?.toString() ?: "--", "") to
                HealthEvaluator.dtcCount(dtcCount),
            Triple("Health", vehiclePct?.toString() ?: "--", if (vehiclePct != null) "%" else "") to
                HealthEvaluator.vehicleHealth(vehiclePct),
        ).mapIndexed { idx, (triple, status) ->
            val pid = when (idx) {
                0 -> ObdPid.COOLANT_TEMP
                1 -> ObdPid.COOLANT_TEMP_2
                2 -> ObdPid.CONTROL_MODULE_VOLTAGE
                3 -> ObdPid.INTAKE_TEMP
                4 -> ObdPid.AMBIENT_TEMP
                5 -> ObdPid.ENGINE_LOAD
                6 -> ObdPid.THROTTLE
                7 -> ObdPid.STFT_B1
                8 -> ObdPid.LTFT_B1
                9 -> ObdPid.MAF
                10 -> ObdPid.INTAKE_MAP
                11 -> ObdPid.TIMING_ADVANCE
                12 -> ObdPid.FUEL_SYSTEM_STATUS
                else -> null
            }
            val unsupported = pid != null && pid.number in snapshot.unsupportedPids
            val recovered = deepFoundValues[triple.first]
            val showNs = unsupported && recovered == null
            val value = when {
                recovered != null -> recovered.substringBefore(" ")
                showNs -> "n/s"
                else -> triple.second
            }
            val unit = when {
                recovered != null -> recovered.substringAfter(" ", "")
                showNs -> ""
                else -> triple.third
            }
            CarDashTile(
                label = triple.first,
                value = value,
                unit = unit,
                status = if (showNs) null else status.label,
                health = if (showNs) null else status.health.name,
            )
        }

        val extras = extraPidIds.mapNotNull { id -> catalog.find { it.id.equals(id, true) } }.map { pid ->
            val recovered = deepFoundValues[pid.label] ?: deepFoundValues[pid.id]
            val text = recovered ?: LiveSnapshotOverlay.formatLiveOrNs(
                pid,
                snapshot,
                fallback = extraValues[pid.id],
            )
            val unsupported = recovered == null && (text.startsWith("n/s") || text == "—")
            CarDashTile(
                label = pid.label.take(18),
                value = if (unsupported) "n/s" else text.substringBefore(" "),
                unit = if (unsupported) "" else text.substringAfter(" ", pid.unit).ifBlank { pid.unit },
                status = null,
            )
        }

        return CarDashState(
            rpm = snapshot.rpm.fmt(),
            speedKmh = snapshot.speedKmh.fmt(),
            gear = gearText,
            gearBadge = badge,
            tiles = base + extras,
            connection = connection,
            sourceIsLive = sourceIsLive,
            sourceName = sourceName,
            logging = logging,
        )
    }

    private fun Double?.fmt(digits: Int = 0): String = this?.let {
        if (digits == 0) it.roundToInt().toString() else "%.${digits}f".format(it)
    } ?: "--"
}
