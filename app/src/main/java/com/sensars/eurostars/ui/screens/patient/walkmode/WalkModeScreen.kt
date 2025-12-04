package com.sensars.eurostars.ui.screens.patient.walkmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sensars.eurostars.EurostarsApp
import com.sensars.eurostars.data.SessionRepository
import com.sensars.eurostars.ui.components.FootHeatmap
import com.sensars.eurostars.ui.navigation.Routes
import com.sensars.eurostars.viewmodel.PairingTarget
import com.sensars.eurostars.viewmodel.bluetoothPairingViewModel
import kotlinx.coroutines.launch

@Composable
fun WalkModeScreen(
    navController: NavController = rememberNavController(),
    parentNavController: NavController? = null
) {
    val pairingViewModel = bluetoothPairingViewModel()
    val pairingUiState by pairingViewModel.uiState.collectAsState()
    val pairingStatus = pairingUiState.pairingStatus
    val context = LocalContext.current
    val connectionManager = (context.applicationContext as EurostarsApp).sensorConnectionManager
    val leftConnectionState by connectionManager.leftSensorConnection.collectAsState()
    val rightConnectionState by connectionManager.rightSensorConnection.collectAsState()
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.sessionFlow.collectAsState(initial = SessionRepository.Session())

    // Determine which legs need sensors based on neuropathic leg
    val neuropathicLeg = session.neuropathicLeg.lowercase()
    val isLeftLegNeeded =
        neuropathicLeg.isEmpty() || neuropathicLeg == "left" || neuropathicLeg == "both"
    val isRightLegNeeded =
        neuropathicLeg.isEmpty() || neuropathicLeg == "right" || neuropathicLeg == "both"

    val isLeftPaired = pairingStatus.isLeftPaired
    val isRightPaired = pairingStatus.isRightPaired

    // Check both pairing and connection status
    val leftEffectiveState =
        connectionManager.getEffectiveConnectionState(PairingTarget.LEFT_SENSOR)
    val rightEffectiveState =
        connectionManager.getEffectiveConnectionState(PairingTarget.RIGHT_SENSOR)
    val isLeftConnected =
        leftEffectiveState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    val isRightConnected =
        rightEffectiveState == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED

    // Calibration buttons should only be enabled if sensor is both paired AND connected
    val isLeftReadyForCalibration = isLeftPaired && isLeftConnected
    val isRightReadyForCalibration = isRightPaired && isRightConnected

    // Only require pairing for the neuropathic leg(s)
    val requiredSensorsPaired = when {
        isLeftLegNeeded && isRightLegNeeded -> isLeftPaired && isRightPaired // Both legs needed
        isLeftLegNeeded -> isLeftPaired // Only left leg needed
        isRightLegNeeded -> isRightPaired // Only right leg needed
        else -> true // No legs needed (shouldn't happen, but handle gracefully)
    }

    var showStartDialog by remember { mutableStateOf(false) }
    var isWalkModeActive by remember { mutableStateOf(false) }
    var sessionStart by remember { mutableStateOf<LocalDateTime?>(null) }
    var sessionEnd by remember { mutableStateOf<LocalDateTime?>(null) }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }

    LaunchedEffect(isWalkModeActive) {
        if (!isWalkModeActive) {
            sessionEnd = sessionEnd ?: sessionStart?.let { LocalDateTime.now() }
        } else {
            sessionEnd = null
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Walk Mode",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        if (isWalkModeActive) {
            SessionStatusCard(
                isActive = isWalkModeActive,
                sessionStart = sessionStart,
                sessionEnd = sessionEnd,
                formatter = timeFormatter
            )
        }

        WalkModeControls(
            isActive = isWalkModeActive,
            areBothSensorsPaired = requiredSensorsPaired,
            isLeftPaired = isLeftPaired,
            isRightPaired = isRightPaired,
            isLeftLegNeeded = isLeftLegNeeded,
            isRightLegNeeded = isRightLegNeeded,
            onStartRequested = { showStartDialog = true },
            onStop = {
                isWalkModeActive = false
                sessionEnd = LocalDateTime.now()
            }
        )

        HeatmapSection(
            isActive = isWalkModeActive,
            isLeftPaired = isLeftReadyForCalibration,
            isRightPaired = isRightReadyForCalibration,
            connectionManager = connectionManager,
            onCalibrateLeft = { 
                parentNavController?.navigate(Routes.CALIBRATION_LEFT)
            },
            onCalibrateRight = { 
                parentNavController?.navigate(Routes.CALIBRATION_RIGHT)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = { Text("Start Walk Mode?") },
            text = {
                val neededSensors = when {
                    isLeftLegNeeded && isRightLegNeeded -> "both sensors"
                    isLeftLegNeeded -> "the left foot sensor"
                    isRightLegNeeded -> "the right foot sensor"
                    else -> "sensors"
                }
                Text(
                    "Confirm to begin capturing pressure data from $neededSensors. " +
                            "Ensure the patient is ready to walk."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStartDialog = false
                        sessionStart = LocalDateTime.now()
                        sessionEnd = null
                        isWalkModeActive = true
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionStatusCard(
    isActive: Boolean,
    sessionStart: LocalDateTime?,
    sessionEnd: LocalDateTime?,
    formatter: DateTimeFormatter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isActive) "Recording in progress" else "Walk mode idle",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = buildString {
                    append("Session start: ")
                    append(sessionStart?.format(formatter) ?: "—")
                    append("\nSession end: ")
                    append(
                        sessionEnd?.format(formatter)
                            ?: if (isActive) "—" else "Awaiting next session"
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Once you stop Walk Mode, the captured data for this session will be uploaded automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WalkModeControls(
    isActive: Boolean,
    areBothSensorsPaired: Boolean,
    isLeftPaired: Boolean,
    isRightPaired: Boolean,
    isLeftLegNeeded: Boolean,
    isRightLegNeeded: Boolean,
    onStartRequested: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) {
                        "Walk mode is active"
                    } else if (areBothSensorsPaired) {
                        "Ready to start"
                    } else {
                        "Sensors not paired"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isActive -> {
                            "Tap Stop when the patient completes the walk. Data streams are buffered locally."
                        }

                        areBothSensorsPaired -> {
                            val neededSensors = when {
                                isLeftLegNeeded && isRightLegNeeded -> "both sensors"
                                isLeftLegNeeded -> "the left foot sensor"
                                isRightLegNeeded -> "the right foot sensor"
                                else -> "sensors"
                            }
                            "Tap Start to begin capturing live pressure data from $neededSensors."
                        }

                        !isLeftPaired && isLeftLegNeeded && !isRightPaired && isRightLegNeeded -> {
                            "Please pair both left and right foot sensors in the Pairing tab before starting."
                        }

                        !isLeftPaired && isLeftLegNeeded -> {
                            "Please pair the left foot sensor in the Pairing tab before starting."
                        }

                        !isRightPaired && isRightLegNeeded -> {
                            "Please pair the right foot sensor in the Pairing tab before starting."
                        }

                        else -> {
                            "Please pair the required sensor(s) in the Pairing tab before starting."
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (isActive) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Text("Stop", fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = onStartRequested,
                    enabled = areBothSensorsPaired,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Text("Start", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun HeatmapSection(
    isActive: Boolean,
    isLeftPaired: Boolean,
    isRightPaired: Boolean,
    connectionManager: com.sensars.eurostars.data.ble.SensorConnectionManager,
    onCalibrateLeft: () -> Unit,
    onCalibrateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Live plantar pressure heatmaps",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FootHeatmapCard(
                title = "Left foot",
                isActive = isActive,
                isPaired = isLeftPaired,
                connectionManager = connectionManager,
                sensorSide = PairingTarget.LEFT_SENSOR,
                onCalibrate = onCalibrateLeft,
                modifier = Modifier.weight(1f)
            )
            FootHeatmapCard(
                title = "Right foot",
                isActive = isActive,
                isPaired = isRightPaired,
                connectionManager = connectionManager,
                sensorSide = PairingTarget.RIGHT_SENSOR,
                onCalibrate = onCalibrateRight,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Hotter colours reflect higher pressure. Values refresh several times per second while Walk Mode runs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FootHeatmapCard(
    title: String,
    isActive: Boolean,
    isPaired: Boolean,
    connectionManager: com.sensars.eurostars.data.ble.SensorConnectionManager,
    sensorSide: PairingTarget,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dataHandler = connectionManager.getDataHandler()
    val isLeftFoot = sensorSide == PairingTarget.LEFT_SENSOR
    
    // Get connection state
    val connectionStateFlow = if (isLeftFoot) {
        connectionManager.leftSensorConnection
    } else {
        connectionManager.rightSensorConnection
    }
    val connectionState by connectionStateFlow.collectAsState()
    val isConnected = connectionState?.state == com.sensars.eurostars.data.ble.SensorConnectionState.CONNECTED
    
    // Collect pressure data for this sensor - using same pattern as Pairing tab
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
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPaired && pressureData.isNotEmpty()) {
                    FootHeatmap(
                        pressureData = pressureData,
                        isLeftFoot = isLeftFoot,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = if (isPaired) "Awaiting data" else "Not connected",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Text(
                text = if (isActive && isPaired) {
                    "Live sensor feed"
                } else if (isPaired) {
                    "Ready - Start walk mode to see data"
                } else {
                    "Sensor not connected"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            OutlinedButton(
                onClick = onCalibrate,
                enabled = isPaired,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Calibrate")
            }
        }
    }
}
