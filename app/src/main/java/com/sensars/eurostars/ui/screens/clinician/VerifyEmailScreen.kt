package com.sensars.eurostars.ui.screens.clinician

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VerifyEmailScreen(
    onContinue: () -> Unit
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp)) {
            Text("Verify Email (Placeholder)", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("TODO: show verification instructions and a 'Resend' option.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onContinue) { Text("Mock: I’ve verified — Continue") }
        }
    }
}
