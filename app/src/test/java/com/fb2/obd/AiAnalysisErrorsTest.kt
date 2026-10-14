package com.fb2.obd

import com.fb2.obd.data.AiAnalysisErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiAnalysisErrorsTest {

    @Test
    fun unknownHost_mapsToNoInternet() {
        val msg = AiAnalysisErrors.friendlyMessage(
            UnknownHostException("Unable to resolve host \"api.openai.com\": No address associated with hostname"),
        )
        assertEquals(AiAnalysisErrors.NO_INTERNET, msg)
    }

    @Test
    fun wrappedIoException_mapsToNoInternet() {
        val wrapped = RuntimeException(
            "call failed",
            ConnectException("failed to connect to api.openai.com/0.0.0.0 (port 443)"),
        )
        assertEquals(AiAnalysisErrors.NO_INTERNET, AiAnalysisErrors.friendlyMessage(wrapped))
    }

    @Test
    fun timeout_mapsToTimeoutMessage() {
        assertEquals(
            AiAnalysisErrors.REQUEST_TIMEOUT,
            AiAnalysisErrors.friendlyMessage(SocketTimeoutException("timeout")),
        )
    }

    @Test
    fun otherErrors_keepShortMessage() {
        val msg = AiAnalysisErrors.friendlyMessage(IllegalStateException("OpenAI returned an empty report"))
        assertTrue(msg.contains("empty report"))
    }
}
