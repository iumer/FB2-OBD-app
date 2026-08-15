package com.fb2.obd

import com.fb2.obd.obd.ElmLinkForensics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ElmLinkForensicsTest {

    @Test
    fun message_includesKeyFieldsForDriveLog() {
        val msg = ElmLinkForensics.message(
            reason = ElmLinkForensics.REASON_SOFT_RECOVER,
            unable = 3,
            timeouts = 1,
            deadCycles = 2,
            softRecover = 2,
            mode01Ok = false,
            atrvOk = true,
            atrvV = 12.4,
            lastOkPid = "010C",
            silenceMs = 3200,
            cycleMs = 1800,
            pidsThisCycle = 8,
            socketConnected = true,
            detail = "UNABLE×3",
        )
        assertTrue(msg.contains("reason=SOFT_RECOVER"))
        assertTrue(msg.contains("unable=3"))
        assertTrue(msg.contains("atrvOk=true"))
        assertTrue(msg.contains("atrvV=12.4"))
        assertTrue(msg.contains("lastOkPid=010C"))
        assertTrue(msg.contains("socket=up"))
        assertTrue(msg.contains("detail=UNABLE×3"))
    }

    @Test
    fun reasonFromThrowable_classifiesSocketVsTimeout() {
        assertEquals(
            ElmLinkForensics.REASON_SOCKET_CLOSED,
            ElmLinkForensics.reasonFromThrowable(IOException("socket closed during '010C'")),
        )
        assertEquals(
            ElmLinkForensics.REASON_READ_TIMEOUT,
            ElmLinkForensics.reasonFromThrowable(IOException("read timeout for '010D'")),
        )
        assertEquals(
            ElmLinkForensics.REASON_HARD_RECONNECT,
            ElmLinkForensics.reasonFromThrowable(
                IOException("ECU bus lost after 5 soft recovers — reconnecting"),
            ),
        )
    }
}
