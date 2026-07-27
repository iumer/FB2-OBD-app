package com.fb2.obd

import com.fb2.obd.data.SessionLogStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLogStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun saveSession_keepsSeparateFiles() {
        val store = SessionLogStore(tmp.newFolder("logs"))
        val a = store.saveSession("# dashboard_snapshots\na", startedMs = 1_700_000_000_000L)
        val b = store.saveSession("# dashboard_snapshots\nb", startedMs = 1_700_000_100_000L)
        assertTrue(a.fileName != b.fileName)
        assertEquals(2, store.list().size)
        assertEquals("# dashboard_snapshots\na", store.read(a.fileName))
        assertEquals("# dashboard_snapshots\nb", store.read(b.fileName))
    }

    @Test
    fun saveSession_sameSecond_getsSuffix() {
        val store = SessionLogStore(tmp.newFolder("logs2"))
        val t = 1_700_000_000_000L
        val a = store.saveSession("one", startedMs = t)
        val b = store.saveSession("two", startedMs = t)
        assertTrue(a.fileName != b.fileName)
        assertTrue(b.fileName.contains("-2") || a.fileName.contains("-2"))
    }

    @Test
    fun saveSession_demo_putsDemoInFileName() {
        val store = SessionLogStore(tmp.newFolder("logs3"))
        val saved = store.saveSession("# demo csv", startedMs = 1_700_000_000_000L, isDemo = true)
        assertTrue(saved.fileName.startsWith("FB2-log-demo-"))
        assertTrue(saved.fileName.endsWith(".csv"))
        val live = store.saveSession("# live csv", startedMs = 1_700_000_100_000L, isDemo = false)
        assertTrue(live.fileName.startsWith("FB2-log-"))
        assertTrue(!live.fileName.startsWith("FB2-log-demo-"))
    }
}
