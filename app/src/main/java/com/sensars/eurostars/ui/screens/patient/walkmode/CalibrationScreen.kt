package com.sensars.eurostars.ui.screens.patient.walkmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.ui.components.FootHeatmap
import com.sensars.eurostars.viewmodel.PairingTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    navController: NavController,
    footSide: String // "Left" or "Right"
) {
    val context = LocalContext.current
    val connectionManager = (context.applicationContext as EurostarsApp).sensorConnectionManager
    val dataHandler = connectionManager.getDataHandler()
    
    val isLeftFoot = footSide.equals("Left", ignoreCase = true)
    val sensorSide = if (isLeftFoot) PairingTarget.LEFT_SENSOR else PairingTarget.RIGHT_SENSOR
    
    // Get connection state - using same pattern as WalkModeScreen
    val connectionStateFlow = if (isLeftFoot) {
        connectionManager.leftSensorConnection
    } else {
        connectionManager.rightSensorConnection
    }
    val connectionState by connectionStateFlow.collectAsState()
    val isConnected = connectionState?.state == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    
    // Collect pressure data for the sensor - using same pattern as Pairing tab
    var pressureData by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    
    LaunchedEffect(isConnected) {
        if (isConnected) {
            val pressureFlow = dataHandler.getPressureFlow(sensorSide)
            
            kotlinx.coroutines.coroutineScope {
                launch {
                    try {
                        pressureFlow.collect { sample ->
                            if (isConnected) { // Check connection state before updating
                                pressureData = pressureData + (sample.taxelIndex to sample.value)
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
            pressureData = emptyMap()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrate $footSide Foot", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Heatmap visualization
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    FootHeatmap(
                        pressureData = pressureData,
                        isLeftFoot = isLeftFoot,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // Legend
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pressure Legend",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem("Blue", Color(0xFF2196F3), "<50 kPa")
                        LegendItem("Green", Color(0xFF4CAF50), "50-100 kPa")
                        LegendItem("Yellow", Color(0xFFFFEB3B), "100-150 kPa")
                        LegendItem("Orange", Color(0xFFFF9800), "150-200 kPa")
                        LegendItem("Red", Color(0xFFF44336), "200+ kPa")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: androidx.compose.ui.graphics.Color, range: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = range,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

