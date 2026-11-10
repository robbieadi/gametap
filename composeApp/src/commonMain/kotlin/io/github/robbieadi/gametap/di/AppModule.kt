package io.github.robbieadi.gametap.di

import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.layout.LayoutStoreQualifier
import io.github.robbieadi.gametap.platform.SettingsRepository
import io.github.robbieadi.gametap.viewmodel.ConnectionViewModel
import io.github.robbieadi.gametap.viewmodel.GamepadViewModel
import io.github.robbieadi.gametap.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Provides HidTransport, Haptics, ScreenControl, BluetoothAccess, the settings DataStore and,
 * under [LayoutStoreQualifier], the layout DataStore.
 */
expect fun platformModule(): Module

val commonModule: Module = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { InputStateHolder() }
    single { SettingsRepository(get(), get()) }
    single { LayoutRepository(get(LayoutStoreQualifier), get()) }
    single { ReportSender(get(), get(), get<SettingsRepository>().settings) }
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::GamepadViewModel)
    viewModelOf(::SettingsViewModel)
}
