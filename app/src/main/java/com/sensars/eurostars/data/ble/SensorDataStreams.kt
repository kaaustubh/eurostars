package com.sensars.eurostars.data.ble

import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Data classes for streamed sensor samples with sensor identification. */
data class PressureSample(
    val taxelIndex: Int,
    val value: Long, // Raw sensor value
    val pascalValue: Double? = null, // Calibrated value in Pascals (null if not yet calibrated)
    val timestampNanos: Long,
    val sensorSide: PairingTarget
)
data class AccelSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long,
    val sensorSide: PairingTarget
)
data class GyroSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long,
    val sensorSide: PairingTarget
)
data class TemperatureSample(
    val celsius: Float,
    val timestampNanos: Long,
    val sensorSide: PairingTarget
)
data class DeviceTimeSample(
    val millis: Long,
    val timestampNanos: Long,
    val sensorSide: PairingTarget
)

/** Holds hot streams for all sensor data types. */
class SensorDataStreams internal constructor() {
    internal val _pressure = MutableSharedFlow<PressureSample>(extraBufferCapacity = 64)
    internal val _accel = MutableSharedFlow<AccelSample>(extraBufferCapacity = 32)
    internal val _gyro = MutableSharedFlow<GyroSample>(extraBufferCapacity = 32)
    internal val _temp = MutableSharedFlow<TemperatureSample>(extraBufferCapacity = 8)
    internal val _time = MutableSharedFlow<DeviceTimeSample>(extraBufferCapacity = 8)

    val pressure: SharedFlow<PressureSample> = _pressure
    val accel: SharedFlow<AccelSample> = _accel
    val gyro: SharedFlow<GyroSample> = _gyro
    val temperature: SharedFlow<TemperatureSample> = _temp
    val deviceTime: SharedFlow<DeviceTimeSample> = _time
}

/** Utility decoders aligned with ArduinoBLE data types. */
internal object SensorDecoders {
    fun decodeUnsignedInt(bytes: ByteArray): Long {
        return when (bytes.size) {
            2 -> ((bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)).toLong()
            4 -> ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            else -> 0L
        }
    }
    fun decodeUnsignedLong(bytes: ByteArray): Long {
        return if (bytes.size >= 8) ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long else 0L
    }
    fun decodeFloat(bytes: ByteArray): Float {
        return if (bytes.size >= 4) ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float else Float.NaN
    }
}

/** Map characteristic UUID to taxel index for pressure. */
internal val pressureUuidToIndex: Map<UUID, Int> = BleUuids.PRESSURE_DATA_CHARS.withIndex().associate { it.value to it.index }

