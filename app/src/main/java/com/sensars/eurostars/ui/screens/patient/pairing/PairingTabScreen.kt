package com.sensars.eurostars.ui.screens.patient.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.viewmodel.bluetoothPairingViewModel

@Composable
fun PairingTabScreen() {
    val vm = bluetoothPairingViewModel()
    val pairingStatus by vm.uiState.collectAsState()
    
    val leftSensorState = SensorPairingState(
        isPaired = pairingStatus.pairingStatus.isLeftPaired,
        deviceName = pairingStatus.pairingStatus.leftSensor.deviceName,
        serialNumber = pairingStatus.pairingStatus.leftSensor.serialNumber,
        firmwareVersion = pairingStatus.pairingStatus.leftSensor.firmwareVersion,
        batteryLevel = pairingStatus.pairingStatus.leftSensor.batteryLevel
    )
    
    val rightSensorState = SensorPairingState(
        isPaired = pairingStatus.pairingStatus.isRightPaired,
        deviceName = pairingStatus.pairingStatus.rightSensor.deviceName,
        serialNumber = pairingStatus.pairingStatus.rightSensor.serialNumber,
        firmwareVersion = pairingStatus.pairingStatus.rightSensor.firmwareVersion,
        batteryLevel = pairingStatus.pairingStatus.rightSensor.batteryLevel
    )
    
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
        
        // Left Foot Sensor Section
        SensorPairingSection(
            title = "Left Foot Sensor",
            state = leftSensorState,
            isExpanded = remember { mutableStateOf(true) }.value,
            onExpandedChange = { },
            onPair = { /* Navigate to pairing flow */ },
            onPairAnother = { 
                vm.clearPairing(com.sensars.eurostars.viewmodel.PairingTarget.LEFT_SENSOR)
                // Navigate to pairing flow
            },
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Right Foot Sensor Section
        SensorPairingSection(
            title = "Right Foot Sensor",
            state = rightSensorState,
            isExpanded = remember { mutableStateOf(true) }.value,
            onExpandedChange = { },
            onPair = { /* Navigate to pairing flow */ },
            onPairAnother = { 
                vm.clearPairing(com.sensars.eurostars.viewmodel.PairingTarget.RIGHT_SENSOR)
                // Navigate to pairing flow
            },
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Clear All Button
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
        
        // About Section with Release Notes
        var showReleaseNotes by remember { mutableStateOf(false) }
        com.sensars.eurostars.ui.screens.shared.AboutSection(
            showReleaseNotes = showReleaseNotes,
            onShowReleaseNotesChange = { showReleaseNotes = it },
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

