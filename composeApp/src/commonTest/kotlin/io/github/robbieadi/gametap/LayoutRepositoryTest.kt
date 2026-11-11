@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.DefaultLayout
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.layout.LayoutStore
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.Shape
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class LayoutRepositoryTest {
    private val menu = MenuControl("menu", Position(Anchor.LEFT, 0.1f, 0.1f), Shape.Circle(0.05f))
    private val jump = ButtonControl("jump", Position(Anchor.RIGHT, 0.3f, 0.5f), Shape.Circle(0.1f), ButtonAction.Pad(Button.A), "🦘")
    private val racing = LayoutProfile("racing", "Racing", listOf(menu, jump))

    private fun store(vararg profiles: LayoutProfile, active: String = DefaultLayout.PROFILE_ID) =
        InMemoryDataStore(LayoutStore(activeProfileId = active, profiles = profiles.toList()))

    private fun TestScope.repository(store: InMemoryDataStore<LayoutStore>): LayoutRepository {
        var next = 0
        return LayoutRepository(store, backgroundScope, newId = { "id${next++}" }).also { runCurrent() }
    }

    @Test
    fun emptyStoreOffersOnlyTheDefault() = runTest {
        val layouts = repository(store()).layouts.value
        assertEquals(listOf(DefaultLayout.profile), layouts.profiles)
        assertEquals(DefaultLayout.profile, layouts.active)
    }

    @Test
    fun saveAddsThenReplaces() = runTest {
        val store = store()
        val repository = repository(store)

        repository.save(racing)
        val renamed = racing.copy(name = "Racing 2")
        repository.save(renamed)
        runCurrent()

        assertEquals(listOf(renamed), store.value.profiles)
        assertEquals(listOf(DefaultLayout.profile, renamed), repository.layouts.value.profiles)
    }

    @Test
    fun defaultIsReadOnly() = runTest {
        val repository = repository(store())
        assertFailsWith<IllegalArgumentException> { repository.save(DefaultLayout.profile.copy(name = "Mine")) }
        assertFailsWith<IllegalArgumentException> { repository.delete(DefaultLayout.PROFILE_ID) }
    }

    @Test
    fun selectSwitchesOnlyToProfilesThatExist() = runTest {
        val repository = repository(store(racing))

        repository.select("missing")
        runCurrent()
        assertEquals(DefaultLayout.profile, repository.layouts.value.active)

        repository.select(racing.id)
        runCurrent()
        assertEquals(racing, repository.layouts.value.active)
    }

    @Test
    fun duplicatePicksAFreeNameAndANewId() = runTest {
        val store = store()
        val repository = repository(store)

        val first = assertNotNull(repository.duplicate(DefaultLayout.PROFILE_ID))
        val second = assertNotNull(repository.duplicate(DefaultLayout.PROFILE_ID))

        assertEquals("Default copy" to "id0", first.name to first.id)
        assertEquals("Default copy 2" to "id1", second.name to second.id)
        assertEquals(DefaultLayout.profile.controls, first.controls)
        assertEquals(listOf(first, second), store.value.profiles)
    }

    @Test
    fun duplicateOfAMissingProfileDoesNothing() = runTest {
        val store = store()
        assertEquals(null, repository(store).duplicate("missing"))
        assertEquals(emptyList(), store.value.profiles)
    }

    @Test
    fun deletingTheActiveProfileFallsBackToTheDefault() = runTest {
        val store = store(racing, active = racing.id)
        val repository = repository(store)

        repository.delete(racing.id)
        runCurrent()

        assertEquals(DefaultLayout.PROFILE_ID, store.value.activeProfileId)
        assertEquals(DefaultLayout.profile, repository.layouts.value.active)
    }

    @Test
    fun unknownActiveIdFallsBackToTheDefault() = runTest {
        val layouts = repository(store(racing, active = "gone")).layouts.value
        assertEquals(DefaultLayout.profile, layouts.active)
    }

    @Test
    fun storedProfileCannotShadowTheDefault() = runTest {
        val impostor = racing.copy(id = DefaultLayout.PROFILE_ID)
        val layouts = repository(store(impostor)).layouts.value
        assertEquals(listOf(DefaultLayout.profile), layouts.profiles)
    }

    @Test
    fun profileWithoutAMenuGetsOneOnTop() = runTest {
        val noMenu = racing.copy(controls = listOf(jump, jump.copy(id = "menu")))
        val loaded = repository(store(noMenu)).layouts.value.find(racing.id)

        val added = assertNotNull(loaded).controls.last()
        assertEquals(MenuControl::class, added::class)
        assertEquals("menu_1", added.id, "must not reuse an id already in the profile")
    }

    @Test
    fun extraMenusAreDropped() = runTest {
        val twoMenus = racing.copy(controls = listOf(menu, jump, menu.copy(id = "menu2")))
        val loaded = repository(store(twoMenus)).layouts.value.find(racing.id)
        assertEquals(listOf(menu, jump), assertNotNull(loaded).controls)
    }

    @Test
    fun draftCopyIsNotStored() = runTest {
        val store = store()
        val copy = assertNotNull(repository(store).draftCopy(DefaultLayout.PROFILE_ID))
        assertEquals("Default copy", copy.name)
        assertEquals(emptyList(), store.value.profiles)
    }

    @Test
    fun renameChangesOnlyTheName() = runTest {
        val store = store(racing)
        val repository = repository(store)
        repository.rename(racing.id, "Drift")
        assertEquals(listOf(racing.copy(name = "Drift")), store.value.profiles)
        assertFailsWith<IllegalArgumentException> { repository.rename(DefaultLayout.PROFILE_ID, "Mine") }
    }
}
