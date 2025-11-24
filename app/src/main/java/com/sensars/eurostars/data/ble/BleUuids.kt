// data/ble/BleUuids.kt
package com.sensars.eurostars.data.ble

import java.util.Locale
import java.util.UUID

object BleUuids {
    private fun from16(u16: Int): UUID {
        val hex = u16.toString(16).uppercase(Locale.US).padStart(4, '0')
        return UUID.fromString("0000$hex-0000-1000-8000-00805f9b34fb")
    }
    private fun uuid(raw: String): UUID = UUID.fromString(raw)

    // Services
    val PRESSURE_SERVICE: UUID = from16(0x183B)
    val TIMING_SERVICE: UUID = from16(0x183F)
    val ACCEL_SERVICE: UUID = uuid("e716871c-b6aa-4f54-819c-13287436122b")
    val GYRO_SERVICE: UUID = uuid("3c4eb6a5-25f7-45ac-9c79-47729dc3cf34")
    val TEMPERATURE_SERVICE: UUID = uuid("1d39e833-69fd-4867-a8a5-ced07b43b85f")

    // Standard services
    val DEVICE_INFO_SERVICE: UUID = from16(0x180A)
    val BATTERY_SERVICE: UUID = from16(0x180F)

    // Pressure characteristics explicit list
    val PRESSURE_DATA_CHARS: List<UUID> = listOf(
        uuid("F000A001-0000-1000-8000-00805F9B34FB"),
        uuid("F000A002-0000-1000-8000-00805F9B34FB"),
        uuid("F000A003-0000-1000-8000-00805F9B34FB"),
        uuid("F000A004-0000-1000-8000-00805F9B34FB"),
        uuid("F000A005-0000-1000-8000-00805F9B34FB"),
        uuid("F000A006-0000-1000-8000-00805F9B34FB"),
        uuid("F000A007-0000-1000-8000-00805F9B34FB"),
        uuid("F000A008-0000-1000-8000-00805F9B34FB"),
        uuid("F000A009-0000-1000-8000-00805F9B34FB"),
        uuid("F000A00A-0000-1000-8000-00805F9B34FB"),
        uuid("F000A00B-0000-1000-8000-00805F9B34FB"),
        uuid("F000A00C-0000-1000-8000-00805F9B34FB"),
        uuid("F000A00D-0000-1000-8000-00805F9B34FB"),
        uuid("F000A00E-0000-1000-8000-00805F9B34FB"),
        uuid("F000A00F-0000-1000-8000-00805F9B34FB"),
        uuid("F000A010-0000-1000-8000-00805F9B34FB"),
        uuid("F000A011-0000-1000-8000-00805F9B34FB"),
        uuid("F000A012-0000-1000-8000-00805F9B34FB")
    )

    // Timing characteristic 0x2B90
    val TIME_CHAR: UUID = from16(0x2B90)

    // Accelerometer characteristics explicit
    // Note: The sensor actually has f100b000, f100b001, f100b002
    // Mapping: f100b000 = Z, f100b001 = X, f100b002 = Y
    val ACCEL_DATA_CHARS: List<UUID> = listOf(
        uuid("F100B001-0000-1000-8000-00805F9B34FB"), // X
        uuid("F100B002-0000-1000-8000-00805F9B34FB"), // Y
        uuid("F100B000-0000-1000-8000-00805F9B34FB")  // Z (was f100b003, but sensor uses f100b000)
    )

    // Gyroscope characteristics explicit
    val GYRO_DATA_CHARS: List<UUID> = listOf(
        uuid("F100B004-0000-1000-8000-00805F9B34FB"),
        uuid("F100B005-0000-1000-8000-00805F9B34FB"),
        uuid("F100B006-0000-1000-8000-00805F9B34FB")
    )

    // Temperature F2000001
    val TEMPERATURE_CHAR: UUID = uuid("F2000001-0000-1000-8000-00805F9B34FB")

    // Device Info / Battery characteristics
    val SERIAL_NUMBER_CHAR: UUID = from16(0x2A25)
    val FIRMWARE_REVISION_CHAR: UUID = from16(0x2A26)
    val BATTERY_LEVEL_CHAR: UUID = from16(0x2A19)

    // CCCD
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = uuid("00002902-0000-1000-8000-00805f9b34fb")

    // Advertised service (pressure service)
    val ADVERTISED_SERVICE: UUID = PRESSURE_SERVICE

    /**
     * Maps pressure characteristic UUIDs to taxel indices.
     * Returns a map where each UUID corresponds to its index in the PRESSURE_DATA_CHARS list.
     */
    fun taxelUuidToIndexMap(): Map<UUID, Int> {
        return PRESSURE_DATA_CHARS.withIndex().associate { it.value to it.index }
    }

    /**
     * Maps IMU characteristic UUIDs to component identifiers.
     * Returns a map where each UUID maps to a component string (accelX, accelY, accelZ, gyroX, gyroY, gyroZ, temp).
     */
    fun imuUuidToComponentMap(): Map<UUID, String> {
        val map = mutableMapOf<UUID, String>()
        // Accelerometer components
        ACCEL_DATA_CHARS.forEachIndexed { index, uuid ->
            map[uuid] = when (index) {
                0 -> "accelX"
                1 -> "accelY"
                2 -> "accelZ"
                else -> "accelUnknown"
            }
        }
        // Gyroscope components
        GYRO_DATA_CHARS.forEachIndexed { index, uuid ->
            map[uuid] = when (index) {
                0 -> "gyroX"
                1 -> "gyroY"
                2 -> "gyroZ"
                else -> "gyroUnknown"
            }
        }
        // Temperature
        map[TEMPERATURE_CHAR] = "temp"
        return map
    }
}
