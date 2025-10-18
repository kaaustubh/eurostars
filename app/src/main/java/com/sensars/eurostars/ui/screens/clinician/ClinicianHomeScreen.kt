package com.sensars.sensole.ui.screens.clinician

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicianHomeScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Clinician") }) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Clinician Dashboard (placeholder)")
            Spacer(Modifier.height(8.dp))
            Text("• Patient List\n• Session History\n• Live Monitoring")
        }
    }
}
