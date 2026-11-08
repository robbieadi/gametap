@file:OptIn(ExperimentalForeignApi::class)

package com.example.gametap.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.gametap.platform.BluetoothAccess
import com.example.gametap.platform.Haptics
import com.example.gametap.platform.HidTransport
import com.example.gametap.platform.IosBluetoothAccess
import com.example.gametap.platform.IosHaptics
import com.example.gametap.platform.IosHogpTransport
import com.example.gametap.platform.IosScreenControl
import com.example.gametap.platform.SETTINGS_FILE_NAME
import com.example.gametap.platform.ScreenControl
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun platformModule(): Module = module {
    single<HidTransport> { IosHogpTransport() }
    single<BluetoothAccess> { IosBluetoothAccess() }
    single<Haptics> { IosHaptics() }
    single<ScreenControl> { IosScreenControl() }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath {
            val documents = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )
            "${requireNotNull(documents?.path)}/$SETTINGS_FILE_NAME".toPath()
        }
    }
}
