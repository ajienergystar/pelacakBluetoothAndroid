package com.example.mobilepelacak.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mobilepelacak.ui.history.HistoryRoute
import com.example.mobilepelacak.ui.radar.RadarRoute
import com.example.mobilepelacak.ui.scanner.ScannerRoute

object Routes {
    const val SCANNER = "scanner"
    const val HISTORY = "history"
    const val ARG_ADDRESS = "address"
    const val RADAR = "radar/{$ARG_ADDRESS}"

    fun radar(address: String): String = "radar/${Uri.encode(address)}"
}

private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.SCANNER, "Scanner", Icons.Filled.Bluetooth),
    TopLevelDestination(Routes.HISTORY, "Riwayat", Icons.Filled.History),
)

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SCANNER,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.SCANNER) {
                ScannerRoute(onDeviceClick = { navController.navigate(Routes.radar(it)) })
            }
            composable(Routes.HISTORY) {
                HistoryRoute(onDeviceClick = { navController.navigate(Routes.radar(it)) })
            }
            composable(
                route = Routes.RADAR,
                arguments = listOf(navArgument(Routes.ARG_ADDRESS) { type = NavType.StringType }),
            ) {
                RadarRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
