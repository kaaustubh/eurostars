package com.sensars.sensole.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleSelectScreen(
    onPatient: () -> Unit,
    onClinician: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Welcome to SENSOLE",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(24.dp))
            Text("Choose your role to continue", fontSize = 16.sp)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onPatient, modifier = Modifier.fillMaxWidth()) {
                Text("I am a Patient")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onClinician, modifier = Modifier.fillMaxWidth()) {
                Text("I am a Clinician")
            }
        }
    }
}
