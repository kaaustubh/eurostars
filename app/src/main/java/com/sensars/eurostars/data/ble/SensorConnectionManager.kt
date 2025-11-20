package com.sensars.eurostars.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        when (sensorSide) {
            PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = connectedState
            PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = connectedState
        }
        
        // Register streams with data handler (streams are already set up in BleRepository)
        dataHandler.registerSensor(sensorSide, streams)
        
        // The GATT connection is already established, notifications are enabled,
        // and the callback is routing to SensorDataHandler via BleRepository
        // Note: Disconnections will be detected by the GATT callback in BleRepository
        // and should update the connection state. We also monitor the connection state here.
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

        // Connect via BleRepository
        bleRepository.connect(
            address = address,
            onConnected = { gatt ->
                val connectedState = SensorConnection(address, sensorSide, SensorConnectionState.CONNECTED, gatt)
                when (sensorSide) {
                    PairingTarget.LEFT_SENSOR -> _leftSensorConnection.value = connectedState
                    PairingTarget.RIGHT_SENSOR -> _rightSensorConnection.value = connectedState
                }
                // Register streams with data handler
                dataHandler.registerSensor(sensorSide, streams)
            },
            onDisconnected = { throwable ->
                handleDisconnection(address, sensorSide)
                // Attempt reconnection after a delay
                // TODO: Implement reconnection logic with exponential backoff
            },
            onDeviceInfo = null,
            streams = streams,
            sensorSide = sensorSide,
            dataHandler = dataHandler
        )
    }

    /**
     * Disconnect a specific sensor.
     */
    @SuppressLint("MissingPermission")
    fun disconnectSensor(sensorSide: PairingTarget) {
        // First, unregister the sensor to stop data processing
        dataHandler.unregisterSensor(sensorSide)
        
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
            val gattManager = com.sensars.eurostars.data.ble.SensorGattManager(gatt)
            return gattManager.writeCommand(serviceUuid, characteristicUuid, command)
        }
        
        // Otherwise, just ensure connection is stable - sensors may exit pairing mode automatically
        // when they detect a stable connection with active notifications
        return true
    }
}

