package com.sensars.eurostars.data.ble

import android.content.Context
import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

/**
 * Unified data handler that processes characteristic updates from both sensors.
 * Implements the main data processing loop (equivalent to pseudocode lines 17-32).
 * Maps UUIDs to taxel indices and IMU components, and emits data to both UI and storage.
 */
class SensorDataHandler(private val context: Context) {
    private val taxelUuidToIndex = BleUuids.taxelUuidToIndexMap()
    private val imuUuidToComponent = BleUuids.imuUuidToComponentMap()

    // Store actual streams per sensor (these are the streams from BleRepository that receive data)
    private var leftSensorStreams: SensorDataStreams? = null
    private var rightSensorStreams: SensorDataStreams? = null

    // Unified streams that combine both sensors
    private val unifiedStreams = SensorDataStreams()

    /**
     * Register a sensor's data streams.
     * These are the actual streams that receive data from BLE characteristics.
     */
    fun registerSensor(sensorSide: PairingTarget, streams: SensorDataStreams) {
        // Store the actual streams that receive data
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams = streams
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams = streams
        }
    }

    /**
     * Unregister a sensor's data streams.
     * This stops data processing for the sensor.
     */
    fun unregisterSensor(sensorSide: PairingTarget) {
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> {
                leftSensorStreams = null
                android.util.Log.d("SensorDataHandler", "Unregistered LEFT_SENSOR streams")
            }
            PairingTarget.RIGHT_SENSOR -> {
                rightSensorStreams = null
                android.util.Log.d("SensorDataHandler", "Unregistered RIGHT_SENSOR streams")
            }
        }
    }

    /**
     * Process a characteristic update (called from BleRepository).
     * This implements the main data processing loop from the pseudocode.
     * Only processes data if the sensor is still registered.
     */
    fun processCharacteristicUpdate(
        characteristicUuid: UUID,
        value: ByteArray,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ) {
        // Check if sensor is still registered - if not, ignore the data
        val registeredStreams = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams
        }
        
        // Only process if sensor is still registered and streams match
        if (registeredStreams == null || registeredStreams != streams) {
            return // Sensor has been unregistered, ignore this data
        }
        
        val updated = updateTaxels(characteristicUuid, value, timestampNanos, sensorSide, streams) ||
                updateIMU(characteristicUuid, value, timestampNanos, sensorSide, streams) ||
                updateTime(characteristicUuid, value, timestampNanos, sensorSide, streams)

        // If data was updated, we could call printData() equivalent here
        // For now, data is automatically emitted via Flow
    }

    /**
     * Update taxel values (equivalent to pseudocode updateTaxels function).
     * Maps UUID to taxel index and updates the pressure sample.
     */
    // Track which taxels have received data per sensor
    private val leftReceivedTaxels = mutableSetOf<Int>()
    private val rightReceivedTaxels = mutableSetOf<Int>()
    
    private fun updateTaxels(
        uuid: UUID,
        value: ByteArray,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ): Boolean {
        val taxelIndex = taxelUuidToIndex[uuid] ?: return false
        val pressureValue = SensorDecoders.decodeUnsignedInt(value)
        val sample = PressureSample(taxelIndex, pressureValue, timestampNanos, sensorSide)
        streams._pressure.tryEmit(sample)
        // Also emit to unified stream
        unifiedStreams._pressure.tryEmit(sample)
        
        // Track which taxels have received data per sensor (for debugging if needed)
        val receivedTaxels = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftReceivedTaxels
            PairingTarget.RIGHT_SENSOR -> rightReceivedTaxels
        }
        receivedTaxels.add(taxelIndex)
        
        return true
    }
    
    /**
     * Reset the received taxels tracking (useful for testing)
     */
    fun resetTaxelTracking() {
        leftReceivedTaxels.clear()
        rightReceivedTaxels.clear()
    }

    /**
     * Update IMU values (equivalent to pseudocode updateIMU function).
     * Maps UUID to IMU component (accelX/Y/Z, gyroX/Y/Z, temp) and updates the sample.
     */
    private fun updateIMU(
        uuid: UUID,
        value: ByteArray,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ): Boolean {
        val component = imuUuidToComponent[uuid] ?: return false

        when {
            component.startsWith("accel") -> {
                val componentValue = SensorDecoders.decodeFloat(value)
                val x = if (component == "accelX") componentValue else Float.NaN
                val y = if (component == "accelY") componentValue else Float.NaN
                val z = if (component == "accelZ") componentValue else Float.NaN
                val sample = AccelSample(x, y, z, timestampNanos, sensorSide)
                streams._accel.tryEmit(sample)
                unifiedStreams._accel.tryEmit(sample)
                return true
            }
            component.startsWith("gyro") -> {
                val componentValue = SensorDecoders.decodeFloat(value)
                val x = if (component == "gyroX") componentValue else Float.NaN
                val y = if (component == "gyroY") componentValue else Float.NaN
                val z = if (component == "gyroZ") componentValue else Float.NaN
                val sample = GyroSample(x, y, z, timestampNanos, sensorSide)
                streams._gyro.tryEmit(sample)
                unifiedStreams._gyro.tryEmit(sample)
                return true
            }
            component == "temp" -> {
                val temp = SensorDecoders.decodeFloat(value)
                val sample = TemperatureSample(temp, timestampNanos, sensorSide)
                streams._temp.tryEmit(sample)
                unifiedStreams._temp.tryEmit(sample)
                return true
            }
        }
        return false
    }

    /**
     * Update device time (handles TIME_CHAR characteristic).
     */
    private fun updateTime(
        uuid: UUID,
        value: ByteArray,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ): Boolean {
        if (uuid != BleUuids.TIME_CHAR) return false
        val ms = SensorDecoders.decodeUnsignedLong(value)
        val sample = DeviceTimeSample(ms, timestampNanos, sensorSide)
        streams._time.tryEmit(sample)
        unifiedStreams._time.tryEmit(sample)
        return true
    }

    /**
     * Get unified data streams (combines both sensors).
     * Use these streams in ViewModels for UI updates.
     */
    fun getUnifiedStreams(): SensorDataStreams = unifiedStreams

    /**
     * Get streams for a specific sensor.
     * Returns the actual streams that receive data, or creates empty streams if not registered.
     */
    fun getSensorStreams(sensorSide: PairingTarget): SensorDataStreams {
        return when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams ?: SensorDataStreams()
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams ?: SensorDataStreams()
        }
    }

    /**
     * Get pressure flow for a specific sensor.
     */
    fun getPressureFlow(sensorSide: PairingTarget): SharedFlow<PressureSample> {
        val streams = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams
        }
        return streams?.pressure ?: SensorDataStreams().pressure
    }

    /**
     * Get unified pressure flow (both sensors).
     */
    fun getUnifiedPressureFlow(): SharedFlow<PressureSample> {
        return unifiedStreams.pressure
    }
}

