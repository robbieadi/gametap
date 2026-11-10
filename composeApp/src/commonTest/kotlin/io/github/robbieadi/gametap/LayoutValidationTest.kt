package io.github.robbieadi.gametap

import io.github.robbieadi.gametap.domain.Button
import io.github.robbieadi.gametap.layout.Anchor
import io.github.robbieadi.gametap.layout.ButtonAction
import io.github.robbieadi.gametap.layout.GamepadControl
import io.github.robbieadi.gametap.layout.GamepadControl.ButtonControl
import io.github.robbieadi.gametap.layout.GamepadControl.MenuControl
import io.github.robbieadi.gametap.layout.GamepadControl.StickControl
import io.github.robbieadi.gametap.layout.LayoutIssue
import io.github.robbieadi.gametap.layout.LayoutJson
import io.github.robbieadi.gametap.layout.LayoutProfile
import io.github.robbieadi.gametap.layout.Position
import io.github.robbieadi.gametap.layout.ScreenSize
import io.github.robbieadi.gametap.layout.Shape
import io.github.robbieadi.gametap.layout.Side
import io.github.robbieadi.gametap.layout.controlIds
import io.github.robbieadi.gametap.layout.screenIssues
import io.github.robbieadi.gametap.layout.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutValidationTest {
    private val screen = ScreenSize(width = 2000f, height = 1000f)
    private val menu = MenuControl("menu", Position(Anchor.LEFT, 0.1f, 0.1f), Shape.Circle(0.05f))

    private fun profile(vararg controls: GamepadControl) =
        LayoutProfile("p", "Test", listOf(menu) + controls)

    private fun button(id: String, x: Float, y: Float, shape: Shape, anchor: Anchor = Anchor.LEFT) =
        ButtonControl(id, Position(anchor, x, y), shape, ButtonAction.Pad(Button.A), "A")

    @Test
    fun overlappingCirclesAreReported() {
        val issues = profile(
            button("a", 0.5f, 0.5f, Shape.Circle(0.1f)),
            button("b", 0.69f, 0.5f, Shape.Circle(0.1f)),
        ).validate(screen)
        assertEquals(listOf(LayoutIssue.Overlap("a", "b")), issues)
    }

    @Test
    fun separateCirclesDoNotOverlap() {
        val issues = profile(
            button("a", 0.5f, 0.5f, Shape.Circle(0.1f)),
            button("b", 0.75f, 0.5f, Shape.Circle(0.1f)),
        ).validate(screen)
        assertEquals(emptyList(), issues)
    }

    /** The circle sits off the rectangle's corner: their bounding boxes overlap, the shapes do not. */
    @Test
    fun circleNearARectangleCornerDoesNotOverlap() {
        val issues = profile(
            button("rect", 0.5f, 0.5f, Shape.Rect(0.1f, 0.1f)),
            button("circle", 0.68f, 0.68f, Shape.Circle(0.1f)),
        ).validate(screen)
        assertEquals(emptyList(), issues)
    }

    @Test
    fun circleOverARectangleEdgeOverlaps() {
        val issues = profile(
            button("rect", 0.5f, 0.5f, Shape.Rect(0.1f, 0.1f)),
            button("circle", 0.65f, 0.5f, Shape.Circle(0.1f)),
        ).validate(screen)
        assertEquals(listOf(LayoutIssue.Overlap("rect", "circle")), issues)
    }

    @Test
    fun anchorsResolveAgainstTheirEdge() {
        // 0.1 from the right edge of a 2:1 screen is 1.9 from the left, clear of a left-anchored button at 1.7.
        val issues = profile(
            button("left", 1.7f, 0.5f, Shape.Circle(0.09f)),
            button("right", 0.1f, 0.5f, Shape.Circle(0.09f), Anchor.RIGHT),
        ).validate(screen)
        assertEquals(emptyList(), issues)

        val narrow = ScreenSize(width = 1800f, height = 1000f)
        assertEquals(
            listOf(LayoutIssue.Overlap("left", "right")),
            profile(
                button("left", 1.7f, 0.5f, Shape.Circle(0.09f)),
                button("right", 0.1f, 0.5f, Shape.Circle(0.09f), Anchor.RIGHT),
            ).validate(narrow),
        )
    }

    @Test
    fun controlPastTheEdgeIsOffScreen() {
        val issues = profile(button("a", 0.05f, 0.5f, Shape.Circle(0.1f))).validate(screen)
        assertEquals(listOf(LayoutIssue.OffScreen("a")), issues)
    }

    @Test
    fun menuIsRequiredExactlyOnce() {
        val none = LayoutProfile("p", "Test", emptyList()).validate(screen)
        assertEquals(listOf(LayoutIssue.MenuCount(0)), none)

        val second = menu.copy(id = "menu2", position = Position(Anchor.RIGHT, 0.1f, 0.1f))
        assertEquals(listOf(LayoutIssue.MenuCount(2)), profile(second).validate(screen))
    }

    @Test
    fun onlyOneStickPerSide() {
        val issues = profile(
            StickControl("s1", Position(Anchor.LEFT, 0.4f, 0.5f), Side.LEFT, 0.15f, 0.2f),
            StickControl("s2", Position(Anchor.RIGHT, 0.4f, 0.5f), Side.LEFT, 0.15f, 0.2f),
        ).validate(screen)
        assertEquals(listOf(LayoutIssue.DuplicateStick(Side.LEFT, listOf("s1", "s2"))), issues)
    }

    @Test
    fun duplicateButtonsAreAllowedButIdsMustBeUnique() {
        val ok = profile(
            button("a1", 0.5f, 0.3f, Shape.Circle(0.1f)),
            button("a2", 0.5f, 0.7f, Shape.Circle(0.1f)),
        ).validate(screen)
        assertEquals(emptyList(), ok)

        val clash = profile(
            button("a", 0.5f, 0.3f, Shape.Circle(0.1f)),
            button("a", 0.5f, 0.7f, Shape.Circle(0.1f)),
        ).validate(screen)
        assertEquals(listOf(LayoutIssue.DuplicateId("a")), clash)
    }

    @Test
    fun nameMustBePresentAndUnique() {
        val blank = profile().copy(name = " ").validate(screen)
        assertEquals(listOf(LayoutIssue.BlankName), blank)

        val taken = profile().copy(name = "Racing ").validate(screen, otherNames = setOf("Racing"))
        assertEquals(listOf(LayoutIssue.DuplicateName), taken)
    }

    /** The stored format is a contract with files already on users' devices. */
    @Test
    fun storedFormatIsStable() {
        val json = """
            {"id":"a","position":{"anchor":"right","x":0.3,"y":0.6},
             "shape":{"type":"circle","r":0.08},
             "action":{"type":"button","button":"a"},"label":"🅰️ Jump","type":"button"}
        """.trimIndent()
        val control = LayoutJson.decodeFromString(GamepadControl.serializer(), json)
        assertEquals(
            ButtonControl("a", Position(Anchor.RIGHT, 0.3f, 0.6f), Shape.Circle(0.08f), ButtonAction.Pad(Button.A), "🅰️ Jump"),
            control,
        )
        assertTrue("\"type\":\"button\"" in LayoutJson.encodeToString(GamepadControl.serializer(), control))
    }

    @Test
    fun screenIssuesKeepOnlyWhatTheScreenCauses() {
        val profile = LayoutProfile(
            "p", " ",
            listOf(
                button("a", 0.5f, 0.5f, Shape.Circle(0.1f)),
                button("b", 0.69f, 0.5f, Shape.Circle(0.1f)),
                button("edge", 0.05f, 0.8f, Shape.Circle(0.1f)),
            ),
        )
        val issues = profile.screenIssues(screen)
        assertEquals(listOf(LayoutIssue.OffScreen("edge"), LayoutIssue.Overlap("a", "b")), issues)
        assertEquals(setOf("a", "b", "edge"), issues.controlIds())
    }

    @Test
    fun defaultFitsSoNothingIsFlagged() {
        val default = io.github.robbieadi.gametap.layout.DefaultLayout.profile
        assertEquals(emptySet(), default.screenIssues(ScreenSize(2220f, 1000f)).controlIds())
    }
}
