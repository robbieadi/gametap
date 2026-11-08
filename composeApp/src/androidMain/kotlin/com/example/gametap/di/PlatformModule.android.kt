package com.example.gametap.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.gametap.platform.ActivityProvider
import com.example.gametap.platform.AndroidBluetoothAccess
import com.example.gametap.platform.AndroidHaptics
import com.example.gametap.platform.AndroidHidTransport
import com.example.gametap.platform.AndroidScreenControl
import com.example.gametap.platform.BluetoothAccess
import com.example.gametap.platform.Haptics
import com.example.gametap.platform.HidTransport
import com.example.gametap.platform.SETTINGS_FILE_NAME
import com.example.gametap.platform.ScreenControl
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
