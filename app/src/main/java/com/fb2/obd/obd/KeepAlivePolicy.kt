package com.fb2.obd.obd

/**
 * When the Nakamichi / phone OEM reclaims RAM it kills [android.app.Activity]
 * (and sometimes the process). Torque survives because the OBD session is not
 * owned by the activity. These rules decide reconnect + battery-exemption UI.
 */
object KeepAlivePolicy {

    fun shouldReconnectAfterDeath(
        lastAddress: String?,
        userDisconnected: Boolean,
    ): Boolean = !userDisconnected && !lastAddress.isNullOrBlank()

    fun shouldPromptBatteryExemption(
        ignoringOptimizations: Boolean,
        liveElmConnected: Boolean,
    ): Boolean = liveElmConnected && !ignoringOptimizations

    fun shouldKeepForegroundService(
        liveElmConnected: Boolean,
        valueLogging: Boolean,
    ): Boolean = liveElmConnected || valueLogging
}
