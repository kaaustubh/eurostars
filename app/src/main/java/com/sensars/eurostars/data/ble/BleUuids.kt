// data/ble/BleUuids.kt
package com.sensars.eurostars.data.ble

import java.util.UUID

object BleUuids {
    private const val PLACEHOLDER = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"

    private fun uuidOrNull(raw: String): UUID? =
        if (raw.equals(PLACEHOLDER, ignoreCase = true)) {
            null
        } else {
            runCatching { UUID.fromString(raw) }.getOrNull()
        }

    // Paste real values from the device specification once available.
    val PRESSURE_SERVICE: UUID? = uuidOrNull("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
    val ACCEL_SERVICE: UUID? = uuidOrNull("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
    val GYRO_SERVICE: UUID? = uuidOrNull("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")

    // If there's a single advertised service, set the UUID above. Otherwise leave null.
    val ADVERTISED_SERVICE: UUID? = uuidOrNull("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") ?: PRESSURE_SERVICE
}
