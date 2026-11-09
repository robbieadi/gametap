package io.github.robbieadi.gametap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.ui.screens.ConnectScreen
import io.github.robbieadi.gametap.ui.screens.GamepadScreen
import io.github.robbieadi.gametap.ui.screens.SettingsScreen
import io.github.robbieadi.gametap.ui.theme.GametapTheme
import io.github.robbieadi.gametap.viewmodel.ConnectionViewModel
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

        // Lets the connection restart after Android drops the gamepad in the background.
        LifecycleStartEffect(connection) {
            connection.onAppVisible(true)
            onStopOrDispose { connection.onAppVisible(false) }
        }

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
