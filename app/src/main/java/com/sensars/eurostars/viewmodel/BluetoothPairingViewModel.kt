package com.sensars.eurostars.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sensars.eurostars.data.PairingRepository
import com.sensars.eurostars.data.PairingStatus
import com.sensars.eurostars.data.ble.BleDeviceItem
import com.sensars.eurostars.data.ble.BleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    }
    
    fun scanPrerequisiteMessage(): String? {
        if (!bleRepository.hasScanPermission()) {
            return "Bluetooth permission required"
        }
        if (!bleRepository.isBluetoothEnabled()) {
            return "Bluetooth is off"
        }
        return null
    }
    
    /**
     * Start scanning for BLE devices
     */
    fun startScanning(target: PairingTarget) {
        if (!bleRepository.hasScanPermission()) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = "Bluetooth permission required"
            )
            return
        }
        
        if (!bleRepository.isBluetoothEnabled()) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = "Bluetooth is off"
            )
            return
        }
        
        scanJob?.cancel()
        
        _uiState.value = _uiState.value.copy(
            pairingState = PairingState.SCANNING,
            currentTarget = target,
            scannedDevices = emptyList(),
            errorMessage = null
        )
        
        scanJob = viewModelScope.launch {
            bleRepository
                .scanForSensors()
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        pairingState = PairingState.ERROR,
                        errorMessage = throwable.message ?: "Scan failed"
                    )
                }
                .collect { device ->
                    val alreadyPresent = _uiState.value.scannedDevices.any { it.address == device.address }
                    if (!alreadyPresent) {
                        _uiState.value = _uiState.value.copy(
                            scannedDevices = _uiState.value.scannedDevices + device
                        )
                    }
                }
        }
    }
    
    /**
     * Stop scanning for BLE devices
     */
    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        if (_uiState.value.pairingState == PairingState.SCANNING) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.IDLE
            )
        }
    }
    
    /**
     * Connect to a selected device and pair it
     */
    fun connectToDevice(device: BleDeviceItem, target: PairingTarget) {
        if (!bleRepository.hasConnectPermission()) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = "Bluetooth connect permission is required"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            pairingState = PairingState.CONNECTING,
            errorMessage = null
        )
        
        bleRepository.connect(
            address = device.address,
            onConnected = { gatt ->
                viewModelScope.launch {
                    try {
                        val deviceInfo = PairingDeviceInfo(
                            deviceId = device.address,
                            deviceName = device.name ?: "Unknown Device",
                            serialNumber = extractSerialNumber(device),
                            firmwareVersion = extractFirmwareVersion(device),
                            batteryLevel = extractBatteryLevel(device)
                        )
                        
                        when (target) {
                            PairingTarget.LEFT_SENSOR -> {
                                pairingRepo.setLeftSensor(
                                    deviceId = deviceInfo.deviceId,
                                    deviceName = deviceInfo.deviceName,
                                    serialNumber = deviceInfo.serialNumber,
                                    firmwareVersion = deviceInfo.firmwareVersion,
                                    batteryLevel = deviceInfo.batteryLevel
                                )
                            }
                            PairingTarget.RIGHT_SENSOR -> {
                                pairingRepo.setRightSensor(
                                    deviceId = deviceInfo.deviceId,
                                    deviceName = deviceInfo.deviceName,
                                    serialNumber = deviceInfo.serialNumber,
                                    firmwareVersion = deviceInfo.firmwareVersion,
                                    batteryLevel = deviceInfo.batteryLevel
                                )
                            }
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            pairingState = PairingState.PAIRED,
                            currentTarget = null
                        )
                        
                        stopScanning()
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            pairingState = PairingState.ERROR,
                            errorMessage = "Connection failed: ${e.message}"
                        )
                    } finally {
                        gatt.close()
                    }
                }
            },
            onDisconnected = { throwable ->
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        pairingState = PairingState.ERROR,
                        errorMessage = throwable?.message ?: "Device disconnected"
                    )
                }
            }
        )
    }
    
    /**
     * Clear pairing for a specific sensor
     */
    fun clearPairing(target: PairingTarget) {
        viewModelScope.launch {
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
            pairingRepo.clearAllPairing()
        }
    }
    
    /**
     * Reset error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            pairingState = if (_uiState.value.pairingState == PairingState.ERROR) {
                PairingState.IDLE
            } else {
                _uiState.value.pairingState
            }
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
    
    // Helper functions to extract device information from scan record
    // These will need to be implemented based on your sensor's BLE characteristics
    private fun extractSerialNumber(device: BleDeviceItem): String? {
        // TODO: Implement based on sensor's BLE service/characteristic
        return null
    }
    
    private fun extractFirmwareVersion(device: BleDeviceItem): String? {
        // TODO: Implement based on sensor's BLE service/characteristic
        return null
    }
    
    private fun extractBatteryLevel(device: BleDeviceItem): Int? {
        // TODO: Implement based on sensor's BLE service/characteristic
        return null
    }
}

/**
 * Data class for storing device information during pairing
 */
private data class PairingDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null
)

