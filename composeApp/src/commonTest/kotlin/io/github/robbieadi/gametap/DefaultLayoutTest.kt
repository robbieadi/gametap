package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.layout.DefaultLayout
import io.github.robbieadi.gametap.layout.LayoutJson
import io.github.robbieadi.gametap.layout.LayoutStore
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.validate
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultLayoutTest {
    /** 16:10 tablets up to 21:9 phones. */
    private val aspects = listOf(16f / 10f, 16f / 9f, 18f / 9f, 19.5f / 9f, 20f / 9f, 21f / 9f)

    @Test
    fun defaultIsValidOnEverySupportedAspectRatio() {
        for (aspect in aspects) {
            val screen = ScreenSize(width = aspect * 1000f, height = 1000f)
            assertEquals(emptyList(), DefaultLayout.profile.validate(screen), "at aspect $aspect")
        }
    }

    @Test
    fun defaultSurvivesAJsonRoundTrip() {
        val store = LayoutStore(activeProfileId = DefaultLayout.PROFILE_ID, profiles = listOf(DefaultLayout.profile))
        val json = LayoutJson.encodeToString(LayoutStore.serializer(), store)
        assertEquals(store, LayoutJson.decodeFromString(LayoutStore.serializer(), json))
    }
}
