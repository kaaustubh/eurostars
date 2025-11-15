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
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.ADVERTISED_SERVICE)).build()
        )
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
        streams: SensorDataStreams? = null
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
                    val info = GattDeviceInfo(
                        serialNumber = readStringCharacteristic(gatt, BleUuids.DEVICE_INFO_SERVICE, BleUuids.SERIAL_NUMBER_CHAR),
                        firmwareRevision = readStringCharacteristic(gatt, BleUuids.DEVICE_INFO_SERVICE, BleUuids.FIRMWARE_REVISION_CHAR),
                        batteryLevel = readBatteryLevel(gatt)
                    )
                    onDeviceInfo?.invoke(info)
                    // Enable notifications for measurement characteristics
                    SensorGattManager(gatt).enableKnownNotifications()
                    onConnected(gatt)
                } else {
                    onDisconnected(IllegalStateException("Service discovery failed: $status"))
                    gatt.close()
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val now = System.nanoTime()
                val uuid = characteristic.uuid
                val value = characteristic.value ?: return
                streams ?: return
                when {
                    pressureUuidToIndex.containsKey(uuid) -> {
                        val idx = pressureUuidToIndex[uuid] ?: return
                        val v = SensorDecoders.decodeUnsignedInt(value)
                        streams._pressure.tryEmit(PressureSample(idx, v, now))
                    }
                    uuid in BleUuids.ACCEL_DATA_CHARS -> {
                        // Collect samples across 3 chars; emit per-char updates as triplet approx
                        val component = SensorDecoders.decodeFloat(value)
                        val x = if (uuid == BleUuids.ACCEL_DATA_CHARS[0]) component else Float.NaN
                        val y = if (uuid == BleUuids.ACCEL_DATA_CHARS[1]) component else Float.NaN
                        val z = if (uuid == BleUuids.ACCEL_DATA_CHARS[2]) component else Float.NaN
                        streams._accel.tryEmit(AccelSample(x, y, z, now))
                    }
                    uuid in BleUuids.GYRO_DATA_CHARS -> {
                        val component = SensorDecoders.decodeFloat(value)
                        val x = if (uuid == BleUuids.GYRO_DATA_CHARS[0]) component else Float.NaN
                        val y = if (uuid == BleUuids.GYRO_DATA_CHARS[1]) component else Float.NaN
                        val z = if (uuid == BleUuids.GYRO_DATA_CHARS[2]) component else Float.NaN
                        streams._gyro.tryEmit(GyroSample(x, y, z, now))
                    }
                    uuid == BleUuids.TEMPERATURE_CHAR -> {
                        val t = SensorDecoders.decodeFloat(value)
                        streams._temp.tryEmit(TemperatureSample(t, now))
                    }
                    uuid == BleUuids.TIME_CHAR -> {
                        val ms = SensorDecoders.decodeUnsignedLong(value)
                        streams._time.tryEmit(DeviceTimeSample(ms, now))
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
}
