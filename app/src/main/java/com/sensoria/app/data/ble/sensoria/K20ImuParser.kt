package com.sensoria.app.data.ble.sensoria

/**
 * Parser for K20 IMU protocol packets (0xF0 header).
 * 
 * K20 IMU Protocol Structure (analyzed from captured data):
 * - Header: 0xF0
 * - Byte 1: Flags/unknown (0x28 observed)
 * - Byte 2: Tick (8-bit, increments 0x09, 0x0a, 0x0b...)
 * - Bytes 3-4: Padding/flags (0x0000)
 * - Bytes 5-6: Accel X (int16 little-endian)
 * - Bytes 7-8: Accel Y (int16 little-endian)
 * - Bytes 9-10: Accel Z (int16 little-endian)
 * - Bytes 11-12: Gyro X (int16 little-endian)
 * - Bytes 13-14: Gyro Y (int16 little-endian)
 * - Bytes 15-16: Gyro Z (int16 little-endian)
 * - Bytes 17-20: Unknown (3 bytes, possibly temperature or other data)
 * 
 * Total: 20 bytes
 */
class K20ImuParser : SensoriaProtocolParser() {
    
    companion object {
        // K20 IMU packet header byte
        private const val PACKET_HEADER_K20_IMU = 0xF0
        private const val PACKET_LENGTH = 20
    }
    
    override fun getHeaderSize(): Int = 3 // Header is 3 bytes: [0xF0, flags, tick]
    
    override fun getHeaderBitSize(): Int = 24 // 3 bytes = 24 bits
    
    override fun parseHeader(data: ByteArray): PacketHeader? {
        if (data.size < 3) {
            android.util.Log.w("K20ImuParser", "Header too short: ${data.size} bytes")
            return null
        }
        
        // Check header byte
        val headerByte = data[0].toInt() and 0xFF
        if (headerByte != PACKET_HEADER_K20_IMU) {
            android.util.Log.w("K20ImuParser", "Invalid header byte: 0x${Integer.toHexString(headerByte)}, expected 0xF0")
            return null
        }
        
        // Extract tick as 8-bit from byte [2]
        val tick = data[2].toInt() and 0xFF
        
        return PacketHeader(
            packetType = PACKET_HEADER_K20_IMU,
            packetLength = PACKET_LENGTH,
            sequenceNumber = tick,
            timestamp = null
        )
    }
    
    override fun parsePayload(
        data: ByteArray,
        header: PacketHeader,
        headerBitSize: Int
    ): ParsedPayload {
        if (data.size < PACKET_LENGTH) {
            android.util.Log.w("K20ImuParser", "Packet too short: expected $PACKET_LENGTH, got ${data.size}")
            return ParsedPayload()
        }
        
        // K20 IMU packets don't contain analog channels (pressure data)
        val analogChannels = emptyList<AnalogChannelData>()
        
        // Parse IMU data starting at byte 5
        // Accel X, Y, Z: bytes 5-10 (3 x int16)
        val accelX = readSignedInt(data, 5, 2)
        val accelY = readSignedInt(data, 7, 2)
        val accelZ = readSignedInt(data, 9, 2)
        
        // Gyro X, Y, Z: bytes 11-16 (3 x int16)
        val gyroX = readSignedInt(data, 11, 2)
        val gyroY = readSignedInt(data, 13, 2)
        val gyroZ = readSignedInt(data, 15, 2)
        
        // Bytes 17-20: Unknown (possibly temperature or other data)
        // For now, we'll skip parsing this
        
        val imuData = ImuData(
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            magX = null,  // K20 IMU doesn't have magnetometer
            magY = null,
            magZ = null
        )
        
        return ParsedPayload(
            analogChannels = analogChannels,
            imuData = imuData,
            temperature = null, // TODO: Parse from bytes 17-20 if it's temperature
            deviceTime = null
        )
    }
    
    /**
     * Convert IMU raw integer values to scaled float values.
     * K20 uses int16 values directly (no 10-bit conversion needed).
     * 
     * @param imuData Raw IMU data from parsed packet
     * @return Triple of (AccelSample values, GyroSample values, null for mag) as Floats
     */
    fun convertImuToFloats(imuData: ImuData): Triple<Triple<Float, Float, Float>?, Triple<Float, Float, Float>?, Triple<Float, Float, Float>?> {
        // K20 IMU values are int16, need scaling factors
        // Typical accelerometer scaling: ±2g = ±32768 (int16 max)
        // Typical gyroscope scaling: depends on range (e.g., ±2000dps = ±32768)
        // For now, return raw values as floats (scaling can be added later based on sensor specs)
        val accelScale = 1.0f / 16384.0f // Typical: ±2g range, 16384 LSB/g
        val gyroScale = 1.0f / 16.4f // Typical: ±2000dps range, 16.4 LSB/dps
        
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
        
        val gyro = imuData.gyroX?.let { x ->
            imuData.gyroY?.let { y ->
                imuData.gyroZ?.let { z ->
                    Triple(
                        x * gyroScale,
                        y * gyroScale,
                        z * gyroScale
                    )
                }
            }
        }
        
        // K20 IMU has no magnetometer
        return Triple(accel, gyro, null)
    }
}
