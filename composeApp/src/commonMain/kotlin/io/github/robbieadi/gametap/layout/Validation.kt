package io.github.robbieadi.gametap.layout

sealed interface LayoutIssue {
    data class Overlap(val first: String, val second: String) : LayoutIssue

    data class OffScreen(val id: String) : LayoutIssue

    data class DuplicateStick(val side: Side, val ids: List<String>) : LayoutIssue

    data class MenuCount(val count: Int) : LayoutIssue

    data class DuplicateId(val id: String) : LayoutIssue

    data object BlankName : LayoutIssue

    data object DuplicateName : LayoutIssue

    /** More than one gyro binding drives [target]. */
    data class DuplicateGyroTarget(val target: GyroTarget) : LayoutIssue

    /** A stick binding with no axis, or a range outside the allowed one. */
    data class InvalidGyroBinding(val target: GyroTarget) : LayoutIssue

    data object InvalidGyroDeadzone : LayoutIssue

    /**
     * Both a Gyro on/off and a Gyro on while held, on buttons ([ids]) or volume keys ([keys]); a
     * layout uses one way or the other.
     */
    data class ConflictingGyroButtons(val ids: List<String>, val keys: List<VolumeKey> = emptyList()) : LayoutIssue
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

        gyro.bindings.groupBy { it.target }
            .filterValues { it.size > 1 }
            .keys.forEach { add(LayoutIssue.DuplicateGyroTarget(it)) }
        gyro.bindings.filterNot { it.isValid() }
            .mapTo(LinkedHashSet()) { it.target }
            .forEach { add(LayoutIssue.InvalidGyroBinding(it)) }
        if (gyro.deadzone !in 0f..GyroSetup.MAX_DEADZONE) add(LayoutIssue.InvalidGyroDeadzone)
        if (buttonActions.filterTo(HashSet()) { it in GYRO_SWITCHES }.size > 1) {
            val ids = controls.filter { (it as? GamepadControl.ButtonControl)?.action in GYRO_SWITCHES }.map { it.id }
            val keys = VolumeKey.entries.filter { volumeKeys[it] in GYRO_SWITCHES }
            add(LayoutIssue.ConflictingGyroButtons(ids, keys))
        }

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

/** The button actions that turn gyro on. A layout may use only one of them. */
val GYRO_SWITCHES: Set<ButtonAction> = setOf(ButtonAction.Gyro(GyroButton.TOGGLE), ButtonAction.Gyro(GyroButton.HOLD))

/** The gyro switches a button or key may not take, given what the [others] already send. */
fun blockedGyroSwitches(others: Iterable<ButtonAction>): Set<ButtonAction> {
    val used = others.filterTo(HashSet()) { it in GYRO_SWITCHES }
    return if (used.isEmpty()) emptySet() else GYRO_SWITCHES - used
}

private fun GyroBinding.isValid(): Boolean {
    if (range !in GyroBinding.MIN_RANGE..GyroBinding.MAX_RANGE) return false
    return this !is GyroBinding.StickBinding || horizontal || vertical
}

/**
 * The issues that depend on the screen: what makes a stored profile not fit the current window,
 * after a fold, split screen or a restored backup. Name and structure issues are left out: the
 * gamepad screen cannot fix them and loading already repairs the menu.
 */
fun LayoutProfile.screenIssues(screen: ScreenSize): List<LayoutIssue> =
    validate(screen).filter { it is LayoutIssue.Overlap || it is LayoutIssue.OffScreen }

/** Issues the gyro panel fixes, rather than a control or the name. */
val LayoutIssue.isGyro: Boolean
    get() = this is LayoutIssue.DuplicateGyroTarget || this is LayoutIssue.InvalidGyroBinding || this == LayoutIssue.InvalidGyroDeadzone

/** Issues the volume keys panel fixes. */
val LayoutIssue.isVolumeKeys: Boolean
    get() = this is LayoutIssue.ConflictingGyroButtons && keys.isNotEmpty()

/** The ids of every control an issue names, for highlighting. */
fun List<LayoutIssue>.controlIds(): Set<String> = flatMapTo(HashSet()) { issue ->
    when (issue) {
        is LayoutIssue.Overlap -> listOf(issue.first, issue.second)
        is LayoutIssue.OffScreen -> listOf(issue.id)
        is LayoutIssue.DuplicateStick -> issue.ids
        is LayoutIssue.DuplicateId -> listOf(issue.id)
        is LayoutIssue.ConflictingGyroButtons -> issue.ids
        is LayoutIssue.MenuCount, LayoutIssue.BlankName, LayoutIssue.DuplicateName,
        is LayoutIssue.DuplicateGyroTarget, is LayoutIssue.InvalidGyroBinding, LayoutIssue.InvalidGyroDeadzone,
        -> emptyList()
    }
}
