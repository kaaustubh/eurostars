package com.sensars.eurostars.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID

/**
 * Manages GATT operations for sensor characteristics, particularly enabling notifications.
 */
class SensorGattManager(private val gatt: BluetoothGatt) {

    /**
     * Enable notifications for all known sensor data characteristics.
     * This includes pressure, accelerometer, gyroscope, temperature, and time characteristics.
     */
    @SuppressLint("MissingPermission")
    fun enableKnownNotifications() {
        // Enable notifications for pressure characteristics
        var enabledCount = 0
        var foundCount = 0
        var notificationSupportedCount = 0
        var descriptorWriteCount = 0
        
        BleUuids.PRESSURE_DATA_CHARS.forEachIndexed { index, charUuid ->
            val result = enableNotificationForCharacteristic(BleUuids.PRESSURE_SERVICE, charUuid)
            if (result.serviceFound) foundCount++
            if (result.characteristicFound) enabledCount++
            if (result.notificationSupported) notificationSupportedCount++
            if (result.descriptorWritten) descriptorWriteCount++
        }
        android.util.Log.d("SensorGattManager", "Pressure characteristics: Found=$foundCount, Enabled=$enabledCount, NotificationSupported=$notificationSupportedCount, DescriptorWritten=$descriptorWriteCount")

        // Enable notifications for accelerometer characteristics
        BleUuids.ACCEL_DATA_CHARS.forEach { charUuid ->
            enableNotificationForCharacteristic(BleUuids.ACCEL_SERVICE, charUuid)
        }

        // Enable notifications for gyroscope characteristics
        BleUuids.GYRO_DATA_CHARS.forEach { charUuid ->
            enableNotificationForCharacteristic(BleUuids.GYRO_SERVICE, charUuid)
        }

        // Enable notification for temperature characteristic
        enableNotificationForCharacteristic(BleUuids.TEMPERATURE_SERVICE, BleUuids.TEMPERATURE_CHAR)

        // Enable notification for time characteristic
        enableNotificationForCharacteristic(BleUuids.TIMING_SERVICE, BleUuids.TIME_CHAR)
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
     */
    @SuppressLint("MissingPermission")
    private fun enableNotificationForCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): NotificationEnableResult {
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
        val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
        android.util.Log.d("SensorGattManager", "setCharacteristicNotification for $characteristicUuid: $notificationSet")

        // Write to the CCCD to enable notifications on the remote device
        val descriptor = characteristic.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            // Value 0x01 enables notifications, 0x02 enables indications
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeOk = gatt.writeDescriptor(descriptor)
            android.util.Log.d("SensorGattManager", "writeDescriptor for $characteristicUuid: $writeOk")
            return resultWithNotify.copy(descriptorWritten = writeOk)
        } else {
            android.util.Log.w("SensorGattManager", "CCCD descriptor not found for $characteristicUuid")
            return resultWithNotify
        }
    }
}

