package com.sensoria.app.data.ble.sensoria

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.sensoria.app.data.ble.BleUuids
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.util.AppLog


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
     * @param context Optional context for logging (can be null)
     * @return Detected SensorType, or SensorType.CURRENT if detection fails
     */
    fun detectFromServices(gatt: BluetoothGatt, context: Context? = null): SensorType? {
        // Check for Sensoria Streaming Service
        val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
        val controlPointService = gatt.getService(BleUuids.SENSORIA_CONTROL_POINT_SERVICE)
        
        if (streamingService != null || controlPointService != null) {
            // This is a Sensoria sensor - determine protocol version
            val protocol = detectProtocolVersion(gatt, streamingService, controlPointService, context)
            return protocol
        }
        
        // Check for current UUID-based sensor (pressure service)
        val pressureService = gatt.getService(BleUuids.PRESSURE_SERVICE)
        if (pressureService != null) {
            return SensorType.CURRENT
        }
        
        // Default to CURRENT if we can't determine (unknown sensor, not Sensoria)
        return SensorType.CURRENT
    }
    
    /**
     * Detect protocol version (D20 vs E20) by analyzing service characteristics.
     * 
     * @param gatt BluetoothGatt instance
     * @param streamingService Sensoria Streaming Service (may be null)
     * @param controlPointService Sensoria Control Point Service (may be null)
     * @param context Optional context for logging
     * @return SensorType.SENSORIA_D20 or SensorType.SENSORIA_E20
     */
    private fun detectProtocolVersion(
        gatt: BluetoothGatt,
        streamingService: BluetoothGattService?,
        controlPointService: BluetoothGattService?,
        context: Context? = null
    ): SensorType {
        // Method 1: Check characteristic count or UUIDs
        // E20 may have additional characteristics or different UUIDs
        // Default to D20 if we can't determine (packet-based detection will refine this later)
        return SensorType.SENSORIA_D20
    }
    
    /**
     * Detect protocol type by analyzing packet structure.
     * This method can be called when the first packet is received.
     * 
     * @param packetData First packet bytes received from the sensor
     * @param context Optional context for logging
     * @return Detected SensorType, or null if detection fails
     */
    fun detectFromPacket(packetData: ByteArray, context: Context? = null): SensorType? {
        if (packetData.isEmpty()) {
            return null
        }
        
        try {
            // Try both parsers and see which one matches better
            val d20Parser = D20ProtocolParser()
            val d20Header = d20Parser.parseHeader(packetData)
            val d20Valid = d20Header != null && packetData.size >= d20Header.packetLength && 
                           (d20Header.packetLength == 19 || packetData.size == 19 || packetData.size == 20)
            
            val e20Parser = E20ProtocolParser()
            val e20Header = e20Parser.parseHeader(packetData)
            val e20Valid = e20Header != null && packetData.size >= e20Header.packetLength && 
                          (e20Header.packetLength == 19 || packetData.size == 19 || packetData.size == 20)
            
            // Prefer D20 if both match (D20 is more common), otherwise use whichever matches
            when {
                d20Valid && e20Valid -> return SensorType.SENSORIA_D20
                d20Valid -> return SensorType.SENSORIA_D20
                e20Valid -> return SensorType.SENSORIA_E20
            }
        } catch (e: Exception) {
            // Silently fail - will default to null
        }
        
        return null
    }
    
    /**
     * Combined detection: first try services, then fall back to packet analysis.
     * 
     * @param gatt BluetoothGatt instance (must have services discovered)
     * @param firstPacket Optional first packet data for packet-based detection
     * @param context Optional context for logging
     * @return Detected SensorType, or null if Sensoria sensor detected but protocol is unsupported
     */
    fun detect(
        gatt: BluetoothGatt,
        firstPacket: ByteArray? = null,
        context: Context? = null
    ): SensorType? {
        // Check if this is a Sensoria sensor first
        val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
        val controlPointService = gatt.getService(BleUuids.SENSORIA_CONTROL_POINT_SERVICE)
        val isSensoriaSensor = streamingService != null || controlPointService != null
        
        if (isSensoriaSensor) {
            // This is a Sensoria sensor - we need to determine if it's D20 or E20
            // Try packet-based detection first (most reliable)
            if (firstPacket != null) {
                val packetBasedType = detectFromPacket(firstPacket, context)
                if (packetBasedType != null && 
                    (packetBasedType == SensorType.SENSORIA_D20 || packetBasedType == SensorType.SENSORIA_E20)) {
                    return packetBasedType
                }
            }
            
            // Fall back to service-based detection
            val serviceBasedType = detectProtocolVersion(gatt, streamingService, controlPointService, context)
            
            // If we still can't determine or got an unsupported type, return null to indicate error
            if (serviceBasedType == SensorType.SENSORIA_D20 || serviceBasedType == SensorType.SENSORIA_E20) {
                return serviceBasedType
            } else {
                return null // Indicates unsupported protocol
            }
        }
        
        // Not a Sensoria sensor - check for current UUID-based sensor
        val pressureService = gatt.getService(BleUuids.PRESSURE_SERVICE)
        if (pressureService != null) {
            return SensorType.CURRENT
        }
        
        // Unknown sensor type - default to CURRENT
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

