package com.sensars.eurostars.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages GATT operations for sensor characteristics, particularly enabling notifications.
 */
class SensorGattManager(private val gatt: BluetoothGatt) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val descriptorWriteQueue = ConcurrentLinkedQueue<Pair<UUID, BluetoothGattDescriptor>>()
    private val isProcessingQueue = AtomicBoolean(false)

    /**
     * Discover characteristics in the pressure service.
     */
    @SuppressLint("MissingPermission")
    private fun discoverPressureServiceCharacteristics() {
        val pressureService = gatt.getService(BleUuids.PRESSURE_SERVICE)
        if (pressureService == null) {
            // android.util.Log.e("SensorGattManager", "Pressure service (${BleUuids.PRESSURE_SERVICE}) not found!")
            return
        }
        
        // Check which expected UUIDs are missing
        val foundUuids = pressureService.characteristics.map { it.uuid }.toSet()
        val missingUuids = BleUuids.PRESSURE_DATA_CHARS.filter { it !in foundUuids }
        if (missingUuids.isNotEmpty()) {
            // android.util.Log.w("SensorGattManager", "Expected UUIDs NOT found in service: ${missingUuids.joinToString()}")
        }
    }

    /**
     * Called when a descriptor write completes. Processes the next item in the queue.
     */
    @SuppressLint("MissingPermission")
    fun onDescriptorWriteComplete(descriptor: BluetoothGattDescriptor, success: Boolean) {
        if (!success) {
            val charUuid = descriptor.characteristic.uuid
            android.util.Log.w("SensorGattManager", "Descriptor write failed for: $charUuid")
        }
        
        // Reset processing flag and process next item in queue
        isProcessingQueue.set(false)
        scope.launch {
            delay(50) // Small delay before next write
            processNextDescriptorWrite()
        }
    }
    
    /**
     * Process the next descriptor write in the queue.
     */
    @SuppressLint("MissingPermission")
    private fun processNextDescriptorWrite() {
        if (!isProcessingQueue.compareAndSet(false, true)) {
            return // Already processing
        }
        
        val next = descriptorWriteQueue.poll()
        if (next != null) {
            val (charUuid, descriptor) = next
            val writeOk = gatt.writeDescriptor(descriptor)
            if (!writeOk) {
                android.util.Log.w("SensorGattManager", "Failed to initiate descriptor write for: $charUuid")
                isProcessingQueue.set(false)
                // Try next one after a delay
                scope.launch {
                    delay(50)
                    processNextDescriptorWrite()
                }
            }
            // If writeOk is true, wait for onDescriptorWriteComplete callback
        } else {
            // android.util.Log.d("SensorGattManager", "Descriptor write queue is empty, all notifications enabled")
            isProcessingQueue.set(false)
        }
    }

    /**
     * Enable notifications for all known sensor data characteristics.
     * This includes pressure, accelerometer, gyroscope, temperature, and time characteristics.
     */
    @SuppressLint("MissingPermission")
    fun enableKnownNotifications() {
        // First, discover and log what's actually in the pressure service
        discoverPressureServiceCharacteristics()
        
        // Clear the queue
        descriptorWriteQueue.clear()
        isProcessingQueue.set(false)
        
        // Try to enable notifications for the f000a000 control characteristic first
        val controlCharUuid = UUID.fromString("f000a000-0000-1000-8000-00805f9b34fb")
        val controlResult = enableNotificationForCharacteristic(BleUuids.PRESSURE_SERVICE, controlCharUuid, queueDescriptor = false)
        // if (!controlResult.characteristicFound) {
        //     android.util.Log.w("SensorGattManager", "Control characteristic f000a000 not found or not subscribable")
        // }
        
        // Wait a bit for the control characteristic write to complete
        scope.launch {
            delay(200) // Wait 200ms for control characteristic to complete
            
            // Enable notifications for pressure characteristics - queue them
            val notFoundUuids = mutableListOf<UUID>()
            
            BleUuids.PRESSURE_DATA_CHARS.forEachIndexed { index, charUuid ->
                val result = enableNotificationForCharacteristic(BleUuids.PRESSURE_SERVICE, charUuid, queueDescriptor = true)
                if (!result.characteristicFound) {
                    notFoundUuids.add(charUuid)
                    // android.util.Log.w("SensorGattManager", "Taxel $index ($charUuid) not found")
                } else if (!result.notificationSupported) {
                    // android.util.Log.w("SensorGattManager", "Taxel $index ($charUuid) does not support notifications")
                }
            }
            
            // if (notFoundUuids.isNotEmpty()) {
            //     android.util.Log.w("SensorGattManager", "Missing characteristics: ${notFoundUuids.joinToString()}")
            // }
            
            // Start processing the queue
            processNextDescriptorWrite()
            
            // Enable notifications for other characteristics (queue them sequentially like pressure)
            delay(500) // Wait a bit before starting other characteristics
            
            // Queue accelerometer characteristics for sequential processing
            BleUuids.ACCEL_DATA_CHARS.forEachIndexed { index, charUuid ->
                enableNotificationForCharacteristic(BleUuids.ACCEL_SERVICE, charUuid, queueDescriptor = true)
            }
            
            // Queue gyroscope characteristics for sequential processing
            BleUuids.GYRO_DATA_CHARS.forEachIndexed { index, charUuid ->
                val result = enableNotificationForCharacteristic(BleUuids.GYRO_SERVICE, charUuid, queueDescriptor = true)
                // if (!result.characteristicFound) {
                //     android.util.Log.w("SensorGattManager", "Gyro characteristic $index ($charUuid) not found")
                // } else if (!result.notificationSupported) {
                //     android.util.Log.w("SensorGattManager", "Gyro characteristic $index ($charUuid) does not support notifications")
                // }
            }
            
            // Continue processing the queue (accelerometer and gyro are now queued)
            processNextDescriptorWrite()
            
            // Temperature and time can be done in parallel (non-critical)
            delay(200) // Small delay before non-critical characteristics
            enableNotificationForCharacteristic(BleUuids.TEMPERATURE_SERVICE, BleUuids.TEMPERATURE_CHAR, queueDescriptor = false)
            enableNotificationForCharacteristic(BleUuids.TIMING_SERVICE, BleUuids.TIME_CHAR, queueDescriptor = false)
        }
    }

    /**
     * Write a command to a characteristic to exit pairing mode or control sensor state.
     * This is called after successful connection and notification setup.
     * @param serviceUuid Service UUID containing the control characteristic
     * @param characteristicUuid Control characteristic UUID
     * @param command Command bytes to write
     * @return true if write was initiated successfully
     */
    @SuppressLint("MissingPermission")
    fun writeCommand(serviceUuid: UUID, characteristicUuid: UUID, command: ByteArray): Boolean {
        val service = gatt.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

        // Check if the characteristic supports write
        val properties = characteristic.properties
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) {
            return false // Characteristic doesn't support write
        }

        // Set the write type
        val writeType = if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        characteristic.writeType = writeType

        // Set the command value and write
        characteristic.value = command
        return gatt.writeCharacteristic(characteristic)
    }

    /**
     * Result of enabling notification for a characteristic.
     */
    private data class NotificationEnableResult(
        val serviceFound: Boolean = false,
        val characteristicFound: Boolean = false,
        val notificationSupported: Boolean = false,
        val descriptorWritten: Boolean = false
    )

    /**
     * Enable notifications for a specific characteristic.
     * This writes the notification enable value to the Client Characteristic Configuration Descriptor (CCCD).
     * @param queueDescriptor If true, queues the descriptor write for sequential processing. If false, writes immediately.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotificationForCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, queueDescriptor: Boolean = false): NotificationEnableResult {
        val service = gatt.getService(serviceUuid) ?: run {
            android.util.Log.w("SensorGattManager", "Service not found: $serviceUuid")
            return NotificationEnableResult()
        }
        val result = NotificationEnableResult(serviceFound = true)
        
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            android.util.Log.w("SensorGattManager", "Characteristic not found: $characteristicUuid in service $serviceUuid")
            return result
        }
        val resultWithChar = result.copy(characteristicFound = true)

        // Check if the characteristic supports notifications
        val properties = characteristic.properties
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
            android.util.Log.w("SensorGattManager", "Characteristic $characteristicUuid does not support notifications (properties: $properties)")
            return resultWithChar // Characteristic doesn't support notifications
        }
        val resultWithNotify = resultWithChar.copy(notificationSupported = true)

        // Enable local notifications
        gatt.setCharacteristicNotification(characteristic, true)

        // Write to the CCCD to enable notifications on the remote device
        val descriptor = characteristic.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            // Value 0x01 enables notifications, 0x02 enables indications
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            
            if (queueDescriptor) {
                // Queue for sequential processing
                descriptorWriteQueue.offer(Pair(characteristicUuid, descriptor))
                return resultWithNotify.copy(descriptorWritten = true) // Consider it queued as success
            } else {
                // Write immediately (for non-critical characteristics)
                val writeOk = gatt.writeDescriptor(descriptor)
                return resultWithNotify.copy(descriptorWritten = writeOk)
            }
        } else {
            android.util.Log.w("SensorGattManager", "CCCD descriptor not found for $characteristicUuid")
            return resultWithNotify
        }
    }
}

