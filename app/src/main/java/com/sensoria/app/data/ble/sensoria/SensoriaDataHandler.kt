package com.sensoria.app.data.ble.sensoria

import android.content.Context
import com.sensoria.app.data.ble.AccelSample
import com.sensoria.app.data.ble.DeviceTimeSample
import com.sensoria.app.data.ble.GyroSample
import com.sensoria.app.data.ble.PressureSample
import com.sensoria.app.data.ble.SensorDataStreams
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.data.ble.TemperatureSample
import com.sensoria.app.util.AppLog
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
    private val sensorType: SensorType,
    private val mirrorUnifiedStreams: SensorDataStreams? = null
) {
    private val parser: SensoriaProtocolParser = when (sensorType) {
        SensorType.SENSORIA_D20 -> D20ProtocolParser()
        SensorType.SENSORIA_E20 -> E20ProtocolParser()
        SensorType.SENSORIA_STREAM_V1 -> SensoriaStreamV1Parser()
        else -> throw IllegalArgumentException("Invalid sensor type for SensoriaDataHandler: $sensorType")
    }
    
    // Store actual streams per sensor
    private var leftSensorStreams: SensorDataStreams? = null
    private var rightSensorStreams: SensorDataStreams? = null
    
    // Unified streams that combine both sensors
    private val unifiedStreams = SensorDataStreams()
    
    // Packet counter for logging
    private var packetCount = 0L
    
    // Note: Sensoria sensors do NOT use the calibration library
    // The calibration library is specifically for TouchLab sensors (18 taxels)
    // Sensoria sensors use raw ADC values directly
    
    /**
     * Expose sensor type for detection/verification.
     */
    fun getSensorType(): SensorType = sensorType
    
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
        
        // VERIFICATION: Process raw packet - Handled in BleRepository now
        // if (SensoriaVerifier.SENSORIA_VERIFY) {
        //    SensoriaVerifier.processPacket(packetData, context)
        // }
        
        // Parse the packet
        val parsedPacket = parser.parsePacket(packetData)
        if (parsedPacket == null) {
            return
        }
        
        // VERIFICATION: Verify decoded values
        if (SensoriaVerifier.SENSORIA_VERIFY) {
            SensoriaVerifier.verifyDecodedValues(parsedPacket, parsedPacket.header, packetData, context, sensorSide)
        }
        
        // Process analog channels (pressure/taxel data)
        processAnalogChannels(parsedPacket.analogChannels, timestampNanos, sensorSide, streams, parsedPacket.header.sequenceNumber)
        
        // Process IMU data
        parsedPacket.imuData?.let { imuData ->
            processImuData(imuData, timestampNanos, sensorSide, streams, parsedPacket.header.sequenceNumber)
        }
        
        // Process temperature
        parsedPacket.temperature?.let { temp ->
            val sample = TemperatureSample(temp, timestampNanos, sensorSide)
            streams._temp.tryEmit(sample)
            unifiedStreams._temp.tryEmit(sample)
            mirrorUnifiedStreams?._temp?.tryEmit(sample)
        }
        
        // Process device time
        parsedPacket.deviceTime?.let { deviceTime ->
            val sample = DeviceTimeSample(deviceTime, timestampNanos, sensorSide)
            streams._time.tryEmit(sample)
            unifiedStreams._time.tryEmit(sample)
            mirrorUnifiedStreams?._time?.tryEmit(sample)
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
        streams: SensorDataStreams,
        tick: Int
    ) {
        packetCount++
        
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
            // For Sensoria sensors, use raw ADC values directly (NO calibration library)
            // The calibration library is specifically for TouchLab sensors, not Sensoria
            // Raw ADC is 10-bit (0-1023)
            // Convert raw ADC to kPa: Using simple linear conversion
            // Scale: raw ADC / 10 = kPa (e.g., raw ADC 100 = 10 kPa = 10000 Pa)
            // This is a placeholder - adjust based on actual Sensoria sensor specifications
            for (taxelIndex in taxelIndices) {
                val rawInt = rawValue.toInt()
                // Convert raw ADC (0-1023) to Pascals using mapper
                val pascalValue = PressureCalibrationMapper.mapRawToPascals(rawValue)
                
                // Emit sample with raw ADC value converted to Pascals (no calibration library)
                val sample = PressureSample(
                    taxelIndex, 
                    rawValue, 
                    pascalValue, 
                    timestampNanos, 
                    sensorSide,
                    tick,
                    sensorType
                )
                streams._pressure.tryEmit(sample)
                unifiedStreams._pressure.tryEmit(sample)
                mirrorUnifiedStreams?._pressure?.tryEmit(sample)
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
        streams: SensorDataStreams,
        tick: Int
    ) {
        // Convert IMU raw integers to scaled floats
        val (accel, gyro, _) = when (sensorType) {
            SensorType.SENSORIA_D20 -> (parser as D20ProtocolParser).convertImuToFloats(imuData)
            SensorType.SENSORIA_E20 -> (parser as E20ProtocolParser).convertImuToFloats(imuData)
            else -> Triple(null, null, null)
        }
        
        // Emit accelerometer sample
        accel?.let { (x, y, z) ->
            val sample = AccelSample(x, y, z, timestampNanos, sensorSide, tick, sensorType)
            streams._accel.tryEmit(sample)
            unifiedStreams._accel.tryEmit(sample)
            mirrorUnifiedStreams?._accel?.tryEmit(sample)
        }
        
        // Emit gyroscope sample
        gyro?.let { (x, y, z) ->
            val sample = GyroSample(x, y, z, timestampNanos, sensorSide, tick, sensorType)
            streams._gyro.tryEmit(sample)
            unifiedStreams._gyro.tryEmit(sample)
            mirrorUnifiedStreams?._gyro?.tryEmit(sample)
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

