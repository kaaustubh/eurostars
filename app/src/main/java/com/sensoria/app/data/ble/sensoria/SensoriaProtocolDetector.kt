package com.sensoria.app.data.ble.sensoria

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import com.sensoria.app.data.ble.BleUuids
import com.sensoria.app.data.ble.SensorType


/**
 * Detects the sensor protocol type (CURRENT, SENSORIA_D20, SENSORIA_E20) by analyzing
 * BLE services and optionally packet structure.
 */
class SensoriaProtocolDetector {
    
    /**
     * Detect sensor protocol type based on GATT services.
     * This is the primary detection method - checks for Sensoria service UUIDs.
     * 
     * @param gatt BluetoothGatt instance (must have services discovered)
     * @return Detected SensorType, or SensorType.CURRENT if detection fails
     */
    fun detectFromServices(gatt: BluetoothGatt): SensorType? {
        // Check for Sensoria Streaming Service
        val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
        val controlPointService = gatt.getService(BleUuids.SENSORIA_CONTROL_POINT_SERVICE)
        
        if (streamingService != null || controlPointService != null) {
            // This is a Sensoria sensor - determine protocol version
            // For now, we'll use packet analysis to distinguish D20 vs E20
            // If we can't determine, default to D20
            android.util.Log.d("SensoriaProtocolDetector", "Sensoria services detected")
            
            // Try to determine D20 vs E20 by checking characteristics or packet structure
            // E20 typically has more characteristics or different structure
            val protocol = detectProtocolVersion(gatt, streamingService, controlPointService)
            android.util.Log.d("SensoriaProtocolDetector", "Detected protocol: $protocol")
            return protocol
        }
        
        // Check for current UUID-based sensor (pressure service)
        val pressureService = gatt.getService(BleUuids.PRESSURE_SERVICE)
        if (pressureService != null) {
            android.util.Log.d("SensoriaProtocolDetector", "Current UUID-based sensor detected")
            return SensorType.CURRENT
        }
        
        // Default to CURRENT if we can't determine (unknown sensor, not Sensoria)
        android.util.Log.w("SensoriaProtocolDetector", "Could not detect sensor type, defaulting to CURRENT")
        return SensorType.CURRENT
    }
    
    /**
     * Detect protocol version (D20 vs E20) by analyzing service characteristics.
     * 
     * @param gatt BluetoothGatt instance
     * @param streamingService Sensoria Streaming Service (may be null)
     * @param controlPointService Sensoria Control Point Service (may be null)
     * @return SensorType.SENSORIA_D20 or SensorType.SENSORIA_E20
     */
    private fun detectProtocolVersion(
        gatt: BluetoothGatt,
        streamingService: BluetoothGattService?,
        controlPointService: BluetoothGattService?
    ): SensorType {
        // Method 1: Check characteristic count or UUIDs
        // E20 may have additional characteristics or different UUIDs
        val streamingChars = streamingService?.characteristics?.size ?: 0
        val controlChars = controlPointService?.characteristics?.size ?: 0
        
        // Method 2: Try to read a sample packet and analyze header size
        // This is more reliable but requires data to be available
        // For now, we'll use a heuristic based on service structure
        
        // Default to D20 if we can't determine
        // In practice, you might want to:
        // 1. Read device info service for firmware version
        // 2. Analyze first packet received
        // 3. Check for E20-specific characteristics
        
        // For now, default to D20
        // TODO: Implement more sophisticated detection based on actual protocol specs
        return SensorType.SENSORIA_D20
    }
    
    /**
     * Detect protocol type by analyzing packet structure.
     * This method can be called when the first packet is received.
     * 
     * @param packetData First packet bytes received from the sensor
     * @return Detected SensorType, or null if detection fails
     */
    fun detectFromPacket(packetData: ByteArray): SensorType? {
        if (packetData.isEmpty()) return null
        
        try {
            // Try D20 parser (4-byte header)
            val d20Parser = D20ProtocolParser()
            val d20Header = d20Parser.parseHeader(packetData)
            if (d20Header != null && packetData.size >= 4) {
                // Check if packet length matches D20 structure
                val expectedD20MinSize = 4 + 16 + 6 + 6 + 6 // header + analog + accel + gyro + mag
                if (d20Header.packetLength >= expectedD20MinSize && d20Header.packetLength <= 50) {
                    android.util.Log.d("SensoriaProtocolDetector", "Packet structure matches D20")
                    return SensorType.SENSORIA_D20
                }
            }
            
            // Try E20 parser (6-byte header)
            val e20Parser = E20ProtocolParser()
            val e20Header = e20Parser.parseHeader(packetData)
            if (e20Header != null && packetData.size >= 6) {
                // Check if packet length matches E20 structure
                val expectedE20MinSize = 6 + 16 + 6 + 6 + 6 + 2 // header + analog + accel + gyro + mag + temp
                if (e20Header.packetLength >= expectedE20MinSize && e20Header.packetLength <= 50) {
                    android.util.Log.d("SensoriaProtocolDetector", "Packet structure matches E20")
                    return SensorType.SENSORIA_E20
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SensoriaProtocolDetector", "Error detecting from packet: ${e.message}", e)
        }
        
        return null
    }
    
    /**
     * Combined detection: first try services, then fall back to packet analysis.
     * 
     * @param gatt BluetoothGatt instance (must have services discovered)
     * @param firstPacket Optional first packet data for packet-based detection
     * @return Detected SensorType, or null if Sensoria sensor detected but protocol is unsupported
     */
    fun detect(
        gatt: BluetoothGatt,
        firstPacket: ByteArray? = null
    ): SensorType? {
        // Check if this is a Sensoria sensor first
        val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
        val controlPointService = gatt.getService(BleUuids.SENSORIA_CONTROL_POINT_SERVICE)
        val isSensoriaSensor = streamingService != null || controlPointService != null
        
        if (isSensoriaSensor) {
            // This is a Sensoria sensor - we need to determine if it's D20 or E20
            android.util.Log.d("SensoriaProtocolDetector", "Sensoria sensor detected, determining protocol version")
            
            // Try packet-based detection first (most reliable)
            if (firstPacket != null) {
                val packetBasedType = detectFromPacket(firstPacket)
                if (packetBasedType != null && 
                    (packetBasedType == SensorType.SENSORIA_D20 || packetBasedType == SensorType.SENSORIA_E20)) {
                    android.util.Log.d("SensoriaProtocolDetector", 
                        "Protocol determined from packet: $packetBasedType")
                    return packetBasedType
                }
            }
            
            // Fall back to service-based detection
            val serviceBasedType = detectProtocolVersion(gatt, streamingService, controlPointService)
            
            // If we still can't determine or got an unsupported type, return null to indicate error
            if (serviceBasedType == SensorType.SENSORIA_D20 || serviceBasedType == SensorType.SENSORIA_E20) {
                android.util.Log.d("SensoriaProtocolDetector", "Protocol determined from services: $serviceBasedType")
                return serviceBasedType
            } else {
                android.util.Log.w("SensoriaProtocolDetector", 
                    "Sensoria sensor detected but protocol is not D20 or E20 (got: $serviceBasedType)")
                return null // Indicates unsupported protocol
            }
        }
        
        // Not a Sensoria sensor - check for current UUID-based sensor
        val pressureService = gatt.getService(BleUuids.PRESSURE_SERVICE)
        if (pressureService != null) {
            android.util.Log.d("SensoriaProtocolDetector", "Current UUID-based sensor detected")
            return SensorType.CURRENT
        }
        
        // Unknown sensor type - default to CURRENT
        android.util.Log.w("SensoriaProtocolDetector", "Could not detect sensor type, defaulting to CURRENT")
        return SensorType.CURRENT
    }
    
    /**
     * Detect protocol from device name or manufacturer data (advertising data).
     * This can be used during scanning before connection.
     * 
     * @param deviceName Device name from scan result
     * @param manufacturerData Optional manufacturer-specific data
     * @return Detected SensorType, or null if detection fails
     */
    fun detectFromAdvertising(
        deviceName: String?,
        manufacturerData: ByteArray? = null
    ): SensorType? {
        // Check device name for Sensoria indicators
        deviceName?.let { name ->
            val lowerName = name.lowercase()
            if (lowerName.contains("sensoria", ignoreCase = true)) {
                // Could be D20 or E20 - default to D20
                // In practice, device name might indicate version
                if (lowerName.contains("e20", ignoreCase = true)) {
                    return SensorType.SENSORIA_E20
                } else if (lowerName.contains("d20", ignoreCase = true)) {
                    return SensorType.SENSORIA_D20
                }
                // Default to D20 if just "sensoria"
                return SensorType.SENSORIA_D20
            }
        }
        
        // Could analyze manufacturer data if available
        // For now, return null (unknown)
        return null
    }
}

