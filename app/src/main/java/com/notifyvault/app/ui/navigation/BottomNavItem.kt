package com.notifyvault.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(AppDestinations.HOME, "Home", Icons.Default.Home)
    data object Messages : BottomNavItem(AppDestinations.SAVED_MESSAGES, "Messages", Icons.Default.Message)
    data object Settings : BottomNavItem(AppDestinations.SETTINGS, "Settings", Icons.Default.Settings)
}
