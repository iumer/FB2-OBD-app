package com.fb2.obd.data

import com.fb2.obd.obd.HealthThresholds
import org.json.JSONObject
import java.io.File

/** Persists [HealthThresholds] as JSON under app files. */
class HealthThresholdStore(private val file: File) {

    fun load(): HealthThresholds {
        if (!file.exists()) return HealthThresholds.DEFAULT
        return runCatching {
            val o = JSONObject(file.readText())
            val d = HealthThresholds.DEFAULT
            val schema = if (o.has("schemaVersion")) o.optInt("schemaVersion", 1) else 1
            fun d(key: String, fallback: Double) =
                if (o.has(key) && !o.isNull(key)) o.getDouble(key) else fallback
            // schema < 3 kept overly harsh R18 MAF bands (idle 6–10, then 2.5/4.0 cruise).
            // Force new defaults for MAF fields once; user can still retune afterward.
            fun maf(key: String, fallback: Double) =
                if (schema >= SCHEMA_VERSION) d(key, fallback) else fallback
            HealthThresholds(
                coolantColdBelow = d("coolantColdBelow", d.coolantColdBelow),
                coolantGoodMax = d("coolantGoodMax", d.coolantGoodMax),
                coolantWarnMax = d("coolantWarnMax", d.coolantWarnMax),
                coolantElevatedMax = d("coolantElevatedMax", d.coolantElevatedMax),
                coolantVoiceAbove = d("coolantVoiceAbove", d.coolantVoiceAbove),
                battRunGoodMin = d("battRunGoodMin", d.battRunGoodMin),
                battRunGoodMax = d("battRunGoodMax", d.battRunGoodMax),
                battRunWarnMin = d("battRunWarnMin", d.battRunWarnMin),
                battRunElevatedMin = d("battRunElevatedMin", d.battRunElevatedMin),
                battRunCriticalAbove = d("battRunCriticalAbove", d.battRunCriticalAbove),
                battRestGoodAbove = d("battRestGoodAbove", d.battRestGoodAbove),
                battRestWarnAbove = d("battRestWarnAbove", d.battRestWarnAbove),
                battRestElevatedAbove = d("battRestElevatedAbove", d.battRestElevatedAbove),
                trimGoodMax = d("trimGoodMax", d.trimGoodMax),
                trimWarnMax = d("trimWarnMax", d.trimWarnMax),
                trimElevatedMax = d("trimElevatedMax", d.trimElevatedMax),
                loadGoodMax = d("loadGoodMax", d.loadGoodMax),
                loadWarnMax = d("loadWarnMax", d.loadWarnMax),
                intakeColdBelow = d("intakeColdBelow", d.intakeColdBelow),
                intakeGoodMax = d("intakeGoodMax", d.intakeGoodMax),
                intakeWarnMax = d("intakeWarnMax", d.intakeWarnMax),
                ambientColdBelow = d("ambientColdBelow", d.ambientColdBelow),
                ambientGoodMax = d("ambientGoodMax", d.ambientGoodMax),
                mafIdleGoodMin = maf("mafIdleGoodMin", d.mafIdleGoodMin),
                mafIdleGoodMax = maf("mafIdleGoodMax", d.mafIdleGoodMax),
                mafIdleWarnMin = maf("mafIdleWarnMin", d.mafIdleWarnMin),
                mafCruiseGoodMin = maf("mafCruiseGoodMin", d.mafCruiseGoodMin),
                mafCruiseGoodMax = maf("mafCruiseGoodMax", d.mafCruiseGoodMax),
                mafHeavyGoodMin = maf("mafHeavyGoodMin", d.mafHeavyGoodMin),
                mafHeavyGoodMax = maf("mafHeavyGoodMax", d.mafHeavyGoodMax),
                mapIdleGoodMin = d("mapIdleGoodMin", d.mapIdleGoodMin),
                mapIdleGoodMax = d("mapIdleGoodMax", d.mapIdleGoodMax),
                mapCruiseGoodMin = d("mapCruiseGoodMin", d.mapCruiseGoodMin),
                mapCruiseGoodMax = d("mapCruiseGoodMax", d.mapCruiseGoodMax),
                mapWotGoodMin = d("mapWotGoodMin", d.mapWotGoodMin),
                mapGoodMax = d("mapGoodMax", d.mapGoodMax),
                mapWarnMax = d("mapWarnMax", d.mapWarnMax),
                mapWotThrottleMin = d("mapWotThrottleMin", d.mapWotThrottleMin),
                timingRetardBelow = d("timingRetardBelow", d.timingRetardBelow),
                timingLowBelow = d("timingLowBelow", d.timingLowBelow),
                rpmIdleLow = d("rpmIdleLow", d.rpmIdleLow),
                rpmIdleHigh = d("rpmIdleHigh", d.rpmIdleHigh),
                rpmNormalMax = d("rpmNormalMax", d.rpmNormalMax),
                rpmHighMax = d("rpmHighMax", d.rpmHighMax),
                atfColdMax = d("atfColdMax", d.atfColdMax),
                atfGoodMax = d("atfGoodMax", d.atfGoodMax),
                atfWarnMax = d("atfWarnMax", d.atfWarnMax),
                atfElevatedMax = d("atfElevatedMax", d.atfElevatedMax),
                slipGoodMax = d("slipGoodMax", d.slipGoodMax),
                slipWarnMax = d("slipWarnMax", d.slipWarnMax),
            ).also { loaded ->
                if (schema < SCHEMA_VERSION) save(loaded)
            }
        }.getOrElse { HealthThresholds.DEFAULT }
    }

    fun save(t: HealthThresholds) {
        val o = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("coolantColdBelow", t.coolantColdBelow)
            put("coolantGoodMax", t.coolantGoodMax)
            put("coolantWarnMax", t.coolantWarnMax)
            put("coolantElevatedMax", t.coolantElevatedMax)
            put("coolantVoiceAbove", t.coolantVoiceAbove)
            put("battRunGoodMin", t.battRunGoodMin)
            put("battRunGoodMax", t.battRunGoodMax)
            put("battRunWarnMin", t.battRunWarnMin)
            put("battRunElevatedMin", t.battRunElevatedMin)
            put("battRunCriticalAbove", t.battRunCriticalAbove)
            put("battRestGoodAbove", t.battRestGoodAbove)
            put("battRestWarnAbove", t.battRestWarnAbove)
            put("battRestElevatedAbove", t.battRestElevatedAbove)
            put("trimGoodMax", t.trimGoodMax)
            put("trimWarnMax", t.trimWarnMax)
            put("trimElevatedMax", t.trimElevatedMax)
            put("loadGoodMax", t.loadGoodMax)
            put("loadWarnMax", t.loadWarnMax)
            put("intakeColdBelow", t.intakeColdBelow)
            put("intakeGoodMax", t.intakeGoodMax)
            put("intakeWarnMax", t.intakeWarnMax)
            put("ambientColdBelow", t.ambientColdBelow)
            put("ambientGoodMax", t.ambientGoodMax)
            put("mafIdleGoodMin", t.mafIdleGoodMin)
            put("mafIdleGoodMax", t.mafIdleGoodMax)
            put("mafIdleWarnMin", t.mafIdleWarnMin)
            put("mafCruiseGoodMin", t.mafCruiseGoodMin)
            put("mafCruiseGoodMax", t.mafCruiseGoodMax)
            put("mafHeavyGoodMin", t.mafHeavyGoodMin)
            put("mafHeavyGoodMax", t.mafHeavyGoodMax)
            put("mapIdleGoodMin", t.mapIdleGoodMin)
            put("mapIdleGoodMax", t.mapIdleGoodMax)
            put("mapCruiseGoodMin", t.mapCruiseGoodMin)
            put("mapCruiseGoodMax", t.mapCruiseGoodMax)
            put("mapWotGoodMin", t.mapWotGoodMin)
            put("mapGoodMax", t.mapGoodMax)
            put("mapWarnMax", t.mapWarnMax)
            put("mapWotThrottleMin", t.mapWotThrottleMin)
            put("timingRetardBelow", t.timingRetardBelow)
            put("timingLowBelow", t.timingLowBelow)
            put("rpmIdleLow", t.rpmIdleLow)
            put("rpmIdleHigh", t.rpmIdleHigh)
            put("rpmNormalMax", t.rpmNormalMax)
            put("rpmHighMax", t.rpmHighMax)
            put("atfColdMax", t.atfColdMax)
            put("atfGoodMax", t.atfGoodMax)
            put("atfWarnMax", t.atfWarnMax)
            put("atfElevatedMax", t.atfElevatedMax)
            put("slipGoodMax", t.slipGoodMax)
            put("slipWarnMax", t.slipWarnMax)
        }
        file.parentFile?.mkdirs()
        file.writeText(o.toString(2))
    }

    /** True when the user (or a prior schema migration) has a saved thresholds file. */
    fun hasUserEdits(): Boolean = file.exists()

    companion object {
        /** Bumped when default MAF bands were retuned for R18 idle/coast (~2–8 g/s). */
        const val SCHEMA_VERSION = 3
    }
}
