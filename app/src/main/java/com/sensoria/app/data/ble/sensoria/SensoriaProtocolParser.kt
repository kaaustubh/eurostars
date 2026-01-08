package com.sensoria.app.data.ble.sensoria

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Abstract base class for parsing Sensoria protocol packets (D20, E20, etc.).
 * Handles common header parsing and provides utilities for protocol-specific payload parsing.
 */
abstract class SensoriaProtocolParser {
    
    /**
     * Represents a parsed Sensoria packet header.
     */
    data class PacketHeader(
        val packetType: Int,
        val packetLength: Int,
        val sequenceNumber: Int,
        val timestamp: Long? = null
    )
    
    /**
     * Represents parsed analog channel data (ADC values).
     */
    data class AnalogChannelData(
        val channelIndex: Int,
        val rawValue: Int
    )
    
    /**
     * Represents parsed IMU data (accelerometer, gyroscope, magnetometer).
     */
    data class ImuData(
        val accelX: Int? = null,
        val accelY: Int? = null,
        val accelZ: Int? = null,
        val gyroX: Int? = null,
        val gyroY: Int? = null,
        val gyroZ: Int? = null,
        val magX: Int? = null,
        val magY: Int? = null,
        val magZ: Int? = null
    )
    
    /**
     * Represents a complete parsed packet with all extracted data.
     */
    data class ParsedPacket(
        val header: PacketHeader,
        val analogChannels: List<AnalogChannelData> = emptyList(),
        val imuData: ImuData? = null,
        val temperature: Float? = null,
        val deviceTime: Long? = null
    )
    
    /**
     * Parse a complete Sensoria packet from raw bytes.
     * 
     * @param data Raw packet bytes received from BLE characteristic
     * @return ParsedPacket if parsing succeeds, null otherwise
     */
    fun parsePacket(data: ByteArray): ParsedPacket? {
        if (data.isEmpty()) return null
        
        try {
            val header = parseHeader(data) ?: return null
            
            // Validate packet length
            if (data.size < header.packetLength) {
                android.util.Log.w("SensoriaProtocolParser", 
                    "Packet too short: expected ${header.packetLength}, got ${data.size}")
                return null
            }
            
            // For bit-packed protocols, pass the full data array and header bit size
            // Subclasses will calculate bit offsets from the start of the packet
            val headerBitSize = getHeaderBitSize()
            val payload = data // Pass full data for bit-packed parsing
            
            // Parse protocol-specific payload
            val parsedPayload = parsePayload(payload, header, headerBitSize)
            
            return ParsedPacket(
                header = header,
                analogChannels = parsedPayload.analogChannels,
                imuData = parsedPayload.imuData,
                temperature = parsedPayload.temperature,
                deviceTime = parsedPayload.deviceTime
            )
        } catch (e: Exception) {
            android.util.Log.e("SensoriaProtocolParser", "Error parsing packet: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Get the header size in bits (for bit-packed protocols).
     * Default implementation returns header size in bytes * 8.
     */
    protected open fun getHeaderBitSize(): Int = getHeaderSize() * 8
    
    /**
     * Parse the packet header (common across all Sensoria protocols).
     * Must be implemented by subclasses to handle protocol-specific header formats.
     * 
     * @param data Raw packet bytes
     * @return PacketHeader if parsing succeeds, null otherwise
     */
    internal abstract fun parseHeader(data: ByteArray): PacketHeader?
    
    /**
     * Get the size of the header in bytes (protocol-specific).
     */
    protected abstract fun getHeaderSize(): Int
    
    /**
     * Parse the protocol-specific payload.
     * 
     * @param data Full packet data (for bit-packed protocols, needed for bit offset calculation)
     * @param header Parsed header for context
     * @param headerBitSize Size of header in bits (for bit-packed protocols)
     * @return ParsedPayload containing extracted data
     */
    protected abstract fun parsePayload(
        data: ByteArray,
        header: PacketHeader,
        headerBitSize: Int
    ): ParsedPayload
    
    /**
     * Protocol-specific payload structure.
     */
    protected data class ParsedPayload(
        val analogChannels: List<AnalogChannelData> = emptyList(),
        val imuData: ImuData? = null,
        val temperature: Float? = null,
        val deviceTime: Long? = null
    )
    
    /**
     * Convert two's complement signed integer from raw bytes.
     * Handles both 16-bit and 32-bit signed integers.
     * 
     * @param bytes Raw bytes (little-endian)
     * @param bits Number of bits (16 or 32)
     * @return Signed integer value
     */
    protected fun twoComplementToSigned(bytes: ByteArray, bits: Int = 16): Int {
        if (bytes.isEmpty()) return 0
        
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val unsigned = when (bits) {
            16 -> if (bytes.size >= 2) buffer.short.toInt() and 0xFFFF else 0
            32 -> if (bytes.size >= 4) buffer.int else 0
            else -> 0
        }
        
        // Convert to signed using two's complement
        return when (bits) {
            16 -> {
                if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
            }
            32 -> {
                unsigned // Already signed for 32-bit
            }
            else -> unsigned
        }
    }
    
    /**
     * Convert two's complement bytes to float (for IMU data).
     * Applies scaling factor if provided.
     * 
     * @param bytes Raw bytes (little-endian)
     * @param bits Number of bits (16 or 32)
     * @param scaleFactor Optional scaling factor (e.g., 1.0/16384.0 for accelerometer)
     * @return Float value
     */
    protected fun twoComplementToFloat(
        bytes: ByteArray,
        bits: Int = 16,
        scaleFactor: Float = 1.0f
    ): Float {
        val signed = twoComplementToSigned(bytes, bits)
        return signed * scaleFactor
    }
    
    /**
     * Read unsigned integer from bytes (little-endian).
     * 
     * @param bytes Raw bytes
     * @param offset Byte offset
     * @param length Number of bytes (1, 2, or 4)
     * @return Unsigned integer value as Long
     */
    protected fun readUnsignedInt(bytes: ByteArray, offset: Int, length: Int): Long {
        if (offset + length > bytes.size) return 0L
        
        val buffer = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)
        return when (length) {
            1 -> (bytes[offset].toInt() and 0xFF).toLong()
            2 -> (buffer.short.toInt() and 0xFFFF).toLong()
            4 -> (buffer.int.toLong() and 0xFFFFFFFFL)
            else -> 0L
        }
    }
    
    /**
     * Read signed integer from bytes (little-endian).
     * 
     * @param bytes Raw bytes
     * @param offset Byte offset
     * @param length Number of bytes (1, 2, or 4)
     * @return Signed integer value
     */
    protected fun readSignedInt(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset + length > bytes.size) return 0
        
        val buffer = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)
        return when (length) {
            1 -> bytes[offset].toInt()
            2 -> buffer.short.toInt()
            4 -> buffer.int
            else -> 0
        }
    }
    
    /**
     * Read float from bytes (little-endian).
     * 
     * @param bytes Raw bytes
     * @param offset Byte offset
     * @return Float value
     */
    protected fun readFloat(bytes: ByteArray, offset: Int): Float {
        if (offset + 4 > bytes.size) return Float.NaN
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float
    }
    
    /**
     * Extract a 10-bit value from bit-packed data.
     * Sensoria protocols use 10-bit values packed bit-wise (not byte-aligned).
     * 
     * @param bytes Raw bytes containing bit-packed data
     * @param bitOffset Starting bit offset (0-based, from start of bytes array)
     * @return 10-bit unsigned value (0-1023)
     */
    protected fun read10BitValue(bytes: ByteArray, bitOffset: Int): Int {
        if (bytes.isEmpty() || bitOffset < 0) return 0
        
        val byteIndex = bitOffset / 8
        val bitInByte = bitOffset % 8
        
        if (byteIndex >= bytes.size) return 0
        
        // Read bytes needed for 10-bit value (may span 2 bytes)
        val firstByte = bytes[byteIndex].toInt() and 0xFF
        val secondByte = if (byteIndex + 1 < bytes.size) bytes[byteIndex + 1].toInt() and 0xFF else 0
        
        // Extract 10 bits starting from bitInByte
        // Case 1: bitInByte <= 6, value fits in 2 bytes
        // Case 2: bitInByte > 6, value wraps to 3rd byte
        val value = when {
            bitInByte <= 6 -> {
                // Bits from first byte: (8 - bitInByte) bits (if bitInByte < 8)
                val lowBits = (firstByte shr bitInByte) and ((1 shl (8 - bitInByte)) - 1)
                // Bits from second byte: remaining bits to make 10 total
                val bitsNeeded = 10 - (8 - bitInByte)
                val highBits = (secondByte and ((1 shl bitsNeeded) - 1)) shl (8 - bitInByte)
                (lowBits or highBits) and 0x3FF
            }
            else -> {
                // bitInByte is 7, value spans 3 bytes
                val lowBits = (firstByte shr 7) and 0x01 // 1 bit from first byte
                val midBits = secondByte and 0xFF // 8 bits from second byte
                val thirdByte = if (byteIndex + 2 < bytes.size) bytes[byteIndex + 2].toInt() and 0xFF else 0
                val highBits = (thirdByte and 0x01) shl 9 // 1 bit from third byte
                (lowBits or (midBits shl 1) or highBits) and 0x3FF
            }
        }
        
        return value
    }
    
    /**
     * Convert 10-bit two's complement value to signed integer.
     * According to protocol doc: if value >= 512, subtract 1024.
     * 
     * @param value 10-bit unsigned value (0-1023)
     * @return Signed integer value (-512 to 511)
     */
    protected fun convert10BitTwoComplement(value: Int): Int {
        return if (value >= 512) value - 1024 else value
    }
}

