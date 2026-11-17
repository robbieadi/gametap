package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import androidx.datastore.core.CorruptionException
import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.DefaultLayout
import io.github.robbieadi.gametap.layout.GyroAxis
import io.github.robbieadi.gametap.layout.GyroBinding
import io.github.robbieadi.gametap.layout.GyroButton
import io.github.robbieadi.gametap.layout.GyroHorizontal
import io.github.robbieadi.gametap.layout.GyroSetup
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.LayoutStore
import io.github.robbieadi.gametap.layout.LayoutStoreSerializer
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.VolumeKeys
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayoutFileTest {
    private val racing = LayoutProfile("racing", "Racing", emptyList())

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

    @Test
    fun gyroSetupReadsBack() = runTest {
        val gyro = GyroSetup(
            bindings = listOf(
                GyroBinding.StickBinding(Side.RIGHT, horizontal = true, vertical = false, range = 40f, invertVertical = true),
                GyroBinding.TriggerBinding(Side.LEFT, GyroAxis.VERTICAL, invert = true, range = 20f),
            ),
            horizontal = GyroHorizontal.YAW,
            deadzone = 2f,
        )
        val store = LayoutStore(activeProfileId = racing.id, profiles = listOf(racing.copy(gyro = gyro)))
        val buffer = Buffer()
        LayoutStoreSerializer.writeTo(store, buffer)
        assertEquals(store, LayoutStoreSerializer.readFrom(buffer))
    }

    /** Written before gyro existed. */
    @Test
    fun profileWithoutGyroReadsWithNoBindings() = runTest {
        val store = read(
            """
            {"version":1,"activeProfileId":"racing","profiles":[
              {"id":"racing","name":"Racing","controls":[
                {"type":"menu","id":"menu","position":{"anchor":"left","x":0.1,"y":0.1},
                 "shape":{"type":"circle","r":0.05},"label":"☰"}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(GyroSetup(), store.profiles.single().gyro)
    }

    @Test
    fun volumeKeysReadBack() = runTest {
        val keys = VolumeKeys(up = ButtonAction.Pad(Button.RB), down = ButtonAction.Gyro(GyroButton.HOLD))
        val store = LayoutStore(activeProfileId = racing.id, profiles = listOf(racing.copy(volumeKeys = keys)))
        val buffer = Buffer()
        LayoutStoreSerializer.writeTo(store, buffer)
        assertEquals(store, LayoutStoreSerializer.readFrom(buffer))
    }

    /** Written before volume keys existed. */
    @Test
    fun profileWithoutVolumeKeysReadsUnbound() = runTest {
        val store = read(
            """
            {"version":1,"activeProfileId":"racing","profiles":[
              {"id":"racing","name":"Racing","controls":[
                {"type":"menu","id":"menu","position":{"anchor":"left","x":0.1,"y":0.1},
                 "shape":{"type":"circle","r":0.05},"label":"☰"}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(VolumeKeys(), store.profiles.single().volumeKeys)
    }

    @Test
    fun stickTuningAndHapticsReadBack() = runTest {
        val stick = StickControl(
            "ls", Position(Anchor.LEFT, 0.4f, 0.5f), Side.LEFT, 0.15f, 0.2f,
            deadzone = 0.2f, curveExponent = 2.5f, floating = true,
        )
        val store = LayoutStore(activeProfileId = racing.id, profiles = listOf(racing.copy(controls = listOf(stick), haptics = false)))
        val buffer = Buffer()
        LayoutStoreSerializer.writeTo(store, buffer)
        assertEquals(store, LayoutStoreSerializer.readFrom(buffer))
    }

    /** Written before sticks and haptics were set per layout. */
    @Test
    fun stickWithoutTuningReadsDefaults() = runTest {
        val store = read(
            """
            {"version":1,"activeProfileId":"racing","profiles":[
              {"id":"racing","name":"Racing","controls":[
                {"type":"stick","id":"ls","position":{"anchor":"left","x":0.4,"y":0.5},
                 "side":"left","radius":0.15,"grabRadius":0.2}]}
            ]}
            """.trimIndent(),
        )
        val profile = store.profiles.single()
        val stick = profile.controls.single() as StickControl
        assertEquals(StickControl.DEFAULT_DEADZONE, stick.deadzone)
        assertEquals(1f, stick.curveExponent)
        assertEquals(false, stick.floating)
        assertEquals(true, profile.haptics)
    }

    /** Written when a menu control opened the menu, before the status pill did. */
    @Test
    fun oldMenuControlIsDroppedAndTheProfileKept() = runTest {
        val store = read(
            """
            {"version":1,"activeProfileId":"racing","profiles":[
              {"id":"racing","name":"Racing","controls":[
                {"type":"menu","id":"menu","position":{"anchor":"left","x":0.1,"y":0.1},
                 "shape":{"type":"circle","r":0.05},"label":"☰"},
                {"type":"dpad","id":"dpad","position":{"anchor":"center","x":0.0,"y":0.5},"radius":0.17}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("dpad"), store.profiles.single().controls.map { it.id })
    }
}
