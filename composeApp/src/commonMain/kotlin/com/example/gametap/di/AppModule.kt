package com.example.gametap.di

import com.example.gametap.input.InputStateHolder
import com.example.gametap.input.ReportSender
import com.example.gametap.platform.SettingsRepository
import com.example.gametap.viewmodel.ConnectionViewModel
import com.example.gametap.viewmodel.GamepadViewModel
import com.example.gametap.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Provides HidTransport, Haptics, ScreenControl, BluetoothAccess and the settings DataStore. */
expect fun platformModule(): Module

val commonModule: Module = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { InputStateHolder() }
    single { SettingsRepository(get(), get()) }
    single { ReportSender(get(), get(), get<SettingsRepository>().settings) }
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::GamepadViewModel)
    viewModelOf(::SettingsViewModel)
}
