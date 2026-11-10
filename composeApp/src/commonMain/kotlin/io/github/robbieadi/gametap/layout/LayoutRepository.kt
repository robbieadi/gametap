package io.github.robbieadi.gametap.layout

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Every profile, the built-in [DefaultLayout] first, and the one in use. */
data class Layouts(val profiles: List<LayoutProfile>, val active: LayoutProfile) {
    fun find(id: String): LayoutProfile? = profiles.firstOrNull { it.id == id }
}

/**
 * Stores the user's profiles. [DefaultLayout] is never written to the file, so an app update can
 * improve it and the user can always fall back to it.
 *
 * Screen-dependent checks (overlap, off screen) are the editor's job before calling [save]: this
 * class does not know the screen size.
 */
class LayoutRepository(
    private val store: DataStore<LayoutStore>,
    scope: CoroutineScope,
    private val newId: () -> String = ::randomId,
) {
    val layouts: StateFlow<Layouts> = store.data
        .map { it.toLayouts() }
        .stateIn(scope, SharingStarted.Eagerly, LayoutStore(activeProfileId = DefaultLayout.PROFILE_ID, profiles = emptyList()).toLayouts())

    /** Ignored if no profile has [id]. */
    suspend fun select(id: String) {
        store.updateData { stored ->
            if (stored.toLayouts().find(id) == null) stored else stored.copy(activeProfileId = id)
        }
    }

    /** Adds [profile], or replaces the stored profile with the same id. */
    suspend fun save(profile: LayoutProfile) {
        require(profile.id != DefaultLayout.PROFILE_ID) { "The default layout is read-only" }
        store.updateData { stored ->
            val profiles = if (stored.profiles.any { it.id == profile.id }) {
                stored.profiles.map { if (it.id == profile.id) profile else it }
            } else {
                stored.profiles + profile
            }
            stored.copy(profiles = profiles)
        }
    }

    /**
     * Stores a copy of the profile with [sourceId] under a free name ("Default copy", "Default copy 2"...)
     * and returns it, or returns null if there is no such profile. Control ids only need to be
     * unique within a profile, so the copy keeps them.
     */
    suspend fun duplicate(sourceId: String): LayoutProfile? {
        var copy: LayoutProfile? = null
        store.updateData { stored ->
            val layouts = stored.toLayouts()
            val source = layouts.find(sourceId) ?: return@updateData stored
            layouts.copyOf(source)
                .also { copy = it }
                .let { stored.copy(profiles = stored.profiles + it) }
        }
        return copy
    }

    private fun Layouts.copyOf(source: LayoutProfile): LayoutProfile {
        val names = profiles.map { it.name }.toSet()
        val base = "${source.name} copy"
        val name = generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base $it" }
            .first { it !in names }
        return source.copy(id = newId(), name = name)
    }

    /** Removes the profile with [id]. If it was in use, the default takes over. */
    suspend fun delete(id: String) {
        require(id != DefaultLayout.PROFILE_ID) { "The default layout cannot be deleted" }
        store.updateData { stored ->
            stored.copy(
                profiles = stored.profiles.filterNot { it.id == id },
                activeProfileId = if (stored.activeProfileId == id) DefaultLayout.PROFILE_ID else stored.activeProfileId,
            )
        }
    }
}

private fun LayoutStore.toLayouts(): Layouts {
    val all = listOf(DefaultLayout.profile) +
        profiles.filter { it.id != DefaultLayout.PROFILE_ID }.map { it.withExactlyOneMenu() }
    return Layouts(all, all.firstOrNull { it.id == activeProfileId } ?: DefaultLayout.profile)
}

/**
 * Without a menu control the player cannot leave the gamepad screen, so a profile that lost it
 * (a bug, an edited file) gets the default one back. It goes last so it is drawn on top and wins
 * any touch it overlaps; the screen then flags the overlap. Extra menus are dropped.
 */
internal fun LayoutProfile.withExactlyOneMenu(): LayoutProfile {
    val menus = controls.filterIsInstance<GamepadControl.MenuControl>()
    return when {
        menus.size == 1 -> this
        menus.size > 1 -> copy(controls = controls.filter { it !is GamepadControl.MenuControl || it === menus.first() })
        else -> {
            val template = DefaultLayout.profile.controls.filterIsInstance<GamepadControl.MenuControl>().single()
            val taken = controls.map { it.id }.toSet()
            val id = generateSequence(0) { it + 1 }
                .map { if (it == 0) template.id else "${template.id}_$it" }
                .first { it !in taken }
            copy(controls = controls + template.copy(id = id))
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
