package io.github.robbieadi.gametap.di

import android.content.Context
import androidx.datastore.core.DataStore
import io.github.robbieadi.gametap.layout.LAYOUT_FILE_NAME
import io.github.robbieadi.gametap.layout.LayoutStore
import io.github.robbieadi.gametap.layout.LayoutStoreQualifier
import io.github.robbieadi.gametap.layout.layoutDataStore
import io.github.robbieadi.gametap.platform.ActivityProvider
import io.github.robbieadi.gametap.platform.AndroidBluetoothAccess
import io.github.robbieadi.gametap.platform.AndroidHaptics
import io.github.robbieadi.gametap.platform.AndroidKeepAlive
import io.github.robbieadi.gametap.platform.GamepadKeepAlive
import io.github.robbieadi.gametap.platform.AndroidHogpTransport
import io.github.robbieadi.gametap.platform.AndroidMotionSensor
import io.github.robbieadi.gametap.platform.AndroidScreenControl
import io.github.robbieadi.gametap.platform.AndroidVolumeKeyInput
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.MotionSensor
import io.github.robbieadi.gametap.platform.ScreenControl
import io.github.robbieadi.gametap.platform.VolumeKeyInput
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { ActivityProvider() }
    single<HidTransport> { AndroidHogpTransport(get()) }
    single { AndroidBluetoothAccess(get()) } bind BluetoothAccess::class
    single<Haptics> { AndroidHaptics(get()) }
    single<ScreenControl> { AndroidScreenControl(get()) }
    single<MotionSensor> { AndroidMotionSensor(get(), get()) }
    single { AndroidVolumeKeyInput() } bind VolumeKeyInput::class
    single<GamepadKeepAlive> { AndroidKeepAlive(get()) }
    single<DataStore<LayoutStore>>(LayoutStoreQualifier) {
        val context = get<Context>()
        // In the folder DataStore uses.
        layoutDataStore(FileSystem.SYSTEM) { context.filesDir.resolve("datastore/$LAYOUT_FILE_NAME").toOkioPath() }
    }
}
