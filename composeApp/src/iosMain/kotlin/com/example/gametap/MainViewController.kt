package com.example.gametap

import androidx.compose.ui.window.ComposeUIViewController
import com.example.gametap.di.commonModule
import com.example.gametap.di.platformModule
import com.example.gametap.ui.App
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
