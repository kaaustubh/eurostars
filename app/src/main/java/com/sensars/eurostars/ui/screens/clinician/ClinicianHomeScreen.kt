package com.sensars.sensole.ui.screens.clinician

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sensars.eurostars.ui.navigation.ClinicianDashboardNavHost
import com.sensars.eurostars.ui.screens.clinician.dashboard.ClinicianBottomBar
import com.sensars.eurostars.ui.screens.clinician.dashboard.ClinicianTabBar
import com.sensars.eurostars.ui.utils.rememberWindowWidthClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicianHomeScreen(onLogout: () -> Unit = {}) {
    val widthClass = rememberWindowWidthClass()

    // Child navController dedicated to the dashboard tabs
    val dashboardNav = rememberNavController()

    Scaffold(
        bottomBar = {
            if (widthClass == WindowWidthSizeClass.Compact) {
                ClinicianBottomBar(navController = dashboardNav)
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (widthClass != WindowWidthSizeClass.Compact) {
                ClinicianTabBar(
                    navController = dashboardNav,
                    modifier = Modifier.width(84.dp),
                    onLogout = onLogout
                )
            }
            // Main pane: the nested dashboard graph content
            Box(Modifier.fillMaxSize()) {
                ClinicianDashboardNavHost(navController = dashboardNav)
            }
        }
    }
}