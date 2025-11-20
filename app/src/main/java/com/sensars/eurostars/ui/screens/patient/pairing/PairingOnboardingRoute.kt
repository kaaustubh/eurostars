package com.sensars.eurostars.ui.screens.patient.pairing

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sensars.eurostars.EurostarsApp
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

    val uiState by vm.uiState.collectAsState()
    val pairingStatus = uiState.pairingStatus

    var activeTarget by remember { mutableStateOf<PairingTarget?>(null) }
    var showScanScreen by remember { mutableStateOf(false) }
    var currentIssue by remember { mutableStateOf<BluetoothIssue?>(null) }
    var lastErrorMessage by remember { mutableStateOf("") }

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

    LaunchedEffect(pairingStatus.areBothPaired) {
        if (pairingStatus.areBothPaired) {
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
            onGoToHome = onPairingComplete,
            leftConnected = leftConnected,
            rightConnected = rightConnected,
            leftDataCount = leftDataCount,
            rightDataCount = rightDataCount,
            leftLastDataTime = leftLastDataTime,
            rightLastDataTime = rightLastDataTime
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
    }
}
