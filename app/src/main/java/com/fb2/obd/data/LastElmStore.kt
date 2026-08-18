package com.fb2.obd.data

import org.json.JSONObject
import java.io.File

/**
 * Last live ELM adapter so a sticky FGS restart can reconnect after the HU
 * kills the process. User Disconnect / Exit clears [userDisconnected].
 */
class LastElmStore(private val file: File) {

    data class State(
        val address: String? = null,
        val name: String? = null,
        val userDisconnected: Boolean = true,
    )

    fun load(): State {
        if (!file.exists()) return State()
        return runCatching {
            val root = JSONObject(file.readText())
            State(
                address = root.optString("address").trim().ifBlank { null },
                name = root.optString("name").trim().ifBlank { null },
                userDisconnected = root.optBoolean("userDisconnected", true),
            )
        }.getOrElse { State() }
    }

    fun saveConnected(address: String, name: String? = null) {
        write(
            JSONObject()
                .put("address", address.trim())
                .put("name", name?.trim().orEmpty())
                .put("userDisconnected", false),
        )
    }

    fun markUserDisconnected() {
        val prev = load()
        write(
            JSONObject()
                .put("address", prev.address.orEmpty())
                .put("name", prev.name.orEmpty())
                .put("userDisconnected", true),
        )
    }

    private fun write(obj: JSONObject) {
        file.parentFile?.mkdirs()
        file.writeText(obj.toString(2))
    }
}
