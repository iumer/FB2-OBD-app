package com.fb2.obd

import com.fb2.obd.data.DashExtraPidStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DashExtraPidStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTrip_persistsExtraPidIds() {
        val file = File(tmp.root, "dash_extra_pids.json")
        val store = DashExtraPidStore(file)
        store.save(listOf("0114", "0115", "0124"))
        val loaded = DashExtraPidStore(file).load()
        assertEquals(listOf("0114", "0115", "0124"), loaded)
        assertTrue(file.exists())
    }

    @Test
    fun save_dedupesAndUppercases() {
        val file = File(tmp.root, "dash_extra_pids.json")
        val store = DashExtraPidStore(file)
        store.save(listOf("0114", " 0114 ", "0115", "", "0114"))
        assertEquals(listOf("0114", "0115"), store.load())
    }

    @Test
    fun missingFile_returnsEmpty() {
        val store = DashExtraPidStore(File(tmp.root, "missing.json"))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun corruptFile_returnsEmpty() {
        val file = File(tmp.root, "dash_extra_pids.json")
        file.writeText("{not json")
        assertTrue(DashExtraPidStore(file).load().isEmpty())
    }
}
