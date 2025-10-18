package com.sensars.sensole.ui.screens.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Patient") }) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Patient Dashboard (placeholder)")
            Spacer(Modifier.height(8.dp))
            Text("• Pair sensors\n• Walking Mode\n• Session History")
        }
    }
}
