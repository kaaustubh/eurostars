package com.sensoria.app.data.ble.sensoria

import android.content.Context
import com.sensars.eurostars.calibration.TaxelCalibrator
import com.sensoria.app.data.ble.AccelSample
import com.sensoria.app.data.ble.DeviceTimeSample
import com.sensoria.app.data.ble.GyroSample
import com.sensoria.app.data.ble.PressureSample
import com.sensoria.app.data.ble.SensorDataStreams
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.data.ble.TemperatureSample
import com.sensoria.app.viewmodel.PairingTarget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Data handler for Sensoria sensors (D20/E20 protocols).
 * Processes packet-based data from Sensoria sensors and converts it to the same
 * data format used by the existing UUID-based sensors.
 */
class SensoriaDataHandler(
    private val context: Context,
    private val sensorType: SensorType
) {
    private val parser: SensoriaProtocolParser = when (sensorType) {
        SensorType.SENSORIA_D20 -> D20ProtocolParser()
        SensorType.SENSORIA_E20 -> E20ProtocolParser()
        else -> throw IllegalArgumentException("Invalid sensor type for SensoriaDataHandler: $sensorType")
    }
    
    // Store actual streams per sensor
    private var leftSensorStreams: SensorDataStreams? = null
    private var rightSensorStreams: SensorDataStreams? = null
    
    // Unified streams that combine both sensors
    private val unifiedStreams = SensorDataStreams()
    
    // Lazy initialize calibrator - loads calibration data from assets
    private val calibrator: TaxelCalibrator? by lazy {
        try {
            val cal = TaxelCalibrator.fromAssets(context)
            android.util.Log.d("SensoriaDataHandler", "Calibration data loaded successfully")
            cal
        } catch (e: Exception) {
            android.util.Log.e("SensoriaDataHandler", "Failed to load calibration data: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    
    // Coroutine scope for calibration to run off main thread
    private val calibrationScope = CoroutineScope(Dispatchers.Default)
    
    /**
     * Register a sensor's data streams.
     */
    fun registerSensor(sensorSide: PairingTarget, streams: SensorDataStreams) {
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams = streams
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams = streams
        }
    }
    
    /**
     * Unregister a sensor's data streams.
     */
    fun unregisterSensor(sensorSide: PairingTarget) {
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams = null
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams = null
        }
    }
    
    /**
     * Process a packet received from Sensoria sensor.
     * This is called when data is received from the Sensoria Streaming Service characteristic.
     * 
     * @param packetData Raw packet bytes from BLE characteristic
     * @param timestampNanos System timestamp when packet was received
     * @param sensorSide Which sensor (LEFT or RIGHT)
     * @param streams SensorDataStreams to emit samples to
     */
    fun processPacket(
        packetData: ByteArray,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ) {
        // Check if sensor is still registered
        val registeredStreams = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorStreams
            PairingTarget.RIGHT_SENSOR -> rightSensorStreams
        }
        
        // Only process if sensor is still registered and streams match
        if (registeredStreams == null || registeredStreams != streams) {
            return
        }
        
        // Parse the packet
        val parsedPacket = parser.parsePacket(packetData)
        if (parsedPacket == null) {
            android.util.Log.w("SensoriaDataHandler", "Failed to parse packet")
            return
        }
        
        // Process analog channels (pressure/taxel data)
        processAnalogChannels(parsedPacket.analogChannels, timestampNanos, sensorSide, streams)
        
        // Process IMU data
        parsedPacket.imuData?.let { imuData ->
            processImuData(imuData, timestampNanos, sensorSide, streams)
        }
        
        // Process temperature
        parsedPacket.temperature?.let { temp ->
            val sample = TemperatureSample(temp, timestampNanos, sensorSide)
            streams._temp.tryEmit(sample)
            unifiedStreams._temp.tryEmit(sample)
        }
        
        // Process device time
        parsedPacket.deviceTime?.let { deviceTime ->
            val sample = DeviceTimeSample(deviceTime, timestampNanos, sensorSide)
            streams._time.tryEmit(sample)
            unifiedStreams._time.tryEmit(sample)
        }
    }
    
    /**
     * Process analog channel data and convert to pressure samples.
     * Maps ADC channels to taxel indices using SensoriaAdcMapping.
     */
    private fun processAnalogChannels(
        analogChannels: List<SensoriaProtocolParser.AnalogChannelData>,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ) {
        for (channelData in analogChannels) {
            val adcChannel = channelData.channelIndex
            val rawValue = channelData.rawValue.toLong()
            
            // Map ADC channel to taxel indices
            val taxelIndices = SensoriaAdcMapping.getTaxelIndices(adcChannel)
            
            if (taxelIndices.isEmpty()) {
                // Channel not mapped to any taxel - skip
                continue
            }
            
            // Emit pressure sample for each mapped taxel
            for (taxelIndex in taxelIndices) {
                // Emit raw sample immediately (without calibrated value)
                val rawSample =
                    PressureSample(taxelIndex, rawValue, null, timestampNanos, sensorSide)
                streams._pressure.tryEmit(rawSample)
                unifiedStreams._pressure.tryEmit(rawSample)
                
                // Perform calibration asynchronously and emit calibrated sample
                calibrator?.let { cal ->
                    calibrationScope.launch {
                        try {
                            val rawInt = rawValue.toInt()
                            val pascalValue = cal.calibrateTaxel(taxelIndex, rawInt)
                            android.util.Log.d("SensoriaDataHandler", 
                                "Taxel $taxelIndex (ADC $adcChannel): raw=$rawInt, calibrated=$pascalValue Pa")
                            val calibratedSample = PressureSample(taxelIndex, rawValue, pascalValue, timestampNanos, sensorSide)
                            streams._pressure.tryEmit(calibratedSample)
                            unifiedStreams._pressure.tryEmit(calibratedSample)
                        } catch (e: Exception) {
                            android.util.Log.e("SensoriaDataHandler", 
                                "Calibration failed for taxel $taxelIndex: ${e.message}", e)
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process IMU data and convert to AccelSample and GyroSample.
     */
    private fun processImuData(
        imuData: SensoriaProtocolParser.ImuData,
        timestampNanos: Long,
        sensorSide: PairingTarget,
        streams: SensorDataStreams
    ) {
        // Convert IMU raw integers to scaled floats
        val (accel, gyro, _) = when (sensorType) {
            SensorType.SENSORIA_D20 -> (parser as D20ProtocolParser).convertImuToFloats(imuData)
            SensorType.SENSORIA_E20 -> (parser as E20ProtocolParser).convertImuToFloats(imuData)
            else -> Triple(null, null, null)
        }
        
        // Emit accelerometer sample
        accel?.let { (x, y, z) ->
            val sample = AccelSample(x, y, z, timestampNanos, sensorSide)
            streams._accel.tryEmit(sample)
            unifiedStreams._accel.tryEmit(sample)
        }
        
        // Emit gyroscope sample
        gyro?.let { (x, y, z) ->
            val sample = GyroSample(x, y, z, timestampNanos, sensorSide)
            streams._gyro.tryEmit(sample)
            unifiedStreams._gyro.tryEmit(sample)
        }
        
        // Note: Magnetometer data is parsed but not currently used in the app
        // It's available in imuData.magX/Y/Z if needed in the future
    }
    
    /**
     * Get unified data streams (combines both sensors).
     */
    fun getUnifiedStreams(): SensorDataStreams = unifiedStreams
    
    /**
     * Get streams for a specific sensor.
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

