package com.sensars.eurostars.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sensars.eurostars.data.PairingRepository
import com.sensars.eurostars.data.PairedSensor
import com.sensars.eurostars.data.PairingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class ScanResultDevice(
    val device: BluetoothDevice,
    val rssi: Int,
    val scanRecord: ByteArray?
)

data class BluetoothPairingUiState(
    val pairingState: PairingState = PairingState.IDLE,
    val currentTarget: PairingTarget? = null,
    val scannedDevices: List<ScanResultDevice> = emptyList(),
    val errorMessage: String? = null,
    val pairingStatus: PairingStatus = PairingStatus()
)

/**
 * ViewModel for handling Bluetooth LE sensor pairing operations.
 * Manages scanning, connection, and pairing state.
 */
class BluetoothPairingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val pairingRepo = PairingRepository(application)
    private val bluetoothManager: BluetoothManager? =
        application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private val _uiState = MutableStateFlow(BluetoothPairingUiState())
    val uiState: StateFlow<BluetoothPairingUiState> = _uiState.asStateFlow()
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val existingDevice = _uiState.value.scannedDevices.find { 
                it.device.address == device.address 
            }
            
            if (existingDevice == null) {
                // Filter for foot sensor devices (adjust filter criteria as needed)
                // For now, we'll accept any BLE device, but you can filter by:
                // - Device name pattern
                // - Service UUID
                // - Manufacturer data
                val newDevice = ScanResultDevice(
                    device = device,
                    rssi = result.rssi,
                    scanRecord = result.scanRecord?.bytes
                )
                
                _uiState.value = _uiState.value.copy(
                    scannedDevices = _uiState.value.scannedDevices + newDevice
                )
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already in progress"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning not supported"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    else -> "Scan failed with error code: $errorCode"
                }
            )
            stopScanning()
        }
    }
    
    init {
        // Observe pairing status
        viewModelScope.launch {
            pairingRepo.pairingStatusFlow.collect { status ->
                _uiState.value = _uiState.value.copy(pairingStatus = status)
            }
        }
    }
    
    /**
     * Start scanning for BLE devices
     */
    fun startScanning(target: PairingTarget) {
        if (bluetoothLeScanner == null) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = "Bluetooth is not available"
            )
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = "Bluetooth is disabled"
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(
            pairingState = PairingState.SCANNING,
            currentTarget = target,
            scannedDevices = emptyList(),
            errorMessage = null
        )
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        val scanFilters = emptyList<ScanFilter>() // No filters for now - accept all BLE devices
        
        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        } catch (e: SecurityException) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.ERROR,
                errorMessage = "Bluetooth permission denied"
            )
        }
    }
    
    /**
     * Stop scanning for BLE devices
     */
    fun stopScanning() {
        bluetoothLeScanner?.stopScan(scanCallback)
        if (_uiState.value.pairingState == PairingState.SCANNING) {
            _uiState.value = _uiState.value.copy(
                pairingState = PairingState.IDLE
            )
        }
    }
    
    /**
     * Connect to a selected device and pair it
     */
    fun connectToDevice(device: ScanResultDevice, target: PairingTarget) {
        _uiState.value = _uiState.value.copy(
            pairingState = PairingState.CONNECTING,
            errorMessage = null
        )
        
        viewModelScope.launch {
            try {
                // TODO: Implement actual BLE connection logic here
                // For now, we'll simulate pairing by storing device info
                // In real implementation, you would:
                // 1. Connect to the device using BluetoothGatt
                // 2. Discover services and characteristics
                // 3. Read device information (serial, firmware, battery)
                // 4. Store pairing information
                
                val deviceInfo = PairingDeviceInfo(
                    deviceId = device.device.address,
                    deviceName = device.device.name ?: "Unknown Device",
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
            }
        }
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
    private fun extractSerialNumber(device: ScanResultDevice): String? {
        // TODO: Implement based on sensor's BLE service/characteristic
        return null
    }
    
    private fun extractFirmwareVersion(device: ScanResultDevice): String? {
        // TODO: Implement based on sensor's BLE service/characteristic
        return null
    }
    
    private fun extractBatteryLevel(device: ScanResultDevice): Int? {
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

