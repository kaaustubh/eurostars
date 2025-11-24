package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.data.PairingRepository
import com.sensars.eurostars.data.PairingStatus
import com.sensars.eurostars.data.ble.BleDeviceItem
import com.sensars.eurostars.data.ble.BleRepository
import com.sensars.eurostars.data.ble.GattDeviceInfo
import com.sensars.eurostars.data.ble.SensorConnectionManager
import com.sensars.eurostars.data.ble.SensorDataStreams
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PairingTarget {
    LEFT_SENSOR,
    RIGHT_SENSOR
}

enum class PairingState {
    IDLE,
    SCANNING,
    CONNECTING,
    PAIRED,
    ERROR
}

data class BluetoothPairingUiState(
    val pairingState: PairingState = PairingState.IDLE,
    val currentTarget: PairingTarget? = null,
    val scannedDevices: List<BleDeviceItem> = emptyList(),
    val errorMessage: String? = null,
    val pairingStatus: PairingStatus = PairingStatus()
)

/**
 * ViewModel for handling Bluetooth LE sensor pairing operations.
 * Manages scanning, connection, and pairing state.
 */
class BluetoothPairingViewModel(application: Application) : AndroidViewModel(application) {
    private val pairingRepo = PairingRepository(application)
    private val bleRepository = BleRepository(application)
    private val connectionManager: SensorConnectionManager = (application as EurostarsApp).sensorConnectionManager
    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(BluetoothPairingUiState())
    val uiState: StateFlow<BluetoothPairingUiState> = _uiState.asStateFlow()

    init {
        // Observe pairing status
        viewModelScope.launch {
            pairingRepo.pairingStatusFlow.collect { status ->
                _uiState.value = _uiState.value.copy(pairingStatus = status)
            }
        }
        
        // Attempt auto-reconnect when ViewModel is created (app restart)
        viewModelScope.launch {
            // Wait a bit for the app to fully initialize
            kotlinx.coroutines.delay(500)
            autoReconnectSensors()
        }
    }
    
    /**
     * Automatically reconnect to sensors if pairing information exists and sensors are not already connected.
     * This is called on app startup to restore connections after app restart.
     * Can also be called manually to attempt reconnection.
     */
    fun autoReconnectSensors() {
        viewModelScope.launch {
            // Check prerequisites
            if (!bleRepository.hasConnectPermission()) {
                return@launch
            }
            if (!bleRepository.isBluetoothEnabled()) {
                return@launch
            }
            
            // Get current pairing status
            val pairingStatus = pairingRepo.pairingStatusFlow.first()
            
            // Check left sensor
            if (pairingStatus.isLeftPaired) {
                val leftDeviceId = pairingStatus.leftSensor.deviceId
                if (leftDeviceId != null) {
                    val leftConnected = connectionManager.isSensorConnected(PairingTarget.LEFT_SENSOR)
                    if (!leftConnected) {
                        // Attempt to reconnect
                        try {
                            connectionManager.connectSensor(leftDeviceId, PairingTarget.LEFT_SENSOR)
                        } catch (e: Exception) {
                            // Connection failed - sensor might be off or out of range
                            // This is expected and not an error
                        }
                    }
                }
            }
            
            // Check right sensor
            if (pairingStatus.isRightPaired) {
                val rightDeviceId = pairingStatus.rightSensor.deviceId
                if (rightDeviceId != null) {
                    val rightConnected = connectionManager.isSensorConnected(PairingTarget.RIGHT_SENSOR)
                    if (!rightConnected) {
                        // Attempt to reconnect
                        try {
                            connectionManager.connectSensor(rightDeviceId, PairingTarget.RIGHT_SENSOR)
                        } catch (e: Exception) {
                            // Connection failed - sensor might be off or out of range
                            // This is expected and not an error
                        }
                    }
                }
            }
        }
    }

    fun scanPrerequisiteMessage(): String? {
        if (!bleRepository.hasScanPermission()) return "Bluetooth permission required"
        if (!bleRepository.isBluetoothEnabled()) return "Bluetooth is off"
        return null
    }

    /**
     * Start scanning for BLE devices
     */
    fun startScanning(target: PairingTarget) {
        if (!bleRepository.hasScanPermission()) {
            _uiState.value = _uiState.value.copy(pairingState = PairingState.ERROR, errorMessage = "Bluetooth permission required")
            return
        }
        if (!bleRepository.isBluetoothEnabled()) {
            _uiState.value = _uiState.value.copy(pairingState = PairingState.ERROR, errorMessage = "Bluetooth is off")
            return
        }
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(pairingState = PairingState.SCANNING, currentTarget = target, scannedDevices = emptyList(), errorMessage = null)

        scanJob = viewModelScope.launch {
            bleRepository.scanForSensors().catch { throwable ->
                _uiState.value = _uiState.value.copy(pairingState = PairingState.ERROR, errorMessage = throwable.message ?: "Scan failed")
            }.collect { device ->
                val alreadyPresent = _uiState.value.scannedDevices.any { it.address == device.address }
                if (!alreadyPresent) {
                    _uiState.value = _uiState.value.copy(scannedDevices = _uiState.value.scannedDevices + device)
                }
            }
        }
    }

    /**
     * Stop scanning for BLE devices
     */
    fun stopScanning() {
        scanJob?.cancel(); scanJob = null
        if (_uiState.value.pairingState == PairingState.SCANNING) {
            _uiState.value = _uiState.value.copy(pairingState = PairingState.IDLE)
        }
    }

    /**
     * Connect to a selected device and pair it.
     * After pairing, transfers connection to SensorConnectionManager for persistent connection.
     */
    fun connectToDevice(device: BleDeviceItem, target: PairingTarget) {
        if (!bleRepository.hasConnectPermission()) {
            _uiState.value = _uiState.value.copy(pairingState = PairingState.ERROR, errorMessage = "Bluetooth connect permission is required")
            return
        }
        _uiState.value = _uiState.value.copy(pairingState = PairingState.CONNECTING, errorMessage = null)

        var gattInfo: GattDeviceInfo? = null
        // Create streams for this sensor (will be used by SensorConnectionManager)
        val streams = SensorDataStreams()
        val dataHandler = connectionManager.getDataHandler()
        
        // Connect with data handler set up from the start
        bleRepository.connect(
            address = device.address,
            onConnected = { gatt ->
                viewModelScope.launch {
                    try {
                        val deviceInfo = PairingDeviceInfo(
                            deviceId = device.address,
                            deviceName = device.name ?: "Unknown Device",
                            serialNumber = gattInfo?.serialNumber ?: extractSerialNumber(device),
                            firmwareVersion = gattInfo?.firmwareRevision ?: extractFirmwareVersion(device),
                            batteryLevel = gattInfo?.batteryLevel ?: extractBatteryLevel(device),
                            rssi = device.rssi
                        )
                        // Save pairing information
                        when (target) {
                            PairingTarget.LEFT_SENSOR -> pairingRepo.setLeftSensor(deviceInfo.deviceId, deviceInfo.deviceName, deviceInfo.serialNumber, deviceInfo.firmwareVersion, deviceInfo.batteryLevel, deviceInfo.rssi)
                            PairingTarget.RIGHT_SENSOR -> pairingRepo.setRightSensor(deviceInfo.deviceId, deviceInfo.deviceName, deviceInfo.serialNumber, deviceInfo.firmwareVersion, deviceInfo.batteryLevel, deviceInfo.rssi)
                        }
                        
                        // Transfer GATT connection to SensorConnectionManager for persistent connection
                        // This reuses the existing connection instead of closing and reconnecting
                        connectionManager.acceptExistingConnection(gatt, device.address, target, streams)
                        
                        _uiState.value = _uiState.value.copy(pairingState = PairingState.PAIRED, currentTarget = null)
                        stopScanning()
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(pairingState = PairingState.ERROR, errorMessage = "Connection failed: ${e.message}")
                    }
                }
            },
            onDisconnected = { throwable ->
                viewModelScope.launch {
                    // Update SensorConnectionManager when disconnection is detected
                    // This handles the case where sensors are turned off
                    connectionManager.handleDisconnection(device.address, target)
                    
                    // Only report error if we haven't already transferred to connection manager
                    if (_uiState.value.pairingState == PairingState.CONNECTING) {
                        _uiState.value = _uiState.value.copy(pairingState = PairingState.ERROR, errorMessage = throwable?.message ?: "Device disconnected")
                    }
                }
            },
            onDeviceInfo = { info -> gattInfo = info },
            streams = streams,
            sensorSide = target,
            dataHandler = dataHandler
        )
    }

    /**
     * Clear pairing for a specific sensor
     */
    fun clearPairing(target: PairingTarget) {
        viewModelScope.launch {
            // Disconnect from SensorConnectionManager
            connectionManager.disconnectSensor(target)
            // Clear pairing data
            when (target) {
                PairingTarget.LEFT_SENSOR -> pairingRepo.clearLeftSensor()
                PairingTarget.RIGHT_SENSOR -> pairingRepo.clearRightSensor()
            }
        }
    }

    /**
     * Clear all pairings
     */
    fun clearAllPairing() {
        viewModelScope.launch {
            connectionManager.disconnectAll()
            pairingRepo.clearAllPairing()
        }
    }

    /**
     * Get the SensorConnectionManager instance for accessing data streams.
     */
    fun getConnectionManager(): SensorConnectionManager = connectionManager

    /**
     * Reset error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            pairingState = if (_uiState.value.pairingState == PairingState.ERROR) PairingState.IDLE else _uiState.value.pairingState
        )
    }

    override fun onCleared() { super.onCleared(); stopScanning() }

    // Fallback helpers (unused once gattInfo is populated, but keep for spec-specific future parsing)
    private fun extractSerialNumber(device: BleDeviceItem): String? = null
    private fun extractFirmwareVersion(device: BleDeviceItem): String? = null
    private fun extractBatteryLevel(device: BleDeviceItem): Int? = null
}

private data class PairingDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null,
    val rssi: Int? = null
)
