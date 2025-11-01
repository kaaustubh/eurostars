package com.sensars.eurostars.ui.navigation

import PatientsScreen
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
        composable(ClinicianTab.Patients.route) {
            PatientsScreen(
                onAddPatient = {
                    // TODO: navController.navigate("add_patient")
                }
            )
        }

        composable(ClinicianTab.Reports.route)   { Text("Reports View") }
        composable(ClinicianTab.Settings.route)  { 
            com.sensars.eurostars.ui.screens.clinician.settings.SettingsScreen()
        }
    }
}
