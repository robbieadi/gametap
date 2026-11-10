package io.github.robbieadi.gametap

import androidx.datastore.core.CorruptionException
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.DefaultLayout
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.LayoutStore
import io.github.robbieadi.gametap.layout.LayoutStoreSerializer
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.Shape
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayoutFileTest {
    private val menu = MenuControl("menu", Position(Anchor.LEFT, 0.1f, 0.1f), Shape.Circle(0.05f))
    private val racing = LayoutProfile("racing", "Racing", listOf(menu))

    private suspend fun read(json: String): LayoutStore = LayoutStoreSerializer.readFrom(Buffer().writeUtf8(json))

    @Test
    fun writtenStoreReadsBack() = runTest {
        val store = LayoutStore(activeProfileId = racing.id, profiles = listOf(racing))
        val buffer = Buffer()
        LayoutStoreSerializer.writeTo(store, buffer)
        assertEquals(store, LayoutStoreSerializer.readFrom(buffer))
    }

    /** For example a control type added in a later version, after the app is downgraded. */
    @Test
    fun unreadableProfileIsDroppedAndTheRestKept() = runTest {
        val store = read(
            """
            {"version":1,"activeProfileId":"racing","profiles":[
              {"id":"future","name":"Future","controls":[{"type":"gyro","id":"g"}]},
              {"id":"racing","name":"Racing","controls":[
                {"type":"menu","id":"menu","position":{"anchor":"left","x":0.1,"y":0.1},
                 "shape":{"type":"circle","r":0.05},"label":"☰"}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(LayoutStore(activeProfileId = "racing", profiles = listOf(racing)), store)
    }

    @Test
    fun missingFieldsFallBackToDefaults() = runTest {
        assertEquals(LayoutStore(activeProfileId = DefaultLayout.PROFILE_ID, profiles = emptyList()), read("{}"))
    }

    @Test
    fun fileThatIsNotJsonIsCorrupt() = runTest {
        assertFailsWith<CorruptionException> { read("not json") }
        assertFailsWith<CorruptionException> { read("") }
        assertFailsWith<CorruptionException> { read("[1, 2]") }
    }
}
