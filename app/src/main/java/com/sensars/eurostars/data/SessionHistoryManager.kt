package com.sensars.eurostars.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}

data class WalkSession(
    val sessionId: String, // Local unique ID (timestamp-based or UUID)
    val displaySessionId: String, 
    val patientId: String,
    val startTime: Long,
    val endTime: Long,
    val status: UploadStatus,
    val dataSizeBytes: Long,
    val fileName: String // Pointer to the data file
)

class SessionHistoryManager(private val context: Context) {
    private val metaFile = File(context.filesDir, "sessions_meta.json")

    suspend fun saveSession(
        displaySessionId: String,
        patientId: String,
        startTime: Long,
        endTime: Long,
        leftData: List<WalkModeRepository.PressureDataPoint>,
        rightData: List<WalkModeRepository.PressureDataPoint>
    ): WalkSession = withContext(Dispatchers.IO) {
        
        val timestamp = System.currentTimeMillis()
        
        // 1. Save calibrated data to CSV file (internal storage)
        val calibratedFileName = "session_${timestamp}_calibrated.csv"
        val calibratedFile = File(context.filesDir, calibratedFileName)
        val calibratedCsvContent = generateCsvContent(leftData, rightData, useCalibrated = true)
        calibratedFile.writeText(calibratedCsvContent)
        
        // 2. Save raw data to CSV file (internal storage)
        val rawFileName = "session_${timestamp}_raw.csv"
        val rawFile = File(context.filesDir, rawFileName)
        val rawCsvContent = generateCsvContent(leftData, rightData, useCalibrated = false)
        rawFile.writeText(rawCsvContent)
        
        // Use calibrated file as the main file (for backward compatibility)
        val sizeBytes = calibratedFile.length()
        
        // 3. Create session object
        val session = WalkSession(
            sessionId = calibratedFileName, // Use calibrated filename as unique local ID
            displaySessionId = displaySessionId,
            patientId = patientId,
            startTime = startTime,
            endTime = endTime,
            status = UploadStatus.PENDING,
            dataSizeBytes = sizeBytes,
            fileName = calibratedFileName
        )
        
        // 4. Update meta file
        val sessions = getSessions().toMutableList()
        sessions.add(0, session) // Add to top
        saveSessionsMeta(sessions)
        
        return@withContext session
    }

    suspend fun getSessions(): List<WalkSession> = withContext(Dispatchers.IO) {
        if (!metaFile.exists()) return@withContext emptyList()
        
        try {
            val jsonStr = metaFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<WalkSession>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(WalkSession(
                    sessionId = obj.getString("sessionId"),
                    displaySessionId = obj.optString("displaySessionId", "?"),
                    patientId = obj.getString("patientId"),
                    startTime = obj.getLong("startTime"),
                    endTime = obj.getLong("endTime"),
                    status = UploadStatus.valueOf(obj.getString("status")),
                    dataSizeBytes = obj.getLong("dataSizeBytes"),
                    fileName = obj.getString("fileName")
                ))
            }
            return@withContext list
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun updateSessionStatus(sessionId: String, status: UploadStatus, remoteId: String? = null) = withContext(Dispatchers.IO) {
        val sessions = getSessions().toMutableList()
        val index = sessions.indexOfFirst { it.sessionId == sessionId }
        if (index != -1) {
            val old = sessions[index]
            val newSession = old.copy(
                status = status,
                displaySessionId = remoteId ?: old.displaySessionId
            )
            sessions[index] = newSession
            saveSessionsMeta(sessions)
        }
    }

    suspend fun getSessionData(fileName: String): Pair<List<WalkModeRepository.PressureDataPoint>, List<WalkModeRepository.PressureDataPoint>> = withContext(Dispatchers.IO) {
        // We no longer parse CSV back to objects since upload just uses the file directly.
        // Returning empty lists to satisfy signature.
        return@withContext Pair(emptyList(), emptyList())
    }
    
    private fun saveSessionsMeta(sessions: List<WalkSession>) {
        val jsonArray = JSONArray()
        sessions.forEach { session ->
            val obj = JSONObject()
            obj.put("sessionId", session.sessionId)
            obj.put("displaySessionId", session.displaySessionId)
            obj.put("patientId", session.patientId)
            obj.put("startTime", session.startTime)
            obj.put("endTime", session.endTime)
            obj.put("status", session.status.name)
            obj.put("dataSizeBytes", session.dataSizeBytes)
            obj.put("fileName", session.fileName)
            jsonArray.put(obj)
        }
        metaFile.writeText(jsonArray.toString())
    }
    
    private fun generateCsvContent(
        leftData: List<WalkModeRepository.PressureDataPoint>,
        rightData: List<WalkModeRepository.PressureDataPoint>,
        useCalibrated: Boolean = true
    ): String {
        val sb = StringBuilder()
        // Header
        sb.append("timestamp,foot")
        if (useCalibrated) {
            for (i in 1..18) sb.append(",taxel${i}_pascal")
        } else {
            for (i in 1..18) sb.append(",taxel${i}_raw")
        }
        sb.append("\n")
        
        data class Event(val time: Long, val foot: String, val index: Int, val value: Number)
        
        val events = ArrayList<Event>(leftData.size + rightData.size)
        leftData.forEach { 
            val value = if (useCalibrated) it.calibratedValue else it.rawValue.toDouble()
            events.add(Event(it.timestamp, "Left", it.taxelIndex, value))
        }
        rightData.forEach { 
            val value = if (useCalibrated) it.calibratedValue else it.rawValue.toDouble()
            events.add(Event(it.timestamp, "Right", it.taxelIndex, value))
        }
        
        // Group by (timestamp, foot)
        // We assume sensor sends frames where all taxels share the same timestamp (or close enough that we treat unique timestamps as frames)
        val grouped = events.groupBy { Pair(it.time, it.foot) }.toSortedMap { a, b ->
            val timeDiff = a.first.compareTo(b.first)
            if (timeDiff != 0) timeDiff else a.second.compareTo(b.second)
        }
        
        grouped.forEach { (key, groupEvents) ->
            val (time, foot) = key
            val values = DoubleArray(18) // Default 0.0
            groupEvents.forEach { event ->
                if (event.index in 0..17) {
                    values[event.index] = event.value.toDouble()
                }
            }
            
            sb.append(time).append(",").append(foot)
            for (v in values) {
                sb.append(",").append(v)
            }
            sb.append("\n")
        }
        
        return sb.toString()
    }
}
