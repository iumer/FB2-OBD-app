package com.fb2.obd.obd

/**
 * Structured ELM / RFCOMM link events for drive-log forensics.
 *
 * Appears in the CSV `# events` section as category `ELM_LINK` so we can tell
 * app-driven soft-recover / hard-reconnect from a tablet killing Bluetooth.
 */
object ElmLinkForensics {

    const val CATEGORY = "ELM_LINK"

    const val REASON_UNABLE_STREAK = "UNABLE_STREAK"
    const val REASON_DEAD_MODE01 = "DEAD_MODE01"
    const val REASON_SOFT_RECOVER = "SOFT_RECOVER"
    const val REASON_HARD_RECONNECT = "HARD_RECONNECT"
    const val REASON_SOCKET_CLOSED = "SOCKET_CLOSED"
    const val REASON_READ_TIMEOUT = "READ_TIMEOUT"
    const val REASON_RECONNECT_ATTEMPT = "RECONNECT_ATTEMPT"
    const val REASON_RECONNECT_ABORT = "RECONNECT_ABORT"
    const val REASON_STALE_UI = "STALE_UI"
    const val REASON_POLL_STOPPED = "POLL_STOPPED"
    const val REASON_LINK_OK = "LINK_OK"

    fun message(
        reason: String,
        unable: Int? = null,
        timeouts: Int? = null,
        deadCycles: Int? = null,
        softRecover: Int? = null,
        mode01Ok: Boolean? = null,
        atrvOk: Boolean? = null,
        atrvV: Double? = null,
        lastOkPid: String? = null,
        silenceMs: Long? = null,
        cycleMs: Long? = null,
        pidsThisCycle: Int? = null,
        socketConnected: Boolean? = null,
        retryAttempt: Long? = null,
        backoffMs: Long? = null,
        detail: String? = null,
    ): String = buildString {
        append("reason=").append(reason)
        unable?.let { append(" unable=").append(it) }
        timeouts?.let { append(" timeouts=").append(it) }
        deadCycles?.let { append(" deadCycles=").append(it) }
        softRecover?.let { append(" softRecover=").append(it) }
        mode01Ok?.let { append(" mode01Ok=").append(it) }
        atrvOk?.let { append(" atrvOk=").append(it) }
        atrvV?.let { append(" atrvV=").append("%.1f".format(it)) }
        lastOkPid?.let { append(" lastOkPid=").append(it) }
        silenceMs?.let { append(" silenceMs=").append(it) }
        cycleMs?.let { append(" cycleMs=").append(it) }
        pidsThisCycle?.let { append(" pids=").append(it) }
        socketConnected?.let { append(" socket=").append(if (it) "up" else "down") }
        retryAttempt?.let { append(" retry=").append(it) }
        backoffMs?.let { append(" backoffMs=").append(it) }
        detail?.takeIf { it.isNotBlank() }?.let { append(" detail=").append(it.take(120)) }
    }

    /** Classify an [IOException] message into a stable reason code. */
    fun reasonFromThrowable(t: Throwable): String {
        val m = (t.message ?: "").lowercase()
        return when {
            m.contains("socket closed") || m.contains("broken pipe") ||
                m.contains("connection reset") || m.contains("bt socket") ->
                REASON_SOCKET_CLOSED
            m.contains("read timeout") || m.contains("timeout") ->
                REASON_READ_TIMEOUT
            m.contains("soft recover") || m.contains("bus lost") ->
                REASON_HARD_RECONNECT
            m.contains("no ecu mode 01") || m.contains("dead cycle") ->
                REASON_HARD_RECONNECT
            else -> REASON_POLL_STOPPED
        }
    }
}
