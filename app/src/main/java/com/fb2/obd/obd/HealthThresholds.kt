package com.fb2.obd.obd

/**
 * User-editable colour-band thresholds for dashboard / transmission metrics.
 * Defaults match the FB2 Civic R18 diagnostic bands.
 */
data class HealthThresholds(
    val coolantColdBelow: Double = 70.0,
    val coolantGoodMax: Double = 97.0,
    val coolantWarnMax: Double = 103.0,
    val coolantElevatedMax: Double = 108.0,

    /** Engine running — green charging band. */
    val battRunGoodMin: Double = 13.8,
    val battRunGoodMax: Double = 14.7,
    val battRunWarnMin: Double = 13.2,
    val battRunCriticalAbove: Double = 15.0,

    /** Engine off — resting battery. */
    val battRestGoodAbove: Double = 12.6,
    val battRestWarnAbove: Double = 12.3,

    val trimGoodMax: Double = 5.0,
    val trimWarnMax: Double = 10.0,
    val trimElevatedMax: Double = 20.0,

    val loadGoodMax: Double = 60.0,
    val loadWarnMax: Double = 85.0,

    val intakeColdBelow: Double = 10.0,
    val intakeGoodMax: Double = 45.0,
    val intakeWarnMax: Double = 60.0,

    val ambientColdBelow: Double = 5.0,
    val ambientGoodMax: Double = 45.0,

    val mafIdleGoodMin: Double = 6.0,
    val mafIdleGoodMax: Double = 10.0,
    val mafIdleWarnMin: Double = 4.0,

    val mapGoodMax: Double = 60.0,
    val mapWarnMax: Double = 90.0,
    val mapWotThrottleMin: Double = 70.0,

    val timingRetardBelow: Double = 0.0,
    val timingLowBelow: Double = 5.0,

    val rpmIdleLow: Double = 650.0,
    val rpmIdleHigh: Double = 750.0,
    val rpmNormalMax: Double = 4500.0,
    val rpmHighMax: Double = 6000.0,

    val atfColdMax: Double = 65.0,
    val atfGoodMax: Double = 95.0,
    val atfWarnMax: Double = 105.0,
    val atfElevatedMax: Double = 115.0,

    val slipGoodMax: Double = 40.0,
    val slipWarnMax: Double = 100.0,
) {
    companion object {
        val DEFAULT = HealthThresholds()
    }
}

/** Which coloured metric the user long-pressed. */
enum class EditableMetric(
    val title: String,
    val unit: String,
) {
    COOLANT("Coolant temperature", "\u00B0C"),
    BATTERY("Battery voltage", "V"),
    FUEL_TRIM("Fuel trim (STFT / LTFT)", "%"),
    ENGINE_LOAD("Engine load", "%"),
    INTAKE("Intake air temperature", "\u00B0C"),
    AMBIENT("Ambient temperature", "\u00B0C"),
    MAF("MAF (idle band)", "g/s"),
    MAP("MAP", "kPa"),
    TIMING("Ignition timing", "\u00B0"),
    RPM("Engine RPM", "rpm"),
    ATF("ATF temperature", "\u00B0C"),
    TC_SLIP("Torque converter slip", "rpm"),
    ;

    companion object {
        fun fromTileLabel(label: String): EditableMetric? {
            val l = label.lowercase()
            return when {
                l.startsWith("coolant") -> COOLANT
                l.startsWith("battery") || l.contains("ecu v") -> BATTERY
                l == "stft" || l == "ltft" || l.contains("fuel trim") -> FUEL_TRIM
                l == "load" || l.contains("engine load") -> ENGINE_LOAD
                l == "intake" || l.contains("intake") -> INTAKE
                l == "ambient" -> AMBIENT
                l == "maf" -> MAF
                l == "map" -> MAP
                l == "timing" || l.contains("ignition") -> TIMING
                l == "rpm" -> RPM
                l.contains("atf") || (l.contains("transmission") && l.contains("temp")) ||
                    (l.contains("fluid") && l.contains("temp")) -> ATF
                l.contains("slip") -> TC_SLIP
                else -> null
            }
        }
    }
}

/** One editable row in the threshold dialog. */
data class ThresholdEditField(
    val id: String,
    val label: String,
    val hint: String,
    val value: Double,
    val band: Health,
)

fun HealthThresholds.fieldsFor(metric: EditableMetric): List<ThresholdEditField> = when (metric) {
    EditableMetric.COOLANT -> listOf(
        ThresholdEditField("coolantColdBelow", "Blue below", "Cold / warming", coolantColdBelow, Health.COLD),
        ThresholdEditField("coolantGoodMax", "Green up to", "Normal", coolantGoodMax, Health.GOOD),
        ThresholdEditField("coolantWarnMax", "Yellow up to", "Caution", coolantWarnMax, Health.WARN),
        ThresholdEditField("coolantElevatedMax", "Orange up to", "Hot (above = red)", coolantElevatedMax, Health.ELEVATED),
    )
    EditableMetric.BATTERY -> listOf(
        ThresholdEditField("battRunGoodMin", "Running green from", "Charging OK min", battRunGoodMin, Health.GOOD),
        ThresholdEditField("battRunGoodMax", "Running green to", "Charging OK max", battRunGoodMax, Health.GOOD),
        ThresholdEditField("battRunWarnMin", "Running yellow from", "Below this = red (alt weak)", battRunWarnMin, Health.WARN),
        ThresholdEditField("battRunCriticalAbove", "Running red above", "Overcharge", battRunCriticalAbove, Health.CRITICAL),
        ThresholdEditField("battRestGoodAbove", "Resting green above", "Engine off", battRestGoodAbove, Health.GOOD),
        ThresholdEditField("battRestWarnAbove", "Resting yellow above", "Below = flat (red)", battRestWarnAbove, Health.WARN),
    )
    EditableMetric.FUEL_TRIM -> listOf(
        ThresholdEditField("trimGoodMax", "Green |trim| up to", "Normal", trimGoodMax, Health.GOOD),
        ThresholdEditField("trimWarnMax", "Yellow |trim| up to", "Slight lean/rich", trimWarnMax, Health.WARN),
        ThresholdEditField("trimElevatedMax", "Orange |trim| up to", "Above = red", trimElevatedMax, Health.ELEVATED),
    )
    EditableMetric.ENGINE_LOAD -> listOf(
        ThresholdEditField("loadGoodMax", "Green up to", "Normal", loadGoodMax, Health.GOOD),
        ThresholdEditField("loadWarnMax", "Yellow up to", "Above = red", loadWarnMax, Health.WARN),
    )
    EditableMetric.INTAKE -> listOf(
        ThresholdEditField("intakeColdBelow", "Blue below", "Cold air", intakeColdBelow, Health.COLD),
        ThresholdEditField("intakeGoodMax", "Green up to", "Normal", intakeGoodMax, Health.GOOD),
        ThresholdEditField("intakeWarnMax", "Yellow up to", "Above = red", intakeWarnMax, Health.WARN),
    )
    EditableMetric.AMBIENT -> listOf(
        ThresholdEditField("ambientColdBelow", "Blue below", "Cold", ambientColdBelow, Health.COLD),
        ThresholdEditField("ambientGoodMax", "Green up to", "Above = yellow", ambientGoodMax, Health.GOOD),
    )
    EditableMetric.MAF -> listOf(
        ThresholdEditField("mafIdleGoodMin", "Idle green from", "g/s at idle", mafIdleGoodMin, Health.GOOD),
        ThresholdEditField("mafIdleGoodMax", "Idle green to", "g/s at idle", mafIdleGoodMax, Health.GOOD),
        ThresholdEditField("mafIdleWarnMin", "Idle yellow from", "Below = red", mafIdleWarnMin, Health.WARN),
    )
    EditableMetric.MAP -> listOf(
        ThresholdEditField("mapGoodMax", "Green up to", "Normal vacuum/load", mapGoodMax, Health.GOOD),
        ThresholdEditField("mapWarnMax", "Yellow up to", "Above + low throttle = warn", mapWarnMax, Health.WARN),
        ThresholdEditField("mapWotThrottleMin", "WOT throttle %", "MAP above yellow = green if throttle ≥ this", mapWotThrottleMin, Health.GOOD),
    )
    EditableMetric.TIMING -> listOf(
        ThresholdEditField("timingRetardBelow", "Red below", "Retard", timingRetardBelow, Health.CRITICAL),
        ThresholdEditField("timingLowBelow", "Yellow below", "Low advance", timingLowBelow, Health.WARN),
    )
    EditableMetric.RPM -> listOf(
        ThresholdEditField("rpmIdleLow", "Idle green from", "rpm", rpmIdleLow, Health.GOOD),
        ThresholdEditField("rpmIdleHigh", "Idle green to", "Then normal band", rpmIdleHigh, Health.GOOD),
        ThresholdEditField("rpmNormalMax", "Green driving up to", "Above = yellow", rpmNormalMax, Health.GOOD),
        ThresholdEditField("rpmHighMax", "Yellow up to", "Above = redline", rpmHighMax, Health.WARN),
    )
    EditableMetric.ATF -> listOf(
        ThresholdEditField("atfColdMax", "Blue up to", "Warming", atfColdMax, Health.COLD),
        ThresholdEditField("atfGoodMax", "Green up to", "Normal", atfGoodMax, Health.GOOD),
        ThresholdEditField("atfWarnMax", "Yellow up to", "Warm", atfWarnMax, Health.WARN),
        ThresholdEditField("atfElevatedMax", "Orange up to", "Above = red", atfElevatedMax, Health.ELEVATED),
    )
    EditableMetric.TC_SLIP -> listOf(
        ThresholdEditField("slipGoodMax", "Green up to", "Locked OK", slipGoodMax, Health.GOOD),
        ThresholdEditField("slipWarnMax", "Yellow up to", "Above = red", slipWarnMax, Health.WARN),
    )
}

fun HealthThresholds.withField(id: String, value: Double): HealthThresholds = when (id) {
    "coolantColdBelow" -> copy(coolantColdBelow = value)
    "coolantGoodMax" -> copy(coolantGoodMax = value)
    "coolantWarnMax" -> copy(coolantWarnMax = value)
    "coolantElevatedMax" -> copy(coolantElevatedMax = value)
    "battRunGoodMin" -> copy(battRunGoodMin = value)
    "battRunGoodMax" -> copy(battRunGoodMax = value)
    "battRunWarnMin" -> copy(battRunWarnMin = value)
    "battRunCriticalAbove" -> copy(battRunCriticalAbove = value)
    "battRestGoodAbove" -> copy(battRestGoodAbove = value)
    "battRestWarnAbove" -> copy(battRestWarnAbove = value)
    "trimGoodMax" -> copy(trimGoodMax = value)
    "trimWarnMax" -> copy(trimWarnMax = value)
    "trimElevatedMax" -> copy(trimElevatedMax = value)
    "loadGoodMax" -> copy(loadGoodMax = value)
    "loadWarnMax" -> copy(loadWarnMax = value)
    "intakeColdBelow" -> copy(intakeColdBelow = value)
    "intakeGoodMax" -> copy(intakeGoodMax = value)
    "intakeWarnMax" -> copy(intakeWarnMax = value)
    "ambientColdBelow" -> copy(ambientColdBelow = value)
    "ambientGoodMax" -> copy(ambientGoodMax = value)
    "mafIdleGoodMin" -> copy(mafIdleGoodMin = value)
    "mafIdleGoodMax" -> copy(mafIdleGoodMax = value)
    "mafIdleWarnMin" -> copy(mafIdleWarnMin = value)
    "mapGoodMax" -> copy(mapGoodMax = value)
    "mapWarnMax" -> copy(mapWarnMax = value)
    "mapWotThrottleMin" -> copy(mapWotThrottleMin = value)
    "timingRetardBelow" -> copy(timingRetardBelow = value)
    "timingLowBelow" -> copy(timingLowBelow = value)
    "rpmIdleLow" -> copy(rpmIdleLow = value)
    "rpmIdleHigh" -> copy(rpmIdleHigh = value)
    "rpmNormalMax" -> copy(rpmNormalMax = value)
    "rpmHighMax" -> copy(rpmHighMax = value)
    "atfColdMax" -> copy(atfColdMax = value)
    "atfGoodMax" -> copy(atfGoodMax = value)
    "atfWarnMax" -> copy(atfWarnMax = value)
    "atfElevatedMax" -> copy(atfElevatedMax = value)
    "slipGoodMax" -> copy(slipGoodMax = value)
    "slipWarnMax" -> copy(slipWarnMax = value)
    else -> this
}
