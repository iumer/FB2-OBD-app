package com.fb2.obd.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MaintenanceEntry(
    val id: String,
    val item: String,
    val spec: String = "",
    val lastChanged: String? = null,
    val nextDueKm: Int? = null,
    val nextDueDate: String? = null,
    val notes: String? = null,
)

/** Simple on-disk maintenance log book (JSON file). */
class MaintenanceStore(private val file: File) {

    fun load(): List<MaintenanceEntry> {
        if (!file.exists()) return defaultTemplate()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MaintenanceEntry(
                    id = o.getString("id"),
                    item = o.getString("item"),
                    spec = o.optString("spec"),
                    lastChanged = o.optString("lastChanged").ifBlank { null },
                    nextDueKm = if (o.has("nextDueKm") && !o.isNull("nextDueKm")) o.optInt("nextDueKm") else null,
                    nextDueDate = o.optString("nextDueDate").ifBlank { null },
                    notes = o.optString("notes").ifBlank { null },
                )
            }
        }.getOrElse { defaultTemplate() }
    }

    fun save(entries: List<MaintenanceEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("item", e.item)
                put("spec", e.spec)
                put("lastChanged", e.lastChanged)
                put("nextDueKm", e.nextDueKm)
                put("nextDueDate", e.nextDueDate)
                put("notes", e.notes)
            })
        }
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(2))
    }

    companion object {
        fun defaultTemplate(): List<MaintenanceEntry> = listOf(
            MaintenanceEntry("oil", "Engine Oil", "Honda 5W-30 API SP"),
            MaintenanceEntry("atf", "ATF", "Honda DW-1"),
            MaintenanceEntry("brake", "Brake Fluid", "DOT 3/4"),
            MaintenanceEntry("coolant", "Coolant", "Honda Type 2"),
            MaintenanceEntry("air", "Air Filter", ""),
            MaintenanceEntry("cabin", "Cabin Filter", ""),
            MaintenanceEntry("plugs", "Spark Plugs", "Iridium"),
            MaintenanceEntry("pcv", "PCV Valve", ""),
            MaintenanceEntry("battery", "Battery", ""),
        )
    }
}
