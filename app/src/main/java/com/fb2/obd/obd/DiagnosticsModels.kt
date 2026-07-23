package com.fb2.obd.obd

/** Mode 09 vehicle identification. */
data class VehicleInfo(
    val vin: String? = null,
    val calibrationIds: List<String> = emptyList(),
    val cvn: List<String> = emptyList(),
    val ecuName: String? = null,
    val rawNotes: List<String> = emptyList(),
)

/** I/M readiness / emissions monitors (from Mode 01 PID 01). */
data class ReadinessStatus(
    val milOn: Boolean = false,
    val dtcCount: Int = 0,
    val monitors: List<MonitorItem> = emptyList(),
    val raw: String? = null,
)

data class MonitorItem(val name: String, val available: Boolean, val complete: Boolean)

/** Freeze-frame (Mode 02) for the DTC that triggered the frame. */
data class FreezeFrame(
    val dtc: String? = null,
    val values: Map<String, String> = emptyMap(),
    val raw: String? = null,
)

/** Mode 05 O2 sensor test result line. */
data class O2TestResult(val sensor: String, val testId: String, val value: String, val raw: String)

/** Mode 06 on-board monitor test result. */
data class Mode06Result(
    val tid: String,
    val cid: String,
    val value: String,
    val min: String,
    val max: String,
    val passed: Boolean?,
    val raw: String,
)

/** Engine / transmission health score. */
data class HealthScore(
    val enginePct: Int = 100,
    val transmissionPct: Int = 100,
    val engineNotes: List<String> = emptyList(),
    val transmissionNotes: List<String> = emptyList(),
)

/** Full-system / Honda menu scan row. */
data class ModuleScanResult(
    val module: String,
    val profileId: String,
    val supportedCount: Int,
    val totalCount: Int,
    val samplePids: List<String>,
    val status: String,
)
