package io.github.robbieadi.gametap.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.robbieadi.gametap.platform.ActivityProvider
import io.github.robbieadi.gametap.platform.AndroidBluetoothAccess
import io.github.robbieadi.gametap.platform.AndroidHaptics
import io.github.robbieadi.gametap.platform.AndroidHidTransport
import io.github.robbieadi.gametap.platform.AndroidScreenControl
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.SETTINGS_FILE_NAME
import io.github.robbieadi.gametap.platform.ScreenControl
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { ActivityProvider() }
    single<HidTransport> { AndroidHidTransport(get()) }
    single { AndroidBluetoothAccess(get()) } bind BluetoothAccess::class
    single<Haptics> { AndroidHaptics(get()) }
    single<ScreenControl> { AndroidScreenControl(get()) }
    single<DataStore<Preferences>> {
        val context = get<Context>()
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE_NAME.removeSuffix(".preferences_pb")) }
    }
}
