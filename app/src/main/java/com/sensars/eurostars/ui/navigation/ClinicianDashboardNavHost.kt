package com.sensars.eurostars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material3.Text
import com.sensars.eurostars.ui.screens.clinician.dashboard.ClinicianTab

@Composable
fun ClinicianDashboardNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = ClinicianTab.Dashboard.route
    ) {
        composable(ClinicianTab.Dashboard.route) { Text("Dashboard View") }
        composable(ClinicianTab.Patients.route)  { Text("Patients List View") }
        composable(ClinicianTab.Reports.route)   { Text("Reports View") }
        composable(ClinicianTab.Settings.route)  { Text("Settings View") }
    }
}
