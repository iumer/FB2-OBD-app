package com.fb2.obd.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists uncaught exceptions so a crash on the car is diagnosable.
 *
 * The app has repeatedly crashed on the user's phone while the whole JVM +
 * Robolectric suite stayed green. Without a stack trace every fix is guesswork,
 * and the user cannot be expected to run `adb logcat` in a car. This writes the
 * trace — plus the recent ELM traffic that led to it — to a file that survives
 * the crash, and the app offers to share it on next launch.
 */
object CrashReporter {

    const val DIR_NAME = "crash_reports"
    private const val MAX_REPORTS = 10

    /** Installed once from [com.fb2.obd.Fb2App]; chains to the platform handler. */
    fun install(context: Context, versionName: String, versionCode: Int) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Never let reporting itself replace the real crash.
            runCatching { write(appContext, versionName, versionCode, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun reportsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /** Newest first. */
    fun listReports(context: Context): List<File> =
        reportsDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun latestReport(context: Context): File? = listReports(context).firstOrNull()

    fun clearReports(context: Context) {
        listReports(context).forEach { runCatching { it.delete() } }
    }

    fun write(
        context: Context,
        versionName: String,
        versionCode: Int,
        threadName: String,
        error: Throwable,
    ): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(reportsDir(context), "FB2-crash-$stamp.txt")
        file.writeText(buildReport(versionName, versionCode, threadName, error))
        prune(context)
        return file
    }

    fun buildReport(
        versionName: String,
        versionCode: Int,
        threadName: String,
        error: Throwable,
    ): String {
        val trace = StringWriter().also { sw -> error.printStackTrace(PrintWriter(sw)) }.toString()
        return buildString {
            appendLine("FB2 Diag crash report")
            appendLine("time:      ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            appendLine("app:       $versionName ($versionCode)")
            appendLine("device:    ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android:   ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("thread:    $threadName")
            appendLine("exception: ${error.javaClass.name}: ${error.message}")
            appendLine()
            appendLine("=== stack trace ===")
            appendLine(trace.trim())
            appendLine()
            appendLine("=== recent ELM / app log ===")
            appendLine(runCatching { ObdLogger.debugText() }.getOrElse { "(log unavailable: ${it.message})" })
        }
    }

    private fun prune(context: Context) {
        listReports(context).drop(MAX_REPORTS).forEach { runCatching { it.delete() } }
    }
}
