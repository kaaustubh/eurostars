package com.sensars.eurostars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.sensars.eurostars.viewmodel.RoleViewModel
import com.sensars.eurostars.ui.screens.SplashRoute
import com.sensars.sensole.ui.screens.RoleSelectScreen
import com.sensars.sensole.ui.screens.clinician.ClinicianHomeScreen
import com.sensars.sensole.ui.screens.patient.PatientHomeScreen


@Composable
fun NavHost(navController: NavHostController) {
    val roleVm: RoleViewModel = viewModel()

    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashRoute(
                readRole = { roleVm.readRole() },
                onPatient = {
                    navController.navigate(Routes.PATIENT_HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onClinician = {
                    navController.navigate(Routes.CLINICIAN_HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onNoRole = {
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }
        composable(Routes.ROLE_SELECT) {
            RoleSelectScreen(
                onPatient = {
                    navController.navigate(Routes.PATIENT_HOME) { popUpTo(Routes.ROLE_SELECT) { inclusive = true } }
                },
                onClinician = {
                    navController.navigate(Routes.CLINICIAN_HOME) { popUpTo(Routes.ROLE_SELECT) { inclusive = true } }
                }
            )
        }
        composable(Routes.PATIENT_HOME) { PatientHomeScreen() }
        composable(Routes.CLINICIAN_HOME) { ClinicianHomeScreen() }
    }
}
