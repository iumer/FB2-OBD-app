package com.fb2.obd

import com.fb2.obd.obd.AppUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun parseVersionJson_readsCodeNameAndApkUrl() {
        val remote = AppUpdateChecker.parseVersionJson(
            """
            {
              "versionCode": 21,
              "versionName": "0.1.21",
              "apkUrl": "https://example.com/FB2.apk"
            }
            """.trimIndent(),
        )
        assertEquals(21, remote.versionCode)
        assertEquals("0.1.21", remote.versionName)
        assertEquals("https://example.com/FB2.apk", remote.apkUrl)
    }

    @Test
    fun parseVersionJson_defaultsApkUrlWhenOmitted() {
        val remote = AppUpdateChecker.parseVersionJson(
            """{"versionCode":20,"versionName":"0.1.20"}""",
        )
        assertEquals(AppUpdateChecker.DEFAULT_APK_URL, remote.apkUrl)
    }

    @Test
    fun parseVersionJsonFromGitHubContents_decodesBase64Manifest() {
        val inner = """{"versionCode":29,"versionName":"0.1.29","apkUrl":"https://example.com/x.apk"}"""
        val b64 = java.util.Base64.getEncoder().encodeToString(inner.toByteArray())
        val api = """{"name":"version.json","content":"$b64"}"""
        val remote = AppUpdateChecker.parseVersionJsonFromGitHubContents(api)
        assertEquals(29, remote.versionCode)
        assertEquals("0.1.29", remote.versionName)
        assertEquals("https://example.com/x.apk", remote.apkUrl)
    }

    @Test
    fun compare_availableWhenRemoteCodeHigher() {
        val result = AppUpdateChecker.compare(
            localCode = 19,
            localName = "0.1.19",
            remote = AppUpdateChecker.RemoteVersion(21, "0.1.21"),
        )
        assertTrue(result is AppUpdateChecker.Result.Available)
        val available = result as AppUpdateChecker.Result.Available
        assertEquals("0.1.19", available.currentName)
        assertEquals(21, available.remote.versionCode)
    }

    @Test
    fun compare_upToDateWhenRemoteCodeEqualOrLower() {
        val equal = AppUpdateChecker.compare(
            localCode = 21,
            localName = "0.1.21",
            remote = AppUpdateChecker.RemoteVersion(21, "0.1.21"),
        )
        assertTrue(equal is AppUpdateChecker.Result.UpToDate)

        val olderRemote = AppUpdateChecker.compare(
            localCode = 21,
            localName = "0.1.21",
            remote = AppUpdateChecker.RemoteVersion(20, "0.1.20"),
        )
        assertTrue(olderRemote is AppUpdateChecker.Result.UpToDate)
    }
}
