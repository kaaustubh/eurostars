package com.sensars.eurostars.ui.screens.clinician.dashboard

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun ClinicianBottomBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    NavigationBar {
        ClinicianTab.values().forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) navController.navigate(tab.route) {
                        popUpTo(ClinicianTab.Dashboard.route)
                        launchSingleTop = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(tab.icon),
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) }
            )
        }
    }
}
