package io.github.robbieadi.gametap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.robbieadi.gametap.platform.TransportState
import io.github.robbieadi.gametap.ui.screens.ConnectScreen
import io.github.robbieadi.gametap.ui.screens.EditorScreen
import io.github.robbieadi.gametap.ui.screens.GamepadScreen
import io.github.robbieadi.gametap.ui.theme.GametapTheme
import io.github.robbieadi.gametap.viewmodel.ConnectionViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private object Routes {
    const val CONNECT = "connect"
    const val GAMEPAD = "gamepad"
}

/** The layout editor, opened on one profile. */
@Serializable
data class EditorRoute(val profileId: String)

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
                )
            }
            composable(Routes.GAMEPAD) {
                GamepadScreen(
                    viewModel = koinViewModel(),
                    transportState = connection.state,
                    onEditLayout = { id -> navController.navigate(EditorRoute(id)) },
                    onLeave = { disconnect ->
                        if (disconnect) connection.stop()
                        navController.popBackStack(Routes.CONNECT, inclusive = false)
                    },
                )
            }
            composable<EditorRoute> { entry ->
                val route = entry.toRoute<EditorRoute>()
                EditorScreen(
                    viewModel = koinViewModel { parametersOf(route.profileId) },
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
