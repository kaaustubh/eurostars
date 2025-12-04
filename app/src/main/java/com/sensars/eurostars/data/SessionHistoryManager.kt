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
    val displaySessionId: String, // The incrementing ID (1, 2, 3...) - assigned at upload or creation? 
                                  // Problem: If upload fails, we don't know the remote ID yet. 
                                  // Solution: Use local timestamp/UUID as key, and display "Pending" for remote ID until uploaded.
                                  // OR: Just use local incrementing ID if we are the only source of truth. 
                                  // Given the requirement "session id", let's persist the one we try to assign.
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
        
        // 1. Save data to a file
        val fileName = "session_${System.currentTimeMillis()}.json"
        val dataFile = File(context.filesDir, fileName)
        
        val dataJson = JSONObject()
        dataJson.put("left", serializeDataPoints(leftData))
        dataJson.put("right", serializeDataPoints(rightData))
        
        dataFile.writeText(dataJson.toString())
        val sizeBytes = dataFile.length()
        
        // 2. Create session object
        val session = WalkSession(
            sessionId = fileName, // Use filename as unique local ID
            displaySessionId = displaySessionId,
            patientId = patientId,
            startTime = startTime,
            endTime = endTime,
            status = UploadStatus.PENDING,
            dataSizeBytes = sizeBytes,
            fileName = fileName
        )
        
        // 3. Update meta file
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
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext Pair(emptyList(), emptyList())
        
        try {
            val json = JSONObject(file.readText())
            val left = parseDataPoints(json.getJSONArray("left"))
            val right = parseDataPoints(json.getJSONArray("right"))
            return@withContext Pair(left, right)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Pair(emptyList(), emptyList())
        }
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
    
    private fun serializeDataPoints(points: List<WalkModeRepository.PressureDataPoint>): JSONArray {
        val arr = JSONArray()
        points.forEach { p ->
            // Compact format: array of [t, i, v] instead of object {t:_, i:_, v:_}
            val pointArr = JSONArray()
            pointArr.put(p.timestamp)
            pointArr.put(p.taxelIndex)
            pointArr.put(p.value)
            arr.put(pointArr)
        }
        return arr
    }
    
    private fun parseDataPoints(arr: JSONArray): List<WalkModeRepository.PressureDataPoint> {
        val list = mutableListOf<WalkModeRepository.PressureDataPoint>()
        for (i in 0 until arr.length()) {
            val item = arr.get(i)
            if (item is JSONArray) {
                // Parse compact format [t, i, v]
                list.add(WalkModeRepository.PressureDataPoint(
                    timestamp = item.getLong(0),
                    taxelIndex = item.getInt(1),
                    value = item.getLong(2)
                ))
            } else if (item is JSONObject) {
                // Backward compatibility for object format {t, i, v}
                list.add(WalkModeRepository.PressureDataPoint(
                    timestamp = item.getLong("t"),
                    taxelIndex = item.getInt("i"),
                    value = item.getLong("v")
                ))
            }
        }
        return list
    }
}

