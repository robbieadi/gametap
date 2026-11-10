package io.github.robbieadi.gametap.layout

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import org.koin.core.qualifier.named

const val LAYOUT_FILE_NAME = "layouts.json"

/** Koin keys definitions by class alone, so this tells the layout DataStore from the settings one. */
val LayoutStoreQualifier = named("layouts")

fun layoutDataStore(fileSystem: FileSystem, path: () -> Path): DataStore<LayoutStore> = DataStoreFactory.create(
    storage = OkioStorage(fileSystem, LayoutStoreSerializer, producePath = path),
    // Only reached when the file as a whole cannot be read. A single bad profile is dropped instead.
    corruptionHandler = ReplaceFileCorruptionHandler { LayoutStoreSerializer.defaultValue },
)

/**
 * Reads each profile on its own, so a profile this version cannot read (written by a newer one,
 * say) is dropped without losing the others.
 */
internal object LayoutStoreSerializer : OkioSerializer<LayoutStore> {
    override val defaultValue: LayoutStore =
        LayoutStore(activeProfileId = DefaultLayout.PROFILE_ID, profiles = emptyList())

    override suspend fun readFrom(source: BufferedSource): LayoutStore {
        val root = try {
            LayoutJson.parseToJsonElement(source.readUtf8()).jsonObject
        } catch (e: SerializationException) {
            throw CorruptionException("Layout file is not valid JSON", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Layout file is not a JSON object", e)
        }
        val profiles = runCatchingFormat { root["profiles"]?.jsonArray }.orEmpty().mapNotNull { element ->
            runCatchingFormat { LayoutJson.decodeFromJsonElement(LayoutProfile.serializer(), element) }
        }
        return LayoutStore(
            version = runCatchingFormat { root["version"]?.jsonPrimitive?.intOrNull } ?: LayoutStore.CURRENT_VERSION,
            activeProfileId = runCatchingFormat { root["activeProfileId"]?.jsonPrimitive?.contentOrNull }
                ?: DefaultLayout.PROFILE_ID,
            profiles = profiles,
        )
    }

    override suspend fun writeTo(t: LayoutStore, sink: BufferedSink) {
        sink.writeUtf8(LayoutJson.encodeToString(LayoutStore.serializer(), t))
    }

    /** Null when the JSON has the wrong shape. Both exception types mean that here. */
    private inline fun <T> runCatchingFormat(block: () -> T): T? = try {
        block()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
