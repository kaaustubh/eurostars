package com.sensoria.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.sensoria.app.data.ble.sensoria.SensoriaVerifier
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
        rightGyro: List<WalkModeRepository.ImuDataPoint> = emptyList(),
        leftSensorType: com.sensoria.app.data.ble.SensorType = com.sensoria.app.data.ble.SensorType.CURRENT,
        rightSensorType: com.sensoria.app.data.ble.SensorType = com.sensoria.app.data.ble.SensorType.CURRENT
    ): WalkSession = withContext(Dispatchers.IO) {
        
        val timestamp = System.currentTimeMillis()
        
        // 1. Save calibrated data to CSV file (internal storage)
        val fileName = "session_${timestamp}.csv"
        val dataFile = File(context.filesDir, fileName)
        val csvContent = generateCsvContent(
            leftData, rightData, 
            leftAccel, rightAccel, 
            leftGyro, rightGyro,
            leftSensorType, rightSensorType
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
        rightGyro: List<WalkModeRepository.ImuDataPoint>,
        leftSensorType: com.sensoria.app.data.ble.SensorType,
        rightSensorType: com.sensoria.app.data.ble.SensorType
    ): String {
        // Determine number of channels for each sensor
        val leftChannelCount = when (leftSensorType) {
            com.sensoria.app.data.ble.SensorType.SENSORIA_D20 -> 8
            com.sensoria.app.data.ble.SensorType.SENSORIA_E20 -> 6
            com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 -> 4
            com.sensoria.app.data.ble.SensorType.CURRENT -> 18
        }
        val rightChannelCount = when (rightSensorType) {
            com.sensoria.app.data.ble.SensorType.SENSORIA_D20 -> 8
            com.sensoria.app.data.ble.SensorType.SENSORIA_E20 -> 6
            com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 -> 4
            com.sensoria.app.data.ble.SensorType.CURRENT -> 18
        }
        
        // Use the maximum channel count for CSV (to accommodate both sensors)
        val maxChannels = maxOf(leftChannelCount, rightChannelCount)
        
        val sb = StringBuilder()
        // Header
        sb.append("timestamp,foot")
        // Use channel_0.. for Sensoria sensors, taxel1.. for CURRENT sensors
        // Check if ANY sensor is Sensoria (not CURRENT)
        val isSensoria = leftSensorType != com.sensoria.app.data.ble.SensorType.CURRENT ||
            rightSensorType != com.sensoria.app.data.ble.SensorType.CURRENT
        val isStreamV1 = leftSensorType == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 ||
            rightSensorType == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1
        
        // Also check data to infer sensor type if not set correctly
        // Check if any data point has SENSORIA_STREAM_V1 protocol
        val hasStreamV1Data = leftData.any { it.protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 } ||
            rightData.any { it.protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 } ||
            leftAccel.any { it.protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 } ||
            rightAccel.any { it.protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 } ||
            leftGyro.any { it.protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 } ||
            rightGyro.any { it.protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1 }
        
        // If we have Stream V1 data, force Sensoria mode
        val actualIsStreamV1 = isStreamV1 || hasStreamV1Data
        val actualIsSensoria = isSensoria || hasStreamV1Data || actualIsStreamV1
        
        if (actualIsSensoria) {
            for (i in 0 until maxChannels) sb.append(",channel_${i}_kpa")
        } else {
            for (i in 1..maxChannels) sb.append(",taxel${i}_kpa")
        }
        
        // Phase 2: Add raw_u16 columns for Stream V1
        if (actualIsStreamV1) {
            for (i in 0 until 4) sb.append(",raw_u16_$i")
        }
        
        sb.append(",accel_x,accel_y,accel_z")
        sb.append(",gyro_x,gyro_y,gyro_z")
        sb.append(",protocol,tick,dtick")
        sb.append("\n")
        
        data class Event(
            val time: Long, 
            val foot: String, 
            val type: String, // "pressure", "accel", "gyro"
            val index: Int = -1, 
            val value: Double = 0.0,
            val rawValue: Long = 0L, // Phase 2: Raw uint16 value for V1 frames
            val x: Float = 0f,
            val y: Float = 0f,
            val z: Float = 0f,
            val tick: Int = -1,
            val protocol: com.sensoria.app.data.ble.SensorType = com.sensoria.app.data.ble.SensorType.CURRENT
        )
        
        val events = mutableListOf<Event>()
        
        leftData.forEach { 
            events.add(Event(it.timestamp, "Left", "pressure", it.taxelIndex, it.calibratedValue, rawValue = it.rawValue, tick = it.tick, protocol = it.protocol))
        }
        rightData.forEach { 
            events.add(Event(it.timestamp, "Right", "pressure", it.taxelIndex, it.calibratedValue, rawValue = it.rawValue, tick = it.tick, protocol = it.protocol))
        }
        leftAccel.forEach {
            events.add(Event(it.timestamp, "Left", "accel", x = it.x, y = it.y, z = it.z, tick = it.tick, protocol = it.protocol))
        }
        rightAccel.forEach {
            events.add(Event(it.timestamp, "Right", "accel", x = it.x, y = it.y, z = it.z, tick = it.tick, protocol = it.protocol))
        }
        leftGyro.forEach {
            events.add(Event(it.timestamp, "Left", "gyro", x = it.x, y = it.y, z = it.z, tick = it.tick, protocol = it.protocol))
        }
        rightGyro.forEach {
            events.add(Event(it.timestamp, "Right", "gyro", x = it.x, y = it.y, z = it.z, tick = it.tick, protocol = it.protocol))
        }
        
        // Group by (timestamp, foot)
        val grouped = events.groupBy { Pair(it.time, it.foot) }.toSortedMap { a, b ->
            val timeDiff = a.first.compareTo(b.first)
            if (timeDiff != 0) timeDiff else a.second.compareTo(b.second)
        }
        
        var sampleCount = 0
        var prevTickLeft = -1
        var prevTickRight = -1
        
        grouped.forEach { (key, groupEvents) ->
            val (time, foot) = key
            val pressureValues = DoubleArray(maxChannels)
            val rawValues = LongArray(8) // Phase 2: Raw uint16 values for V1 frames (8 channels)
            var ax = 0f; var ay = 0f; var az = 0f
            var gx = 0f; var gy = 0f; var gz = 0f
            var tick = -1
            var protocol = com.sensoria.app.data.ble.SensorType.CURRENT
            
            groupEvents.forEach { event ->
                if (event.tick != -1) tick = event.tick
                if (event.protocol != com.sensoria.app.data.ble.SensorType.CURRENT) protocol = event.protocol
                
                when (event.type) {
                    "pressure" -> {
                        if (event.index in 0 until maxChannels) {
                            pressureValues[event.index] = event.value
                            // Phase 2: Store raw value for V1 frames
                            if (event.index < 8) rawValues[event.index] = event.rawValue
                        }
                    }
                    "accel" -> { ax = event.x; ay = event.y; az = event.z }
                    "gyro" -> { gx = event.x; gy = event.y; gz = event.z }
                }
            }
            
            // Compute dtick
            // Phase 1: Use appropriate wrap logic based on protocol
            var dtick = 0
            if (tick != -1) {
                val isStreamV1 = (protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1)
                val tickWrapValue = if (isStreamV1) 0x10000 else 0x1000000 // 16-bit for V1, 24-bit for D20/E20
                
                if (foot == "Left") {
                    if (prevTickLeft != -1) {
                        dtick = tick - prevTickLeft
                        if (dtick < 0) dtick += tickWrapValue
                    }
                    prevTickLeft = tick
                } else {
                    if (prevTickRight != -1) {
                        dtick = tick - prevTickRight
                        if (dtick < 0) dtick += tickWrapValue
                    }
                    prevTickRight = tick
                }
            }
            
            val rowStart = sb.length
            // Use the actual protocol from data
            val isStreamV1Row = (protocol == com.sensoria.app.data.ble.SensorType.SENSORIA_STREAM_V1)
            
            sb.append(time).append(",").append(foot)
            for (v in pressureValues) {
                sb.append(",").append(v)
            }
            
            // Phase 2: Add raw_u16 values for Stream V1 (only if header has these columns)
            if (actualIsStreamV1) {
                for (i in 0 until 4) {
                    sb.append(",").append(rawValues[i])
                }
            }
            
            sb.append(",").append(ax).append(",").append(ay).append(",").append(az)
            sb.append(",").append(gx).append(",").append(gy).append(",").append(gz)
            sb.append(",").append(protocol.name).append(",").append(tick).append(",").append(dtick)
            
            // VERIFICATION: CSV Correctness Check
            if (SensoriaVerifier.SENSORIA_VERIFY && sampleCount < 20) {
                val rowStr = sb.substring(rowStart)
                val expected = mutableMapOf<String, Any>()
                expected["time"] = time
                expected["foot"] = foot
                // Add non-zero values to check
                pressureValues.forEachIndexed { i, v -> if (v > 0) expected["p$i"] = v }
                if (ax != 0f) expected["ax"] = ax
                expected["tick"] = tick
                expected["dtick"] = dtick
                
                SensoriaVerifier.verifyCsvRow(rowStr, "csv_row", expected, context)
                sampleCount++
            }
            
            sb.append("\n")
        }
        
        return sb.toString()
    }
}
