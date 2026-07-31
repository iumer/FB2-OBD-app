package com.fb2.obd.data

import org.json.JSONObject
import java.io.File

/**
 * Persists main-Dash tile remaps: built-in label → catalog PID id
 * (e.g. "Coolant 1" → "01A4" for gear ratio).
 */
class DashTileOverrideStore(private val file: File) {

    fun load(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val o = JSONObject(file.readText())
            buildMap {
                o.keys().forEach { key ->
                    val v = o.optString(key, "").trim()
                    if (key.isNotBlank() && v.isNotBlank()) put(key, v)
                }
            }
        }.getOrElse { emptyMap() }
    }

    fun save(overrides: Map<String, String>) {
        val o = JSONObject()
        overrides.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) o.put(k, v)
        }
        file.parentFile?.mkdirs()
        file.writeText(o.toString(2))
    }
}
