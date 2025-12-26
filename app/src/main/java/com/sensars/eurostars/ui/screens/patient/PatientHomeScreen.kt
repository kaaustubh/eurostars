package com.sensars.eurostars.ui.screens.patient

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sensars.eurostars.ui.navigation.PatientDashboardNavHost
import com.sensars.eurostars.ui.screens.patient.dashboard.PatientBottomBar
import com.sensars.eurostars.viewmodel.patientAuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onLogout: () -> Unit = {},
    parentNavController: NavController? = null
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val vm = patientAuthViewModel()
    
    // Child navController for patient dashboard tabs
    val dashboardNav = rememberNavController()
    val dashboardBackStackEntry by dashboardNav.currentBackStackEntryAsState()
    val dashboardRoute = dashboardBackStackEntry?.destination?.route.orEmpty()
    val isGaitAnalysisDetail = dashboardRoute.startsWith("gait_analysis_session")

    Scaffold(
        topBar = {
            if (!isGaitAnalysisDetail) {
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
        },
        bottomBar = {
            if (!isGaitAnalysisDetail) {
                PatientBottomBar(navController = dashboardNav)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PatientDashboardNavHost(
                navController = dashboardNav,
                parentNavController = parentNavController
            )
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
