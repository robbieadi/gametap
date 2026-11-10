package io.github.robbieadi.gametap.layout

sealed interface LayoutIssue {
    data class Overlap(val first: String, val second: String) : LayoutIssue

    data class OffScreen(val id: String) : LayoutIssue

    data class DuplicateStick(val side: Side, val ids: List<String>) : LayoutIssue

    data class MenuCount(val count: Int) : LayoutIssue

    data class DuplicateId(val id: String) : LayoutIssue

    data object BlankName : LayoutIssue

    data object DuplicateName : LayoutIssue
}

/**
 * Everything that stops [this] from being saved on [screen]. [otherNames] holds the names of
 * the other profiles. The minimum control size is not checked here: it is a physical size, so
 * the editor enforces it while resizing.
 */
fun LayoutProfile.validate(screen: ScreenSize, otherNames: Set<String> = emptySet()): List<LayoutIssue> =
    buildList {
        if (name.isBlank()) add(LayoutIssue.BlankName)
        if (name.trim() in otherNames.map { it.trim() }) add(LayoutIssue.DuplicateName)

        controls.groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys.forEach { add(LayoutIssue.DuplicateId(it)) }

        val menus = controls.count { it is GamepadControl.MenuControl }
        if (menus != 1) add(LayoutIssue.MenuCount(menus))

        controls.filterIsInstance<GamepadControl.StickControl>()
            .groupBy { it.side }
            .filterValues { it.size > 1 }
            .forEach { (side, sticks) -> add(LayoutIssue.DuplicateStick(side, sticks.map { it.id })) }

        val areas = controls.map { it.id to it.area(screen) }
        for ((id, area) in areas) {
            if (!area.isInside(screen)) add(LayoutIssue.OffScreen(id))
        }
        for (i in areas.indices) {
            for (j in i + 1 until areas.size) {
                if (areas[i].second.overlaps(areas[j].second)) {
                    add(LayoutIssue.Overlap(areas[i].first, areas[j].first))
                }
            }
        }
    }

/**
 * The issues that depend on the screen: what makes a stored profile not fit the current window,
 * after a fold, split screen or a restored backup. Name and structure issues are left out: the
 * gamepad screen cannot fix them and loading already repairs the menu.
 */
fun LayoutProfile.screenIssues(screen: ScreenSize): List<LayoutIssue> =
    validate(screen).filter { it is LayoutIssue.Overlap || it is LayoutIssue.OffScreen }

/** The ids of every control an issue names, for highlighting. */
fun List<LayoutIssue>.controlIds(): Set<String> = flatMapTo(HashSet()) { issue ->
    when (issue) {
        is LayoutIssue.Overlap -> listOf(issue.first, issue.second)
        is LayoutIssue.OffScreen -> listOf(issue.id)
        is LayoutIssue.DuplicateStick -> issue.ids
        is LayoutIssue.DuplicateId -> listOf(issue.id)
        is LayoutIssue.MenuCount, LayoutIssue.BlankName, LayoutIssue.DuplicateName -> emptyList()
    }
}
