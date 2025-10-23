package com.sensars.eurostars.ui.screens.clinician.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun ClinicianTabBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Custom colors
    val sidebarBg = Color(0xFF466871)
    val selectedBg = Color(0xFF5A7E87)
    val iconColor = Color.White

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(sidebarBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))

        ClinicianTab.values().forEach { tab ->
            val selected = currentRoute == tab.route
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) selectedBg else Color.Transparent)
                    .width(72.dp)
                    .height(72.dp)
                    .clickable {
                        if (!selected) {
                            navController.navigate(tab.route) {
                                popUpTo(ClinicianTab.Dashboard.route)
                                launchSingleTop = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(tab.icon),
                        contentDescription = tab.label,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = iconColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
