package com.sensars.eurostars.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectScreen(
    onClinicianSelected: () -> Unit,
    onPatientSelected: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Select your role",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onClinicianSelected,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Clinician")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onPatientSelected,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Patient")
            }
        }
    }
}
