package com.sensoria.app.data.ble.sensoria

import android.content.Context
import com.sensoria.app.util.AppLog
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.sensoria.app.data.ble.sensoria.SensoriaVerifier // For accessing counters

/**
 * Analysis module for reverse-engineering Sensoria BLE protocol.
 * Captures raw packets per characteristic to identify the correct data stream.
 */
object SensoriaAnalysis {
    // Feature Flag - Set to false for release
    const val SENSORIA_ANALYSIS_MODE = true
    
    // Phase 3: Feature flag for capturing all NOTIFY characteristics
    const val SENSORIA_CAPTURE_ALL_NOTIFY = true // Set to true to enable IMU discovery mode
    
    private const val TAG = "SensoriaAnalysis"
    
    // Stats per characteristic
    data class CharStats(
        var packetCount: Int = 0,
        var totalPayloadLen: Long = 0,
        var minPayloadLen: Int = Int.MAX_VALUE,
        var maxPayloadLen: Int = Int.MIN_VALUE,
        val firstByteHistogram: MutableMap<Int, Int> = mutableMapOf() // Byte value (0-255) -> Count
    )
    
    private val statsMap = ConcurrentHashMap<UUID, CharStats>()
    private val activeFiles = ConcurrentHashMap.newKeySet<String>()

    /**
     * Process a packet in analysis mode.
     * Phase 3: Updated to support device_id and new CSV format when SENSORIA_CAPTURE_ALL_NOTIFY is enabled.
     */
    fun processPacket(uuid: UUID, value: ByteArray, context: Context, deviceId: String? = null): Boolean {
        if (!SENSORIA_ANALYSIS_MODE && !SENSORIA_CAPTURE_ALL_NOTIFY) return false
        
        val now = System.nanoTime()
        val len = value.size
        
        // Update stats
        val stats = statsMap.getOrPut(uuid) { CharStats() }
        synchronized(stats) {
            stats.packetCount++
            stats.totalPayloadLen += len
            stats.minPayloadLen = minOf(stats.minPayloadLen, len)
            stats.maxPayloadLen = maxOf(stats.maxPayloadLen, len)
            
            if (len > 0) {
                val firstByte = value[0].toInt() and 0xFF
                stats.firstByteHistogram[firstByte] = (stats.firstByteHistogram[firstByte] ?: 0) + 1
            }
        }
        
        // Phase 3: Log to CSV with new format when SENSORIA_CAPTURE_ALL_NOTIFY is enabled
        try {
            val hex = value.joinToString("") { "%02x".format(it) }
            val fileName = "char_${uuid}.csv"
            activeFiles.add(fileName)
            
            val file = File(context.filesDir, fileName)
            val append = file.exists()
            FileOutputStream(file, true).use { fos ->
                if (!append) {
                    // Phase 3: New CSV format with device_id, char_uuid, timestamp_ns, len, payload_hex
                    if (SENSORIA_CAPTURE_ALL_NOTIFY) {
                        fos.write("device_id,char_uuid,timestamp_ns,len,payload_hex\n".toByteArray())
                    } else {
                        fos.write("timestamp_ns,payload_hex\n".toByteArray())
                    }
                }
                
                if (SENSORIA_CAPTURE_ALL_NOTIFY && deviceId != null) {
                    fos.write("$deviceId,$uuid,$now,$len,$hex\n".toByteArray())
                } else {
                    fos.write("$now,$hex\n".toByteArray())
                }
            }
            
            // Log to AppLog (sampled to avoid flooding)
            if (stats.packetCount <= 10 || stats.packetCount % 50 == 0) {
                AppLog.i(context, TAG, "UUID=$uuid, Len=$len, Hex=${hex.take(32)}")
            }
        } catch (e: Exception) {
            AppLog.e(context, TAG, "Failed to write CSV for $uuid: ${e.message}")
        }
        
        return true
    }

    fun generateIndexJson(context: Context): File {
        val file = File(context.filesDir, "analysis_index.json")
        val json = JSONObject()
        
        // Phase 4: Include verifier counters
        val packetsFromStreamChar = SensoriaVerifier.getPacketsFromStreamChar()
        val badLenCount = SensoriaVerifier.getBadLengthCount()
        
        json.put("packetsFromStreamChar", packetsFromStreamChar)
        json.put("badLenCount", badLenCount)
        
        // Phase 4: First byte histogram for streaming char only
        val streamingCharUuid = com.sensoria.app.data.ble.BleUuids.SENSORIA_STREAMING_CHAR
        val streamingCharStats = statsMap[streamingCharUuid]
        val firstByteHistogram = JSONObject()
        
        if (streamingCharStats != null && streamingCharStats.packetCount > 0) {
            streamingCharStats.firstByteHistogram.forEach { (byteVal, count) ->
                firstByteHistogram.put("0x${Integer.toHexString(byteVal)}", count)
            }
        }
        
        json.put("firstByteHistogram", firstByteHistogram)
        
        // Phase 4: Always include per-characteristic stats (even if empty)
        statsMap.forEach { (uuid, stats) ->
            val charObj = JSONObject()
            charObj.put("packetCount", stats.packetCount)
            charObj.put("minPayloadLen", if (stats.packetCount > 0) stats.minPayloadLen else 0)
            charObj.put("maxPayloadLen", if (stats.packetCount > 0) stats.maxPayloadLen else 0)
            val avg = if (stats.packetCount > 0) stats.totalPayloadLen.toDouble() / stats.packetCount else 0.0
            charObj.put("avgPayloadLen", avg)
            
            val histObj = JSONObject()
            stats.firstByteHistogram.forEach { (byteVal, count) ->
                histObj.put("0x${Integer.toHexString(byteVal)}", count)
            }
            charObj.put("firstByteHistogram", histObj)
            
            json.put(uuid.toString(), charObj)
        }
        
        // Phase 4: If no packets, still write meaningful JSON (never {})
        if (packetsFromStreamChar == 0 && statsMap.isEmpty()) {
            json.put("note", "No packets received from streaming characteristic")
        }
        
        file.writeText(json.toString())
        return file
    }

    fun getCapturedFiles(context: Context): List<File> {
        val files = mutableListOf<File>()
        activeFiles.forEach { fileName ->
            val f = File(context.filesDir, fileName)
            if (f.exists()) files.add(f)
        }
        return files
    }

    fun reset(context: Context) {
        statsMap.clear()
        
        // Delete csv files
        activeFiles.forEach { fileName ->
            try {
                File(context.filesDir, fileName).delete()
            } catch (e: Exception) {
                // ignore
            }
        }
        activeFiles.clear()
        
        // Delete index
        try {
            File(context.filesDir, "analysis_index.json").delete()
        } catch (e: Exception) {
            // ignore
        }
    }
}
