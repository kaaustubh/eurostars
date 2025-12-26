package com.sensars.eurostars.ui.navigation

import PatientsScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.material3.Text
import com.sensars.eurostars.ui.screens.clinician.dashboard.ClinicianTab
import com.sensars.eurostars.ui.screens.clinician.patients_tab.PatientSessionsScreen
import com.sensars.eurostars.ui.screens.clinician.patients_tab.ClinicianGaitAnalysisScreen

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
                },
                onPatientClick = { patientId ->
                    navController.navigate("patient_sessions/$patientId")
                }
            )
        }
        
        composable(
            route = "patient_sessions/{patientId}",
            arguments = listOf(navArgument("patientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
            PatientSessionsScreen(
                patientId = patientId,
                onBack = { navController.popBackStack() },
                onSessionClick = { sessionStartTime ->
                    navController.navigate("gait_analysis/$patientId/$sessionStartTime")
                }
            )
        }
        
        composable(
            route = "gait_analysis/{patientId}/{sessionStartTime}",
            arguments = listOf(
                navArgument("patientId") { type = NavType.StringType },
                navArgument("sessionStartTime") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
            val sessionStartTime = backStackEntry.arguments?.getLong("sessionStartTime") ?: return@composable
            ClinicianGaitAnalysisScreen(
                patientId = patientId,
                sessionStartTime = sessionStartTime,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ClinicianTab.Reports.route)   { Text("Reports View") }
        composable(ClinicianTab.Settings.route)  { 
            com.sensars.eurostars.ui.screens.clinician.settings.SettingsScreen()
        }
    }
}
