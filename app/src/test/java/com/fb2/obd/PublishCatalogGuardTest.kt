package com.fb2.obd

import com.fb2.obd.obd.AppUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Guards against repeating the 0.1.22 publish mistake: catalog on `latest`
 * without versions.json → Settings → App update HTTP 404.
 *
 * Also guards the security rule: APKs must be GitHub Release assets, never
 * committed under dist/archive/.
 */
class PublishCatalogGuardTest {

    private val repoRoot: File = findRepoRoot()

    private fun findRepoRoot(): File {
        var dir = File(checkNotNull(System.getProperty("user.dir")))
        while (true) {
            if (File(dir, "gradlew").exists() && File(dir, "dist").isDirectory) return dir
            dir = dir.parentFile ?: error("repo root not found")
        }
    }

    private val distDir = File(repoRoot, "dist")
    private val publishScript = File(repoRoot, "scripts/publish-latest-apk.sh")
    private val verifyScript = File(repoRoot, "scripts/verify-latest-catalog.sh")

    @Test
    fun publishScript_copiesCatalogJsonAndUploadsReleaseAssets() {
        assertTrue("missing ${publishScript.path}", publishScript.isFile)
        val script = publishScript.readText()
        assertTrue(
            "publish script must copy versions.json",
            script.contains("cp dist/versions.json"),
        )
        assertTrue(
            "publish script must copy version.json",
            script.contains("cp dist/version.json"),
        )
        assertTrue(
            "publish script must create/upload a GitHub Release",
            script.contains("gh release"),
        )
        assertFalse(
            "publish script must not push APKs to the latest branch",
            script.contains("cp dist/FB2-Diag-debug.apk \"$TMP/dist/\"") ||
                script.contains("cp dist/archive"),
        )
        assertTrue(
            "publish script must invoke catalog verification",
            script.contains("verify-latest-catalog.sh"),
        )
    }

    @Test
    fun verifyScript_checksVersionsAndVersionJsonUrls() {
        assertTrue("missing ${verifyScript.path}", verifyScript.isFile)
        val script = verifyScript.readText()
        assertTrue(script.contains("versions.json"))
        assertTrue(script.contains("version.json"))
        assertTrue(script.contains("/releases/"))
    }

    @Test
    fun distCatalog_matchesInstalledAppVersion() {
        val versionsFile = File(distDir, "versions.json")
        val versionFile = File(distDir, "version.json")
        assertTrue("commit dist/versions.json", versionsFile.isFile)
        assertTrue("commit dist/version.json", versionFile.isFile)

        val catalog = AppUpdateChecker.parseCatalog(versionsFile.readText())
        val fallback = AppUpdateChecker.parseCatalog(versionFile.readText())

        assertEquals(
            "dist/version.json must match dist/versions.json latest.versionCode",
            catalog.latest.versionCode,
            fallback.latest.versionCode,
        )
        assertEquals(
            "dist/version.json must match dist/versions.json latest.versionName",
            catalog.latest.versionName,
            fallback.latest.versionName,
        )
        assertEquals(
            "BuildConfig.VERSION_CODE must match dist catalog latest",
            BuildConfig.VERSION_CODE,
            catalog.latest.versionCode,
        )
        assertEquals(
            "BuildConfig.VERSION_NAME must match dist catalog latest",
            BuildConfig.VERSION_NAME,
            catalog.latest.versionName,
        )
        assertTrue(
            "latest must appear in releases[]",
            catalog.releases.any { it.versionCode == catalog.latest.versionCode },
        )
    }

    @Test
    fun distCatalog_apkUrlsAreGitHubReleaseAssets() {
        val catalog = AppUpdateChecker.parseCatalog(File(distDir, "versions.json").readText())
        for (release in catalog.releases + catalog.latest) {
            assertTrue(
                "apkUrl must be a GitHub Release asset: ${release.apkUrl}",
                release.apkUrl.contains("github.com/iumer/FB2-OBD-app/releases/"),
            )
            assertFalse(
                "apkUrl must not point at committed dist/archive/: ${release.apkUrl}",
                release.apkUrl.contains("/dist/archive/"),
            )
            assertFalse(
                "apkUrl must not use raw FB2-Diag-debug.apk: ${release.apkUrl}",
                release.apkUrl.contains("FB2-Diag-debug.apk"),
            )
        }
    }

    @Test
    fun distCatalog_urlsUseCorrectGitHubRepo() {
        val wrongRepo = Pattern.compile("raw\\.githubusercontent\\.com/lumer/", Pattern.CASE_INSENSITIVE)
        val catalog = AppUpdateChecker.parseCatalog(File(distDir, "versions.json").readText())
        for (release in catalog.releases + catalog.latest) {
            assertTrue(
                "bad repo slug in ${release.versionName} apkUrl (expected iumer not lumer): ${release.apkUrl}",
                !wrongRepo.matcher(release.apkUrl).find(),
            )
            assertTrue(
                "apkUrl must point at iumer/FB2-OBD-app: ${release.apkUrl}",
                release.apkUrl.contains("iumer/FB2-OBD-app"),
            )
        }
    }

    @Test
    fun distArchive_mustNotExistInRepo() {
        val archive = File(distDir, "archive")
        assertFalse(
            "dist/archive/ must not be committed (APKs are GitHub Release assets)",
            archive.exists() && archive.walkTopDown().any { it.extension == "apk" },
        )
    }
}
