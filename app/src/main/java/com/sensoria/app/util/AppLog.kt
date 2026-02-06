package com.sensoria.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal app log that can be uploaded without adb/logcat access.
 */
object AppLog {
    private const val LOG_FILE_NAME = "app-log.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    // Phase 0: Dedicated logging tags
    const val TAG_SENSORIA_SUB = "SENSORIA_SUB"
    const val TAG_SENSORIA_NOTIFY = "SENSORIA_NOTIFY"
    const val TAG_UPLOAD_BUNDLE = "UPLOAD_BUNDLE"

    @Synchronized
    fun i(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        append(context, "I", tag, message)
    }

    @Synchronized
    fun w(context: Context, tag: String, message: String) {
        Log.w(tag, message)
        append(context, "W", tag, message)
    }

    @Synchronized
    fun e(context: Context, tag: String, message: String) {
        Log.e(tag, message)
        append(context, "E", tag, message)
    }

    @Synchronized
    fun snapshotToFile(context: Context, outputFileName: String, maxLines: Int = 2000): File? {
        val src = File(context.filesDir, LOG_FILE_NAME)
        if (!src.exists()) return null

        val lines = try {
            src.readLines()
        } catch (_: Exception) {
            return null
        }

        val startIndex = (lines.size - maxLines).coerceAtLeast(0)
        val output = File(context.filesDir, outputFileName)
        output.writeText(lines.subList(startIndex, lines.size).joinToString("\n"))
        return output
    }

    private fun append(context: Context, level: String, tag: String, message: String) {
        val ts = dateFormat.format(Date())
        val line = "$ts [$level/$tag] $message\n"
        val file = File(context.filesDir, LOG_FILE_NAME)
        file.appendText(line)
    }
}
