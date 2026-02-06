package com.sensoria.app.data.ble.sensoria

import android.content.Context
import com.sensoria.app.data.ble.AccelSample
import com.sensoria.app.data.ble.GyroSample
import com.sensoria.app.data.ble.PressureSample
import com.sensoria.app.data.ble.SensorDataStreams
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.util.AppLog
import com.sensoria.app.viewmodel.PairingTarget

import io.sensoria.sdk.DeviceDescriptor
import io.sensoria.sdk.data.DataPoint
import io.sensoria.sdk.enums.SdkError
import io.sensoria.sdk.enums.ServiceEvent
import io.sensoria.sdk.enums.ServiceType
import io.sensoria.sdk.enums.ProtocolType
import io.sensoria.sdk.interfaces.IStreamingServiceCallback
import io.sensoria.sdk.services.StreamingService

/**
 * Adapter that wraps the official Sensoria SDK StreamingService
 * and converts SDK DataPoint to our SensorDataStreams format.
 * 
 * Reverted to "Passive Mode" (no active configuration) to ensure stability and 4-channel support.
 * This restores the state where 4 channels were working, but relies on SensorConnectionManager
 * for connection priority to handle frequency issues.
 */
class SensoriaSdkAdapter(
    private val context: Context,
    private val deviceAddress: String,
    private val sensorSide: PairingTarget,
    private val streams: SensorDataStreams,
    private val mirrorUnifiedStreams: SensorDataStreams? = null
) : IStreamingServiceCallback {
    
    private var streamingService: StreamingService? = null
    private var isConnected = false
    private var isStopped = false
    private var lastDataNs: Long = 0L

    private var isStreamingActive: Boolean = false
    
    companion object {
        // Feature flag to enable SDK implementation
        const val USE_SENSORIA_SDK = false 
        
        private const val TAG = "SensoriaSdkAdapter"
    }
    
    /**
     * Start the SDK streaming service and connect to the device.
     */
    fun start() {
        try {
            streamingService = StreamingService(deviceAddress)
            // SDK's start() method signature: start(IServiceCallback callback, Context context)
            streamingService?.start(this, context)
            
            AppLog.i(context, TAG, "SDK StreamingService started for $deviceAddress")
        } catch (e: Exception) {
            AppLog.e(context, TAG, "Failed to start SDK Services: ${e.message}")
        }
    }
    
    /**
     * Connect to the device using SDK.
     */
    fun connect() {
        try {
            streamingService?.connect()
            AppLog.i(context, TAG, "SDK connect() called for $deviceAddress")
        } catch (e: Exception) {
            AppLog.e(context, TAG, "Failed to connect SDK: ${e.message}")
        }
    }
    
    /**
     * Disconnect from the device.
     */
    fun disconnect() {
        streamingService?.disconnect()
        isConnected = false
        AppLog.i(context, TAG, "SDK disconnected from $deviceAddress")
    }
    
    /**
     * Stop the streaming service.
     */
    fun stop() {
        isStopped = true
        streamingService?.stop()
        streamingService = null
        isConnected = false
        AppLog.i(context, TAG, "SDK Services stopped for $deviceAddress")
    }
    
    /**
     * Pause streaming.
     */
    fun pause() {
        streamingService?.pauseStreaming()
    }
    
    /**
     * Resume streaming.
     */
    fun resume() {
        streamingService?.resumeStreaming()
    }
    
    // IStreamingServiceCallback implementation
    
    override fun didServiceError(
        deviceDescriptor: DeviceDescriptor,
        serviceType: ServiceType,
        serviceName: String,
        functionName: String,
        errorCode: SdkError,
        innerErrorCode: String?
    ) {
        AppLog.e(context, TAG, "SDK Error: $serviceName.$functionName - $errorCode (inner: $innerErrorCode)")
    }
    
    override fun didServiceEvent(
        deviceDescriptor: DeviceDescriptor,
        serviceType: ServiceType,
        event: ServiceEvent
    ) {
        when (event) {
            ServiceEvent.CONNECTED -> {
                isConnected = true
                AppLog.i(context, TAG, "SDK connected to ${deviceDescriptor.deviceName} ($serviceType)")
            }
            ServiceEvent.READY -> {
                AppLog.i(context, TAG, "SDK READY for ${deviceDescriptor.deviceName} ($serviceType)")
            }
            ServiceEvent.DISCONNECTED -> {
                isConnected = false
                AppLog.i(context, TAG, "SDK disconnected from ${deviceDescriptor.deviceName} ($serviceType)")
            }
            ServiceEvent.SIGNAL_LOST -> {
                AppLog.w(context, TAG, "SDK signal lost for ${deviceDescriptor.deviceName}")
            }
            else -> {
                AppLog.i(context, TAG, "SDK event: $event for ${deviceDescriptor.deviceName}")
            }
        }
    }
    
    override fun didUpdateData(
        deviceDescriptor: DeviceDescriptor,
        serviceType: ServiceType,
        dataPoint: DataPoint
    ) {
        if (isStopped) return

        val now = System.nanoTime()
        lastDataNs = now
        
        // Log SDK data point for analysis
        SensoriaSdkAnalysis.processDataPoint(deviceAddress, dataPoint, now)
        
        // Determine protocol from SDK DataPoint
        val protocol = when (dataPoint.protocolType) {
            ProtocolType.K20 -> SensorType.SENSORIA_STREAM_V1
            ProtocolType.D20 -> SensorType.SENSORIA_D20
            ProtocolType.E20 -> SensorType.SENSORIA_E20
            else -> SensorType.SENSORIA_STREAM_V1 // Default to V1 for this adapter context
        }
        
        // Convert pressure channels (K20 sensors use 4 channels)
        // Map SDK data directly to CSV - no conversion applied
        // Force exactly 4 channels for K20/V1 protocol
        val channelCount = if (protocol == SensorType.SENSORIA_STREAM_V1) 4 else minOf(dataPoint.channels.size, 4)
        
        for (i in 0 until channelCount) {
            // Safe access to channel data - use 0 if index out of bounds
            val rawValue = if (i < dataPoint.channels.size) dataPoint.channels[i].toLong() else 0L
            
            val pressureSample = PressureSample(
                taxelIndex = i,
                value = rawValue,
                pascalValue = null, // Keep raw values as per user request
                timestampNanos = now,
                sensorSide = sensorSide,
                tick = dataPoint.tick,
                protocol = protocol
            )
            
            streams._pressure.tryEmit(pressureSample)
            mirrorUnifiedStreams?._pressure?.tryEmit(pressureSample)
        }
        
        // Convert accelerometer data
        if (dataPoint.accelerometer != null && dataPoint.accelerometer.size >= 3) {
            val accelSample = AccelSample(
                x = dataPoint.accelerometer[0].toFloat(),
                y = dataPoint.accelerometer[1].toFloat(),
                z = dataPoint.accelerometer[2].toFloat(),
                timestampNanos = now,
                sensorSide = sensorSide,
                tick = dataPoint.tick,
                protocol = SensorType.SENSORIA_STREAM_V1
            )
            streams._accel.tryEmit(accelSample)
            mirrorUnifiedStreams?._accel?.tryEmit(accelSample)
        }
        
        // Convert gyroscope data
        if (dataPoint.gyroscope != null && dataPoint.gyroscope.size >= 3) {
            val gyroSample = GyroSample(
                x = dataPoint.gyroscope[0].toFloat(),
                y = dataPoint.gyroscope[1].toFloat(),
                z = dataPoint.gyroscope[2].toFloat(),
                timestampNanos = now,
                sensorSide = sensorSide,
                tick = dataPoint.tick,
                protocol = SensorType.SENSORIA_STREAM_V1
            )
            streams._gyro.tryEmit(gyroSample)
            mirrorUnifiedStreams?._gyro?.tryEmit(gyroSample)
        }
    }
    
    override fun didChangeStreaming(
        deviceDescriptor: DeviceDescriptor,
        serviceType: ServiceType,
        isStreaming: Boolean
    ) {
        isStreamingActive = isStreaming
        AppLog.i(context, TAG, "SDK streaming changed: $isStreaming for ${deviceDescriptor.deviceName}")
    }
    
    /**
     * Check if the adapter is currently connected.
     */
    fun isConnected(): Boolean = isConnected
}
