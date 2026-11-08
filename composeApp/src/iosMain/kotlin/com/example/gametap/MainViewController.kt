package com.example.gametap

import androidx.compose.ui.window.ComposeUIViewController
import com.example.gametap.ui.App
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused") // Called from Swift.
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
