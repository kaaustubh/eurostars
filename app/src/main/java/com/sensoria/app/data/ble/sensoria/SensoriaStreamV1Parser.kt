package com.sensoria.app.data.ble.sensoria

/**
 * Parser for Sensoria Stream V1 protocol packets (0x5A header).
 * 
 * Stream V1 Protocol Structure:
 * - Header byte: 0x5A
 * - Tick: uint16 little-endian from bytes [1..2]
 * - Payload: 8 uint16 little-endian words from bytes [3..18] (16 bytes = 8 words * 2 bytes)
 * 
 * Total: 20 bytes
 */
class SensoriaStreamV1Parser : SensoriaProtocolParser() {
    
    companion object {
        // Stream V1 packet header byte
        private const val PACKET_HEADER_V1 = 0x5A
        private const val PACKET_LENGTH = 20
    }
    
    override fun getHeaderSize(): Int = 3 // Header is 3 bytes: [0x5A, tick_low, tick_high]
    
    override fun getHeaderBitSize(): Int = 24 // 3 bytes = 24 bits
    
    override fun parseHeader(data: ByteArray): PacketHeader? {
        if (data.size < 3) {
            android.util.Log.w("SensoriaStreamV1Parser", "Header too short: ${data.size} bytes")
            return null
        }
        
        // Check header byte
        val headerByte = data[0].toInt() and 0xFF
        if (headerByte != PACKET_HEADER_V1) {
            android.util.Log.w("SensoriaStreamV1Parser", "Invalid header byte: 0x${Integer.toHexString(headerByte)}, expected 0x5A")
            return null
        }
        
        // Extract tick as uint16 little-endian from bytes [1..2]
        // Phase 1: 16-bit tick extraction
        val tick = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        
        return PacketHeader(
            packetType = PACKET_HEADER_V1,
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
            android.util.Log.w("SensoriaStreamV1Parser", "Packet too short: expected $PACKET_LENGTH, got ${data.size}")
            return ParsedPayload()
        }
        
        val analogChannels = mutableListOf<AnalogChannelData>()
        
        // Phase 2: Decode bytes [3..18] as 8 little-endian uint16 words
        // Bytes 3-18 = 16 bytes = 8 words * 2 bytes each
        for (i in 0 until 8) {
            val offset = 3 + (i * 2) // Start at byte 3, each word is 2 bytes
            val rawValue = readUnsignedInt(data, offset, 2).toInt() // Read as uint16
            // Do NOT apply ADC 0..1023 range checks - store raw uint16 value
            analogChannels.add(AnalogChannelData(channelIndex = i, rawValue = rawValue))
        }
        
        // Stream V1 does not contain IMU data in this packet
        // IMU is expected to come from a separate characteristic
        
        return ParsedPayload(
            analogChannels = analogChannels,
            imuData = null,
            temperature = null,
            deviceTime = null
        )
    }
}
