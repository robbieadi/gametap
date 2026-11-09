package io.github.robbieadi.gametap

import androidx.compose.ui.window.ComposeUIViewController
import io.github.robbieadi.gametap.di.commonModule
import io.github.robbieadi.gametap.di.platformModule
import io.github.robbieadi.gametap.ui.App
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

private val koin by lazy {
    startKoin { modules(commonModule, platformModule()) }
}

@Suppress("FunctionName", "unused") // Called from Swift.
fun MainViewController(): UIViewController {
    koin
    return ComposeUIViewController { App() }
}
