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
        BleUuids.PRESSURE_DATA_CHARS.forEach { charUuid ->
            enableNotificationForCharacteristic(BleUuids.PRESSURE_SERVICE, charUuid)
        }

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
     * Enable notifications for a specific characteristic.
     * This writes the notification enable value to the Client Characteristic Configuration Descriptor (CCCD).
     */
    @SuppressLint("MissingPermission")
    private fun enableNotificationForCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        val service = gatt.getService(serviceUuid) ?: return
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return

        // Check if the characteristic supports notifications
        val properties = characteristic.properties
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
            return // Characteristic doesn't support notifications
        }

        // Enable local notifications
        gatt.setCharacteristicNotification(characteristic, true)

        // Write to the CCCD to enable notifications on the remote device
        val descriptor = characteristic.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            // Value 0x01 enables notifications, 0x02 enables indications
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }
}

