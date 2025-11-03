package com.sensars.eurostars.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create a single DataStore instance for pairing data
private val Context.pairingDataStore by preferencesDataStore(name = "sensor_pairing")

// Keys for storing paired sensor information
private val KEY_LEFT_SENSOR_ID = stringPreferencesKey("left_sensor_id")
private val KEY_LEFT_SENSOR_NAME = stringPreferencesKey("left_sensor_name")
private val KEY_LEFT_SENSOR_SERIAL = stringPreferencesKey("left_sensor_serial")
private val KEY_LEFT_SENSOR_FW_VERSION = stringPreferencesKey("left_sensor_fw_version")
private val KEY_LEFT_SENSOR_BATTERY = stringPreferencesKey("left_sensor_battery")

private val KEY_RIGHT_SENSOR_ID = stringPreferencesKey("right_sensor_id")
private val KEY_RIGHT_SENSOR_NAME = stringPreferencesKey("right_sensor_name")
private val KEY_RIGHT_SENSOR_SERIAL = stringPreferencesKey("right_sensor_serial")
private val KEY_RIGHT_SENSOR_FW_VERSION = stringPreferencesKey("right_sensor_fw_version")
private val KEY_RIGHT_SENSOR_BATTERY = stringPreferencesKey("right_sensor_battery")

data class PairedSensor(
    val deviceId: String? = null,
    val deviceName: String? = null,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null
)

data class PairingStatus(
    val leftSensor: PairedSensor = PairedSensor(),
    val rightSensor: PairedSensor = PairedSensor()
) {
    val isLeftPaired: Boolean = leftSensor.deviceId != null
    val isRightPaired: Boolean = rightSensor.deviceId != null
    val areBothPaired: Boolean = isLeftPaired && isRightPaired
}

/**
 * Repository for managing paired foot sensor persistence.
 * Stores left and right sensor information separately.
 */
class PairingRepository(private val context: Context) {

    /** Observe the current pairing status */
    val pairingStatusFlow: Flow<PairingStatus> = context.pairingDataStore.data.map { prefs ->
        PairingStatus(
            leftSensor = PairedSensor(
                deviceId = prefs[KEY_LEFT_SENSOR_ID],
                deviceName = prefs[KEY_LEFT_SENSOR_NAME],
                serialNumber = prefs[KEY_LEFT_SENSOR_SERIAL],
                firmwareVersion = prefs[KEY_LEFT_SENSOR_FW_VERSION],
                batteryLevel = prefs[KEY_LEFT_SENSOR_BATTERY]?.toIntOrNull()
            ),
            rightSensor = PairedSensor(
                deviceId = prefs[KEY_RIGHT_SENSOR_ID],
                deviceName = prefs[KEY_RIGHT_SENSOR_NAME],
                serialNumber = prefs[KEY_RIGHT_SENSOR_SERIAL],
                firmwareVersion = prefs[KEY_RIGHT_SENSOR_FW_VERSION],
                batteryLevel = prefs[KEY_RIGHT_SENSOR_BATTERY]?.toIntOrNull()
            )
        )
    }

    /** Set left sensor pairing information */
    suspend fun setLeftSensor(
        deviceId: String,
        deviceName: String? = null,
        serialNumber: String? = null,
        firmwareVersion: String? = null,
        batteryLevel: Int? = null
    ) {
        context.pairingDataStore.edit { prefs ->
            prefs[KEY_LEFT_SENSOR_ID] = deviceId
            deviceName?.let { prefs[KEY_LEFT_SENSOR_NAME] = it }
            serialNumber?.let { prefs[KEY_LEFT_SENSOR_SERIAL] = it }
            firmwareVersion?.let { prefs[KEY_LEFT_SENSOR_FW_VERSION] = it }
            batteryLevel?.let { prefs[KEY_LEFT_SENSOR_BATTERY] = it.toString() }
        }
    }

    /** Set right sensor pairing information */
    suspend fun setRightSensor(
        deviceId: String,
        deviceName: String? = null,
        serialNumber: String? = null,
        firmwareVersion: String? = null,
        batteryLevel: Int? = null
    ) {
        context.pairingDataStore.edit { prefs ->
            prefs[KEY_RIGHT_SENSOR_ID] = deviceId
            deviceName?.let { prefs[KEY_RIGHT_SENSOR_NAME] = it }
            serialNumber?.let { prefs[KEY_RIGHT_SENSOR_SERIAL] = it }
            firmwareVersion?.let { prefs[KEY_RIGHT_SENSOR_FW_VERSION] = it }
            batteryLevel?.let { prefs[KEY_RIGHT_SENSOR_BATTERY] = it.toString() }
        }
    }

    /** Clear left sensor pairing */
    suspend fun clearLeftSensor() {
        context.pairingDataStore.edit { prefs ->
            prefs.remove(KEY_LEFT_SENSOR_ID)
            prefs.remove(KEY_LEFT_SENSOR_NAME)
            prefs.remove(KEY_LEFT_SENSOR_SERIAL)
            prefs.remove(KEY_LEFT_SENSOR_FW_VERSION)
            prefs.remove(KEY_LEFT_SENSOR_BATTERY)
        }
    }

    /** Clear right sensor pairing */
    suspend fun clearRightSensor() {
        context.pairingDataStore.edit { prefs ->
            prefs.remove(KEY_RIGHT_SENSOR_ID)
            prefs.remove(KEY_RIGHT_SENSOR_NAME)
            prefs.remove(KEY_RIGHT_SENSOR_SERIAL)
            prefs.remove(KEY_RIGHT_SENSOR_FW_VERSION)
            prefs.remove(KEY_RIGHT_SENSOR_BATTERY)
        }
    }

    /** Clear all pairing data */
    suspend fun clearAllPairing() {
        context.pairingDataStore.edit { it.clear() }
    }
}

