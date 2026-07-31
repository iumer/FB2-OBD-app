package com.fb2.obd.data

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/** User-facing error messages for Analyze via AI. */
object AiAnalysisErrors {
    const val NO_INTERNET =
        "Internet is not connected. Turn on Wi-Fi or mobile data, then try Analyze again."

    const val REQUEST_TIMEOUT =
        "The AI request timed out. Check your connection and try again."

    /**
     * Map raw OpenAI / network throwables to a short on-screen message
     * (avoid dumping `Unable to resolve host "api.openai.com"…` etc.).
     */
    fun friendlyMessage(error: Throwable): String {
        var cur: Throwable? = error
        while (cur != null) {
            when (cur) {
                is UnknownHostException,
                is ConnectException,
                is NoRouteToHostException,
                -> return NO_INTERNET
                is SocketTimeoutException,
                is TimeoutException,
                -> return REQUEST_TIMEOUT
                is IOException -> {
                    val m = cur.message.orEmpty()
                    if (looksOffline(m)) return NO_INTERNET
                    if (looksTimeout(m)) return REQUEST_TIMEOUT
                }
            }
            val m = cur.message.orEmpty()
            if (looksOffline(m)) return NO_INTERNET
            if (looksTimeout(m)) return REQUEST_TIMEOUT
            cur = cur.cause
        }
        val raw = error.message?.trim().orEmpty()
        return raw.ifBlank { error.javaClass.simpleName }.take(220)
    }

    private fun looksOffline(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("unable to resolve host") ||
            m.contains("no address associated") ||
            m.contains("network is unreachable") ||
            m.contains("failed to connect") ||
            m.contains("connection refused") ||
            m.contains("enotconn") ||
            m.contains("enetunreach") ||
            m.contains("econnrefused") ||
            m.contains("ehostunreach") ||
            m.contains("cleartext") ||
            (m.contains("api.openai.com") && (
                m.contains("resolve") ||
                    m.contains("connect") ||
                    m.contains("unreachable") ||
                    m.contains("unknownhost")
                ))
    }

    private fun looksTimeout(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("timed out") ||
            m.contains("timeout") ||
            m.contains("took too long")
    }
}
