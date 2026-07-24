package com.fb2.obd.obd

import com.fb2.obd.data.ConnectionState
import com.fb2.obd.data.ObdLogger

/**
 * Event-based diagnostic log: only records important state transitions
 * (not continuous time-series). Works independently of value LOG toggle.
 */
class DiagnosticEventTracker {

    private val lastZone = mutableMapOf<String, Health>()
    private var lastGear: Int? = null
    private var lastFuelLoop: String? = null
    private var lastEngineOn: Boolean? = null
    private var lastConnected: Boolean? = null
    private var lastDtcCount: Int? = null
    private var lastLockUp: String? = null
    private var overheating = false
    private var chargingProblem = false
    private var tripActive = false

    fun reset() {
        lastZone.clear()
        lastGear = null
        lastFuelLoop = null
        lastEngineOn = null
        lastConnected = null
        lastDtcCount = null
        lastLockUp = null
        overheating = false
        chargingProblem = false
        tripActive = false
    }

    fun onConnection(
        connection: ConnectionState,
        sourceIsLive: Boolean,
        sourceName: String,
    ) {
        val connected = connection == ConnectionState.CONNECTED
        if (lastConnected == null) {
            lastConnected = connected
            if (connected) {
                emit(
                    "ELM",
                    if (sourceIsLive) "ELM Connected ($sourceName)" else "Demo Connected",
                )
            }
            return
        }
        if (lastConnected != connected) {
            lastConnected = connected
            emit(
                "ELM",
                if (connected) {
                    if (sourceIsLive) "ELM Connected ($sourceName)" else "Demo Connected"
                } else {
                    "ELM Disconnected"
                },
            )
        }
    }

    fun onDtcCount(count: Int?) {
        if (count == null) return
        val prev = lastDtcCount
        if (prev == null) {
            lastDtcCount = count
            if (count > 0) emit("DTC", "DTC present: $count code(s)")
            return
        }
        if (prev != count) {
            when {
                count > prev -> emit("DTC", "DTC added — now $count code(s)")
                count == 0 -> emit("DTC", "DTCs cleared")
                else -> emit("DTC", "DTC count changed $prev → $count")
            }
            lastDtcCount = count
        }
    }

    fun onSnapshot(
        snapshot: VehicleSnapshot,
        thresholds: HealthThresholds,
        lockUpText: String? = null,
    ) {
        // Ignore blank reconnect / disconnect frames so we don't fake Engine Stop.
        if (snapshot.rpm == null && snapshot.coolantC == null && snapshot.batteryVolts == null &&
            snapshot.mafGps == null && snapshot.speedKmh == null
        ) {
            return
        }
        val rpm = snapshot.rpm ?: 0.0
        val speed = snapshot.speedKmh ?: 0.0
        val engineOn = rpm > 400.0
        val ignitionOn = rpm > 0.0 || (snapshot.batteryVolts ?: 0.0) > 11.0

        // Ignition / engine start-stop
        if (lastEngineOn == null) {
            lastEngineOn = engineOn
        } else if (lastEngineOn != engineOn) {
            emit("ENGINE", if (engineOn) "Engine Start" else "Engine Stop")
            lastEngineOn = engineOn
        }

        // Trip start/end (moving vs stopped with engine)
        val moving = speed >= 3.0
        if (moving && !tripActive) {
            tripActive = true
            emit("TRIP", "Trip Start")
        } else if (tripActive && !moving && !engineOn) {
            tripActive = false
            emit("TRIP", "Trip End")
        }

        // Gear changes
        val gear = snapshot.gear
        if (gear != null && lastGear != null && lastGear != gear) {
            emit("GEAR", "Estimated gear $lastGear → $gear")
        }
        if (gear != null) lastGear = gear

        // Fuel system loop
        val loop = snapshot.fuelSystemStatus
        if (!loop.isNullOrBlank()) {
            if (lastFuelLoop != null && lastFuelLoop != loop) {
                emit("FUEL", "Fuel system $lastFuelLoop → $loop")
            }
            lastFuelLoop = loop
        }

        // Colour-zone transitions
        zone("coolant", HealthEvaluator.coolant(snapshot.coolantC, thresholds))
        zone("battery", HealthEvaluator.battery(snapshot.batteryVolts, engineOn, thresholds))
        zone("stft", HealthEvaluator.fuelTrim(snapshot.stftPct, thresholds))
        zone("ltft", HealthEvaluator.fuelTrim(snapshot.ltftPct, thresholds))
        zone("intake", HealthEvaluator.intakeAir(snapshot.intakeC, thresholds))
        zone(
            "maf",
            HealthEvaluator.maf(
                snapshot.mafGps,
                snapshot.rpm,
                snapshot.speedKmh,
                snapshot.throttlePct,
                thresholds,
            ),
        )
        zone(
            "map",
            HealthEvaluator.map(
                snapshot.mapKpa,
                snapshot.throttlePct,
                snapshot.rpm,
                snapshot.speedKmh,
                thresholds,
            ),
        )

        // Overheating begin/end
        val coolStatus = HealthEvaluator.coolant(snapshot.coolantC, thresholds)
        val isOverheat = coolStatus.health == Health.CRITICAL ||
            (snapshot.coolantC != null && snapshot.coolantC > thresholds.coolantElevatedMax)
        if (isOverheat && !overheating) {
            overheating = true
            emit("OVERHEAT", "Overheating began (${snapshot.coolantC?.let { "%.0f°C".format(it) } ?: "?"})")
        } else if (!isOverheat && overheating) {
            overheating = false
            emit("OVERHEAT", "Overheating ended")
        }

        // Charging problem begin/end (engine running only)
        val batt = HealthEvaluator.battery(snapshot.batteryVolts, engineOn, thresholds)
        val chargeBad = engineOn &&
            (batt.health == Health.CRITICAL || batt.health == Health.ELEVATED)
        if (chargeBad && !chargingProblem) {
            chargingProblem = true
            emit("CHARGE", "Charging problem began (${batt.label})")
        } else if (!chargeBad && chargingProblem) {
            chargingProblem = false
            emit("CHARGE", "Charging problem ended")
        }

        // Torque converter lock-up
        if (!lockUpText.isNullOrBlank() && !lockUpText.startsWith("n/s")) {
            val norm = lockUpText.take(24)
            if (lastLockUp != null && lastLockUp != norm) {
                emit("TCM", "TC lock-up $lastLockUp → $norm")
            }
            lastLockUp = norm
        }

        // Suppress unused warning for ignition (reserved for future key-on without RPM)
        @Suppress("UNUSED_VARIABLE")
        val _ign = ignitionOn
    }

    private fun zone(key: String, status: MetricStatus) {
        if (status.health == Health.UNKNOWN) return
        val prev = lastZone[key]
        if (prev == null) {
            lastZone[key] = status.health
            return
        }
        if (prev != status.health) {
            emit(
                "ZONE",
                "$key ${prev.name} → ${status.health.name} (${status.label})",
            )
            lastZone[key] = status.health
        }
    }

    private fun emit(category: String, message: String) {
        ObdLogger.logEvent(category, message)
    }
}
