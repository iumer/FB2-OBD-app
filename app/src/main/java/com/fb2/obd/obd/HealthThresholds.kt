package com.fb2.obd.obd

/**
 * User-editable colour-band thresholds for dashboard / transmission metrics.
 * Defaults match the FB2 Civic R18 diagnostic bands (Honda-focused diag spec).
 */
data class HealthThresholds(
    val coolantColdBelow: Double = 70.0,
    /** Green / NORMAL up to this (°C). FB2: 95. */
    val coolantGoodMax: Double = 95.0,
    /** Yellow / WARM up to this. FB2: 100. */
    val coolantWarnMax: Double = 100.0,
    /** Orange / HOT up to this; above = red OVERHEAT. FB2: 103. */
    val coolantElevatedMax: Double = 103.0,
    /** Voice + beep at or above this (°C). FB2: 104. */
    val coolantVoiceAbove: Double = 104.0,

    /** Engine running — green charging band (Honda ELD often sits ~13.2–14.5). */
    val battRunGoodMin: Double = 13.2,
    val battRunGoodMax: Double = 14.8,
    val battRunWarnMin: Double = 12.9,
    /** Orange band floor while running (below warn, above critical colour). */
    val battRunElevatedMin: Double = 12.6,
    val battRunCriticalAbove: Double = 15.0,
    /**
     * Spoken "Battery critical" only at or below this (V) while running.
     * Tile colours still use the bands above; cheap ELM ATRV often under-reads
     * vs a multimeter on the posts, so voice is reserved for true deep discharge.
     */
    val battVoiceCriticalBelow: Double = 11.8,
    /** Extra volts required to leave a worse band (hysteresis). */
    val battHysteresisV: Double = 0.15,

    /** Engine off — resting battery. */
    val battRestGoodAbove: Double = 12.6,
    val battRestWarnAbove: Double = 12.3,
    val battRestElevatedAbove: Double = 12.0,

    val trimGoodMax: Double = 5.0,
    val trimWarnMax: Double = 10.0,
    val trimElevatedMax: Double = 20.0,

    /** Kept for editor compatibility; load/throttle are display-only (no warn colours). */
    val loadGoodMax: Double = 60.0,
    val loadWarnMax: Double = 85.0,

    val intakeColdBelow: Double = 15.0,
    val intakeGoodMax: Double = 45.0,
    val intakeWarnMax: Double = 60.0,

    val ambientColdBelow: Double = 5.0,
    val ambientGoodMax: Double = 45.0,

    // R18 1.8L idle MAF is typically ~3–5 g/s (Torque-normal). Older 6–10
    // (and even 2.5–6.5 with a high warn floor) falsely flagged idle as CRITICAL.
    val mafIdleGoodMin: Double = 2.0,
    val mafIdleGoodMax: Double = 8.0,
    val mafIdleWarnMin: Double = 1.0,
    // Light city / coast on FB2 often sits at 2.5–12 g/s — not highway 15–35.
    val mafCruiseGoodMin: Double = 2.5,
    val mafCruiseGoodMax: Double = 45.0,
    val mafHeavyGoodMin: Double = 30.0,
    val mafHeavyGoodMax: Double = 130.0,

    val mapIdleGoodMin: Double = 25.0,
    val mapIdleGoodMax: Double = 40.0,
    val mapCruiseGoodMin: Double = 40.0,
    val mapCruiseGoodMax: Double = 60.0,
    val mapWotGoodMin: Double = 90.0,
    val mapGoodMax: Double = 60.0,
    val mapWarnMax: Double = 90.0,
    val mapWotThrottleMin: Double = 70.0,

    /** Red below this (degrees). Yellow from this up through [timingYellowMax]. */
    val timingRetardBelow: Double = -5.0,
    /** Yellow includes 0°; green is strictly positive. */
    val timingLowBelow: Double = 0.0,

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

        /**
         * Wider SAE-safe bands for Generic OBD2 — not tuned to R18 idle MAF / Honda ELD.
         * Prefer fewer false CRITICAL colours on unknown engines.
         */
        fun genericObd2(): HealthThresholds = HealthThresholds(
            coolantColdBelow = 60.0,
            coolantGoodMax = 95.0,
            coolantWarnMax = 105.0,
            coolantElevatedMax = 110.0,
            coolantVoiceAbove = 115.0,
            battRunGoodMin = 13.0,
            battRunGoodMax = 15.0,
            battRunWarnMin = 12.6,
            battRunElevatedMin = 12.2,
            battRunCriticalAbove = 15.5,
            battVoiceCriticalBelow = 11.8,
            mafIdleGoodMin = 1.0,
            mafIdleGoodMax = 12.0,
            mafIdleWarnMin = 0.5,
            mafCruiseGoodMin = 2.0,
            mafCruiseGoodMax = 60.0,
            mafHeavyGoodMin = 20.0,
            mafHeavyGoodMax = 180.0,
            mapIdleGoodMin = 20.0,
            mapIdleGoodMax = 50.0,
            mapCruiseGoodMin = 35.0,
            mapCruiseGoodMax = 80.0,
            mapWotGoodMin = 85.0,
            mapGoodMax = 80.0,
            mapWarnMax = 100.0,
            rpmIdleLow = 550.0,
            rpmIdleHigh = 950.0,
            rpmNormalMax = 5000.0,
            rpmHighMax = 6500.0,
            // ATF / slip unused on Generic (no TCM page) — leave permissive.
            atfColdMax = 50.0,
            atfGoodMax = 110.0,
            atfWarnMax = 120.0,
            atfElevatedMax = 130.0,
            slipGoodMax = 80.0,
            slipWarnMax = 200.0,
        )
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
    MAF("MAF (context bands)", "g/s"),
    MAP("MAP (context bands)", "kPa"),
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
        ThresholdEditField("coolantWarnMax", "Yellow up to", "Warm", coolantWarnMax, Health.WARN),
        ThresholdEditField("coolantElevatedMax", "Orange up to", "Hot (above = red)", coolantElevatedMax, Health.ELEVATED),
        ThresholdEditField("coolantVoiceAbove", "Voice at/above", "Spoken alert", coolantVoiceAbove, Health.CRITICAL),
    )
    EditableMetric.BATTERY -> listOf(
        ThresholdEditField("battRunGoodMin", "Running green from", "Charging OK min", battRunGoodMin, Health.GOOD),
        ThresholdEditField("battRunGoodMax", "Running green to", "Charging OK max", battRunGoodMax, Health.GOOD),
        ThresholdEditField("battRunWarnMin", "Running yellow from", "Low charge", battRunWarnMin, Health.WARN),
        ThresholdEditField("battRunElevatedMin", "Running orange from", "Weak alt", battRunElevatedMin, Health.ELEVATED),
        ThresholdEditField("battRunCriticalAbove", "Running red above", "Overcharge", battRunCriticalAbove, Health.CRITICAL),
        ThresholdEditField("battVoiceCriticalBelow", "Voice at/below", "Spoken alert (V)", battVoiceCriticalBelow, Health.CRITICAL),
        ThresholdEditField("battRestGoodAbove", "Resting green above", "Engine off", battRestGoodAbove, Health.GOOD),
        ThresholdEditField("battRestWarnAbove", "Resting yellow above", "Weak rest", battRestWarnAbove, Health.WARN),
        ThresholdEditField("battRestElevatedAbove", "Resting orange above", "Below = flat", battRestElevatedAbove, Health.ELEVATED),
    )
    EditableMetric.FUEL_TRIM -> listOf(
        ThresholdEditField("trimGoodMax", "Green |trim| up to", "Normal", trimGoodMax, Health.GOOD),
        ThresholdEditField("trimWarnMax", "Yellow |trim| up to", "Slight lean/rich", trimWarnMax, Health.WARN),
        ThresholdEditField("trimElevatedMax", "Orange |trim| up to", "Above = red", trimElevatedMax, Health.ELEVATED),
    )
    EditableMetric.ENGINE_LOAD -> listOf(
        ThresholdEditField("loadGoodMax", "Info only — green up to", "Display only (no warn colours)", loadGoodMax, Health.GOOD),
        ThresholdEditField("loadWarnMax", "Info only — yellow up to", "Not used for tile colour", loadWarnMax, Health.WARN),
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
        ThresholdEditField("mafCruiseGoodMin", "Cruise green from", "g/s", mafCruiseGoodMin, Health.GOOD),
        ThresholdEditField("mafCruiseGoodMax", "Cruise green to", "g/s", mafCruiseGoodMax, Health.GOOD),
        ThresholdEditField("mafHeavyGoodMin", "Heavy green from", "g/s", mafHeavyGoodMin, Health.GOOD),
        ThresholdEditField("mafHeavyGoodMax", "Heavy green to", "g/s", mafHeavyGoodMax, Health.GOOD),
    )
    EditableMetric.MAP -> listOf(
        ThresholdEditField("mapIdleGoodMin", "Idle green from", "kPa", mapIdleGoodMin, Health.GOOD),
        ThresholdEditField("mapIdleGoodMax", "Idle green to", "kPa", mapIdleGoodMax, Health.GOOD),
        ThresholdEditField("mapCruiseGoodMin", "Cruise green from", "kPa", mapCruiseGoodMin, Health.GOOD),
        ThresholdEditField("mapCruiseGoodMax", "Cruise green to", "kPa", mapCruiseGoodMax, Health.GOOD),
        ThresholdEditField("mapWotGoodMin", "WOT green from", "kPa", mapWotGoodMin, Health.GOOD),
        ThresholdEditField("mapWotThrottleMin", "WOT throttle %", "Throttle ≥ this = WOT", mapWotThrottleMin, Health.GOOD),
    )
    EditableMetric.TIMING -> listOf(
        ThresholdEditField("timingRetardBelow", "Red below", "Retard", timingRetardBelow, Health.CRITICAL),
        ThresholdEditField("timingLowBelow", "Yellow up to", "0° = yellow; >0 = green", timingLowBelow, Health.WARN),
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
    "coolantVoiceAbove" -> copy(coolantVoiceAbove = value)
    "battRunGoodMin" -> copy(battRunGoodMin = value)
    "battRunGoodMax" -> copy(battRunGoodMax = value)
    "battRunWarnMin" -> copy(battRunWarnMin = value)
    "battRunElevatedMin" -> copy(battRunElevatedMin = value)
    "battRunCriticalAbove" -> copy(battRunCriticalAbove = value)
    "battVoiceCriticalBelow" -> copy(battVoiceCriticalBelow = value)
    "battRestGoodAbove" -> copy(battRestGoodAbove = value)
    "battRestWarnAbove" -> copy(battRestWarnAbove = value)
    "battRestElevatedAbove" -> copy(battRestElevatedAbove = value)
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
    "mafCruiseGoodMin" -> copy(mafCruiseGoodMin = value)
    "mafCruiseGoodMax" -> copy(mafCruiseGoodMax = value)
    "mafHeavyGoodMin" -> copy(mafHeavyGoodMin = value)
    "mafHeavyGoodMax" -> copy(mafHeavyGoodMax = value)
    "mapIdleGoodMin" -> copy(mapIdleGoodMin = value)
    "mapIdleGoodMax" -> copy(mapIdleGoodMax = value)
    "mapCruiseGoodMin" -> copy(mapCruiseGoodMin = value)
    "mapCruiseGoodMax" -> copy(mapCruiseGoodMax = value)
    "mapWotGoodMin" -> copy(mapWotGoodMin = value)
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
