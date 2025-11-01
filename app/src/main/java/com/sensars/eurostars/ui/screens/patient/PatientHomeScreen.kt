package com.sensars.sensole.ui.screens.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sensars.eurostars.viewmodel.patientAuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(onLogout: () -> Unit = {}) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val vm = patientAuthViewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient") },
                actions = {
                    IconButton(onClick = { showLogoutConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Patient Dashboard (placeholder)")
            Spacer(Modifier.height(8.dp))
            Text("• Pair sensors\n• Walking Mode\n• Session History")
        }

        // Logout confirmation dialog
        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("Logging out?") },
                text = { Text("You will need to log in again.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirm = false
                            vm.signOut {
                                onLogout()
                            }
                        }
                    ) {
                        Text("Log out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
