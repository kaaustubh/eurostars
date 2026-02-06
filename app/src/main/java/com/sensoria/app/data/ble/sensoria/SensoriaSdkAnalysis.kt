package com.sensoria.app.data.ble.sensoria

import android.content.Context
import com.sensoria.app.util.AppLog
import io.sensoria.sdk.data.DataPoint
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Analysis module for Sensoria SDK data.
 * Logs relevant information from SDK DataPoint objects to sdk_analysis_index.json.
 */
object SensoriaSdkAnalysis {
    private const val TAG = "SensoriaSdkAnalysis"
    private const val MAX_CHANNEL_LOG_ENTRIES = 5000
    
    // Stats per device
    data class DeviceStats(
        var packetCount: AtomicInteger = AtomicInteger(0),
        var firstPacketTime: Long = 0L,
        var lastPacketTime: Long = 0L,
        var minTick: Int = Int.MAX_VALUE,
        var maxTick: Int = Int.MIN_VALUE,
        var tickRange: Int = 0,
        var minChannelValue: Int = Int.MAX_VALUE,
        var maxChannelValue: Int = Int.MIN_VALUE,
        var channelStats: MutableMap<Int, ChannelStats> = mutableMapOf(), // Channel index -> stats
        var accelMin: Triple<Float, Float, Float>? = null, // min x, y, z
        var accelMax: Triple<Float, Float, Float>? = null, // max x, y, z
        var gyroMin: Triple<Float, Float, Float>? = null,
        var gyroMax: Triple<Float, Float, Float>? = null,
        var actualSampleRateMin: Int = Int.MAX_VALUE,
        var actualSampleRateMax: Int = Int.MIN_VALUE,
        var actualSampleRateSum: Long = 0L,
        var nominalSampleRate: Int = 0,
        var packetsLostTotal: Long = 0L,
        var packetsLostMax: Int = 0
    )
    
    data class ChannelStats(
        var min: Int = Int.MAX_VALUE,
        var max: Int = Int.MIN_VALUE,
        var sum: Long = 0L,
        var count: Int = 0
    )
    
    private val deviceStatsMap = ConcurrentHashMap<String, DeviceStats>() // deviceAddress -> stats
    private val channelSizeLogMap = ConcurrentHashMap<String, MutableList<ChannelSizeEntry>>() // deviceAddress -> list

    data class ChannelSizeEntry(
        val timestampNs: Long,
        val channelsSize: Int,
        val tick: Int,
        val protocolType: String
    )
    
    /**
     * Process a DataPoint from SDK.
     */
    fun processDataPoint(deviceAddress: String, dataPoint: DataPoint, timestampNs: Long) {
        val stats = deviceStatsMap.getOrPut(deviceAddress) { DeviceStats() }
        
        synchronized(stats) {
            val count = stats.packetCount.incrementAndGet()
            
            // First packet timestamp
            if (count == 1) {
                stats.firstPacketTime = timestampNs
            }
            stats.lastPacketTime = timestampNs
            
            // Tick statistics
            val tick = dataPoint.tick
            stats.minTick = minOf(stats.minTick, tick)
            stats.maxTick = maxOf(stats.maxTick, tick)
            stats.tickRange = stats.maxTick - stats.minTick
            
            // Channel statistics
            for (i in 0 until dataPoint.channels.size) {
                val value = dataPoint.channels[i]
                stats.minChannelValue = minOf(stats.minChannelValue, value)
                stats.maxChannelValue = maxOf(stats.maxChannelValue, value)
                
                val channelStat = stats.channelStats.getOrPut(i) { ChannelStats() }
                channelStat.min = minOf(channelStat.min, value)
                channelStat.max = maxOf(channelStat.max, value)
                channelStat.sum += value
                channelStat.count++
            }
            
            // Accelerometer statistics
            if (dataPoint.accelerometer != null && dataPoint.accelerometer.size >= 3) {
                val (x, y, z) = Triple(
                    dataPoint.accelerometer[0].toFloat(),
                    dataPoint.accelerometer[1].toFloat(),
                    dataPoint.accelerometer[2].toFloat()
                )
                
                if (stats.accelMin == null) {
                    stats.accelMin = Triple(x, y, z)
                    stats.accelMax = Triple(x, y, z)
                } else {
                    val (minX, minY, minZ) = stats.accelMin!!
                    val (maxX, maxY, maxZ) = stats.accelMax!!
                    stats.accelMin = Triple(minOf(minX, x), minOf(minY, y), minOf(minZ, z))
                    stats.accelMax = Triple(maxOf(maxX, x), maxOf(maxY, y), maxOf(maxZ, z))
                }
            }
            
            // Gyroscope statistics
            if (dataPoint.gyroscope != null && dataPoint.gyroscope.size >= 3) {
                val (x, y, z) = Triple(
                    dataPoint.gyroscope[0].toFloat(),
                    dataPoint.gyroscope[1].toFloat(),
                    dataPoint.gyroscope[2].toFloat()
                )
                
                if (stats.gyroMin == null) {
                    stats.gyroMin = Triple(x, y, z)
                    stats.gyroMax = Triple(x, y, z)
                } else {
                    val (minX, minY, minZ) = stats.gyroMin!!
                    val (maxX, maxY, maxZ) = stats.gyroMax!!
                    stats.gyroMin = Triple(minOf(minX, x), minOf(minY, y), minOf(minZ, z))
                    stats.gyroMax = Triple(maxOf(maxX, x), maxOf(maxY, y), maxOf(maxZ, z))
                }
            }
            
            // Sample rate statistics
            val actualRate = dataPoint.actualSamplingFrequency
            stats.actualSampleRateMin = minOf(stats.actualSampleRateMin, actualRate)
            stats.actualSampleRateMax = maxOf(stats.actualSampleRateMax, actualRate)
            stats.actualSampleRateSum += actualRate
            stats.nominalSampleRate = dataPoint.nominalSamplingFrequency
            
            // Packet loss statistics
            val lost = dataPoint.packetsLost
            stats.packetsLostTotal += lost
            stats.packetsLostMax = maxOf(stats.packetsLostMax, lost)
        }

        // Channel size log (bounded)
        val channelLog = channelSizeLogMap.getOrPut(deviceAddress) { mutableListOf() }
        synchronized(channelLog) {
            if (channelLog.size < MAX_CHANNEL_LOG_ENTRIES) {
                channelLog.add(
                    ChannelSizeEntry(
                        timestampNs = timestampNs,
                        channelsSize = dataPoint.channels.size,
                        tick = dataPoint.tick,
                        protocolType = dataPoint.protocolType.name
                    )
                )
            }
        }
    }
    
    /**
     * Generate sdk_analysis_index.json file.
     */
    fun generateIndexJson(context: Context): File {
        val file = File(context.filesDir, "sdk_analysis_index.json")
        val json = JSONObject()
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        deviceStatsMap.forEach { (deviceAddress, stats) ->
            val deviceObj = JSONObject()
            
            deviceObj.put("deviceAddress", deviceAddress)
            deviceObj.put("packetCount", stats.packetCount.get())
            
            if (stats.packetCount.get() > 0) {
                deviceObj.put("firstPacketTime", dateFormat.format(Date(stats.firstPacketTime / 1_000_000)))
                deviceObj.put("lastPacketTime", dateFormat.format(Date(stats.lastPacketTime / 1_000_000)))
                deviceObj.put("durationMs", (stats.lastPacketTime - stats.firstPacketTime) / 1_000_000)
                
                // Tick statistics
                val tickObj = JSONObject()
                tickObj.put("min", stats.minTick)
                tickObj.put("max", stats.maxTick)
                tickObj.put("range", stats.tickRange)
                deviceObj.put("tick", tickObj)
                
                // Channel statistics
                val channelsObj = JSONObject()
                synchronized(stats) {
                    stats.channelStats.forEach { (index, channelStat) ->
                        val channelObj = JSONObject()
                        channelObj.put("min", channelStat.min)
                        channelObj.put("max", channelStat.max)
                        if (channelStat.count > 0) {
                            channelObj.put("mean", channelStat.sum.toDouble() / channelStat.count)
                        }
                        channelObj.put("count", channelStat.count)
                        channelsObj.put("channel_$index", channelObj)
                    }
                }
                deviceObj.put("channels", channelsObj)
                deviceObj.put("globalChannelMin", stats.minChannelValue)
                deviceObj.put("globalChannelMax", stats.maxChannelValue)
                
                // Accelerometer statistics
                stats.accelMin?.let { (x, y, z) ->
                    val accelMinObj = JSONObject()
                    accelMinObj.put("x", x)
                    accelMinObj.put("y", y)
                    accelMinObj.put("z", z)
                    deviceObj.put("accelMin", accelMinObj)
                }
                stats.accelMax?.let { (x, y, z) ->
                    val accelMaxObj = JSONObject()
                    accelMaxObj.put("x", x)
                    accelMaxObj.put("y", y)
                    accelMaxObj.put("z", z)
                    deviceObj.put("accelMax", accelMaxObj)
                }
                
                // Gyroscope statistics
                stats.gyroMin?.let { (x, y, z) ->
                    val gyroMinObj = JSONObject()
                    gyroMinObj.put("x", x)
                    gyroMinObj.put("y", y)
                    gyroMinObj.put("z", z)
                    deviceObj.put("gyroMin", gyroMinObj)
                }
                stats.gyroMax?.let { (x, y, z) ->
                    val gyroMaxObj = JSONObject()
                    gyroMaxObj.put("x", x)
                    gyroMaxObj.put("y", y)
                    gyroMaxObj.put("z", z)
                    deviceObj.put("gyroMax", gyroMaxObj)
                }
                
                // Sample rate statistics
                val sampleRateObj = JSONObject()
                sampleRateObj.put("nominal", stats.nominalSampleRate)
                if (stats.packetCount.get() > 0) {
                    sampleRateObj.put("actualMin", stats.actualSampleRateMin)
                    sampleRateObj.put("actualMax", stats.actualSampleRateMax)
                    sampleRateObj.put("actualMean", stats.actualSampleRateSum.toDouble() / stats.packetCount.get())
                }
                deviceObj.put("sampleRate", sampleRateObj)
                
                // Packet loss statistics
                val packetLossObj = JSONObject()
                packetLossObj.put("total", stats.packetsLostTotal)
                packetLossObj.put("max", stats.packetsLostMax)
                if (stats.packetCount.get() > 0) {
                    packetLossObj.put("mean", stats.packetsLostTotal.toDouble() / stats.packetCount.get())
                }
                deviceObj.put("packetLoss", packetLossObj)
            }
            
            json.put(deviceAddress, deviceObj)
        }
        
        // If no data, still write a valid JSON
        if (deviceStatsMap.isEmpty()) {
            json.put("note", "No SDK data points received")
        }
        
        file.writeText(json.toString(2)) // Pretty print with 2-space indent
        return file
    }

    /**
     * Generate sdk_channel_sizes.csv file.
     */
    fun generateChannelSizeDump(context: Context): File {
        val file = File(context.filesDir, "sdk_channel_sizes.csv")
        val sb = StringBuilder()
        sb.append("timestamp_ns,device_address,channels_size,tick,protocol").append("\n")

        channelSizeLogMap.forEach { (deviceAddress, entries) ->
            synchronized(entries) {
                entries.forEach { entry ->
                    sb.append(entry.timestampNs).append(",")
                        .append(deviceAddress).append(",")
                        .append(entry.channelsSize).append(",")
                        .append(entry.tick).append(",")
                        .append(entry.protocolType)
                        .append("\n")
                }
            }
        }

        file.writeText(sb.toString())
        return file
    }
    
    /**
     * Reset analysis state.
     */
    fun reset(context: Context) {
        deviceStatsMap.clear()
        channelSizeLogMap.clear()
        
        // Delete index file
        try {
            File(context.filesDir, "sdk_analysis_index.json").delete()
            File(context.filesDir, "sdk_channel_sizes.csv").delete()
        } catch (e: Exception) {
            // ignore
        }
    }
}
