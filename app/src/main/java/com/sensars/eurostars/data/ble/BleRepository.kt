package com.sensars.eurostars.data.ble
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow

data class BleDeviceItem(
    val name: String?,
    val address: String,
    val rssi: Int
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
        // Filter by advertised service if available; otherwise filter by name prefix.
        val advertisedService = BleUuids.ADVERTISED_SERVICE
        val filters = if (advertisedService != null) {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(advertisedService))
                    .build()
            )
        } else {
            emptyList()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

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
        onDisconnected: (Throwable?) -> Unit
    ) {
        val device = btAdapter?.getRemoteDevice(address)
            ?: run { onDisconnected(IllegalArgumentException("Device not found")); return }

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onDisconnected(IllegalStateException("GATT error $status"))
                    gatt.close(); return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onDisconnected(null); gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onConnected(gatt) // you can start enabling notifications here
                } else {
                    onDisconnected(IllegalStateException("Service discovery failed: $status"))
                    gatt.close()
                }
            }
        })
    }
}
