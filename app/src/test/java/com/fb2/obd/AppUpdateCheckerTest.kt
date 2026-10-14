package com.fb2.obd

import com.fb2.obd.obd.AppUpdateChecker
import com.fb2.obd.obd.AppUpdateChecker.RemoteVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    private val v16 = RemoteVersion(16, "0.1.16", "https://ex/16.apk", "wheel scroll")
    private val v17 = RemoteVersion(17, "0.1.17", "https://ex/17.apk", "OptB clip")
    private val v18 = RemoteVersion(18, "0.1.18", "https://ex/18.apk", "dial digits")
    private val v19 = RemoteVersion(19, "0.1.19", "https://ex/19.apk")
    private val v20 = RemoteVersion(20, "0.1.20", "https://ex/20.apk", "forensics")

    @Test
    fun parseLegacyVersionJson_asSingleReleaseCatalog() {
        val catalog = AppUpdateChecker.parseCatalog(
            """
            {
              "versionCode": 21,
              "versionName": "0.1.21",
              "apkUrl": "https://example.com/FB2.apk"
            }
            """.trimIndent(),
        )
        assertEquals(21, catalog.latest.versionCode)
        assertEquals("0.1.21", catalog.latest.versionName)
        assertEquals("https://example.com/FB2.apk", catalog.latest.apkUrl)
        assertEquals(1, catalog.releases.size)
    }

    @Test
    fun parseCatalog_readsLatestAndReleases() {
        val catalog = AppUpdateChecker.parseCatalog(
            """
            {
              "latest": {"versionCode":20,"versionName":"0.1.20","apkUrl":"https://ex/latest.apk"},
              "releases": [
                {"versionCode":16,"versionName":"0.1.16","apkUrl":"https://ex/16.apk","notes":"a"},
                {"versionCode":18,"versionName":"0.1.18","apkUrl":"https://ex/18.apk","notes":"b"},
                {"versionCode":20,"versionName":"0.1.20","apkUrl":"https://ex/20.apk","notes":"c"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(20, catalog.latest.versionCode)
        assertEquals(listOf(16, 18, 20), catalog.releases.map { it.versionCode })
        assertEquals("a", catalog.releases[0].notes)
    }

    @Test
    fun parseCatalogFromGitHubContents_decodesBase64() {
        val inner = """{"versionCode":29,"versionName":"0.1.29","apkUrl":"https://example.com/x.apk"}"""
        val b64 = java.util.Base64.getEncoder().encodeToString(inner.toByteArray())
        val api = """{"name":"version.json","content":"$b64"}"""
        val catalog = AppUpdateChecker.parseCatalogFromGitHubContents(api)
        assertEquals(29, catalog.latest.versionCode)
        assertEquals("0.1.29", catalog.latest.versionName)
    }

    @Test
    fun newerThan_on115_lists116through120_oldestFirst() {
        val newer = AppUpdateChecker.newerThan(
            localCode = 15,
            releases = listOf(v20, v16, v18, v17, v19, v16),
        )
        assertEquals(listOf("0.1.16", "0.1.17", "0.1.18", "0.1.19", "0.1.20"), newer.map { it.versionName })
    }

    @Test
    fun newerThan_afterInstalling118_onlyShows119and120() {
        val newer = AppUpdateChecker.newerThan(
            localCode = 18,
            releases = listOf(v16, v17, v18, v19, v20),
        )
        assertEquals(listOf("0.1.19", "0.1.20"), newer.map { it.versionName })
    }

    @Test
    fun compare_availableWhenAnyNewer() {
        val catalog = AppUpdateChecker.Catalog(latest = v20, releases = listOf(v16, v18, v20))
        val result = AppUpdateChecker.compare(localCode = 15, localName = "0.1.15", catalog = catalog)
        assertTrue(result is AppUpdateChecker.Result.Available)
        val available = result as AppUpdateChecker.Result.Available
        assertEquals(listOf(16, 18, 20), available.newer.map { it.versionCode })
        assertEquals("0.1.15", available.currentName)
    }

    @Test
    fun compare_upToDateWhenNothingNewer() {
        val catalog = AppUpdateChecker.Catalog(latest = v20, releases = listOf(v16, v20))
        val result = AppUpdateChecker.compare(localCode = 20, localName = "0.1.20", catalog = catalog)
        assertTrue(result is AppUpdateChecker.Result.UpToDate)
        val up = result as AppUpdateChecker.Result.UpToDate
        assertEquals("0.1.20", up.latestName)
    }

    @Test
    fun compare_upToDateWhenRemoteOlderThanInstalled() {
        val catalog = AppUpdateChecker.Catalog(latest = v16, releases = listOf(v16))
        val result = AppUpdateChecker.compare(localCode = 35, localName = "0.1.15-revert", catalog = catalog)
        assertTrue(result is AppUpdateChecker.Result.UpToDate)
    }
}
