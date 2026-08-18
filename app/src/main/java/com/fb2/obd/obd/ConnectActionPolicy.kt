package com.fb2.obd.obd

import com.fb2.obd.data.ConnectionState

/**
 * Dash / AA Connect chip: while a live ELM is up, the control is Disconnect
 * (tap drops the adapter). Demo keeps Connect — stop that from Settings.
 */
object ConnectActionPolicy {

    enum class Kind { CONNECT, DISCONNECT, RETRY, RECONNECT }

    data class Appearance(
        val label: String,
        val kind: Kind,
    )

    fun of(
        connection: ConnectionState,
        sourceIsLive: Boolean,
        reconnecting: Boolean = false,
    ): Appearance {
        val liveLinked = connection == ConnectionState.CONNECTED &&
            sourceIsLive &&
            !reconnecting
        return when {
            liveLinked -> Appearance("DISCONNECT", Kind.DISCONNECT)
            reconnecting ||
                (connection == ConnectionState.CONNECTING && sourceIsLive) ->
                Appearance("RETRY…", Kind.RETRY)
            connection == ConnectionState.ERROR -> Appearance("RECONNECT", Kind.RECONNECT)
            else -> Appearance("CONNECT", Kind.CONNECT)
        }
    }

    fun isDisconnectAction(
        connection: ConnectionState,
        sourceIsLive: Boolean,
        reconnecting: Boolean = false,
    ): Boolean = of(connection, sourceIsLive, reconnecting).kind == Kind.DISCONNECT
}
