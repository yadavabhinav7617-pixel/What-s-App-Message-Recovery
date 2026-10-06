package com.notifyvault.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notifyvault.app.ui.screens.BrowserScreen
import com.notifyvault.app.ui.screens.DetailScreen
import com.notifyvault.app.ui.screens.HomeScreen
import com.notifyvault.app.ui.screens.PrivacyScreen
import com.notifyvault.app.ui.screens.SavedMessagesScreen
import com.notifyvault.app.ui.screens.SettingsScreen
import com.notifyvault.app.ui.theme.AppThemeMode
import com.notifyvault.app.ui.viewmodel.MessageViewModel

object AppDestinations {
    const val CALCULATOR = "calculator"
    const val LOGIN = "login"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val HOME = "home"
    const val SAVED_MESSAGES = "saved_messages"
    const val BROWSER = "browser"
    const val MESSAGE_DETAILS = "message_details/{messageId}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun messageDetailRoute(messageId: Long): String = "message_details/$messageId"
}

@Composable
fun AppNavGraph(
    viewModel: MessageViewModel,
    navController: NavHostController = rememberNavController(),
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(
        AppDestinations.HOME,
        AppDestinations.SAVED_MESSAGES,
        AppDestinations.BROWSER,
        AppDestinations.SETTINGS
    ) && !uiState.isAdmin

    // Global Guard: Force navigation to Login if the user is not logged in.
    LaunchedEffect(uiState.isUserLoggedIn, currentRoute) {
        if (!uiState.isUserLoggedIn && currentRoute != null && currentRoute != AppDestinations.LOGIN && currentRoute != AppDestinations.CALCULATOR) {
            navController.navigate(AppDestinations.LOGIN) {
                popUpTo(0)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    listOf(
                        BottomNavItem.Home,
                        BottomNavItem.Messages,
                        BottomNavItem.Browse,
                        BottomNavItem.Settings
                    ).forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
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
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { androidx.compose.material3.Text(item.title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = AppDestinations.CALCULATOR,
            modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues)
        ) {
            composable(AppDestinations.CALCULATOR) {
                com.notifyvault.app.ui.screens.CalculatorScreen(
                    onUnlock = {
                        val nextScreen = if (uiState.isUserLoggedIn) {
                            if (uiState.isAdmin) AppDestinations.ADMIN_DASHBOARD else AppDestinations.HOME
                        } else AppDestinations.LOGIN
                        
                        navController.navigate(nextScreen) {
                            popUpTo(AppDestinations.CALCULATOR) { inclusive = true }
                        }
                    }
                )
            }
            composable(AppDestinations.LOGIN) {
                com.notifyvault.app.ui.screens.LoginScreen(viewModel = viewModel, navController = navController)
            }
            composable(AppDestinations.ADMIN_DASHBOARD) {
                com.notifyvault.app.ui.screens.AdminDashboardScreen(viewModel = viewModel, navController = navController)
            }
            composable(AppDestinations.HOME) {
                HomeScreen(viewModel = viewModel, navController = navController)
            }
            composable(AppDestinations.SAVED_MESSAGES) {
                SavedMessagesScreen(viewModel = viewModel, navController = navController)
            }
            composable(AppDestinations.BROWSER) {
                BrowserScreen(viewModel = viewModel)
            }
            composable(AppDestinations.MESSAGE_DETAILS) { backStackEntry ->
                val messageId = backStackEntry.arguments?.getString("messageId")?.toLongOrNull()
                val message = messageId?.let { viewModel.getMessageById(it) }
                DetailScreen(message = message, navController = navController)
            }
            composable(AppDestinations.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    navController = navController,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
            composable(AppDestinations.PRIVACY) {
                PrivacyScreen(navController = navController)
            }
        }
    }
}
