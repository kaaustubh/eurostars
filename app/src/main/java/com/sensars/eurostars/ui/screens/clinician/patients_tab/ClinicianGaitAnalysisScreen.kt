package com.sensars.eurostars.ui.screens.clinician.patients_tab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.ui.components.CoPTraceView
import com.sensars.eurostars.ui.components.GaitMetricChart
import com.sensars.eurostars.ui.components.MetricChartConfig
import com.sensars.eurostars.viewmodel.clinicianGaitAnalysisViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicianGaitAnalysisScreen(
    patientId: String,
    sessionStartTime: Long,
    onBack: () -> Unit
) {
    val viewModel = clinicianGaitAnalysisViewModel(patientId, sessionStartTime)
    val grfValue by viewModel.grfValue.collectAsState()
    val velocity by viewModel.velocity.collectAsState()
    val cadence by viewModel.cadence.collectAsState()
    val strideLength by viewModel.strideLength.collectAsState()
    val centerOfPressure by viewModel.centerOfPressure.collectAsState()
    val lateralCenterOfMass by viewModel.lateralCenterOfMass.collectAsState()
    val extrapolatedCenterOfMass by viewModel.extrapolatedCenterOfMass.collectAsState()
    val marginOfStability by viewModel.marginOfStability.collectAsState()
    val balance by viewModel.balance.collectAsState()
    val copTraceLeft by viewModel.copTraceLeft.collectAsState()
    val copTraceRight by viewModel.copTraceRight.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    val startTimeStr = dateFormat.format(Date(sessionStartTime))
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gait analysis - $startTimeStr") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Text(
                    text = error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Display all 9 gait metrics
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Velocity",
                        minValue = 0.0,
                        maxValue = 2.0,
                        greenMin = 1.2,
                        greenMax = 1.4,
                        yellowMin1 = 1.0,
                        yellowMax1 = 1.2,
                        yellowMin2 = 1.4,
                        yellowMax2 = 1.6,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = velocity
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Cadence",
                        minValue = 50.0,
                        maxValue = 140.0,
                        greenMin = 100.0,
                        greenMax = 120.0,
                        yellowMin1 = 85.0,
                        yellowMax1 = 100.0,
                        yellowMin2 = 120.0,
                        yellowMax2 = 135.0,
                        valueFormat = { String.format("%.0f", it) }
                    ),
                    patientValue = cadence
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Stride length",
                        minValue = 0.0,
                        maxValue = 2.0,
                        greenMin = 1.0,
                        greenMax = 1.5,
                        yellowMin1 = 0.8,
                        yellowMax1 = 1.0,
                        yellowMin2 = 1.5,
                        yellowMax2 = 1.8,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = strideLength
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Ground reaction force",
                        minValue = 0.0,
                        maxValue = 2.0,
                        greenMin = 1.2,
                        greenMax = 1.4,
                        yellowMin1 = 1.0,
                        yellowMax1 = 1.2,
                        yellowMin2 = 1.4,
                        yellowMax2 = 1.6,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = grfValue
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Center of pressure",
                        minValue = 0.0,
                        maxValue = 1.0,
                        greenMin = 0.4,
                        greenMax = 0.6,
                        yellowMin1 = 0.3,
                        yellowMax1 = 0.4,
                        yellowMin2 = 0.6,
                        yellowMax2 = 0.7,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = centerOfPressure
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Lateral center of mass",
                        minValue = 0.0,
                        maxValue = 1.0,
                        greenMin = 0.4,
                        greenMax = 0.6,
                        yellowMin1 = 0.3,
                        yellowMax1 = 0.4,
                        yellowMin2 = 0.6,
                        yellowMax2 = 0.7,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = lateralCenterOfMass
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Extrapolated center of mass",
                        minValue = 0.0,
                        maxValue = 1.0,
                        greenMin = 0.4,
                        greenMax = 0.6,
                        yellowMin1 = 0.3,
                        yellowMax1 = 0.4,
                        yellowMin2 = 0.6,
                        yellowMax2 = 0.7,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = extrapolatedCenterOfMass
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Margin of stability",
                        minValue = 0.0,
                        maxValue = 0.3,
                        greenMin = 0.1,
                        greenMax = 0.2,
                        yellowMin1 = 0.05,
                        yellowMax1 = 0.1,
                        yellowMin2 = 0.2,
                        yellowMax2 = 0.25,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = marginOfStability
                )
                
                GaitMetricChart(
                    config = MetricChartConfig(
                        title = "Balance",
                        minValue = 0.0,
                        maxValue = 1.0,
                        greenMin = 0.7,
                        greenMax = 0.9,
                        yellowMin1 = 0.5,
                        yellowMax1 = 0.7,
                        yellowMin2 = 0.9,
                        yellowMax2 = 1.0,
                        valueFormat = { String.format("%.2f", it) }
                    ),
                    patientValue = balance
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Center of Pressure Trace",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CoPTraceView(
                        isLeftFoot = true,
                        trace = copTraceLeft,
                        modifier = Modifier.weight(1f)
                    )
                    CoPTraceView(
                        isLeftFoot = false,
                        trace = copTraceRight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

