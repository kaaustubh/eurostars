package com.sensoria.app.data.ble.sensoria

/**
 * Parser for Sensoria E20 protocol packets.
 * 
 * E20 Protocol Structure (from Sensoria-Core-Protocols-2.2.pdf):
 * - Header: 32 bits (packed)
 *   - 4 bits: Message type (0xE)
 *   - 2 bits: G force range (00=±2g, 01=±4g, 10=±8g, 11=±16g)
 *   - 2 bits: DPS range (00=245dps, 01=500dps, 10=1000dps, 11=2000dps)
 *   - 24 bits: Tick (incrementing value 0-0xFFFFFF)
 * 
 * - Payload (bit-packed):
 *   - 6 ADC channels (Channel_0 to Channel_5): each 10 bits (0-1023)
 *   - 3 Accelerometer (X, Y, Z): each 10 bits (0-1023, two's complement)
 *   - 3 Gyroscope (X, Y, Z): each 10 bits (0-1023, two's complement)
 * 
 * Total: ~20 bytes (152 bits)
 */
class E20ProtocolParser : SensoriaProtocolParser() {
    
    companion object {
        // E20 packet type (4-bit value: 0xE = 14)
        private const val PACKET_TYPE_E20 = 0x0E
        
        // Accelerometer scaling based on G range
        private fun getAccelScale(gRange: Int): Float {
            return when (gRange) {
                0 -> 2.0f / 512.0f  // ±2g
                1 -> 4.0f / 512.0f  // ±4g
                2 -> 8.0f / 512.0f  // ±8g
                3 -> 16.0f / 512.0f // ±16g
                else -> 2.0f / 512.0f
            }
        }
        
        // Gyroscope scaling based on DPS range
        private fun getGyroScale(dpsRange: Int): Float {
            return when (dpsRange) {
                0 -> 245.0f / 512.0f  // ±245dps
                1 -> 500.0f / 512.0f  // ±500dps
                2 -> 1000.0f / 512.0f // ±1000dps
                3 -> 2000.0f / 512.0f // ±2000dps
                else -> 245.0f / 512.0f
            }
        }
    }
    
    // Configuration for E20 (can be adjusted based on sensor configuration)
    private var accelRange: Float = getAccelScale(0)
    private var gyroRange: Float = getGyroScale(0)
    
    override fun getHeaderSize(): Int = 4 // Minimum bytes needed for header
    
    override fun getHeaderBitSize(): Int = 32 // E20 header is 32 bits
    
    override fun parseHeader(data: ByteArray): PacketHeader? {
        if (data.size < 4) {
            android.util.Log.w("E20ProtocolParser", "Header too short: ${data.size} bytes")
            return null
        }
        
        // Parse bit-packed header
        // Bits 0-3: Message type (should be 0xE)
        val messageType = (data[0].toInt() and 0xFF) shr 4
        if (messageType != PACKET_TYPE_E20) {
            android.util.Log.w("E20ProtocolParser", "Invalid E20 message type: $messageType")
        }
        
        // Bits 4-5: G range
        val gRange = (data[0].toInt() and 0x0F) shr 2
        
        // Bits 6-7: DPS range
        val dpsRange = data[0].toInt() and 0x03
        
        // Bits 8-31: Tick (24 bits)
        val tickByte1 = data[1].toInt() and 0xFF
        val tickByte2 = data[2].toInt() and 0xFF
        val tickByte3 = data[3].toInt() and 0xFF
        val tick = (tickByte1) or (tickByte2 shl 8) or (tickByte3 shl 16)
        
        // Store ranges for scaling
        accelRange = getAccelScale(gRange)
        gyroRange = getGyroScale(dpsRange)
        
        // Calculate packet length (E20 is ~20 bytes total)
        val packetLength = 20
        
        return PacketHeader(
            packetType = messageType,
            packetLength = packetLength,
            sequenceNumber = tick,
            timestamp = tick.toLong() // Use tick as timestamp
        )
    }
    
    override fun parsePayload(
        data: ByteArray,
        header: PacketHeader,
        headerBitSize: Int
    ): ParsedPayload {
        if (data.isEmpty()) {
            return ParsedPayload()
        }
        
        val analogChannels = mutableListOf<AnalogChannelData>()
        var imuData: ImuData? = null
        
        // Extract G range and DPS range from header (already stored in accelRange/gyroRange)
        
        // E20 payload starts at bit 32 (after 32-bit header)
        // Payload is bit-packed:
        // - 6 ADC channels: 10 bits each = 60 bits starting at bit 32
        // - 3 Accelerometer: 10 bits each = 30 bits starting at bit 92
        // - 3 Gyroscope: 10 bits each = 30 bits starting at bit 122
        
        var bitOffset = headerBitSize // Start after header (bit 32)
        
        // Parse 6 ADC channels (10 bits each, unsigned 0-1023)
        for (i in 0 until 6) {
            val rawValue = read10BitValue(data, bitOffset)
            analogChannels.add(AnalogChannelData(channelIndex = i, rawValue = rawValue))
            bitOffset += 10
        }
        
        // Parse accelerometer (X, Y, Z) - 10 bits each, two's complement
        var accelX: Int? = null
        var accelY: Int? = null
        var accelZ: Int? = null
        
        if (data.size * 8 >= bitOffset + 30) {
            val accelXRaw = read10BitValue(data, bitOffset)
            accelX = convert10BitTwoComplement(accelXRaw)
            bitOffset += 10
            
            val accelYRaw = read10BitValue(data, bitOffset)
            accelY = convert10BitTwoComplement(accelYRaw)
            bitOffset += 10
            
            val accelZRaw = read10BitValue(data, bitOffset)
            accelZ = convert10BitTwoComplement(accelZRaw)
            bitOffset += 10
        }
        
        // Parse gyroscope (X, Y, Z) - 10 bits each, two's complement
        var gyroX: Int? = null
        var gyroY: Int? = null
        var gyroZ: Int? = null
        
        if (data.size * 8 >= bitOffset + 30) {
            val gyroXRaw = read10BitValue(data, bitOffset)
            gyroX = convert10BitTwoComplement(gyroXRaw)
            bitOffset += 10
            
            val gyroYRaw = read10BitValue(data, bitOffset)
            gyroY = convert10BitTwoComplement(gyroYRaw)
            bitOffset += 10
            
            val gyroZRaw = read10BitValue(data, bitOffset)
            gyroZ = convert10BitTwoComplement(gyroZRaw)
            bitOffset += 10
        }
        
        if (accelX != null || gyroX != null) {
            imuData = ImuData(
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ,
                magX = null,  // E20 has no magnetometer
                magY = null,
                magZ = null
            )
        }
        
        return ParsedPayload(
            analogChannels = analogChannels,
            imuData = imuData,
            temperature = null, // E20 has no temperature in packet
            deviceTime = header.timestamp
        )
    }
    
    /**
     * Configure IMU scaling factors based on sensor configuration.
     */
    fun configureImuScaling(accelRangeScale: Float, gyroRangeScale: Float) {
        accelRange = accelRangeScale
        gyroRange = gyroRangeScale
        android.util.Log.d("E20ProtocolParser", "IMU scaling configured: accel=$accelRangeScale, gyro=$gyroRangeScale")
    }
    
    /**
     * Convert IMU raw integer values to scaled float values.
     * E20 has accelerometer and gyroscope (both 10-bit two's complement).
     * 
     * @param imuData Raw IMU data from parsed packet
     * @return Triple of (AccelSample values, GyroSample values, null for mag) as Floats
     */
    fun convertImuToFloats(imuData: ImuData): Triple<Triple<Float, Float, Float>?, Triple<Float, Float, Float>?, Triple<Float, Float, Float>?> {
        val accel = imuData.accelX?.let { x ->
            imuData.accelY?.let { y ->
                imuData.accelZ?.let { z ->
                    Triple(
                        x * accelRange,
                        y * accelRange,
                        z * accelRange
                    )
                }
            }
        }
        
        val gyro = imuData.gyroX?.let { x ->
            imuData.gyroY?.let { y ->
                imuData.gyroZ?.let { z ->
                    Triple(
                        x * gyroRange,
                        y * gyroRange,
                        z * gyroRange
                    )
                }
            }
        }
        
        // E20 has no magnetometer
        return Triple(accel, gyro, null)
    }
}
