package com.sensars.eurostars.ui.screens.patient.pairing

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.data.ble.AccelSample
import com.sensars.eurostars.data.ble.BleRepository
import com.sensars.eurostars.data.ble.DeviceTimeSample
import com.sensars.eurostars.data.ble.GyroSample
import com.sensars.eurostars.data.ble.PressureSample
import com.sensars.eurostars.data.ble.SensorConnectionManager
import com.sensars.eurostars.data.ble.TemperatureSample
import com.sensars.eurostars.viewmodel.PairingState
import com.sensars.eurostars.viewmodel.PairingTarget
import com.sensars.eurostars.viewmodel.bluetoothPairingViewModel
import kotlinx.coroutines.flow.first

@Composable
fun PairingTabScreen() {
    val vm = bluetoothPairingViewModel()
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current
    val connectionManager = (context.applicationContext as EurostarsApp).sensorConnectionManager

    var activeTarget by remember { mutableStateOf<PairingTarget?>(null) }
    var showScanScreen by remember { mutableStateOf(false) }
    var currentIssue by remember { mutableStateOf<BluetoothIssue?>(null) }
    var lastErrorMessage by remember { mutableStateOf("") }
    var showClearPairingDialog by remember { mutableStateOf(false) }

    val pairingStatus = uiState.pairingStatus
    
    // Monitor data reception
    var leftDataCount by remember { mutableStateOf(0L) }
    var rightDataCount by remember { mutableStateOf(0L) }
    var leftLastDataTime by remember { mutableStateOf<Long?>(null) }
    var rightLastDataTime by remember { mutableStateOf<Long?>(null) }
    
    val leftConnectionState by connectionManager.leftSensorConnection.collectAsState()
    val rightConnectionState by connectionManager.rightSensorConnection.collectAsState()
    
    // Use effective connection state that accounts for Bluetooth being disabled
    val leftEffectiveState = connectionManager.getEffectiveConnectionState(PairingTarget.LEFT_SENSOR)
    val rightEffectiveState = connectionManager.getEffectiveConnectionState(PairingTarget.RIGHT_SENSOR)
    
    val leftConnected = leftEffectiveState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    val rightConnected = rightEffectiveState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    
    // Monitor data reception for left sensor
    LaunchedEffect(leftConnected) {
        if (leftConnected) {
            val dataHandler = connectionManager.getDataHandler()
            dataHandler.getPressureFlow(PairingTarget.LEFT_SENSOR).collect { sample ->
                leftDataCount++
                leftLastDataTime = System.currentTimeMillis()
            }
        } else {
            leftDataCount = 0
            leftLastDataTime = null
        }
    }
    
    // Monitor data reception for right sensor
    LaunchedEffect(rightConnected) {
        if (rightConnected) {
            val dataHandler = connectionManager.getDataHandler()
            dataHandler.getPressureFlow(PairingTarget.RIGHT_SENSOR).collect { sample ->
                rightDataCount++
                rightLastDataTime = System.currentTimeMillis()
            }
        } else {
            rightDataCount = 0
            rightLastDataTime = null
        }
    }

    val leftSensorState = SensorPairingState(
        isPaired = pairingStatus.isLeftPaired,
        deviceName = pairingStatus.leftSensor.deviceName,
        serialNumber = pairingStatus.leftSensor.serialNumber,
        firmwareVersion = pairingStatus.leftSensor.firmwareVersion,
        batteryLevel = pairingStatus.leftSensor.batteryLevel
    )

    val rightSensorState = SensorPairingState(
        isPaired = pairingStatus.isRightPaired,
        deviceName = pairingStatus.rightSensor.deviceName,
        serialNumber = pairingStatus.rightSensor.serialNumber,
        firmwareVersion = pairingStatus.rightSensor.firmwareVersion,
        batteryLevel = pairingStatus.rightSensor.batteryLevel
    )

    // State for expand/collapse
    var leftExpanded by remember { mutableStateOf(!pairingStatus.isLeftPaired) }
    var rightExpanded by remember { mutableStateOf(!pairingStatus.isRightPaired) }
    
    // Periodically check Bluetooth state to ensure UI reflects actual connection status
    // This is a backup in case the BroadcastReceiver doesn't fire immediately
    val bleRepository = remember { BleRepository(context) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // Check every second
            // If Bluetooth is off but connection state says connected, update it
            if (!bleRepository.isBluetoothEnabled()) {
                val leftState = leftConnectionState?.state
                val rightState = rightConnectionState?.state
                if (leftState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED ||
                    rightState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED) {
                    connectionManager.handleBluetoothDisabled()
                }
            }
        }
    }
    
    // Update expanded state when pairing status changes
    LaunchedEffect(pairingStatus.isLeftPaired) {
        if (!pairingStatus.isLeftPaired) {
            leftExpanded = true // Expand when not paired
        }
    }
    
    LaunchedEffect(pairingStatus.isRightPaired) {
        if (!pairingStatus.isRightPaired) {
            rightExpanded = true // Expand when not paired
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            vm.stopScanning()
            currentIssue = bluetoothIssueFromMessage(message)
            lastErrorMessage = message
            showScanScreen = false
        } else {
            currentIssue = null
        }
    }

    LaunchedEffect(uiState.pairingState) {
        if (uiState.pairingState == PairingState.PAIRED) {
            showScanScreen = false
            activeTarget = null
        }
    }

    fun startPairing(target: PairingTarget, resetExisting: Boolean) {
        activeTarget = target
        vm.clearError()

        val prerequisiteMessage = vm.scanPrerequisiteMessage()
        if (prerequisiteMessage != null) {
            currentIssue = bluetoothIssueFromMessage(prerequisiteMessage)
            lastErrorMessage = prerequisiteMessage
            showScanScreen = false
            return
        }

        currentIssue = null
        showScanScreen = true
        if (resetExisting) {
            vm.clearPairing(target)
        }
        vm.startScanning(target)
    }

    fun closePairingFlow() {
        vm.stopScanning()
        vm.clearError()
        showScanScreen = false
        activeTarget = null
        currentIssue = null
        lastErrorMessage = ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Pairing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            SensorPairingSection(
                title = "Left Foot Sensor",
                state = leftSensorState,
                isExpanded = leftExpanded,
                onExpandedChange = { leftExpanded = it },
                onPair = { startPairing(PairingTarget.LEFT_SENSOR, resetExisting = false) },
                onPairAnother = { startPairing(PairingTarget.LEFT_SENSOR, resetExisting = true) },
                showPairAnother = false, // Don't show "Pair another one" for left sensor
                isConnected = leftConnected,
                dataSampleCount = leftDataCount,
                lastDataTime = leftLastDataTime,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            SensorPairingSection(
                title = "Right Foot Sensor",
                state = rightSensorState,
                isExpanded = rightExpanded,
                onExpandedChange = { rightExpanded = it },
                onPair = { startPairing(PairingTarget.RIGHT_SENSOR, resetExisting = false) },
                onPairAnother = { startPairing(PairingTarget.RIGHT_SENSOR, resetExisting = true) },
                isConnected = rightConnected,
                dataSampleCount = rightDataCount,
                lastDataTime = rightLastDataTime,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sensor Data Display Section
            if (leftConnected || rightConnected) {
                SensorDataDisplaySection(
                    connectionManager = connectionManager,
                    leftConnected = leftConnected,
                    rightConnected = rightConnected,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            OutlinedButton(
                onClick = { showClearPairingDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp)
            ) {
                Text(
                    text = "Clear All Pairings",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            var showReleaseNotes by remember { mutableStateOf(false) }
            com.sensars.eurostars.ui.screens.shared.AboutSection(
                showReleaseNotes = showReleaseNotes,
                onShowReleaseNotesChange = { showReleaseNotes = it },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showScanScreen && activeTarget != null) {
            PairingFullScreenDialog(onDismiss = { closePairingFlow() }) {
                PairingScanScreen(
                    target = activeTarget!!,
                    devices = uiState.scannedDevices,
                    pairingState = uiState.pairingState,
                    onDeviceSelected = { device ->
                        val target = activeTarget ?: return@PairingScanScreen
                        vm.connectToDevice(device, target)
                    },
                    onBack = {
                        closePairingFlow()
                    },
                    onRetry = {
                        val target = activeTarget
                        if (target != null) {
                            vm.clearError()
                            vm.startScanning(target)
                        }
                    }
                )
            }
        }

        currentIssue?.let { issue ->
            val openSettingsAction: (() -> Unit)? = when (issue) {
                BluetoothIssue.BLUETOOTH_OFF -> {
                    {
                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
                BluetoothIssue.PERMISSION_MISSING -> {
                    {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
                BluetoothIssue.UNKNOWN -> null
            }

            PairingFullScreenDialog(onDismiss = { closePairingFlow() }) {
                BluetoothStatusScreen(
                    issue = issue,
                    message = lastErrorMessage,
                    onOpenSettings = openSettingsAction,
                    onRetry = {
                        vm.clearError()
                        val target = activeTarget
                        if (target != null) {
                            val prerequisiteMessage = vm.scanPrerequisiteMessage()
                            return@BluetoothStatusScreen if (prerequisiteMessage != null) {
                                currentIssue = bluetoothIssueFromMessage(prerequisiteMessage)
                                lastErrorMessage = prerequisiteMessage
                                showScanScreen = false
                            } else {
                                currentIssue = null
                                showScanScreen = true
                                vm.startScanning(target)
                            }
                        }
                    },
                    onBack = {
                        closePairingFlow()
                    }
                )
            }
        }
        
        // Clear Pairing Confirmation Dialog
        if (showClearPairingDialog) {
            AlertDialog(
                onDismissRequest = { showClearPairingDialog = false },
                title = {
                    Text(
                        text = "Clear All Pairings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to clear all sensor pairings? This will disconnect all paired sensors.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.clearAllPairing()
                            showClearPairingDialog = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearPairingDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
