package com.sensoria.app.data.ble.sensoria

/**
 * Parser for Sensoria D20 protocol packets.
 * 
 * D20 Protocol Structure (from Sensoria-Core-Protocols-2.2.pdf):
 * - Header: 30 bits (packed)
 *   - 4 bits: Message type (0xD)
 *   - 2 bits: G force range (00=±2g, 01=±4g, 10=±8g, 11=±16g)
 *   - 24 bits: Tick (incrementing value 0-0xFFFFFF)
 * 
 * - Payload (bit-packed):
 *   - 8 ADC channels (Channel_0 to Channel_7): each 10 bits (0-1023)
 *   - 3 Accelerometer (X, Y, Z): each 10 bits (0-1023, two's complement)
 *   - 10 bits: Padding
 * 
 * Total: ~19 bytes (150 bits)
 */
class D20ProtocolParser : SensoriaProtocolParser() {
    
    companion object {
        // D20 packet type (4-bit value: 0xD = 13)
        private const val PACKET_TYPE_D20 = 0x0D
        
        // Accelerometer scaling based on G range (from protocol doc)
        // Values are 10-bit two's complement, need to convert and scale
        // Scaling factors depend on G range setting in header
        private fun getAccelScale(gRange: Int): Float {
            return when (gRange) {
                0 -> 2.0f / 512.0f  // ±2g: range/512 (since 10-bit, max is 512 for ±range)
                1 -> 4.0f / 512.0f  // ±4g
                2 -> 8.0f / 512.0f  // ±8g
                3 -> 16.0f / 512.0f // ±16g
                else -> 2.0f / 512.0f // Default to ±2g
            }
        }
    }
    
    override fun getHeaderSize(): Int = 4 // Minimum bytes needed
    
    override fun getHeaderBitSize(): Int = 30 // D20 header is 30 bits
    
    override fun parseHeader(data: ByteArray): PacketHeader? {
        if (data.size < 4) {
            android.util.Log.w("D20ProtocolParser", "Header too short: ${data.size} bytes")
            return null
        }
        
        // Parse bit-packed header
        // Bits 0-3: Message type (should be 0xD)
        val messageType = (data[0].toInt() and 0x0F)
        if (messageType != PACKET_TYPE_D20) {
            android.util.Log.w("D20ProtocolParser", "Invalid D20 message type: ${messageType and 0x0F}")
            // Still try to parse, might be valid
        }
        
        // Bits 4-5: G range
        val gRange = (read10BitValue(data, 0) shr 4) and 0x03
        
        // Bits 6-29: Tick (24 bits)
        // Tick spans bits 6-29, which is 24 bits across bytes
        val tickByte0 = data[0].toInt() and 0xFF
        val tickByte1 = data[1].toInt() and 0xFF
        val tickByte2 = data[2].toInt() and 0xFF
        val tickByte3 = data[3].toInt() and 0xFF
        
        // Extract 24-bit tick: bits 6-7 from byte 0, all of bytes 1-2, bits 0-5 from byte 3
        val tickLow = (tickByte0 and 0x03) shl 22  // Bits 6-7 from byte 0
        val tickMid = (tickByte1 and 0xFF) shl 14   // Byte 1
        val tickHigh = (tickByte2 and 0xFF) shl 6   // Byte 2
        val tickTop = (tickByte3 and 0x3F)          // Bits 0-5 from byte 3
        val tick = tickLow or tickMid or tickHigh or tickTop
        
        // Calculate packet length (D20 is ~19 bytes total)
        val packetLength = 19
        
        return PacketHeader(
            packetType = messageType and 0x0F,
            packetLength = packetLength,
            sequenceNumber = tick, // Use tick as sequence number
            timestamp = null
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
        
        // Extract G range from header (bits 4-5)
        val gRange = ((data[0].toInt() and 0x0F) shr 2) and 0x03
        val accelScale = getAccelScale(gRange)
        
        // D20 payload starts at bit 30 (after 30-bit header)
        // Payload is bit-packed:
        // - 8 ADC channels: 10 bits each = 80 bits starting at bit 30
        // - 3 Accelerometer: 10 bits each = 30 bits starting at bit 110
        // - 10 bits padding at bit 140
        
        var bitOffset = headerBitSize // Start after header (bit 30)
        
        // Parse 8 ADC channels (10 bits each, unsigned 0-1023)
        for (i in 0 until 8) {
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
            
            imuData = ImuData(
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                gyroX = null, // D20 has no gyroscope
                gyroY = null,
                gyroZ = null,
                magX = null,  // D20 has no magnetometer
                magY = null,
                magZ = null
            )
        }
        
        return ParsedPayload(
            analogChannels = analogChannels,
            imuData = imuData,
            temperature = null, // D20 has no temperature
            deviceTime = null    // D20 has no device time
        )
    }
    
    /**
     * Convert IMU raw integer values to scaled float values.
     * D20 only has accelerometer (10-bit two's complement).
     * 
     * @param imuData Raw IMU data from parsed packet
     * @param gRange G range setting from header (0-3)
     * @return Triple of (AccelSample values, null for gyro, null for mag) as Floats
     */
    fun convertImuToFloats(imuData: ImuData, gRange: Int = 0): Triple<Triple<Float, Float, Float>?, Triple<Float, Float, Float>?, Triple<Float, Float, Float>?> {
        val accelScale = getAccelScale(gRange)
        
        val accel = imuData.accelX?.let { x ->
            imuData.accelY?.let { y ->
                imuData.accelZ?.let { z ->
                    Triple(
                        x * accelScale,
                        y * accelScale,
                        z * accelScale
                    )
                }
            }
        }
        
        // D20 has no gyroscope or magnetometer
        return Triple(accel, null, null)
    }
}
