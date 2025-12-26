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
        rightData: List<WalkModeRepository.PressureDataPoint>,
        leftAccel: List<WalkModeRepository.ImuDataPoint> = emptyList(),
        rightAccel: List<WalkModeRepository.ImuDataPoint> = emptyList(),
        leftGyro: List<WalkModeRepository.ImuDataPoint> = emptyList(),
        rightGyro: List<WalkModeRepository.ImuDataPoint> = emptyList()
    ): WalkSession = withContext(Dispatchers.IO) {
        
        val timestamp = System.currentTimeMillis()
        
        // 1. Save calibrated data to CSV file (internal storage)
        val fileName = "session_${timestamp}.csv"
        val dataFile = File(context.filesDir, fileName)
        val csvContent = generateCsvContent(
            leftData, rightData, 
            leftAccel, rightAccel, 
            leftGyro, rightGyro
        )
        dataFile.writeText(csvContent)
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
        leftAccel: List<WalkModeRepository.ImuDataPoint>,
        rightAccel: List<WalkModeRepository.ImuDataPoint>,
        leftGyro: List<WalkModeRepository.ImuDataPoint>,
        rightGyro: List<WalkModeRepository.ImuDataPoint>
    ): String {
        val sb = StringBuilder()
        // Header
        sb.append("timestamp,foot")
        for (i in 1..18) sb.append(",taxel${i}_kpa")
        sb.append(",accel_x,accel_y,accel_z")
        sb.append(",gyro_x,gyro_y,gyro_z")
        sb.append("\n")
        
        data class Event(
            val time: Long, 
            val foot: String, 
            val type: String, // "pressure", "accel", "gyro"
            val index: Int = -1, 
            val value: Double = 0.0,
            val x: Float = 0f,
            val y: Float = 0f,
            val z: Float = 0f
        )
        
        val events = mutableListOf<Event>()
        
        leftData.forEach { 
            events.add(Event(it.timestamp, "Left", "pressure", it.taxelIndex, it.calibratedValue))
        }
        rightData.forEach { 
            events.add(Event(it.timestamp, "Right", "pressure", it.taxelIndex, it.calibratedValue))
        }
        leftAccel.forEach {
            events.add(Event(it.timestamp, "Left", "accel", x = it.x, y = it.y, z = it.z))
        }
        rightAccel.forEach {
            events.add(Event(it.timestamp, "Right", "accel", x = it.x, y = it.y, z = it.z))
        }
        leftGyro.forEach {
            events.add(Event(it.timestamp, "Left", "gyro", x = it.x, y = it.y, z = it.z))
        }
        rightGyro.forEach {
            events.add(Event(it.timestamp, "Right", "gyro", x = it.x, y = it.y, z = it.z))
        }
        
        // Group by (timestamp, foot)
        val grouped = events.groupBy { Pair(it.time, it.foot) }.toSortedMap { a, b ->
            val timeDiff = a.first.compareTo(b.first)
            if (timeDiff != 0) timeDiff else a.second.compareTo(b.second)
        }
        
        grouped.forEach { (key, groupEvents) ->
            val (time, foot) = key
            val pressureValues = DoubleArray(18)
            var ax = 0f; var ay = 0f; var az = 0f
            var gx = 0f; var gy = 0f; var gz = 0f
            
            groupEvents.forEach { event ->
                when (event.type) {
                    "pressure" -> if (event.index in 0..17) pressureValues[event.index] = event.value
                    "accel" -> { ax = event.x; ay = event.y; az = event.z }
                    "gyro" -> { gx = event.x; gy = event.y; gz = event.z }
                }
            }
            
            sb.append(time).append(",").append(foot)
            for (v in pressureValues) {
                sb.append(",").append(v)
            }
            sb.append(",").append(ax).append(",").append(ay).append(",").append(az)
            sb.append(",").append(gx).append(",").append(gy).append(",").append(gz)
            sb.append("\n")
        }
        
        return sb.toString()
    }
}
