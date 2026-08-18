package com.fb2.obd.obd

import com.fb2.obd.data.ConnectionState

/**
 * Settings → Simulation. Pure so unit tests can cover stop/start without Android.
 *
 * Turning Demo off while not on a live ELM clears the fake feed (disconnect).
 * Turning Demo on while idle starts the simulated source.
 */
object DemoAllowPolicy {

    enum class Next { DISCONNECT, START_DEMO, NONE }

    fun next(
        enabled: Boolean,
        sourceIsLive: Boolean,
        connection: ConnectionState,
    ): Next = when {
        !enabled && !sourceIsLive -> Next.DISCONNECT
        enabled &&
            !sourceIsLive &&
            (connection == ConnectionState.DISCONNECTED ||
                connection == ConnectionState.ERROR) -> Next.START_DEMO
        else -> Next.NONE
    }

    fun isDemoRunning(sourceIsLive: Boolean, connection: ConnectionState): Boolean =
        connection == ConnectionState.CONNECTED && !sourceIsLive
}
