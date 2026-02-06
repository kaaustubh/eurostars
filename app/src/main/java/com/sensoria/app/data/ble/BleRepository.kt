package com.sensoria.app.data.ble
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sensoria.app.EurostarsApp
import com.sensoria.app.data.PairingRepository
import com.sensoria.app.data.ble.sensoria.SensoriaAnalysis
import com.sensoria.app.data.ble.sensoria.SensoriaDataHandler
import com.sensoria.app.data.ble.sensoria.SensoriaDataHandlerFactory
import com.sensoria.app.data.ble.sensoria.SensoriaSdkAdapter
import com.sensoria.app.data.ble.sensoria.SensoriaVerifier // Added
import com.sensoria.app.data.ble.sensoria.SensoriaProtocolDetector
import com.sensoria.app.data.ble.sensoria.K20ImuParser
import com.sensoria.app.util.AppLog
import com.sensoria.app.viewmodel.PairingTarget
import com.sensoria.app.data.ble.SensorDataStreams
import com.sensoria.app.data.ble.SensorGattManager
import com.sensoria.app.data.ble.SensorDataHandler
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.data.ble.BleUuids
import com.sensoria.app.data.ble.PressureSample
import com.sensoria.app.data.ble.AccelSample
import com.sensoria.app.data.ble.GyroSample
import com.sensoria.app.data.ble.TemperatureSample
import com.sensoria.app.data.ble.DeviceTimeSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class BleDeviceItem(
    val name: String?,
    val address: String,
    val rssi: Int
)

data class GattDeviceInfo(
    val serialNumber: String? = null,
    val firmwareRevision: String? = null,
    val batteryLevel: Int? = null
)

class BleRepository(private val context: Context) {

    private val btManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val btAdapter by lazy { btManager?.adapter }
    private val scanner: BluetoothLeScanner? get() = btAdapter?.bluetoothLeScanner

    fun hasScanPermission(): Boolean {
        val p = Manifest.permission.BLUETOOTH_SCAN
        return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    fun hasConnectPermission(): Boolean {
        val p = Manifest.permission.BLUETOOTH_CONNECT
        return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothEnabled(): Boolean {
        return btAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun scanForSensors(): Flow<BleDeviceItem> = callbackFlow {
        if (btAdapter?.isEnabled != true) {
            close(IllegalStateException("Bluetooth is off"))
            return@callbackFlow
        }
        // TODO: UUID filtering commented out to scan for all BLE devices
        // val filters = listOf(
        //     ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.ADVERTISED_SERVICE)).build()
        // )
        val filters = emptyList<ScanFilter>() // Scan all devices for now
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    BleDeviceItem(
                        name = result.device.name ?: result.scanRecord?.deviceName,
                        address = result.device.address,
                        rssi = result.rssi
                    )
                )
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed: $errorCode"))
            }
        }

        scanner?.startScan(filters, settings, cb)

        awaitClose { scanner?.stopScan(cb) }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        address: String,
        onConnected: (BluetoothGatt) -> Unit,
        onDisconnected: (Throwable?) -> Unit,
        onDeviceInfo: ((GattDeviceInfo) -> Unit)? = null,
        onRssiRead: ((Int) -> Unit)? = null,
        streams: SensorDataStreams? = null,
        sensorSide: com.sensoria.app.viewmodel.PairingTarget? = null,
        dataHandler: SensorDataHandler? = null,
        sensoriaDataHandler: Any? = null, // Can be SensoriaDataHandler or SensoriaSdkAdapter
        onSensorTypeDetected: ((SensorType) -> Unit)? = null
    ) {
        val device = btAdapter?.getRemoteDevice(address)
            ?: run { onDisconnected(IllegalArgumentException("Device not found")); return }

        // Use autoConnect=true for persistent connections to maintain stability
        // This helps sensors exit pairing mode by maintaining a stable connection
        device.connectGatt(context, true, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onDisconnected(IllegalStateException("GATT error $status"))
                    gatt.close(); return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Read RSSI when connected
                    try {
                        gatt.readRemoteRssi()
                    } catch (e: Exception) {
                        android.util.Log.w("BleRepository", "Failed to read RSSI: ${e.message}")
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Notify SensorConnectionManager if this connection was registered
                    // This handles disconnections for connections established during pairing
                    if (sensorSide != null && dataHandler != null) {
                        val connectionManager = (context.applicationContext as? com.sensoria.app.EurostarsApp)?.sensorConnectionManager
                        connectionManager?.let { manager ->
                            // Check if there's a registered handler for this GATT
                            val handler = manager.getDisconnectionHandler(gatt)
                            handler?.invoke(device.address, sensorSide);
                        }
                    }
                    onDisconnected(null)
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onDisconnected(IllegalStateException("Service discovery failed: $status"))
                    gatt.close()
                    return
                }
                
                // LOG DISCOVERED SERVICES AND CHARACTERISTICS
                AppLog.i(context, "BleRepository", "Discovered services for ${gatt.device.address}:")
                gatt.services.forEach { service ->
                    AppLog.i(context, "BleRepository", "Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        val props = char.properties
                        val propsStr = StringBuilder()
                        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) propsStr.append("READ ")
                        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) propsStr.append("WRITE ")
                        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) propsStr.append("NOTIFY ")
                        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) propsStr.append("INDICATE ")
                        AppLog.i(context, "BleRepository", "  Char: ${char.uuid} Properties: $propsStr")
                    }
                }
                
                // Assume K20 protocol (SENSORIA_STREAM_V1) when Sensoria service is detected
                val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
                val isK20Sensor = streamingService != null
                
                if (isK20Sensor) {
                    // Always assume K20 protocol (SENSORIA_STREAM_V1)
                    val k20Type = SensorType.SENSORIA_STREAM_V1
                    
                    if (sensorSide != null) {
                        persistDetectedSensorType(sensorSide, address, k20Type)
                    }
                    
                    onSensorTypeDetected?.invoke(k20Type)
                    
                    if (sensorSide != null && streams != null) {
                        val connectionManager = (context.applicationContext as? EurostarsApp)?.sensorConnectionManager
                        val existingHandler = connectionManager?.getSensoriaDataHandler(sensorSide)
                        val existingHandlerType = connectionManager?.getSensoriaHandlerSensorType(sensorSide)
                        if (existingHandler == null || existingHandlerType != k20Type) {
                            // Create handler with K20 protocol
                            connectionManager?.cleanupSensoriaHandler(sensorSide)
                            connectionManager?.createSensoriaHandler(sensorSide, k20Type, streams, address)
                        }
                    }
                    
                    // Phase 1: C-client style subscription for K20 sensors
                    // Skip subscription if using SDK (SDK handles BLE internally)
                    if (SensoriaDataHandlerFactory.isSdkEnabled()) {
                        // SDK handles BLE connection and subscription internally
                        AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Using SDK - skipping manual BLE subscription")
                    } else {
                        // Step 1: Find service
                        if (streamingService == null) {
                            AppLog.e(context, AppLog.TAG_SENSORIA_SUB, "Service not found: ${BleUuids.SENSORIA_STREAMING_SERVICE}")
                        } else {
                            AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Found service: ${BleUuids.SENSORIA_STREAMING_SERVICE}")
                            
                            // Phase 3: Subscribe to all NOTIFY characteristics when SENSORIA_CAPTURE_ALL_NOTIFY is enabled
                            if (SensoriaAnalysis.SENSORIA_CAPTURE_ALL_NOTIFY) {
                                val allNotifyChars = streamingService.characteristics.filter { char ->
                                    (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                                }
                                AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Found ${allNotifyChars.size} NOTIFY characteristics (capture_all mode)")
                                
                                allNotifyChars.forEach { char ->
                                    val descriptor = char.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
                                    if (descriptor != null) {
                                        AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Subscribing to char: ${char.uuid}")
                                        gatt.setCharacteristicNotification(char, true)
                                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        gatt.writeDescriptor(descriptor)
                                    }
                                }
                            }
                            
                            // Step 2: Find characteristic (always subscribe to main streaming char)
                            val streamingChar = streamingService.getCharacteristic(BleUuids.SENSORIA_STREAMING_CHAR)
                            if (streamingChar == null) {
                                AppLog.e(context, AppLog.TAG_SENSORIA_SUB, "Characteristic not found: ${BleUuids.SENSORIA_STREAMING_CHAR}")
                            } else {
                                AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Found characteristic: ${BleUuids.SENSORIA_STREAMING_CHAR}")
                                
                                // C-client check: Verify NOTIFY property is supported
                                val props = streamingChar.properties
                                if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
                                    AppLog.e(context, AppLog.TAG_SENSORIA_SUB, "Characteristic does not support NOTIFY (properties: $props)")
                                } else {
                                    AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Characteristic supports NOTIFY (properties: $props)")
                                    
                                    // Step 3: Find CCCD
                                    val descriptor = streamingChar.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
                                    if (descriptor == null) {
                                        AppLog.e(context, AppLog.TAG_SENSORIA_SUB, "CCCD not found for ${BleUuids.SENSORIA_STREAMING_CHAR}")
                                    } else {
                                        AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "Found CCCD: ${BleUuids.CLIENT_CHARACTERISTIC_CONFIG}")
                                        
                                        // Step 4: Enable local notifications (Android equivalent of bt_gatt_subscribe setup)
                                        val notifyResult = gatt.setCharacteristicNotification(streamingChar, true)
                                        AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "setCharacteristicNotification result: $notifyResult")
                                        
                                        // Step 5: Set descriptor value (C-client uses BT_GATT_CCC_NOTIFY = 0x01)
                                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE // 0x01
                                        
                                        // Step 6: Write descriptor (Android equivalent of bt_gatt_subscribe CCCD write)
                                        val writeResult = gatt.writeDescriptor(descriptor)
                                        AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "writeDescriptor started: $writeResult")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Handle current UUID-based sensors
                    val pressureService = gatt.getService(BleUuids.PRESSURE_SERVICE)
                    if (pressureService == null) {
                        android.util.Log.w("BleRepository", "Pressure service (0x183B) NOT FOUND!")
                    }
                    
                    // Enable notifications for measurement characteristics
                    val gattManager = SensorGattManager(gatt)
                    gattManagerRef = gattManager // Store reference for descriptor write callbacks
                    gattManager.enableKnownNotifications()
                }
                
                val info = GattDeviceInfo(
                    serialNumber = readStringCharacteristic(gatt, BleUuids.DEVICE_INFO_SERVICE, BleUuids.SERIAL_NUMBER_CHAR),
                    firmwareRevision = readStringCharacteristic(gatt, BleUuids.DEVICE_INFO_SERVICE, BleUuids.FIRMWARE_REVISION_CHAR),
                    batteryLevel = readBatteryLevel(gatt)
                )
                onDeviceInfo?.invoke(info)
                
                onConnected(gatt)
            }

            private var gattManagerRef: SensorGattManager? = null
            
            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val success = status == BluetoothGatt.GATT_SUCCESS
                val charUuid = descriptor.characteristic.uuid
                
                // Check if this is a Sensoria Streaming Service characteristic
                val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
                val isSensoriaChar = streamingService?.characteristics?.any { it.uuid == charUuid } == true
                
                // Phase 0: Log ALL Sensoria descriptor writes with SENSORIA_SUB tag
                if (isSensoriaChar || SensoriaAnalysis.SENSORIA_CAPTURE_ALL_NOTIFY) {
                    AppLog.i(context, AppLog.TAG_SENSORIA_SUB, "onDescriptorWrite: char=$charUuid, status=$status (${if (success) "SUCCESS" else "FAILED"})")
                } else {
                if (!success) {
                    android.util.Log.w("BleRepository", "Descriptor write failed for characteristic: $charUuid, status: $status")
                    }
                }
                
                // Notify SensorGattManager about the completion (for non-Sensoria characteristics)
                if (!isSensoriaChar) {
                gattManagerRef?.onDescriptorWriteComplete(descriptor, success)
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    android.util.Log.w("BleRepository", "Characteristic read failed: ${characteristic.uuid}, status: $status")
                }
            }
            
            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // First try the callback passed to connect()
                    onRssiRead?.invoke(rssi)
                    
                    // Also try SensorConnectionManager's RSSI handler (for existing connections)
                    if (sensorSide != null && dataHandler != null) {
                        val connectionManager = (context.applicationContext as? com.sensoria.app.EurostarsApp)?.sensorConnectionManager
                        connectionManager?.let { manager ->
                            val handler = manager.getRssiHandler(gatt)
                            handler?.invoke(rssi)
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val now = System.nanoTime()
                val uuid = characteristic.uuid
                val value = characteristic.value ?: return
                val deviceId = gatt.device.address

                // Check if this is a Sensoria Streaming Service characteristic
                val streamingService = gatt.getService(BleUuids.SENSORIA_STREAMING_SERVICE)
                val isSensoriaCharacteristic = streamingService?.characteristics?.any { it.uuid == uuid } == true
                
                // Phase 2: Log ALL Sensoria service notifications (not just streaming char)
                if (isSensoriaCharacteristic) {
                    val len = value.size
                    val firstByte = if (len > 0) value[0].toInt() and 0xFF else -1
                    val hexPrefix = value.take(16).joinToString("") { "%02x".format(it) }
                    
                    // Log with SENSORIA_NOTIFY tag
                    AppLog.i(context, AppLog.TAG_SENSORIA_NOTIFY, "deviceId=$deviceId, charUuid=$uuid, len=$len, firstByte=0x${Integer.toHexString(firstByte)}, hexPrefix=$hexPrefix")
                    
                    // Phase 3: Route all Sensoria service notifications to analysis when SENSORIA_CAPTURE_ALL_NOTIFY is enabled
                    if (SensoriaAnalysis.SENSORIA_CAPTURE_ALL_NOTIFY) {
                        SensoriaAnalysis.processPacket(uuid, value, context, deviceId)
                    }
                }
                
                // Phase 2: Handle streaming char (0003) specifically for verifier
                if (uuid == BleUuids.SENSORIA_STREAMING_CHAR) {
                    val len = value.size
                    
                    // Increment packet counter
                    SensoriaVerifier.incrementPacketsFromStreamChar()
                    
                    // Phase 3: Enforce 20-byte length check
                    if (len != 20) {
                        SensoriaVerifier.incrementBadLengthCount()
                        val hexPrefix = value.take(16).joinToString("") { "%02x".format(it) }
                        AppLog.w(context, AppLog.TAG_SENSORIA_NOTIFY, "Bad length: $len (expected 20), dropping packet. Hex: $hexPrefix")
                        return // Drop packet
                    }
                    
                    // Pass to Verifier with identity (only for streaming char)
                    SensoriaVerifier.processPacket(value, deviceId, uuid.toString(), context)
                }
                
                // Phase 4: Route 0xF0 IMU packets to K20ImuParser
                val isImuChar = (uuid == BleUuids.SENSORIA_IMU_CHAR_0004 || uuid == BleUuids.SENSORIA_IMU_CHAR_0005)
                val isImuPacket = (value.isNotEmpty() && (value[0].toInt() and 0xFF) == 0xF0 && value.size == 20)
                
                if (isImuChar && isImuPacket && sensorSide != null && streams != null) {
                    // Determine sensor side from characteristic UUID
                    val imuSensorSide = when (uuid) {
                        BleUuids.SENSORIA_IMU_CHAR_0004 -> PairingTarget.LEFT_SENSOR
                        BleUuids.SENSORIA_IMU_CHAR_0005 -> PairingTarget.RIGHT_SENSOR
                        else -> sensorSide // Fallback to provided sensorSide
                    }
                    
                    // Parse IMU packet
                    val imuParser = K20ImuParser()
                    val parsedPacket = imuParser.parsePacket(value)
                    
                    if (parsedPacket != null && parsedPacket.imuData != null) {
                        val imuData = parsedPacket.imuData
                        val tick = parsedPacket.header.sequenceNumber
                        
                        // Convert to float values
                        val (accel, gyro, _) = imuParser.convertImuToFloats(imuData)
                        
                        // Get unified streams for mirroring (if available)
                        val connectionManager = (context.applicationContext as? EurostarsApp)?.sensorConnectionManager
                        val unifiedStreams = connectionManager?.getUnifiedStreams()
                        
                        // Emit accelerometer sample
                        accel?.let { (x, y, z) ->
                            val accelSample = AccelSample(x, y, z, now, imuSensorSide, tick, SensorType.SENSORIA_STREAM_V1)
                            streams._accel.tryEmit(accelSample)
                            unifiedStreams?._accel?.tryEmit(accelSample) // Mirror to unified streams
                        }
                        
                        // Emit gyroscope sample
                        gyro?.let { (x, y, z) ->
                            val gyroSample = GyroSample(x, y, z, now, imuSensorSide, tick, SensorType.SENSORIA_STREAM_V1)
                            streams._gyro.tryEmit(gyroSample)
                            unifiedStreams?._gyro?.tryEmit(gyroSample) // Mirror to unified streams
                        }
                    }
                    
                    return // Don't process as pressure stream
                }
                
                // Assume K20 protocol for all Sensoria characteristics
                if (isSensoriaCharacteristic && sensorSide != null && streams != null) {
                    val connectionManager = (context.applicationContext as? EurostarsApp)?.sensorConnectionManager
                    var handlerFromManager = connectionManager?.getSensoriaDataHandler(sensorSide)

                    // Always assume K20 protocol (SENSORIA_STREAM_V1)
                    val k20Type = SensorType.SENSORIA_STREAM_V1
                    
                    // Ensure handler exists and is K20 type
                    val handlerType = connectionManager?.getSensoriaHandlerSensorType(sensorSide)
                        if (handlerFromManager == null || handlerType != k20Type) {
                            persistDetectedSensorType(sensorSide, address, k20Type)
                            connectionManager?.cleanupSensoriaHandler(sensorSide)
                            handlerFromManager = connectionManager?.createSensoriaHandler(sensorSide, k20Type, streams, deviceId)
                            onSensorTypeDetected?.invoke(k20Type)
                        }
                    
                    // Process packet with K20 handler
                    // Only process if using custom implementation (SDK handles packets internally)
                    if (!SensoriaDataHandlerFactory.isSdkEnabled()) {
                        // Only process 0x5A packets (pressure) here - 0xF0 packets (IMU) are handled above
                        if (value.isNotEmpty() && (value[0].toInt() and 0xFF) == 0x5A && value.size == 20) {
                            if (handlerFromManager is SensoriaDataHandler) {
                                handlerFromManager.processPacket(value, now, sensorSide, streams)
                            }
                        }
                    }
                    // If using SDK, data comes via IStreamingServiceCallback, so we don't process here
                    return
                }

                // Route to SensorDataHandler if available (new architecture for UUID-based sensors)
                if (sensorSide != null && dataHandler != null && streams != null) {
                    dataHandler.processCharacteristicUpdate(uuid, value, now, sensorSide, streams)
                    return
                }

                // Fallback to old implementation for backward compatibility
                streams ?: return
                val taxelMap = BleUuids.taxelUuidToIndexMap()
                when {
                    taxelMap.containsKey(uuid) -> {
                        val idx = taxelMap[uuid] ?: return
                        val v = SensorDecoders.decodeUnsignedInt(value)
                        // Note: Old code path doesn't have sensorSide, using LEFT_SENSOR as default
                        // This should only happen during transition period
                        // Calibration is now handled in SensorDataHandler, so pass null for pascalValue
                        streams._pressure.tryEmit(PressureSample(idx, v, null, now, com.sensoria.app.viewmodel.PairingTarget.LEFT_SENSOR))
                    }
                    uuid in BleUuids.ACCEL_DATA_CHARS -> {
                        // Collect samples across 3 chars; emit per-char updates as triplet approx
                        val component = SensorDecoders.decodeFloat(value)
                        val x = if (uuid == BleUuids.ACCEL_DATA_CHARS[0]) component else Float.NaN
                        val y = if (uuid == BleUuids.ACCEL_DATA_CHARS[1]) component else Float.NaN
                        val z = if (uuid == BleUuids.ACCEL_DATA_CHARS[2]) component else Float.NaN
                        streams._accel.tryEmit(AccelSample(x, y, z, now, com.sensoria.app.viewmodel.PairingTarget.LEFT_SENSOR))
                    }
                    uuid in BleUuids.GYRO_DATA_CHARS -> {
                        val component = SensorDecoders.decodeFloat(value)
                        val x = if (uuid == BleUuids.GYRO_DATA_CHARS[0]) component else Float.NaN
                        val y = if (uuid == BleUuids.GYRO_DATA_CHARS[1]) component else Float.NaN
                        val z = if (uuid == BleUuids.GYRO_DATA_CHARS[2]) component else Float.NaN
                        streams._gyro.tryEmit(GyroSample(x, y, z, now, com.sensoria.app.viewmodel.PairingTarget.LEFT_SENSOR))
                    }
                    uuid == BleUuids.TEMPERATURE_CHAR -> {
                        val t = SensorDecoders.decodeFloat(value)
                        streams._temp.tryEmit(TemperatureSample(t, now, com.sensoria.app.viewmodel.PairingTarget.LEFT_SENSOR))
                    }
                    uuid == BleUuids.TIME_CHAR -> {
                        val ms = SensorDecoders.decodeUnsignedLong(value)
                        streams._time.tryEmit(DeviceTimeSample(ms, now, com.sensoria.app.viewmodel.PairingTarget.LEFT_SENSOR))
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun readStringCharacteristic(gatt: BluetoothGatt, serviceUuid: UUID, charUuid: UUID): String? {
        val service = gatt.getService(serviceUuid) ?: return null
        val characteristic = service.getCharacteristic(charUuid) ?: return null
        val readOk = gatt.readCharacteristic(characteristic)
        if (!readOk) return null
        // Android will callback onCharacteristicRead synchronously in some stacks after readCharacteristic returns.
        // But to keep simple in this MVP approach, use the cached value if available.
        val value = characteristic.value ?: return null
        return try {
            String(value, Charsets.UTF_8).trim().ifBlank { null }
        } catch (_: Exception) { null }
    }

    @SuppressLint("MissingPermission")
    private fun readBatteryLevel(gatt: BluetoothGatt): Int? {
        val service = gatt.getService(BleUuids.BATTERY_SERVICE) ?: return null
        val characteristic = service.getCharacteristic(BleUuids.BATTERY_LEVEL_CHAR) ?: return null
        val ok = gatt.readCharacteristic(characteristic)
        if (!ok) return null
        val v = characteristic.value ?: return null
        return v.firstOrNull()?.toInt()
    }

    private fun persistDetectedSensorType(sensorSide: PairingTarget, address: String, sensorType: SensorType) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pairingRepo = PairingRepository(context)
                val currentStatus = pairingRepo.pairingStatusFlow.first()
                
                when (sensorSide) {
                    PairingTarget.LEFT_SENSOR -> {
                        val deviceIdMatches = currentStatus.leftSensor.deviceId?.equals(address, ignoreCase = true) == true
                        val needsUpdate = currentStatus.leftSensor.sensorType != sensorType
                        
                        if (currentStatus.isLeftPaired && deviceIdMatches && needsUpdate) {
                            pairingRepo.setLeftSensor(
                                currentStatus.leftSensor.deviceId ?: return@launch,
                                currentStatus.leftSensor.deviceName,
                                currentStatus.leftSensor.serialNumber,
                                currentStatus.leftSensor.firmwareVersion,
                                currentStatus.leftSensor.batteryLevel,
                                currentStatus.leftSensor.rssi,
                                sensorType
                            )
                        }
                    }
                    PairingTarget.RIGHT_SENSOR -> {
                        val deviceIdMatches = currentStatus.rightSensor.deviceId?.equals(address, ignoreCase = true) == true
                        val needsUpdate = currentStatus.rightSensor.sensorType != sensorType
                        
                        if (currentStatus.isRightPaired && deviceIdMatches && needsUpdate) {
                            pairingRepo.setRightSensor(
                                currentStatus.rightSensor.deviceId ?: return@launch,
                                currentStatus.rightSensor.deviceName,
                                currentStatus.rightSensor.serialNumber,
                                currentStatus.rightSensor.firmwareVersion,
                                currentStatus.rightSensor.batteryLevel,
                                currentStatus.rightSensor.rssi,
                                sensorType
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.e(context, "BleRepository", "Error persisting sensor type: ${e.message} - ${e.stackTraceToString()}")
            }
        }
    }
}
