package com.sensars.eurostars.ui.screens.clinician.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sensars.eurostars.R


@Composable
fun ClinicianTabBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Icon(
                painter = painterResource(id = R.drawable.ic_dashboard),
                contentDescription = "App icon",
                modifier = Modifier.padding(12.dp)
            )
        }
    ) {
        ClinicianTab.values().forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationRailItem(
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
                label = { Text(tab.label) },
                alwaysShowLabel = false
            )
        }
    }
}
