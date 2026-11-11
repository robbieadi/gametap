package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.EditorMetrics
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import io.github.robbieadi.gametap.layout.Guide
import io.github.robbieadi.gametap.layout.LayoutEditor
import io.github.robbieadi.gametap.layout.LayoutIssue
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.NewControl
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.centerX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A 1000x500 screen, so one screen height is 500 pixels and a left-anchored x of 1.0 is 500 px. */
class LayoutEditorTest {
    private val screen = ScreenSize(1000f, 500f)
    private val metrics = EditorMetrics(minHalfPx = 10f, snapPx = 8f, handlePx = 20f)

    private fun at(xPx: Float, yPx: Float) = Position(Anchor.LEFT, xPx / 500f, yPx / 500f)

    private val menu = MenuControl("menu", at(40f, 40f), Shape.Circle(0.06f))                                    // r30
    private val a = ButtonControl("a", at(500f, 250f), Shape.Circle(0.1f), ButtonAction.Pad(Button.A), "A")      // r50
    private val b = ButtonControl("b", at(800f, 250f), Shape.Circle(0.1f), ButtonAction.Pad(Button.B), "B")      // r50

    private fun editor(vararg controls: GamepadControl, otherNames: Set<String> = emptySet()): LayoutEditor {
        var next = 0
        return LayoutEditor(LayoutProfile("p", "Mine", controls.toList()), screen, metrics, otherNames) { "n${next++}" }
    }

    private val LayoutEditor.s get() = state.value
    private fun LayoutEditor.control(id: String) = s.draft.controls.single { it.id == id }
    private fun LayoutEditor.tap(x: Float, y: Float) {
        press(x, y)
        release(dragged = false)
    }
    private fun LayoutEditor.drag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        press(fromX, fromY)
        dragTo(toX, toY)
        release(dragged = true)
    }

    @Test
    fun tapSelectsAndTapOnEmptySpaceDeselects() {
        val editor = editor(menu, a, b)
        editor.tap(500f, 250f)
        assertEquals("a", editor.s.selectedId)
        editor.tap(300f, 450f)
        assertNull(editor.s.selectedId)
    }

    @Test
    fun repeatedTapsCycleThroughStackedControls() {
        val editor = editor(menu, a, a.copy(id = "c"))
        editor.tap(500f, 250f)
        assertEquals("c", editor.s.selectedId, "the control on top first")
        editor.tap(500f, 250f)
        assertEquals("a", editor.s.selectedId)
        editor.tap(500f, 250f)
        assertEquals("c", editor.s.selectedId)
    }

    @Test
    fun dragMovesAndIsOneUndoStep() {
        val editor = editor(menu, a, b)
        editor.press(500f, 250f)
        editor.dragTo(400f, 300f)
        editor.dragTo(300f, 350f)
        editor.release(dragged = true)
        assertEquals(at(300f, 350f), editor.control("a").position)

        editor.undo()
        assertEquals(a, editor.control("a"))
        assertFalse(editor.s.canUndo)
        editor.redo()
        assertEquals(at(300f, 350f), editor.control("a").position)
    }

    @Test
    fun tapLeavesNoUndoStep() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        assertFalse(editor.s.canUndo)
    }

    @Test
    fun dragSnapsToOtherControlsAndShowsGuides() {
        val editor = editor(menu, a, b)
        editor.press(500f, 250f)
        // Raw center (505, 350): the center is 5 px off the screen's middle, the top edge lines up with b's bottom.
        editor.dragTo(505f, 350f)
        assertEquals(500f, editor.control("a").position.centerX(screen), 0.01f)
        assertEquals(350f / 500f, editor.control("a").position.y, 0.0001f)
        assertEquals(setOf(Guide.Vertical(500f), Guide.Horizontal(300f)), editor.s.guides.toSet())
        editor.release(dragged = true)
        assertTrue(editor.s.guides.isEmpty())
    }

    @Test
    fun dragWithNothingToSnapToRoundsToTheGrid() {
        val editor = editor(menu, a, b)
        editor.drag(500f, 250f, 300.4f, 333.3f)
        assertEquals(Position(Anchor.LEFT, 300f / 500f, 335f / 500f), editor.control("a").position)
    }

    @Test
    fun droppingInTheRightThirdAnchorsToTheRightEdge() {
        val editor = editor(menu, a, b)
        editor.drag(500f, 250f, 905f, 450f)
        // Snapped 5 px left to line up with b's right edge.
        assertEquals(Position(Anchor.RIGHT, 100f / 500f, 450f / 500f), editor.control("a").position)
    }

    @Test
    fun handleResizesWithAMinimum() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        val (hx, hy) = editor.handleOf(editor.control("a"))
        editor.press(hx, hy)
        editor.dragTo(580f, 250f)
        assertEquals(Shape.Circle(80f / 500f), editor.control("a").shape)
        editor.dragTo(502f, 250f)
        editor.release(dragged = true)
        assertEquals(Shape.Circle(10f / 500f), editor.control("a").shape, "clamped to the minimum")
    }

    @Test
    fun stickResizeKeepsBaseAndTouchAreaInProportion() {
        val stick = StickControl("s", at(500f, 250f), Side.LEFT, radius = 0.17f, grabRadius = 0.2f)
        val editor = editor(menu, stick)
        editor.tap(500f, 250f)
        val (hx, hy) = editor.handleOf(editor.control("s"))
        editor.drag(hx, hy, 500f, 400f)
        val resized = editor.control("s") as StickControl
        assertEquals(0.3f, resized.grabRadius, 0.0001f)
        assertEquals(0.3f * LayoutEditor.STICK_BASE_RATIO, resized.radius, 0.0001f)
    }

    @Test
    fun addedControlLandsInFreeSpaceAndIsSelected() {
        val editor = editor(menu, a, b)
        editor.add(NewControl.Button(ButtonAction.Pad(Button.Y)))
        val added = editor.control("n0") as ButtonControl
        assertEquals("Y", added.label)
        assertEquals("n0", editor.s.selectedId)
        assertEquals(emptyList(), editor.s.issues)
    }

    @Test
    fun stickSidesInUseAreReported() {
        val editor = editor(menu, a)
        editor.add(NewControl.Stick(Side.RIGHT))
        assertEquals(setOf(Side.RIGHT), editor.s.stickSides)
    }

    @Test
    fun duplicateAndDeleteSkipTheMenu() {
        val editor = editor(menu, a)
        editor.tap(40f, 40f)
        editor.duplicate()
        editor.delete()
        assertEquals(listOf(menu, a), editor.s.draft.controls)

        editor.tap(500f, 250f)
        editor.duplicate()
        assertEquals(3, editor.s.draft.controls.size)
        assertEquals(emptyList(), editor.s.issues, "the copy goes into free space")
        editor.delete()
        assertEquals(listOf(menu, a), editor.s.draft.controls)
        assertNull(editor.s.selectedId)
    }

    @Test
    fun defaultLabelFollowsTheActionButACustomOneStays() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        editor.setAction(ButtonAction.Pad(Button.B))
        assertEquals("B", (editor.control("a") as ButtonControl).label)
        editor.setLabel("Jump")
        editor.setAction(ButtonAction.Pad(Button.X))
        assertEquals("Jump", (editor.control("a") as ButtonControl).label)
    }

    @Test
    fun typingALabelIsOneUndoStep() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        editor.setLabel("J")
        editor.setLabel("Ju")
        editor.setLabel("Jump")
        editor.undo()
        assertEquals("A", (editor.control("a") as ButtonControl).label)
    }

    @Test
    fun shapeConvertsBothWays() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        editor.setShape(round = false)
        assertEquals(Shape.Rect(0.1f, 0.1f), editor.control("a").shape)
        editor.setShape(round = true)
        assertEquals(Shape.Circle(0.1f), editor.control("a").shape)
    }

    @Test
    fun changingTheAnchorKeepsTheControlInPlace() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        editor.setAnchor(Anchor.RIGHT)
        assertEquals(Position(Anchor.RIGHT, 1f, 0.5f), editor.control("a").position)
        assertEquals(500f, editor.control("a").position.centerX(screen), 0.01f)
    }

    @Test
    fun nudgeMovesOneGridStep() {
        val editor = editor(menu, a)
        editor.tap(500f, 250f)
        editor.nudge(1, -1)
        assertEquals(at(505f, 245f), editor.control("a").position)
    }

    @Test
    fun issueButtonCyclesThroughFlaggedControls() {
        val editor = editor(menu, a, a.copy(id = "c"))
        assertEquals(listOf(LayoutIssue.Overlap("a", "c")), editor.s.issues)
        editor.selectNextIssue()
        assertEquals("a", editor.s.selectedId)
        editor.selectNextIssue()
        assertEquals("c", editor.s.selectedId)
        editor.selectNextIssue()
        assertEquals("a", editor.s.selectedId)
    }

    @Test
    fun takenNameIsAnIssue() {
        val editor = editor(menu, a, otherNames = setOf("Racing"))
        editor.rename("Racing")
        assertEquals(listOf(LayoutIssue.DuplicateName), editor.s.issues)
    }
}
