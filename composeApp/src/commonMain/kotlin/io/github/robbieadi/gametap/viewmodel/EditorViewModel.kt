package io.github.robbieadi.gametap.viewmodel

import androidx.lifecycle.ViewModel
import io.github.robbieadi.gametap.layout.DefaultLayout
import io.github.robbieadi.gametap.layout.EditorMetrics
import io.github.robbieadi.gametap.layout.LayoutEditor
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.LayoutRepository
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.platform.ScreenModeLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Holds one editing session, so the draft and its undo history survive configuration changes.
 * Editing the read-only default starts from an unsaved copy.
 */
class EditorViewModel(
    profileId: String,
    private val repository: LayoutRepository,
    private val screenMode: ScreenModeLock,
) : ViewModel() {
    private val source: LayoutProfile
    private val isNewCopy: Boolean

    init {
        val existing = repository.layouts.value.find(profileId)
        if (existing == null || existing.id == DefaultLayout.PROFILE_ID) {
            source = requireNotNull(repository.draftCopy(existing?.id ?: DefaultLayout.PROFILE_ID))
            isNewCopy = true
        } else {
            source = existing
            isNewCopy = false
        }
    }

    private val otherNames: Set<String> =
        repository.layouts.value.profiles.filter { it.id != source.id }.mapTo(HashSet()) { it.name }

    private var editor: LayoutEditor? = null

    fun isNameTaken(name: String): Boolean = name.trim() in otherNames

    /** The session's editor, created on first use. The screen size and metrics come from the UI. */
    @OptIn(ExperimentalUuidApi::class)
    fun editor(screen: ScreenSize, metrics: EditorMetrics): LayoutEditor =
        editor ?: LayoutEditor(source, screen, metrics, otherNames, newId = { Uuid.random().toString() })
            .also { editor = it }

    /** A copy of the default counts as unsaved even before any change. */
    fun hasUnsavedChanges(): Boolean {
        val draft = editor?.state?.value?.draft ?: return isNewCopy
        return isNewCopy || draft != source
    }

    /** Stores the draft and makes it the active profile. Returns false if the draft has issues. */
    suspend fun save(): Boolean {
        val state = editor?.state?.value ?: return false
        if (state.issues.isNotEmpty()) return false
        repository.save(state.draft)
        repository.select(state.draft.id)
        return true
    }

    fun onScreenShown() = screenMode.acquire()

    fun onScreenHidden() = screenMode.release()
}
