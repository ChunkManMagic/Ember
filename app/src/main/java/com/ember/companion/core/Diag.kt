package com.ember.companion.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ember's own crash and diagnostic capture.
 *
 * Termux (and this build's own tooling) cannot read logcat, so the app cannot
 * ask the user to paste a stack trace. Instead Ember persists the last report
 * to its own prefs, shows it in-app, and also drops a copy into the public
 * Downloads collection via MediaStore — which Termux *can* read, so a crash can
 * be diagnosed with one command and no user effort.
 */
object Diag {

    private const val PREFS = "ember_diag"
    private const val KEY_PENDING_CRASH = "pending_crash"
    private const val KEY_AT = "pending_crash_at"
    private const val KEY_RING = "ring"
    private const val RING_LINES = 300
    private const val MAX_REPORT_CHARS = 24_000

    private val lock = Any()
    private val ring = ArrayDeque<String>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var installed = false

    /** Lightweight logging that survives the process, for hard-to-reproduce faults. */
    fun log(message: String) {
        val line = "${timestamp()}  $message"
        synchronized(lock) {
            ring.addLast(line)
            while (ring.size > RING_LINES) ring.removeFirst()
        }
    }

    fun logLines(): List<String> = synchronized(lock) { ring.toList() }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        lastCrashMillis = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_AT, 0L).takeIf { it > 0L }
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val report = buildReport(thread, error)
            try {
                log("FATAL ${thread.name}: ${error.javaClass.name}: ${error.message}")
                runCatching { log(error.stackTraceToString()) }
                persistPending(app, report)
                lastCrashMillis = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getLong(KEY_AT, 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
                writeToDownloads(app, report)
            } catch (t: Throwable) {
                // Never mask the original crash with a failure to record it.
                runCatching { android.util.Log.e("Ember", "crash reporting failed", t) }
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }

    /** Returns and clears any crash recorded before this launch. */
    fun consumePending(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val report = prefs.getString(KEY_PENDING_CRASH, null) ?: return null
        prefs.edit().remove(KEY_PENDING_CRASH).apply()
        return report
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PENDING_CRASH).remove(KEY_AT).apply()
        lastCrashMillis = null
    }

    fun clearRing() = synchronized(lock) { ring.clear() }

    /** Human-readable timestamp of the last recorded crash, or "never". */
    fun lastCrashTimestamp(): String {
        val millis = lastCrashMillis ?: return "never"
        return SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    /** Set by [install]; only used by [lastCrashTimestamp]. */
    private var lastCrashMillis: Long? = null

    private fun persistPending(context: Context, report: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_CRASH, report.take(MAX_REPORT_CHARS))
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    private fun buildReport(thread: Thread, error: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val body = sw.toString()
        return buildString {
            appendLine("Ember crash report")
            appendLine("when: ${timestamp()}")
            appendLine("thread: ${thread.name}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("version: ${com.ember.companion.BuildConfig.VERSION_NAME} (${com.ember.companion.BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("--- stack trace ---")
            append(body.take(MAX_REPORT_CHARS))
            appendLine()
            appendLine("--- last ${RING_LINES} diagnostics ---")
            synchronized(lock) { ring.forEach { appendLine(it) } }
        }
    }

    /**
     * Writes the report where Termux can read it. Uses the MediaStore Downloads
     * collection on API 29+, which needs no permission; falls back to
     * app-external storage otherwise.
     */
    private fun writeToDownloads(context: Context, report: String) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "Ember-crash-$stamp.txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val inserted = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(report.toByteArray(Charsets.UTF_8))
                } ?: return@runCatching false
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
                true
            }.getOrDefault(false)
            if (inserted) return
        }

        // Pre-Q, or MediaStore refused: app-specific external dir. Termux cannot
        // always read this, so the in-app crash screen remains the fallback.
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, "crash-latest.txt").writeText(report)
        }
        runCatching {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Ember",
            )
            if (dir.exists() || dir.mkdirs()) File(dir, fileName).writeText(report)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}
