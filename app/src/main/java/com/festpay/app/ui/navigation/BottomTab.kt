// File: BottomTab.kt
package com.festpay.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import com.festpay.app.ui.theme.V4SHColors

/**
 * Enum representing the four locked bottom‑tab destinations.
 * The `route` values must match the screen ids defined in screens.json.
 */
enum class BottomTab(val route: String, val label: String) {
    HOME("home", "Home"),
    TRANSACTIONS("transactions", "Transactions"),
    RECEIVE("receive_money", "Receive"),
    PROFILE("profile", "Profile")
}

@Composable
fun V4SHBottomNavigation(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Only show bottom navigation on top-level main app shell destinations
    if (currentRoute in listOf("home", "transactions", "receive_money", "profile")) {
        NavigationBar(
            containerColor = V4SHColors.Primary,
            contentColor = V4SHColors.Tertiary
        ) {
            BottomTab.values().forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = {
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                // Pop up to the start destination of the graph to avoid building up a large stack
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        val icon = when (tab) {
                            BottomTab.HOME -> Icons.Default.Home
                            BottomTab.TRANSACTIONS -> Icons.AutoMirrored.Filled.ReceiptLong
                            BottomTab.RECEIVE -> Icons.Default.QrCodeScanner
                            BottomTab.PROFILE -> Icons.Default.Person
                        }
                        Icon(imageVector = icon, contentDescription = tab.label)
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = V4SHColors.Tertiary,
                        selectedTextColor = V4SHColors.Tertiary,
                        unselectedIconColor = V4SHColors.Tertiary.copy(alpha = 0.6f),
                        unselectedTextColor = V4SHColors.Tertiary.copy(alpha = 0.6f),
                        indicatorColor = V4SHColors.Primary.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}
