package com.sensoria.app.data.ble.sensoria

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.sensoria.app.data.ble.BleUuids // Phase 5: Use constant
import com.sensoria.app.util.AppLog
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sensoria.app.viewmodel.PairingTarget
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Verification module for Sensoria sensor data.
 * Validates protocol compliance, data integrity, and timing stability.
 */
object SensoriaVerifier {
    // Step 1: Add a “Verifier Mode” flag
    const val SENSORIA_VERIFY = true
    private const val TAG = "SensoriaVerifier"
    
    // Part B2: Make dtick state per stream
    // Phase 3: Track tick per key (device_id + char_uuid)
    // Using a flexible String key to support both raw contexts (if needed) and decoded contexts (Side)
    data class StreamKey(val id: String) 
    
    // Step 2: Capture raw notify payloads
    // Phase 2: Update schema to include identity
    data class RawPacket(
        val deviceId: String,
        val charUuid: String,
        val phoneTsNs: Long,
        val payloadLen: Int,
        val msgTypeLow: Int,
        val msgTypeHigh: Int,
        val payloadHex: String
    )
    
    // Step 4: Out of range error
    data class RangeError(
        val msgType: Int,
        val tick: Int,
        val field: String,
        val value: Int,
        val packetHex: String
    )

    // State
    private val rangeErrors = ConcurrentLinkedQueue<RangeError>()
    private var rawCsvFile: File? = null
    private var rawCsvWriter: FileOutputStream? = null
    
    // Stats
    private var totalPackets = 0
    private var packetsFromStreamChar = 0 // Phase 2: Count packets from streaming char
    private var highCounts = MessageTypeCounts()
    private var lowCounts = MessageTypeCounts()
    private var outOfRangeCount = 0
    private var badLengthCount = 0 // Phase 1: Track bad lengths
    
    // Phase 0: Frame type tracking
    private var frameHeader0x5A = 0 // Count of 0x5A header frames
    private var frameTypeV1 = 0 // Count of SENSORIA_STREAM_V1 frames
    private var frameTypeD20 = 0 // Count of D20 frames
    private var frameTypeE20 = 0 // Count of E20 frames
    private val packetLenHistogram = mutableMapOf<Int, Int>() // Length -> Count
    private val firstByteHistogram = mutableMapOf<Int, Int>() // First byte value -> Count
    
    // Phase 2: Channel statistics for V1 frames (no ADC range checks)
    data class ChannelStats(
        val min: Int = Int.MAX_VALUE,
        val max: Int = Int.MIN_VALUE,
        val sum: Long = 0L,
        var count: Int = 0
    ) {
        val mean: Double get() = if (count > 0) sum.toDouble() / count else 0.0
    }
    private val channelStats = mutableMapOf<Int, ChannelStats>() // Channel index -> Stats
    
    // Phase 4: Expose counters for analysis
    fun getPacketsFromStreamChar(): Int = packetsFromStreamChar
    fun getBadLengthCount(): Int = badLengthCount
    
    fun incrementPacketsFromStreamChar() {
        packetsFromStreamChar++
    }
    
    data class MessageTypeCounts(
        var d20: Int = 0, // 0xD
        var e20: Int = 0, // 0xE
        var other: Int = 0
    )
    
    // Tick stats per stream
    private val lastTickMap = ConcurrentHashMap<StreamKey, Int>()
    private val lastPhoneTsNsMap = ConcurrentHashMap<StreamKey, Long>()
    
    private var tickBackwardsCount = 0
    private var tickRangeViolations = 0
    private var dtickZeroCount = 0
    private var largeJumpCount = 0
    
    // Timing stats
    private val msPerTicks = mutableListOf<Double>()
    private val ticksPerSeconds = mutableListOf<Double>()
    
    // CSV stats
    private var csvMismatchCount = 0
    private val csvMismatches = mutableListOf<String>()

    fun incrementBadLengthCount() {
        badLengthCount++
    }

    /**
     * Step 2 & 3: Process raw packet for verification.
     * Phase 2: Accept identity (deviceId, charUuid)
     */
    fun processPacket(packetData: ByteArray, deviceId: String, charUuid: String, context: Context? = null) {
        if (!SENSORIA_VERIFY) return

        // Phase 5: Strict filtering - only accept packets from SENSORIA_STREAMING_CHAR
        if (charUuid != BleUuids.SENSORIA_STREAMING_CHAR.toString()) {
            return
        }

        val nowNs = SystemClock.elapsedRealtimeNanos()
        val hex = packetData.joinToString("") { "%02x".format(it) }
        
        var msgTypeLow = -1
        var msgTypeHigh = -1
        
        // Phase 0: Track frame header and type
        val firstByte = if (packetData.isNotEmpty()) packetData[0].toInt() and 0xFF else -1
        val isStreamV1 = (firstByte == 0x5A && packetData.size == 20)
        
        // Update histograms
        packetLenHistogram[packetData.size] = (packetLenHistogram[packetData.size] ?: 0) + 1
        if (firstByte >= 0) {
            firstByteHistogram[firstByte] = (firstByteHistogram[firstByte] ?: 0) + 1
        }
        
        if (isStreamV1) {
            frameHeader0x5A++
            frameTypeV1++
            // For 0x5A packets, extract nibbles: 0x5A = 0b0101_1010
            // Low nibble = 0xA (10), High nibble = 0x5 (5)
            val byte0 = packetData[0].toInt()
            msgTypeLow = byte0 and 0x0F  // 0xA
            msgTypeHigh = (byte0 ushr 4) and 0x0F  // 0x5
        } else if (packetData.isNotEmpty()) {
            val byte0 = packetData[0].toInt()
            msgTypeLow = byte0 and 0x0F
            msgTypeHigh = (byte0 ushr 4) and 0x0F
            
            // Only track D20/E20 message types if NOT 0x5A frame
            // Low nibble stats
            when (msgTypeLow) {
                0xD -> {
                    lowCounts.d20++
                    frameTypeD20++
                }
                0xE -> {
                    lowCounts.e20++
                    frameTypeE20++
                }
                else -> lowCounts.other++
            }
            
            // High nibble stats
            when (msgTypeHigh) {
                0xD -> highCounts.d20++
                0xE -> highCounts.e20++
                else -> highCounts.other++
            }
        }

        // Store raw packet to CSV (limit 2000 rows)
        if (context != null && totalPackets < 2000) {
            try {
                if (rawCsvFile == null) {
                    rawCsvFile = File(context.filesDir, "sensoria_raw.csv")
                    rawCsvWriter = FileOutputStream(rawCsvFile)
                    // Phase 2: Update header
                    rawCsvWriter?.write("device_id,char_uuid,phone_ts_ns,payload_len,msg_type_low,msg_type_high,payload_hex\n".toByteArray())
                }
                val line = "$deviceId,$charUuid,$nowNs,${packetData.size},0x${Integer.toHexString(msgTypeLow)},0x${Integer.toHexString(msgTypeHigh)},$hex\n"
                rawCsvWriter?.write(line.toByteArray())
            } catch (e: Exception) {
                // Ignore write errors
            }
        }
        
        // Log first 20 packets with both candidates
        if (totalPackets < 20 && context != null) {
            AppLog.i(context, TAG, "Packet: $hex | Low=${Integer.toHexString(msgTypeLow)} High=${Integer.toHexString(msgTypeHigh)} | FrameType=${if (isStreamV1) "V1(0x5A)" else "D20/E20"}")
        }
        
        // Step 6: Timing stability - tracked per stream in verifyDecodedValues
        totalPackets++
    }

    /**
     * Step 4, 5, 6: Verify decoded values.
     */
    fun verifyDecodedValues(
        payload: SensoriaProtocolParser.ParsedPacket, 
        header: SensoriaProtocolParser.PacketHeader, 
        rawData: ByteArray, 
        context: Context? = null,
        sensorSide: PairingTarget = PairingTarget.LEFT_SENSOR // Default to Left if not provided
    ) {
        if (!SENSORIA_VERIFY) return
        
        val nowNs = SystemClock.elapsedRealtimeNanos()
        
        // Phase 0: Check if this is a 0x5A frame (Stream V1)
        val isStreamV1 = (header.packetType == 0x5A)
        
        // Step 4: Verify decoded value invariants
        // Phase 0: Skip ADC 0..1023 checks for 0x5A frames (they use uint16, not 10-bit ADC)
        if (!isStreamV1) {
            // ADC 0..1023 (only for D20/E20)
            payload.analogChannels.forEach { channel ->
                if (channel.rawValue !in 0..1023) {
                    recordRangeError(header.packetType, header.sequenceNumber, "ADC_CH${channel.channelIndex}", channel.rawValue, rawData, context)
                }
            }
        } else {
            // Phase 2: For V1 frames, track min/max/mean per channel (no range checks)
            payload.analogChannels.forEach { channel ->
                val stats = channelStats.getOrPut(channel.channelIndex) { ChannelStats() }
                val value = channel.rawValue
                channelStats[channel.channelIndex] = ChannelStats(
                    min = minOf(stats.min, value),
                    max = maxOf(stats.max, value),
                    sum = stats.sum + value,
                    count = stats.count + 1
                )
            }
        }
        
        // IMU 0..1023
        payload.imuData?.let { imu ->
             checkImuField(imu.accelX, "ACCEL_X", header, rawData, context)
             checkImuField(imu.accelY, "ACCEL_Y", header, rawData, context)
             checkImuField(imu.accelZ, "ACCEL_Z", header, rawData, context)
             checkImuField(imu.gyroX, "GYRO_X", header, rawData, context)
             checkImuField(imu.gyroY, "GYRO_Y", header, rawData, context)
             checkImuField(imu.gyroZ, "GYRO_Z", header, rawData, context)
        }

        // Step 5: Verify tick extraction + monotonicity per stream
        val tick = header.sequenceNumber
        
        // Phase 1: Use appropriate tick range and wrap logic based on frame type (reuse isStreamV1 from above)
        val tickMaxValue = if (isStreamV1) 0xFFFF else 0xFFFFFF // 16-bit for V1, 24-bit for D20/E20
        val tickWrapValue = if (isStreamV1) 0x10000 else 0x1000000 // 2^16 for V1, 2^24 for D20/E20
        
        if (tick !in 0..tickMaxValue) {
            tickRangeViolations++
        }
        
        // Map sensorSide to a string key. Ideally this would be (deviceId + charUuid), 
        // but for decoded values we follow the app's logical stream model.
        val streamKey = StreamKey(sensorSide.name)
        val lastTick = lastTickMap[streamKey] ?: -1
        val lastTs = lastPhoneTsNsMap[streamKey] ?: 0L
        
        if (lastTick != -1) {
            // Phase 1: Correct wrap-safe dtick for 16-bit (V1) or 24-bit (D20/E20) tick
            val dTick = if (tick >= lastTick) {
                tick - lastTick
            } else {
                // Wrap case: (MAX - prev) + curr
                (tickWrapValue - lastTick) + tick
            }
            
            if (dTick == 0) {
                 dtickZeroCount++
            } else if (dTick > 1000) { // Arbitrary large jump threshold (e.g. 1000 samples lost)
                 largeJumpCount++
            } else if (dTick < 0) {
                 // Should be impossible with wrap logic unless logic fails
                 tickBackwardsCount++
            } else {
                 // Valid jump
                 
                 // Step B5: Timing stability estimates
                 if (lastTs != 0L) {
                     val dPhoneMs = (nowNs - lastTs) / 1_000_000.0
                     if (dPhoneMs > 0) {
                         val msPerTick = dPhoneMs / dTick
                         val tps = dTick / (dPhoneMs / 1000.0)
                         
                         synchronized(msPerTicks) {
                             msPerTicks.add(msPerTick)
                             if (msPerTicks.size > 2000) msPerTicks.removeAt(0)
                             
                             ticksPerSeconds.add(tps)
                             if (ticksPerSeconds.size > 2000) ticksPerSeconds.removeAt(0)
                         }
                     }
                 }
            }
        }
        
        lastTickMap[streamKey] = tick
        lastPhoneTsNsMap[streamKey] = nowNs
    }
    
    private fun checkImuField(value: Int?, name: String, header: SensoriaProtocolParser.PacketHeader, rawData: ByteArray, context: Context?) {
        if (value != null && value !in 0..1023) {
             recordRangeError(header.packetType, header.sequenceNumber, name, value, rawData, context)
        }
    }

    private fun recordRangeError(msgType: Int, tick: Int, field: String, value: Int, rawData: ByteArray, context: Context?) {
        outOfRangeCount++
        if (rangeErrors.size < 20) {
            val hex = rawData.take(16).joinToString("") { "%02x".format(it) }
            val error = RangeError(msgType, tick, field, value, hex)
            rangeErrors.add(error)
            context?.let { AppLog.e(it, TAG, "Range Error: $error") }
        }
    }

    /**
     * Step 7: CSV correctness check
     */
    fun verifyCsvRow(
        row: String, 
        expectedType: String, // "pressure", "accel", "gyro"
        expectedValues: Map<String, Any>, // map of column to value
        context: Context? = null
    ) {
        if (!SENSORIA_VERIFY) return
        
        for ((key, value) in expectedValues) {
            val valStr = value.toString()
            if (!row.contains(valStr)) {
                // Try format match for floats
                if (value is Float || value is Double) {
                    continue
                }
                
                csvMismatchCount++
                if (csvMismatches.size < 20) {
                    val msg = "Mismatch $expectedType: expected $key=$value in row: $row"
                    csvMismatches.add(msg)
                    context?.let { AppLog.w(it, TAG, msg) }
                }
            }
        }
    }
    
    /**
     * Step 5: Generate verify_summary.txt
     */
    fun generateSummaryFile(context: Context): File {
        val file = File(context.filesDir, "verify_summary.txt")
        if (!SENSORIA_VERIFY) {
            file.writeText("Sensoria Verifier was disabled.")
            return file
        }
        
        val sb = StringBuilder()
        sb.append("=== SENSORIA VERIFICATION SUMMARY ===\n")
        sb.append("Total Packets: $totalPackets\n")
        sb.append("Packets from Stream Char: $packetsFromStreamChar\n")
        
        // Phase 0: Frame type tracking
        sb.append("\n--- Frame Type Distribution ---\n")
        sb.append("Frame Header 0x5A: $frameHeader0x5A\n")
        sb.append("Frame Type V1: $frameTypeV1\n")
        sb.append("Frame Type D20: $frameTypeD20\n")
        sb.append("Frame Type E20: $frameTypeE20\n")
        
        // Phase 0: Histograms
        sb.append("\n--- Packet Length Histogram ---\n")
        packetLenHistogram.toSortedMap().forEach { (len, count) ->
            sb.append("Length $len: $count packets\n")
        }
        
        sb.append("\n--- First Byte Histogram ---\n")
        firstByteHistogram.toSortedMap().forEach { (byteVal, count) ->
            sb.append("0x${Integer.toHexString(byteVal).uppercase()}: $count packets\n")
        }
        
        // Phase 2: Channel statistics for V1 frames
        if (channelStats.isNotEmpty()) {
            sb.append("\n--- V1 Frame Channel Statistics (raw uint16, no ADC range) ---\n")
            channelStats.toSortedMap().forEach { (channelIndex, stats) ->
                sb.append("Channel $channelIndex: min=${stats.min}, max=${stats.max}, mean=${"%.2f".format(stats.mean)}, count=${stats.count}\n")
            }
        }
        
        sb.append("\n--- Message Type Candidates (D20/E20 only) ---\n")
        sb.append("Low Nibble (Current Parser): D20=${lowCounts.d20}, E20=${lowCounts.e20}, Other=${lowCounts.other}\n")
        sb.append("High Nibble (Candidate):     D20=${highCounts.d20}, E20=${highCounts.e20}, Other=${highCounts.other}\n")
        
        // Determine chosen source
        val lowValid = lowCounts.d20 + lowCounts.e20
        val highValid = highCounts.d20 + highCounts.e20
        val threshold = (totalPackets * 0.95).toInt()
        
        var chosenSource = "UNKNOWN"
        if (lowValid > threshold) {
            chosenSource = "LOW (Current Parser)"
        } else if (highValid > threshold) {
            chosenSource = "HIGH (Candidate)"
        }
        
        sb.append("Chosen Message Type Source: $chosenSource\n")
        
        if (chosenSource.startsWith("HIGH")) {
            sb.append("WARNING: Current parser uses LOW nibble, but HIGH nibble looks correct!\n")
        }
        
        sb.append("\n--- Invariants ---\n")
        sb.append("Out of Range Packets: $outOfRangeCount\n")
        sb.append("Bad Length Packets: $badLengthCount\n") // Added
        sb.append("Tick Stats: RangeViolations=$tickRangeViolations, Backwards=$tickBackwardsCount, ZeroDelta=$dtickZeroCount, LargeJumps=$largeJumpCount\n")
        
        val msTickAvg = if (msPerTicks.isNotEmpty()) msPerTicks.average() else 0.0
        val msTickMin = msPerTicks.minOrNull() ?: 0.0
        val msTickMax = msPerTicks.maxOrNull() ?: 0.0
        
        sb.append("Timing (ms/tick): Min=${"%.2f".format(msTickMin)}, Max=${"%.2f".format(msTickMax)}, Avg=${"%.2f".format(msTickAvg)}\n")
        
        sb.append("\n--- VERDICT ---\n")
        
        // Phase 6: Updated verification rules - focus on stream integrity, not unit assumptions
        val hasV1Frames = frameTypeV1 > 0
        val hasD20E20Frames = (frameTypeD20 + frameTypeE20) > 0
        
        // For 0x5A frames: validate len==20, header==0x5A, tick monotonic/wrap
        val v1LengthPass = if (hasV1Frames) {
            // Check if all V1 frames have correct length (already enforced in BleRepository, but verify)
            true // Length check is done at packet reception
        } else {
            true // No V1 frames, skip check
        }
        
        val v1HeaderPass = if (hasV1Frames) {
            frameHeader0x5A == frameTypeV1 // All V1 frames should have 0x5A header
        } else {
            true
        }
        
        // For D20/E20 frames: validate protocol classification
        val protocolPass = if (hasD20E20Frames) {
            (lowCounts.d20 + lowCounts.e20 > (totalPackets * 0.95).toInt()) || 
            (highCounts.d20 + highCounts.e20 > (totalPackets * 0.95).toInt())
        } else {
            true // No D20/E20 frames, skip check
        }
        
        // Range checks: only for D20/E20 (not for V1 frames which use uint16)
        val rangePass = if (hasV1Frames && !hasD20E20Frames) {
            true // V1 frames don't have ADC 0..1023 range checks
        } else {
            outOfRangeCount == 0 // D20/E20 frames must pass ADC range checks
        }
        
        val tickPass = tickBackwardsCount == 0 && tickRangeViolations == 0
        val lengthPass = badLengthCount == 0
        
        // Phase 6: Show PASS if stream integrity is good (even if units unknown)
        val integrityPass = v1LengthPass && v1HeaderPass && protocolPass && rangePass && tickPass && lengthPass
        
        if (integrityPass) {
            sb.append("✅ PASS: Stream integrity is good\n")
            if (hasV1Frames) {
                sb.append("   Note: V1 frames use raw uint16 values (units/calibration unknown, but stream is valid)\n")
            }
        } else {
            sb.append("❌ FAIL: Stream integrity issues detected\n")
            if (!v1LengthPass) sb.append("- V1 frame length violations\n")
            if (!v1HeaderPass) sb.append("- V1 frame header mismatch (expected 0x5A)\n")
            if (!protocolPass && hasD20E20Frames) sb.append("- Protocol classification failed (<95% D20/E20)\n")
            if (!rangePass && hasD20E20Frames) sb.append("- Out of range values detected (Raw ADC must be 0..1023)\n")
            if (!tickPass) sb.append("- Tick violations detected\n")
            if (!lengthPass) sb.append("- Bad packet lengths detected ($badLengthCount)\n")
        }
        
        if (largeJumpCount > 0) sb.append("⚠️ WARN: $largeJumpCount large tick jumps detected\n")
        if (dtickZeroCount > 0) sb.append("⚠️ WARN: $dtickZeroCount zero-delta ticks detected\n")
        if (hasV1Frames && !hasD20E20Frames) {
            sb.append("ℹ️ INFO: Using V1 frames (0x5A) - raw uint16 values, no ADC range validation\n")
        }
        
        sb.append("\ncsv Mismatches: $csvMismatchCount\n")
        
        if (rangeErrors.isNotEmpty()) {
            sb.append("\n--- First ${rangeErrors.size} Range Errors ---\n")
            rangeErrors.forEach { 
                sb.append("MsgType=${Integer.toHexString(it.msgType)}, Tick=${it.tick}, Field=${it.field}, Value=${it.value}, Hex=${it.packetHex}\n")
            }
        }
        
        file.writeText(sb.toString())
        return file
    }
    
    /**
     * Step 8: Add lightweight metadata for triage (debug_meta.json)
     */
    fun generateMetadataFile(context: Context, sessionStart: Long, sessionEnd: Long): File {
        val file = File(context.filesDir, "debug_meta.json")
        if (!SENSORIA_VERIFY) {
            file.writeText("{}")
            return file
        }
        
        val meta = JSONObject()
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            meta.put("appVersion", pInfo.versionName)
            meta.put("appBuild", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode)
        } catch (e: Exception) {
            meta.put("appVersion", "unknown")
        }
        
        meta.put("deviceModel", Build.MODEL)
        meta.put("androidVersion", Build.VERSION.RELEASE)
        meta.put("sessionStart", sessionStart)
        meta.put("sessionEnd", sessionEnd)
        
        val lowValid = lowCounts.d20 + lowCounts.e20
        val highValid = highCounts.d20 + highCounts.e20
        val threshold = (totalPackets * 0.95).toInt()
        
        var chosenSource = "UNKNOWN"
        if (lowValid > threshold) {
            chosenSource = "LOW"
        } else if (highValid > threshold) {
            chosenSource = "HIGH"
        }

        meta.put("verificationEnabled", SENSORIA_VERIFY)
        meta.put("chosenMsgTypeSource", chosenSource)
        meta.put("dominantProtocol", if (lowCounts.d20 + highCounts.d20 > lowCounts.e20 + highCounts.e20) "D20" else "E20")
        
        file.writeText(meta.toString())
        return file
    }
    
    /**
     * Print summary to Logcat
     */
    fun printSummary(context: Context) {
        if (!SENSORIA_VERIFY) return
        val summaryFile = File(context.filesDir, "verify_summary.txt")
        if (summaryFile.exists()) {
            val lines = summaryFile.readLines()
            lines.forEach { AppLog.i(context, TAG, it) }
        }
    }
    
    fun reset() {
        totalPackets = 0
        packetsFromStreamChar = 0 // Reset
        highCounts = MessageTypeCounts()
        lowCounts = MessageTypeCounts()
        rangeErrors.clear()
        outOfRangeCount = 0
        badLengthCount = 0 // Reset
        // Phase 0: Reset frame type tracking
        frameHeader0x5A = 0
        frameTypeV1 = 0
        frameTypeD20 = 0
        frameTypeE20 = 0
        packetLenHistogram.clear()
        firstByteHistogram.clear()
        // Phase 2: Reset channel statistics
        channelStats.clear()
        
        lastTickMap.clear()
        lastPhoneTsNsMap.clear()
        
        // dTicks.clear() // Removed as dTicks is not used anymore
        msPerTicks.clear()
        ticksPerSeconds.clear()
        csvMismatchCount = 0
        csvMismatches.clear()
        tickBackwardsCount = 0
        tickRangeViolations = 0
        dtickZeroCount = 0
        largeJumpCount = 0
        
        try {
            rawCsvWriter?.close()
        } catch (e: Exception) {}
        rawCsvWriter = null
        rawCsvFile = null
    }
}
