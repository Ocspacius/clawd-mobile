package com.clawd.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clawd.mobile.ui.approval.ApprovalDialog
import com.clawd.mobile.ui.approval.ApprovalViewModel
import com.clawd.mobile.ui.sessions.SessionsScreen
import com.clawd.mobile.ui.settings.SettingsScreen
import com.clawd.mobile.ui.theme.ClawdAccent
import com.clawd.mobile.ui.theme.ClawdCard
import com.clawd.mobile.ui.theme.ClawdMuted
import com.clawd.mobile.ui.theme.ClawdSubtle

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val navItems = listOf(
    NavItem("sessions", "会话", Icons.Rounded.ViewList),
    NavItem("settings", "设置", Icons.Rounded.Settings)
)

@Composable
fun ClawdNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Approval dialog state
    val approvalViewModel: ApprovalViewModel = hiltViewModel()
    val pendingApproval by approvalViewModel.pendingApproval.collectAsState()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar(
                containerColor = ClawdCard
            ) {
                navItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ClawdAccent,
                            selectedTextColor = ClawdAccent,
                            unselectedIconColor = ClawdSubtle,
                            unselectedTextColor = ClawdMuted,
                            indicatorColor = ClawdAccent.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "sessions",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("sessions") {
                SessionsScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }

    // ── Approval dialog overlay ──
    // Shown when a remote approval request arrives from the desktop.
    val request = pendingApproval
    if (request != null) {
        ApprovalDialog(
            request = request,
            onApprove = { approvalViewModel.approve() },
            onDeny = { approvalViewModel.deny("User denied from mobile") },
            onDismiss = { approvalViewModel.dismiss() }
        )
    }
}
