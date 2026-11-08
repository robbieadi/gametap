package com.example.gametap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gametap.platform.TransportState
import com.example.gametap.ui.screens.ConnectScreen
import com.example.gametap.ui.screens.GamepadScreen
import com.example.gametap.ui.screens.SettingsScreen
import com.example.gametap.ui.theme.GametapTheme
import com.example.gametap.viewmodel.ConnectionViewModel
import org.koin.compose.viewmodel.koinViewModel

private object Routes {
    const val CONNECT = "connect"
    const val GAMEPAD = "gamepad"
    const val SETTINGS = "settings"
}

@Composable
fun App() {
    GametapTheme {
        val navController = rememberNavController()
        // Created outside the NavHost so the connection and sender outlive any one screen.
        val connection = koinViewModel<ConnectionViewModel>()

        // Lives here, not in ConnectScreen, so going back while connected does not bounce forward.
        val state by connection.state.collectAsState()
        val isConnected = state is TransportState.Connected
        LaunchedEffect(isConnected) {
            if (isConnected) navController.navigate(Routes.GAMEPAD) { launchSingleTop = true }
        }

        NavHost(navController, startDestination = Routes.CONNECT) {
            composable(Routes.CONNECT) {
                ConnectScreen(
                    viewModel = connection,
                    onOpenGamepad = {
                        navController.navigate(Routes.GAMEPAD) { launchSingleTop = true }
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.GAMEPAD) {
                GamepadScreen(
                    viewModel = koinViewModel(),
                    transportState = connection.state,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = koinViewModel(), onBack = { navController.popBackStack() })
            }
        }
    }
}
