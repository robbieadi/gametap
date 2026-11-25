package io.github.robbieadi.gametap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.robbieadi.gametap.ui.screens.EditorScreen
import io.github.robbieadi.gametap.ui.screens.GamepadScreen
import io.github.robbieadi.gametap.ui.theme.GametapTheme
import io.github.robbieadi.gametap.viewmodel.ConnectionViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private object Routes {
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

        // No start screen: opening the app starts the gamepad, asking for Bluetooth if needed.
        // Starting again, for example after the screen was swiped away, keeps a running gamepad.
        LaunchedEffect(connection) { connection.start() }

        NavHost(navController, startDestination = Routes.GAMEPAD) {
            composable(Routes.GAMEPAD) {
                GamepadScreen(
                    viewModel = koinViewModel(),
                    transportState = connection.state,
                    permissionDenied = connection.permissionDenied,
                    forgottenHost = connection.forgottenHost,
                    wifiClash = connection.wifiClash,
                    onDismissWifiClash = connection::dismissWifiClash,
                    onTryAgain = connection::start,
                    onEditLayout = { id -> navController.navigate(EditorRoute(id)) },
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
