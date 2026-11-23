package io.github.robbieadi.gametap.di

import io.github.robbieadi.gametap.input.InputStateHolder
import io.github.robbieadi.gametap.input.ReportSender
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.layout.LayoutStoreQualifier
import io.github.robbieadi.gametap.platform.ScreenModeLock
import io.github.robbieadi.gametap.session.GamepadSession
import io.github.robbieadi.gametap.viewmodel.ConnectionViewModel
import io.github.robbieadi.gametap.viewmodel.EditorViewModel
import io.github.robbieadi.gametap.viewmodel.GamepadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Provides HidTransport, Haptics, ScreenControl, BluetoothAccess, MotionSensor, VolumeKeyInput,
 * GamepadKeepAlive and,
 * under [LayoutStoreQualifier], the layout DataStore.
 */
expect fun platformModule(): Module

val commonModule: Module = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { InputStateHolder() }
    single { ScreenModeLock(get()) }
    single { LayoutRepository(get(LayoutStoreQualifier), get()) }
    single {
        val layouts = get<LayoutRepository>()
        ReportSender(get(), get(), stickCurve = { side -> layouts.layouts.value.active.stickCurve(side) })
    }
    single { GamepadSession(get(), get(), get(), get()) }
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::GamepadViewModel)
    viewModel { params -> EditorViewModel(params.get(), get(), get(), get(), get(), get()) }
}
