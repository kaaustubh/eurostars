package com.sensoria.app.data.ble.sensoria

import android.content.Context
import com.sensoria.app.data.ble.SensorDataStreams
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.viewmodel.PairingTarget

/**
 * Factory that creates either SDK-based or custom implementation
 * for Sensoria sensor data handling.
 * 
 * Routes to:
 * - SensoriaSdkAdapter: Official SDK implementation (when USE_SENSORIA_SDK = true)
 * - SensoriaDataHandler: Custom implementation (when USE_SENSORIA_SDK = false)
 */
object SensoriaDataHandlerFactory {
    
    /**
     * Create a data handler for Sensoria sensors.
     * 
     * @param context Application context
     * @param deviceAddress BLE device address
     * @param sensorType Detected sensor type (should be SENSORIA_STREAM_V1 for K20)
     * @param sensorSide Left or right sensor
     * @param streams Per-sensor data streams
     * @param mirrorUnifiedStreams Unified streams to mirror data to (for Walk Mode)
     * @return Either SensoriaSdkAdapter or SensoriaDataHandler based on feature flag
     */
    fun createHandler(
        context: Context,
        deviceAddress: String,
        sensorType: SensorType,
        sensorSide: PairingTarget,
        streams: SensorDataStreams,
        mirrorUnifiedStreams: SensorDataStreams? = null
    ): Any {
        return if (SensoriaSdkAdapter.USE_SENSORIA_SDK && 
                   sensorType == SensorType.SENSORIA_STREAM_V1) {
            // Use official SDK for K20 sensors
            SensoriaSdkAdapter(context, deviceAddress, sensorSide, streams, mirrorUnifiedStreams)
        } else {
            // Use custom implementation (default)
            SensoriaDataHandler(context, sensorType, mirrorUnifiedStreams).also {
                it.registerSensor(sensorSide, streams)
            }
        }
    }
    
    /**
     * Check if SDK implementation is enabled.
     */
    fun isSdkEnabled(): Boolean = SensoriaSdkAdapter.USE_SENSORIA_SDK
    
    /**
     * Get sensor type from handler (works for both implementations).
     */
    fun getSensorType(handler: Any): SensorType {
        return when (handler) {
            is SensoriaSdkAdapter -> SensorType.SENSORIA_STREAM_V1
            is SensoriaDataHandler -> handler.getSensorType()
            else -> SensorType.CURRENT
        }
    }
    
    /**
     * Start handler (for SDK adapter only).
     */
    fun start(handler: Any) {
        if (handler is SensoriaSdkAdapter) {
            handler.start()
        }
    }
    
    /**
     * Connect handler (for SDK adapter only).
     */
    fun connect(handler: Any) {
        if (handler is SensoriaSdkAdapter) {
            handler.connect()
        }
    }
    
    /**
     * Disconnect handler (for SDK adapter only).
     */
    fun disconnect(handler: Any) {
        if (handler is SensoriaSdkAdapter) {
            handler.disconnect()
        }
    }
    
    /**
     * Stop handler (for SDK adapter only).
     */
    fun stop(handler: Any) {
        if (handler is SensoriaSdkAdapter) {
            handler.stop()
        }
    }
}
