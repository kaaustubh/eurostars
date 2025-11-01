package com.sensars.eurostars.ui.screens.patient

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.viewmodel.patientAuthViewModel

@Composable
fun PatientLoginScreen(
    onLoginSuccess: () -> Unit
) {
    var patientId by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val vm = patientAuthViewModel()

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Patient Sign In",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter your Clinical Study ID to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // Error message
            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        error!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Patient ID input field
            OutlinedTextField(
                value = patientId,
                onValueChange = { 
                    patientId = it
                    error = null // Clear error on input change
                },
                label = { Text("Clinical Study ID") },
                placeholder = { Text("Enter your patient ID") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )
            Spacer(Modifier.height(24.dp))

            // Login button
            Button(
                onClick = {
                    if (patientId.isNotBlank() && !loading) {
                        loading = true
                        error = null
                        vm.loginPatient(
                            patientIdRaw = patientId,
                            onSuccess = {
                                loading = false
                                onLoginSuccess()
                            },
                            onError = { e ->
                                loading = false
                                error = e
                            }
                        )
                    }
                },
                enabled = patientId.isNotBlank() && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in...")
                } else {
                    Text("Sign In")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Help text
            Text(
                text = "Your Clinical Study ID was provided by your clinician",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
