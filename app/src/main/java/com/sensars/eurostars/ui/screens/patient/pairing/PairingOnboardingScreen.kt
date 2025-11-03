package com.sensars.eurostars.ui.screens.patient.pairing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SensorPairingState(
    val isPaired: Boolean = false,
    val deviceName: String? = null,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null
)

@Composable
fun PairingOnboardingScreen(
    leftSensorState: SensorPairingState = SensorPairingState(),
    rightSensorState: SensorPairingState = SensorPairingState(),
    onPairLeftSensor: () -> Unit = {},
    onPairRightSensor: () -> Unit = {},
    onPairAnotherLeft: () -> Unit = {},
    onPairAnotherRight: () -> Unit = {},
    onGoToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var leftExpanded by remember { mutableStateOf(!leftSensorState.isPaired) }
    var rightExpanded by remember { mutableStateOf(!rightSensorState.isPaired) }
    
    val bothPaired = leftSensorState.isPaired && rightSensorState.isPaired
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with proper spacing from top
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Pairing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Left Foot Sensor Section
        SensorPairingSection(
            title = "Left Foot Sensor",
            state = leftSensorState,
            isExpanded = leftExpanded,
            onExpandedChange = { leftExpanded = it },
            onPair = onPairLeftSensor,
            onPairAnother = onPairAnotherLeft,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Right Foot Sensor Section
        SensorPairingSection(
            title = "Right Foot Sensor",
            state = rightSensorState,
            isExpanded = rightExpanded,
            onExpandedChange = { rightExpanded = it },
            onPair = onPairRightSensor,
            onPairAnother = onPairAnotherRight,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Go to Home Screen Button (always enabled)
        Button(
            onClick = onGoToHome,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            enabled = true,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Go to the Home screen",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun SensorPairingSection(
    title: String,
    state: SensorPairingState,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPair: () -> Unit,
    onPairAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isPaired) {
                        // Pair another button
                        TextButton(
                            onClick = { onPairAnother() },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Pair another one",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Pair button
                        Button(
                            onClick = { onPair() },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Pair",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Expanded Content
            if (isExpanded && state.isPaired) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.deviceName?.let { name ->
                        SensorDetailRow(
                            label = "Device Name",
                            value = name
                        )
                    }
                    
                    state.serialNumber?.let { serial ->
                        SensorDetailRow(
                            label = "Sensor serial number",
                            value = serial
                        )
                    }
                    
                    state.firmwareVersion?.let { fw ->
                        SensorDetailRow(
                            label = "Sensor FW version",
                            value = fw
                        )
                    }
                    
                    state.batteryLevel?.let { battery ->
                        SensorDetailRow(
                            label = "Sensor battery status",
                            value = "$battery%"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

