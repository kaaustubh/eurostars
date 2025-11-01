package com.sensars.eurostars.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Read release notes from assets folder
 */
suspend fun readReleaseNotes(context: Context): String = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.assets.open("RELEASE_NOTES.md")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val content = reader.readText()
        reader.close()
        inputStream.close()
        content
    } catch (e: Exception) {
        "Unable to load release notes."
    }
}

