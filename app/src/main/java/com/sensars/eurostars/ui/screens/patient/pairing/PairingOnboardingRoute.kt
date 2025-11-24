package com.sensars.eurostars.ui.screens.patient.pairing

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.data.SessionRepository
import com.sensars.eurostars.data.ble.SensorConnectionManager
import com.sensars.eurostars.viewmodel.PairingState
import com.sensars.eurostars.viewmodel.PairingTarget
import com.sensars.eurostars.viewmodel.bluetoothPairingViewModel
import kotlinx.coroutines.flow.first

@Composable
fun PairingOnboardingRoute(
    onPairingComplete: () -> Unit
) {
    val context = LocalContext.current
    val vm = bluetoothPairingViewModel()
    val connectionManager = (context.applicationContext as EurostarsApp).sensorConnectionManager
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.sessionFlow.collectAsState(initial = SessionRepository.Session())
    
    // Determine which legs need sensors based on neuropathic leg
    val neuropathicLeg = session.neuropathicLeg.lowercase()
    val isLeftLegNeeded = neuropathicLeg.isEmpty() || neuropathicLeg == "left" || neuropathicLeg == "both"
    val isRightLegNeeded = neuropathicLeg.isEmpty() || neuropathicLeg == "right" || neuropathicLeg == "both"

    val uiState by vm.uiState.collectAsState()
    val pairingStatus = uiState.pairingStatus

    var activeTarget by remember { mutableStateOf<PairingTarget?>(null) }
    var showScanScreen by remember { mutableStateOf(false) }
    var currentIssue by remember { mutableStateOf<BluetoothIssue?>(null) }
    var lastErrorMessage by remember { mutableStateOf("") }
    var showUnpairLeftDialog by remember { mutableStateOf(false) }
    var showUnpairRightDialog by remember { mutableStateOf(false) }

    // Monitor connection status
    val leftConnectionState by connectionManager.leftSensorConnection.collectAsState()
    val rightConnectionState by connectionManager.rightSensorConnection.collectAsState()
    
    // Use effective connection state that accounts for Bluetooth being disabled
    val leftEffectiveState = connectionManager.getEffectiveConnectionState(PairingTarget.LEFT_SENSOR)
    val rightEffectiveState = connectionManager.getEffectiveConnectionState(PairingTarget.RIGHT_SENSOR)
    
    val leftConnected = leftEffectiveState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    val rightConnected = rightEffectiveState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    
    // Monitor data reception
    var leftDataCount by remember { mutableStateOf(0L) }
    var rightDataCount by remember { mutableStateOf(0L) }
    var leftLastDataTime by remember { mutableStateOf<Long?>(null) }
    var rightLastDataTime by remember { mutableStateOf<Long?>(null) }
    
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

    // Check if all required sensors are paired
    val allRequiredSensorsPaired = when {
        isLeftLegNeeded && isRightLegNeeded -> pairingStatus.areBothPaired
        isLeftLegNeeded -> pairingStatus.isLeftPaired
        isRightLegNeeded -> pairingStatus.isRightPaired
        else -> true // No sensors needed
    }
    
    LaunchedEffect(allRequiredSensorsPaired) {
        if (allRequiredSensorsPaired) {
            onPairingComplete()
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
        PairingOnboardingScreen(
            leftSensorState = leftSensorState,
            rightSensorState = rightSensorState,
            onPairLeftSensor = { startPairing(PairingTarget.LEFT_SENSOR, resetExisting = false) },
            onPairRightSensor = { startPairing(PairingTarget.RIGHT_SENSOR, resetExisting = false) },
            onPairAnotherLeft = { startPairing(PairingTarget.LEFT_SENSOR, resetExisting = true) },
            onPairAnotherRight = { startPairing(PairingTarget.RIGHT_SENSOR, resetExisting = true) },
            onUnpairLeft = if (leftSensorState.isPaired) { { showUnpairLeftDialog = true } } else null,
            onUnpairRight = if (rightSensorState.isPaired) { { showUnpairRightDialog = true } } else null,
            onGoToHome = onPairingComplete,
            leftConnected = leftConnected,
            rightConnected = rightConnected,
            leftDataCount = leftDataCount,
            rightDataCount = rightDataCount,
            leftLastDataTime = leftLastDataTime,
            rightLastDataTime = rightLastDataTime,
            isLeftLegNeeded = isLeftLegNeeded,
            isRightLegNeeded = isRightLegNeeded
        )

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
                    onBack = { closePairingFlow() },
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
                    onBack = { closePairingFlow() }
                )
            }
        }
        
        // Unpair Left Sensor Confirmation Dialog
        if (showUnpairLeftDialog) {
            AlertDialog(
                onDismissRequest = { showUnpairLeftDialog = false },
                title = {
                    Text(
                        text = "Unpair Left Foot Sensor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to unpair the left foot sensor? This will disconnect the sensor.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.clearPairing(PairingTarget.LEFT_SENSOR)
                            showUnpairLeftDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Unpair")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showUnpairLeftDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Unpair Right Sensor Confirmation Dialog
        if (showUnpairRightDialog) {
            AlertDialog(
                onDismissRequest = { showUnpairRightDialog = false },
                title = {
                    Text(
                        text = "Unpair Right Foot Sensor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to unpair the right foot sensor? This will disconnect the sensor.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.clearPairing(PairingTarget.RIGHT_SENSOR)
                            showUnpairRightDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Unpair")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showUnpairRightDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
