@file:OptIn(ExperimentalForeignApi::class)

package io.github.robbieadi.gametap.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.robbieadi.gametap.layout.LAYOUT_FILE_NAME
import io.github.robbieadi.gametap.layout.LayoutStore
import io.github.robbieadi.gametap.layout.LayoutStoreQualifier
import io.github.robbieadi.gametap.layout.layoutDataStore
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
import okio.FileSystem
import okio.Path
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
        PreferenceDataStoreFactory.createWithPath { documentsFile(SETTINGS_FILE_NAME) }
    }
    single<DataStore<LayoutStore>>(LayoutStoreQualifier) {
        layoutDataStore(FileSystem.SYSTEM) { documentsFile(LAYOUT_FILE_NAME) }
    }
}

private fun documentsFile(name: String): Path {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return "${requireNotNull(documents?.path)}/$name".toPath()
}
