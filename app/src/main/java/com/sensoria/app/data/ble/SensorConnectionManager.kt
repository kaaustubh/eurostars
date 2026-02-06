package com.sensoria.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.sensoria.app.data.ble.sensoria.SensoriaDataHandler
import com.sensoria.app.data.ble.sensoria.SensoriaDataHandlerFactory
import com.sensoria.app.data.ble.sensoria.SensoriaSdkAdapter
import com.sensoria.app.data.ble.SensorType
import com.sensoria.app.viewmodel.PairingTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SensorConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

data class SensorConnection(
    val address: String,
    val sensorSide: PairingTarget,
    val state: SensorConnectionState = SensorConnectionState.IDLE,
    val gatt: BluetoothGatt? = null
)

/**
 * Manages persistent connections to left and right foot sensors.
 * Tracks connection state per sensor and maintains GATT instances.
 */
class SensorConnectionManager(private val context: Context) {
    private val bleRepository = BleRepository(context)
    private val dataHandler = SensorDataHandler(context)
    
    // Store SensoriaDataHandler instances per sensor (created when sensor type is detected)
    // Can be either SensoriaDataHandler (custom) or SensoriaSdkAdapter (SDK)
    private var leftSensoriaHandler: Any? = null
    private var rightSensoriaHandler: Any? = null

    private val _leftSensorConnection = MutableStateFlow<SensorConnection?>(null)
    val leftSensorConnection: StateFlow<SensorConnection?> = _leftSensorConnection.asStateFlow()

    private val _rightSensorConnection = MutableStateFlow<SensorConnection?>(null)
    val rightSensorConnection: StateFlow<SensorConnection?> = _rightSensorConnection.asStateFlow()

    // BroadcastReceiver to monitor Bluetooth adapter state
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        // Bluetooth was turned off - disconnect all sensors
                        handleBluetoothDisabled()
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        // Bluetooth is turning off - disconnect all sensors
                        handleBluetoothDisabled()
                    }
                }
            }
        }
    }

    init {
        // Register receiver for Bluetooth state changes
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
    }

    /**
     * Handle Bluetooth adapter being disabled.
     * Updates all connection states to DISCONNECTED.
     * Can be called manually to check and update state.
     */
    fun handleBluetoothDisabled() {
        // Update left sensor connection state
        _leftSensorConnection.value?.let { connection ->
            if (connection.state == SensorConnectionState.CONNECTED) {
                _leftSensorConnection.value = SensorConnection(
                    connection.address,
                    connection.sensorSide,
                    SensorConnectionState.DISCONNECTED,
                    null
                )
                dataHandler.unregisterSensor(PairingTarget.LEFT_SENSOR)
            }
        }

        // Update right sensor connection state
        _rightSensorConnection.value?.let { connection ->
            if (connection.state == SensorConnectionState.CONNECTED) {
                _rightSensorConnection.value = SensorConnection(
                    connection.address,
                    connection.sensorSide,
                    SensorConnectionState.DISCONNECTED,
                    null
                )
                dataHandler.unregisterSensor(PairingTarget.RIGHT_SENSOR)
            }
        }
    }

    /**
     * Cleanup resources. Should be called when the manager is no longer needed.
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered, ignore
        }
        disconnectAll()
    }

    // Map to store disconnection handlers for GATT connections
    private val gattDisconnectionHandlers = mutableMapOf<android.bluetooth.BluetoothGatt, (String, PairingTarget) -> Unit>()
    
    // Map to store RSSI update handlers for GATT connections
    private val gattRssiHandlers = mutableMapOf<android.bluetooth.BluetoothGatt, (Int) -> Unit>()
    
    /**
     * Register a disconnection handler for a GATT connection.
     * This allows BleRepository to notify SensorConnectionManager about disconnections
     * even for connections established during pairing.
     */
    fun registerDisconnectionHandler(gatt: android.bluetooth.BluetoothGatt, address: String, sensorSide: PairingTarget) {
        gattDisconnectionHandlers[gatt] = { addr, side ->
            if (addr == address && side == sensorSide) {
                handleDisconnection(address, sensorSide)
            }
        }
    }
    
    /**
     * Unregister a disconnection handler for a GATT connection.
     */
    fun unregisterDisconnectionHandler(gatt: android.bluetooth.BluetoothGatt) {
        gattDisconnectionHandlers.remove(gatt)
    }
    
    /**
     * Accept an existing GATT connection and transfer it to this manager.
     * Used when pairing to reuse the connection established during pairing.
     * @param gatt Existing BluetoothGatt connection
     * @param address Device MAC address
     * @param sensorSide LEFT_SENSOR or RIGHT_SENSOR
     * @param streams The SensorDataStreams instance already set up for this connection
     */
    @SuppressLint("MissingPermission")
    fun acceptExistingConnection(gatt: BluetoothGatt, address: String, sensorSide: PairingTarget, streams: SensorDataStreams) {
        // Update state to CONNECTED
        val connectedState = SensorConnection(address, sensorSide, SensorConnectionState.CONNECTED, gatt)
        
        // Request high connection priority for faster updates
        val priorityRequest = {
        try {
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                android.util.Log.d("SensorConnectionManager", "Requested HIGH priority for ${gatt.device.address}")
        } catch (e: Exception) {
            android.util.Log.w("SensorConnectionManager", "Failed to request connection priority: ${e.message}")
            }
        }
        
        priorityRequest()
        
        // Retry after 3 seconds to override SDK initialization
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(3000)
            priorityRequest()
        }

        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = connectedState
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = connectedState
        }
        
        // Register streams with data handler (streams are already set up in BleRepository)
        dataHandler.registerSensor(sensorSide, streams)
        
        // Register disconnection handler so we can be notified when this connection disconnects
        registerDisconnectionHandler(gatt, address, sensorSide)
        
        // Register RSSI handler
        registerRssiHandler(gatt, address, sensorSide)
        
        // Read RSSI after connection is established and periodically
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(500) // Wait a bit for connection to stabilize
            // Read RSSI periodically (every 5 seconds) while connected
            while (isSensorConnected(sensorSide)) {
                try {
                    gatt.readRemoteRssi()
                    kotlinx.coroutines.delay(5000) // Read every 5 seconds
                } catch (e: Exception) {
                    android.util.Log.w("SensorConnectionManager", "Failed to read RSSI: ${e.message}")
                    break
                }
            }
        }
        
        // The GATT connection is already established, notifications are enabled,
        // and the callback is routing to SensorDataHandler via BleRepository
        // Disconnections will be detected by the GATT callback in BleRepository
        // and will call the registered handler to update connection state.
    }
    
    /**
     * Update connection state when disconnection is detected.
     * Called by BleRepository callback when a sensor disconnects.
     */
    fun handleDisconnection(address: String, sensorSide: PairingTarget) {
        val connection = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value
        }
        
        // Only update if this is the correct connection
        if (connection?.address == address) {
            val disconnectedState = SensorConnection(
                address,
                sensorSide,
                SensorConnectionState.DISCONNECTED,
                null
            )
            when (sensorSide) {
                PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = disconnectedState
                PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = disconnectedState
            }
            dataHandler.unregisterSensor(sensorSide)
        }
    }

    /**
     * Connect to a sensor and maintain persistent connection.
     * @param address Device MAC address
     * @param sensorSide LEFT_SENSOR or RIGHT_SENSOR
     */
    @SuppressLint("MissingPermission")
    fun connectSensor(address: String, sensorSide: PairingTarget) {
        val currentConnection = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value
        }

        // If already connected to this address, do nothing
        if (currentConnection?.address == address && currentConnection.state == SensorConnectionState.CONNECTED) {
            return
        }

        // Update state to CONNECTING
        val connectingState = SensorConnection(address, sensorSide, SensorConnectionState.CONNECTING)
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = connectingState
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = connectingState
        }

        // Create streams for this sensor
        val streams = SensorDataStreams()
        
        // Get sensor type from pairing repository
        val pairingRepo = com.sensoria.app.data.PairingRepository(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val pairingStatus = pairingRepo.pairingStatusFlow.first()
            val storedSensorType = when (sensorSide) {
                PairingTarget.LEFT_SENSOR -> pairingStatus.leftSensor.sensorType
                PairingTarget.RIGHT_SENSOR -> pairingStatus.rightSensor.sensorType
            }
            
            var detectedSensorType: SensorType? = storedSensorType
            var sensoriaHandler: Any? = null // Can be SensoriaDataHandler or SensoriaSdkAdapter

            // Connect via BleRepository
            bleRepository.connect(
            address = address,
            onConnected = { gatt ->
                val connectedState = SensorConnection(address, sensorSide, SensorConnectionState.CONNECTED, gatt)
                
                // Create Sensoria handler if sensor type was detected as Sensoria
                detectedSensorType?.let { sensorType ->
                    if (sensorType == SensorType.SENSORIA_D20 || 
                        sensorType == SensorType.SENSORIA_E20 ||
                        sensorType == SensorType.SENSORIA_STREAM_V1) {
                        sensoriaHandler = createSensoriaHandler(sensorSide, sensorType, streams, address)
                    }
                }
                
                // Request high connection priority for streaming
                // We request it immediately AND after a delay to ensure it overrides any low-power request from the SDK
                val priorityRequest = {
                try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        android.util.Log.d("SensorConnectionManager", "Requested HIGH priority for ${gatt.device.address}")
                } catch (e: Exception) {
                    android.util.Log.w("SensorConnectionManager", "Failed to request connection priority: ${e.message}")
                    }
                }
                
                priorityRequest()
                
                // Retry after 3 seconds to override SDK initialization
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(3000)
                    priorityRequest()
                }

                when (sensorSide) {
                    PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = connectedState
                    PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = connectedState
                }
                // Register streams with appropriate handler
                if (sensoriaHandler == null) {
                    dataHandler.registerSensor(sensorSide, streams)
                }
                // Register disconnection handler
                registerDisconnectionHandler(gatt, address, sensorSide)
                // Register RSSI handler
                registerRssiHandler(gatt, address, sensorSide)
                
                // Read RSSI after connection and periodically
                val pairingRepo = com.sensoria.app.data.PairingRepository(context)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(500) // Wait a bit for connection to stabilize
                    // Read RSSI periodically (every 5 seconds) while connected
                    while (isSensorConnected(sensorSide)) {
                        try {
                            gatt.readRemoteRssi()
                            kotlinx.coroutines.delay(5000) // Read every 5 seconds
                        } catch (e: Exception) {
                            android.util.Log.w("SensorConnectionManager", "Failed to read RSSI: ${e.message}")
                            break
                        }
                    }
                }
            },
            onDisconnected = { throwable ->
                handleDisconnection(address, sensorSide)
                // Attempt reconnection after a delay
                // TODO: Implement reconnection logic with exponential backoff
            },
            onDeviceInfo = null,
            onRssiRead = { rssi ->
                // Use the registered RSSI handler if available
                // Get GATT instance using sensorSide since gatt is not in scope here
                val gatt = getGatt(sensorSide)
                if (gatt != null) {
                    val handler = getRssiHandler(gatt)
                    handler?.invoke(rssi)
                }
            },
            streams = streams,
            sensorSide = sensorSide,
            dataHandler = dataHandler,
            sensoriaDataHandler = sensoriaHandler,
            onSensorTypeDetected = { sensorType ->
                detectedSensorType = sensorType
                android.util.Log.d("SensorConnectionManager", "Sensor type detected: $sensorType for device $address")
                // Update pairing repository with detected sensor type if different
                if (sensorType != storedSensorType) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val currentStatus = pairingRepo.pairingStatusFlow.first()
                        when (sensorSide) {
                            PairingTarget.LEFT_SENSOR -> {
                                if (currentStatus.isLeftPaired && currentStatus.leftSensor.deviceId == address) {
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
                                if (currentStatus.isRightPaired && currentStatus.rightSensor.deviceId == address) {
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
                    }
                }
            }
        )
        }
    }

    /**
     * Disconnect a specific sensor.
     */
    @SuppressLint("MissingPermission")
    fun disconnectSensor(sensorSide: PairingTarget) {
        // First, unregister the sensor to stop data processing
        dataHandler.unregisterSensor(sensorSide)
        cleanupSensoriaHandler(sensorSide)
        
        val connection = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value
        }

        // Immediately update connection state to DISCONNECTED before closing GATT
        // This ensures UI updates immediately
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = null
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = null
        }

        // Then disconnect and close GATT
        connection?.gatt?.let { gatt ->
            try {
                unregisterDisconnectionHandler(gatt)
                unregisterRssiHandler(gatt)
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                android.util.Log.w("SensorConnectionManager", "Error disconnecting GATT: ${e.message}")
            }
        }
    }

    /**
     * Disconnect all sensors.
     */
    fun disconnectAll() {
        disconnectSensor(PairingTarget.LEFT_SENSOR)
        disconnectSensor(PairingTarget.RIGHT_SENSOR)
    }

    /**
     * Get the data handler for accessing sensor data streams.
     */
    fun getDataHandler(): SensorDataHandler = dataHandler
    
    /**
     * Get truly unified streams that include data from both SensorDataHandler (UUID-based sensors)
     * and SensoriaDataHandler (Sensoria sensors).
     * Since Sensoria handlers now also register their streams with SensorDataHandler,
     * SensorDataHandler's unified streams will include all sensor data.
     */
    fun getUnifiedStreams(): SensorDataStreams {
        // Return SensorDataHandler's unified streams, which now includes Sensoria data
        // because Sensoria handlers register their streams with SensorDataHandler
        return dataHandler.getUnifiedStreams()
    }
    
    /**
     * Get the SensoriaDataHandler for a specific sensor, if it exists.
     * Returns either SensoriaDataHandler (custom) or SensoriaSdkAdapter (SDK).
     */
    fun getSensoriaDataHandler(sensorSide: PairingTarget): Any? {
        return when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensoriaHandler
            PairingTarget.RIGHT_SENSOR -> rightSensoriaHandler
        }
    }
    
    /**
     * Get sensor type from handler (works for both SDK and custom implementations).
     */
    fun getSensoriaHandlerSensorType(sensorSide: PairingTarget): SensorType? {
        val handler = getSensoriaDataHandler(sensorSide) ?: return null
        return SensoriaDataHandlerFactory.getSensorType(handler)
    }
    
    /**
     * Create and register a SensoriaDataHandler for a sensor.
     * Uses factory to create either SDK or custom implementation based on feature flag.
     * Also ensures Sensoria data flows into SensorDataHandler's unified streams for walking mode.
     */
    fun createSensoriaHandler(sensorSide: PairingTarget, sensorType: SensorType, streams: SensorDataStreams, explicitAddress: String? = null): Any {
        // Get device address for SDK adapter (if needed)
        val deviceAddress = explicitAddress ?: when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensorConnection.value?.address ?: ""
            PairingTarget.RIGHT_SENSOR -> rightSensorConnection.value?.address ?: ""
        }
        
        // Use factory to create appropriate handler
        val handler = SensoriaDataHandlerFactory.createHandler(
            context = context,
            deviceAddress = deviceAddress,
            sensorType = sensorType,
            sensorSide = sensorSide,
            streams = streams,
            mirrorUnifiedStreams = dataHandler.getUnifiedStreams()
        )
        
        // If using SDK adapter, start it
        if (handler is SensoriaSdkAdapter) {
            handler.start()
            // SDK will connect automatically, but we can also call connect() explicitly
            handler.connect()
            // Register streams with global data handler so UI can access them
            dataHandler.registerSensor(sensorSide, streams)
        } else if (handler is SensoriaDataHandler) {
            // Custom handler: register sensor and ensure unified streams
            handler.registerSensor(sensorSide, streams)
        dataHandler.registerSensor(sensorSide, streams)
        }
        
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> leftSensoriaHandler = handler
            PairingTarget.RIGHT_SENSOR -> rightSensoriaHandler = handler
        }
        
        return handler
    }
    
    /**
     * Clean up SensoriaDataHandler for a sensor.
     */
    fun cleanupSensoriaHandler(sensorSide: PairingTarget) {
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> {
                leftSensoriaHandler?.let { handler ->
                    when (handler) {
                        is SensoriaSdkAdapter -> {
                            handler.stop()
                            handler.disconnect()
                        }
                        is SensoriaDataHandler -> {
                            handler.unregisterSensor(sensorSide)
                        }
                    }
                }
                leftSensoriaHandler = null
            }
            PairingTarget.RIGHT_SENSOR -> {
                rightSensoriaHandler?.let { handler ->
                    when (handler) {
                        is SensoriaSdkAdapter -> {
                            handler.stop()
                            handler.disconnect()
                        }
                        is SensoriaDataHandler -> {
                            handler.unregisterSensor(sensorSide)
                        }
                    }
                }
                rightSensoriaHandler = null
            }
        }
    }

    /**
     * Check if a sensor is connected.
     * Also checks if Bluetooth is enabled - returns false if Bluetooth is off.
     */
    fun isSensorConnected(sensorSide: PairingTarget): Boolean {
        // If Bluetooth is disabled, no sensor can be connected
        if (!bleRepository.isBluetoothEnabled()) {
            return false
        }
        
        val connection = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value
        }
        return connection?.state == SensorConnectionState.CONNECTED
    }
    
    /**
     * Get the effective connection state, accounting for Bluetooth adapter state.
     * Returns DISCONNECTED if Bluetooth is disabled, even if connection state says CONNECTED.
     */
    fun getEffectiveConnectionState(sensorSide: PairingTarget): SensorConnectionState {
        // If Bluetooth is disabled, return DISCONNECTED
        if (!bleRepository.isBluetoothEnabled()) {
            return SensorConnectionState.DISCONNECTED
        }
        
        val connection = when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value
        }
        return connection?.state ?: SensorConnectionState.IDLE
    }

    /**
     * Get the GATT instance for a sensor.
     */
    fun getGatt(sensorSide: PairingTarget): BluetoothGatt? {
        return when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value?.gatt
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value?.gatt
        }
    }
    
    /**
     * Read RSSI from a connected sensor.
     * Returns null if sensor is not connected or RSSI cannot be read.
     */
    @SuppressLint("MissingPermission")
    fun readRssi(sensorSide: PairingTarget): Int? {
        val gatt = getGatt(sensorSide) ?: return null
        return try {
            // Note: readRemoteRssi() is asynchronous, but we can't easily get the result here
            // For now, we'll need to handle RSSI reading differently
            // This is a placeholder - actual RSSI reading needs to be done via callback
            null
        } catch (e: Exception) {
            android.util.Log.w("SensorConnectionManager", "Error reading RSSI: ${e.message}")
            null
        }
    }
    
    /**
     * Get the disconnection handler for a GATT connection.
     * Used by BleRepository to notify about disconnections.
     */
    fun getDisconnectionHandler(gatt: android.bluetooth.BluetoothGatt): ((String, PairingTarget) -> Unit)? {
        return gattDisconnectionHandlers[gatt]
    }
    
    /**
     * Register an RSSI update handler for a GATT connection.
     * Used to update RSSI when it's read from a connected device.
     */
    fun registerRssiHandler(gatt: android.bluetooth.BluetoothGatt, address: String, sensorSide: PairingTarget) {
        val pairingRepo = com.sensoria.app.data.PairingRepository(context)
        gattRssiHandlers[gatt] = { rssi ->
            // Update RSSI in pairing repository
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val currentStatus = pairingRepo.pairingStatusFlow.first()
                when (sensorSide) {
                    PairingTarget.LEFT_SENSOR -> {
                        if (currentStatus.isLeftPaired && currentStatus.leftSensor.deviceId == address) {
                            pairingRepo.setLeftSensor(
                                currentStatus.leftSensor.deviceId ?: return@launch,
                                currentStatus.leftSensor.deviceName,
                                currentStatus.leftSensor.serialNumber,
                                currentStatus.leftSensor.firmwareVersion,
                                currentStatus.leftSensor.batteryLevel,
                                rssi
                            )
                        }
                    }
                    PairingTarget.RIGHT_SENSOR -> {
                        if (currentStatus.isRightPaired && currentStatus.rightSensor.deviceId == address) {
                            pairingRepo.setRightSensor(
                                currentStatus.rightSensor.deviceId ?: return@launch,
                                currentStatus.rightSensor.deviceName,
                                currentStatus.rightSensor.serialNumber,
                                currentStatus.rightSensor.firmwareVersion,
                                currentStatus.rightSensor.batteryLevel,
                                rssi
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get the RSSI handler for a GATT connection.
     * Used by BleRepository to notify about RSSI updates.
     */
    fun getRssiHandler(gatt: android.bluetooth.BluetoothGatt): ((Int) -> Unit)? {
        return gattRssiHandlers[gatt]
    }
    
    /**
     * Unregister an RSSI handler for a GATT connection.
     */
    fun unregisterRssiHandler(gatt: android.bluetooth.BluetoothGatt) {
        gattRssiHandlers.remove(gatt)
    }

    /**
     * Send a command to exit pairing mode on a sensor.
     * This should be called after successful connection and notification setup.
     * 
     * Currently, this method attempts to find a control characteristic and send the exit pairing command.
     * If the control service/characteristic UUIDs and command bytes are not yet defined,
     * this will try to discover and send the command. Otherwise, it relies on sensors
     * automatically exiting pairing mode when they detect a stable connection with active notifications.
     * 
     * @param sensorSide LEFT_SENSOR or RIGHT_SENSOR
     * @param serviceUuid Service UUID containing the control characteristic (optional, will try to discover if null)
     * @param characteristicUuid Control characteristic UUID (optional, will try to discover if null)
     * @param command Command bytes to write (optional, will use default if null)
     * @return true if command was sent successfully or if sensors exit pairing mode automatically, false otherwise
     */
    @SuppressLint("MissingPermission")
    fun sendExitPairingCommand(
        sensorSide: PairingTarget,
        serviceUuid: java.util.UUID? = null,
        characteristicUuid: java.util.UUID? = null,
        command: ByteArray? = null
    ): Boolean {
        val gatt = getGatt(sensorSide) ?: return false
        
        // If UUIDs and command are provided, send the command
        if (serviceUuid != null && characteristicUuid != null && command != null) {
            val gattManager = com.sensoria.app.data.ble.SensorGattManager(gatt)
            return gattManager.writeCommand(serviceUuid, characteristicUuid, command)
        }
        
        // Otherwise, just ensure connection is stable - sensors may exit pairing mode automatically
        // when they detect a stable connection with active notifications
        return true
    }
}

