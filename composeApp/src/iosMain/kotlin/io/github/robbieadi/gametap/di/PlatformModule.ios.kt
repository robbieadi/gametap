@file:OptIn(ExperimentalForeignApi::class)

package io.github.robbieadi.gametap.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.robbieadi.gametap.platform.BluetoothAccess
import io.github.robbieadi.gametap.platform.Haptics
import io.github.robbieadi.gametap.platform.HidTransport
import io.github.robbieadi.gametap.platform.IosBluetoothAccess
import io.github.robbieadi.gametap.platform.IosHaptics
import io.github.robbieadi.gametap.platform.IosHogpTransport
import io.github.robbieadi.gametap.platform.IosScreenControl
import io.github.robbieadi.gametap.platform.SETTINGS_FILE_NAME
import io.github.robbieadi.gametap.platform.ScreenControl
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
