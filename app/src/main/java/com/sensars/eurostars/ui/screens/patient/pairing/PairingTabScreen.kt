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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.sensars.eurostars.viewmodel.PairingState
import com.sensars.eurostars.viewmodel.PairingTarget
import com.sensars.eurostars.viewmodel.bluetoothPairingViewModel

@Composable
fun PairingTabScreen() {
    val vm = bluetoothPairingViewModel()
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current

    var activeTarget by remember { mutableStateOf<PairingTarget?>(null) }
    var showScanScreen by remember { mutableStateOf(false) }
    var currentIssue by remember { mutableStateOf<BluetoothIssue?>(null) }
    var lastErrorMessage by remember { mutableStateOf("") }

    val pairingStatus = uiState.pairingStatus

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
                isExpanded = remember { mutableStateOf(true) }.value,
                onExpandedChange = { },
                onPair = { startPairing(PairingTarget.LEFT_SENSOR, resetExisting = false) },
                onPairAnother = { startPairing(PairingTarget.LEFT_SENSOR, resetExisting = true) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            SensorPairingSection(
                title = "Right Foot Sensor",
                state = rightSensorState,
                isExpanded = remember { mutableStateOf(true) }.value,
                onExpandedChange = { },
                onPair = { startPairing(PairingTarget.RIGHT_SENSOR, resetExisting = false) },
                onPairAnother = { startPairing(PairingTarget.RIGHT_SENSOR, resetExisting = true) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = { vm.clearAllPairing() },
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
    }
}
