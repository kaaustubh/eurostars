package com.sensars.eurostars.ui.screens.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PatientLoginScreen(
    onLoginSuccess: () -> Unit
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp)) {
            Text("Patient Login (Placeholder)", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("TODO: implement Patient Clinical Study ID input and validation.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLoginSuccess) { Text("Mock: Continue to Patient Home") }
        }
    }
}
