package com.sensars.eurostars.ui.screens.patient.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.data.ble.BleDeviceItem
import com.sensars.eurostars.viewmodel.PairingState
import com.sensars.eurostars.viewmodel.PairingTarget
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Classification of Bluetooth issues that can block pairing. */
@Immutable
internal enum class BluetoothIssue {
    BLUETOOTH_OFF,
    PERMISSION_MISSING,
    UNKNOWN
}

internal fun bluetoothIssueFromMessage(message: String): BluetoothIssue {
    val lower = message.lowercase()
    return when {
        "permission" in lower -> BluetoothIssue.PERMISSION_MISSING
        "bluetooth" in lower && ("off" in lower || "disabled" in lower) -> BluetoothIssue.BLUETOOTH_OFF
        else -> BluetoothIssue.UNKNOWN
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairingScanScreen(
    target: PairingTarget,
    devices: List<BleDeviceItem>,
    pairingState: PairingState,
    pairingStatus: com.sensars.eurostars.data.PairingStatus? = null,
    onDeviceSelected: (BleDeviceItem) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pairingTitle = if (target == PairingTarget.LEFT_SENSOR) {
        "Pair Left Sensor"
    } else {
        "Pair Right Sensor"
    }

    val isScanning = pairingState == PairingState.SCANNING
    val isConnecting = pairingState == PairingState.CONNECTING

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(pairingTitle, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isScanning) "Searching nearby..." else "Select a device to pair",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (isScanning) {
                                "No sensors discovered yet.\nStay close to your sensors while we scan."
                            } else {
                                "We didn't find any sensors.\nTry scanning again."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        // Check if this device is already paired to the opposite foot
                        val isAlreadyPaired = pairingStatus?.let { status ->
                            when (target) {
                                PairingTarget.LEFT_SENSOR -> {
                                    // If pairing left, check if device is already paired to right
                                    status.rightSensor.deviceId == device.address
                                }
                                PairingTarget.RIGHT_SENSOR -> {
                                    // If pairing right, check if device is already paired to left
                                    status.leftSensor.deviceId == device.address
                                }
                            }
                        } ?: false
                        
                        DeviceRow(
                            device = device,
                            onConnect = { onDeviceSelected(device) },
                            connecting = isConnecting,
                            isAlreadyPaired = isAlreadyPaired
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRetry, enabled = !isScanning && !isConnecting) {
                    Text("Scan again")
                }

                if (isConnecting) {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DeviceRow(
    device: BleDeviceItem,
    onConnect: () -> Unit,
    connecting: Boolean,
    isAlreadyPaired: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = device.name ?: "Unknown device",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAlreadyPaired) {
                    Text(
                        text = "Already paired to other foot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Button(
                onClick = onConnect,
                enabled = !connecting && !isAlreadyPaired,
            ) {
                Text(if (isAlreadyPaired) "Paired" else "Connect")
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BluetoothStatusScreen(
    issue: BluetoothIssue,
    message: String,
    onOpenSettings: (() -> Unit)?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, description, icon) = when (issue) {
        BluetoothIssue.BLUETOOTH_OFF -> Triple(
            "Bluetooth is off",
            "Enable Bluetooth to connect your sensors.",
            Icons.Default.BluetoothDisabled
        )
        BluetoothIssue.PERMISSION_MISSING -> Triple(
            "Bluetooth permission required",
            "We need Bluetooth access to discover your sensors. Allow it in settings.",
            Icons.Default.BluetoothDisabled
        )
        BluetoothIssue.UNKNOWN -> Triple(
            "Unable to start pairing",
            message.ifBlank { "Something went wrong while trying to pair your sensors." },
            Icons.Default.BluetoothDisabled
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Pair sensors", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (issue == BluetoothIssue.UNKNOWN && message.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            onOpenSettings?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                    Text("Open settings")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Try again")
            }
        }
    }
}

@Composable
internal fun PairingFullScreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            content()
        }
    }
}

