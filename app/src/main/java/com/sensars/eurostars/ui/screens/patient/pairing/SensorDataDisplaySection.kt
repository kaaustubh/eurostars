package com.sensars.eurostars.ui.screens.patient.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sensars.eurostars.data.ble.SensorConnectionManager
import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Displays real-time sensor data for both left and right sensors.
 * Shows pressure taxels, accelerometer, gyroscope, temperature, and time data.
 */
@Composable
fun SensorDataDisplaySection(
    connectionManager: SensorConnectionManager,
    leftConnected: Boolean,
    rightConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val dataHandler = connectionManager.getDataHandler()
    
    // State for left sensor data
    var leftPressures by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var leftAccelX by remember { mutableStateOf<Float?>(null) }
    var leftAccelY by remember { mutableStateOf<Float?>(null) }
    var leftAccelZ by remember { mutableStateOf<Float?>(null) }
    var leftGyroX by remember { mutableStateOf<Float?>(null) }
    var leftGyroY by remember { mutableStateOf<Float?>(null) }
    var leftGyroZ by remember { mutableStateOf<Float?>(null) }
    var leftTemp by remember { mutableStateOf<Float?>(null) }
    var leftTime by remember { mutableStateOf<Long?>(null) }
    
    // State for right sensor data
    var rightPressures by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var rightAccelX by remember { mutableStateOf<Float?>(null) }
    var rightAccelY by remember { mutableStateOf<Float?>(null) }
    var rightAccelZ by remember { mutableStateOf<Float?>(null) }
    var rightGyroX by remember { mutableStateOf<Float?>(null) }
    var rightGyroY by remember { mutableStateOf<Float?>(null) }
    var rightGyroZ by remember { mutableStateOf<Float?>(null) }
    var rightTemp by remember { mutableStateOf<Float?>(null) }
    var rightTime by remember { mutableStateOf<Long?>(null) }
    
    // Collect data from left sensor
    LaunchedEffect(leftConnected) {
        if (leftConnected) {
            val pressureFlow = dataHandler.getPressureFlow(PairingTarget.LEFT_SENSOR)
            val accelFlow = dataHandler.getSensorStreams(PairingTarget.LEFT_SENSOR).accel
            val gyroFlow = dataHandler.getSensorStreams(PairingTarget.LEFT_SENSOR).gyro
            val tempFlow = dataHandler.getSensorStreams(PairingTarget.LEFT_SENSOR).temperature
            val timeFlow = dataHandler.getSensorStreams(PairingTarget.LEFT_SENSOR).deviceTime
            
            kotlinx.coroutines.coroutineScope {
                launch {
                    try {
                        pressureFlow.collect { sample ->
                            if (leftConnected) { // Check connection state before updating
                                leftPressures = leftPressures + (sample.taxelIndex to sample.value)
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        accelFlow.collect { sample ->
                            if (leftConnected) {
                                if (!sample.x.isNaN()) leftAccelX = sample.x
                                if (!sample.y.isNaN()) leftAccelY = sample.y
                                if (!sample.z.isNaN()) leftAccelZ = sample.z
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        gyroFlow.collect { sample ->
                            if (leftConnected) {
                                if (!sample.x.isNaN()) leftGyroX = sample.x
                                if (!sample.y.isNaN()) leftGyroY = sample.y
                                if (!sample.z.isNaN()) leftGyroZ = sample.z
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        tempFlow.collect { sample ->
                            if (leftConnected) {
                                leftTemp = sample.celsius
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        timeFlow.collect { sample ->
                            if (leftConnected) {
                                leftTime = sample.millis
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
            }
        } else {
            // Clear data when disconnected
            leftPressures = emptyMap()
            leftAccelX = null
            leftAccelY = null
            leftAccelZ = null
            leftGyroX = null
            leftGyroY = null
            leftGyroZ = null
            leftTemp = null
            leftTime = null
        }
    }
    
    // Collect data from right sensor
    LaunchedEffect(rightConnected) {
        if (rightConnected) {
            val pressureFlow = dataHandler.getPressureFlow(PairingTarget.RIGHT_SENSOR)
            val accelFlow = dataHandler.getSensorStreams(PairingTarget.RIGHT_SENSOR).accel
            val gyroFlow = dataHandler.getSensorStreams(PairingTarget.RIGHT_SENSOR).gyro
            val tempFlow = dataHandler.getSensorStreams(PairingTarget.RIGHT_SENSOR).temperature
            val timeFlow = dataHandler.getSensorStreams(PairingTarget.RIGHT_SENSOR).deviceTime
            
            kotlinx.coroutines.coroutineScope {
                launch {
                    try {
                        pressureFlow.collect { sample ->
                            if (rightConnected) { // Check connection state before updating
                                rightPressures = rightPressures + (sample.taxelIndex to sample.value)
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        accelFlow.collect { sample ->
                            if (rightConnected) {
                                if (!sample.x.isNaN()) rightAccelX = sample.x
                                if (!sample.y.isNaN()) rightAccelY = sample.y
                                if (!sample.z.isNaN()) rightAccelZ = sample.z
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        gyroFlow.collect { sample ->
                            if (rightConnected) {
                                if (!sample.x.isNaN()) rightGyroX = sample.x
                                if (!sample.y.isNaN()) rightGyroY = sample.y
                                if (!sample.z.isNaN()) rightGyroZ = sample.z
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        tempFlow.collect { sample ->
                            if (rightConnected) {
                                rightTemp = sample.celsius
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
                launch {
                    try {
                        timeFlow.collect { sample ->
                            if (rightConnected) {
                                rightTime = sample.millis
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ignore other exceptions
                    }
                }
            }
        } else {
            // Clear data when disconnected
            rightPressures = emptyMap()
            rightAccelX = null
            rightAccelY = null
            rightAccelZ = null
            rightGyroX = null
            rightGyroY = null
            rightGyroZ = null
            rightTemp = null
            rightTime = null
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sensor Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (leftConnected) {
                SensorDataCard(
                    title = "Left Foot Sensor",
                    pressures = leftPressures,
                    accelX = leftAccelX,
                    accelY = leftAccelY,
                    accelZ = leftAccelZ,
                    gyroX = leftGyroX,
                    gyroY = leftGyroY,
                    gyroZ = leftGyroZ,
                    temp = leftTemp,
                    time = leftTime
                )
            }
            
            if (rightConnected) {
                SensorDataCard(
                    title = "Right Foot Sensor",
                    pressures = rightPressures,
                    accelX = rightAccelX,
                    accelY = rightAccelY,
                    accelZ = rightAccelZ,
                    gyroX = rightGyroX,
                    gyroY = rightGyroY,
                    gyroZ = rightGyroZ,
                    temp = rightTemp,
                    time = rightTime
                )
            }
            
            if (!leftConnected && !rightConnected) {
                Text(
                    text = "No sensors connected. Pair sensors to see data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SensorDataCard(
    title: String,
    pressures: Map<Int, Long>,
    accelX: Float?,
    accelY: Float?,
    accelZ: Float?,
    gyroX: Float?,
    gyroY: Float?,
    gyroZ: Float?,
    temp: Float?,
    time: Long?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Pressure Taxels (18)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Pressure Taxels (${pressures.size}/18):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (0..17).forEach { index ->
                        val value = pressures[index] ?: 0L
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (value > 0) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(4.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                text = if (value > 0) value.toString() else "-",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (pressures.isEmpty()) {
                    Text(
                        text = "Waiting for pressure data...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Accelerometer
            if (accelX != null || accelY != null || accelZ != null) {
                DataRow(
                    label = "Accelerometer",
                    value = "X: ${accelX?.let { String.format("%.2f", it) } ?: "-"}, Y: ${accelY?.let { String.format("%.2f", it) } ?: "-"}, Z: ${accelZ?.let { String.format("%.2f", it) } ?: "-"}"
                )
            }
            
            // Gyroscope
            if (gyroX != null || gyroY != null || gyroZ != null) {
                DataRow(
                    label = "Gyroscope",
                    value = "X: ${gyroX?.let { String.format("%.2f", it) } ?: "-"}, Y: ${gyroY?.let { String.format("%.2f", it) } ?: "-"}, Z: ${gyroZ?.let { String.format("%.2f", it) } ?: "-"}"
                )
            }
            
            // Temperature
            if (temp != null) {
                DataRow(
                    label = "Temperature",
                    value = "${String.format("%.2f", temp)}°C"
                )
            }
            
            // Time
            if (time != null) {
                DataRow(
                    label = "Device Time",
                    value = "$time ms"
                )
            }
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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

