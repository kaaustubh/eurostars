package com.sensars.eurostars.ui.screens.patient.pairing

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.sensars.eurostars.data.PairingRepository
import com.sensars.eurostars.viewmodel.*

@Composable
fun PairingOnboardingRoute(
    onPairingComplete: () -> Unit
) {
    val context = LocalContext.current
    val pairingRepo = remember { PairingRepository(context) }
    val vm = bluetoothPairingViewModel()
    
    val uiState by vm.uiState.collectAsState()
    val pairingStatus = uiState.pairingStatus
    
    // Convert PairingStatus to SensorPairingState for UI
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
    
    // Navigate to home when both sensors are paired
    LaunchedEffect(pairingStatus.areBothPaired) {
        if (pairingStatus.areBothPaired) {
            onPairingComplete()
        }
    }
    
    PairingOnboardingScreen(
        leftSensorState = leftSensorState,
        rightSensorState = rightSensorState,
        onPairLeftSensor = {
            // TODO: Navigate to device search screen for left sensor
            vm.startScanning(PairingTarget.LEFT_SENSOR)
        },
        onPairRightSensor = {
            // TODO: Navigate to device search screen for right sensor
            vm.startScanning(PairingTarget.RIGHT_SENSOR)
        },
        onPairAnotherLeft = {
            vm.clearPairing(PairingTarget.LEFT_SENSOR)
            vm.startScanning(PairingTarget.LEFT_SENSOR)
        },
        onPairAnotherRight = {
            vm.clearPairing(PairingTarget.RIGHT_SENSOR)
            vm.startScanning(PairingTarget.RIGHT_SENSOR)
        },
        onGoToHome = {
            // Always navigate to home screen, regardless of pairing status
            onPairingComplete()
        }
    )
}

