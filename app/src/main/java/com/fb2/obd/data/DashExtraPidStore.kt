package com.fb2.obd.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists extra Dash tiles added via **+**. Without this they vanish on process death.
 * File: `filesDir/dash_extra_pids.json` → `{"ids":["0114","0115",…]}`.
 */
class DashExtraPidStore(private val file: File) {

    fun load(): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("ids") ?: JSONArray()
            val out = ArrayList<String>(arr.length())
            val seen = HashSet<String>()
            for (i in 0 until arr.length()) {
                val raw = arr.optString(i).trim().uppercase()
                if (raw.isEmpty() || raw in seen) continue
                seen += raw
                out += raw
            }
            out
        }.getOrElse { emptyList() }
    }

    fun save(ids: List<String>) {
        val arr = JSONArray()
        val seen = HashSet<String>()
        ids.forEach { raw ->
            val id = raw.trim().uppercase()
            if (id.isEmpty() || id in seen) return@forEach
            seen += id
            arr.put(id)
        }
        file.parentFile?.mkdirs()
        file.writeText(JSONObject().put("ids", arr).toString(2))
    }
}
