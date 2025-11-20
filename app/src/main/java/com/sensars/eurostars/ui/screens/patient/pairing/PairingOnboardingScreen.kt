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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    leftConnected: Boolean = false,
    rightConnected: Boolean = false,
    leftDataCount: Long = 0,
    rightDataCount: Long = 0,
    leftLastDataTime: Long? = null,
    rightLastDataTime: Long? = null,
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
            showPairAnother = false, // Don't show "Pair another one" for left sensor
            isConnected = leftConnected,
            dataSampleCount = leftDataCount,
            lastDataTime = leftLastDataTime,
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
            isConnected = rightConnected,
            dataSampleCount = rightDataCount,
            lastDataTime = rightLastDataTime,
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
    showPairAnother: Boolean = true,
    isConnected: Boolean = false,
    dataSampleCount: Long = 0,
    lastDataTime: Long? = null,
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Show summary info when paired (even when collapsed)
                    if (state.isPaired) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.deviceName?.let { name ->
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                state.batteryLevel?.let { battery ->
                                    Text(
                                        text = "🔋 $battery%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Data reception status with colored indicator
                            if (isConnected) {
                                val timeSinceLastData = lastDataTime?.let { System.currentTimeMillis() - it }
                                val isReceivingData = timeSinceLastData != null && timeSinceLastData < 5000 // 5 seconds threshold
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Green bullet when receiving data, gray when connected but not receiving
                                    Text(
                                        text = if (isReceivingData) "●" else "○",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isReceivingData) 
                                            Color(0xFF4CAF50) // Green
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = if (isReceivingData) {
                                            "Receiving data ($dataSampleCount samples)"
                                        } else {
                                            "Connected, waiting for data..."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            } else if (state.isPaired) {
                                // Red bullet when disconnected
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "●",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error // Red
                                    )
                                    Text(
                                        text = "Not connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isPaired && showPairAnother) {
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
                    } else if (!state.isPaired) {
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
                    
                    state.batteryLevel?.let { battery ->
                        SensorDetailRow(
                            label = "Battery Level",
                            value = "$battery%",
                            isHighlighted = true
                        )
                    }
                    
                    state.serialNumber?.let { serial ->
                        SensorDetailRow(
                            label = "Serial Number",
                            value = serial
                        )
                    }
                    
                    state.firmwareVersion?.let { fw ->
                        SensorDetailRow(
                            label = "Firmware Version",
                            value = fw
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
    isHighlighted: Boolean = false,
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
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

