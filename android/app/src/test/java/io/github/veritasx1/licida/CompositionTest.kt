package io.github.veritasx1.licida

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/** Card 3/8: the geometry of composing and drawing. */
class CompositionTest {
    private val screen = Size(1000f, 2000f)
    private val centre = Offset(500f, 1000f)

    /** Where an image point (relative to the image centre, at scale 1) ends up on the screen. */
    private fun onScreen(p: Placement, point: Offset): Offset {
        val r = Math.toRadians(p.rotation.toDouble())
        val x = point.x * p.scale
        val y = point.y * p.scale
        return centre + p.offset + Offset((x * Math.cos(r) - y * Math.sin(r)).toFloat(), (x * Math.sin(r) + y * Math.cos(r)).toFloat())
    }

    private fun near(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test
    fun theSpotUnderTheFingersStaysThere() {
        var p = Placement()
        val fingers = Offset(700f, 600f)
        val imagePoint = fingers - centre  // what lies under the fingers before
        p = Composition.transform(p, fingers, Offset.Zero, 2f, 30f, screen)
        near(fingers, onScreen(p, imagePoint))  // zoomed 2× and turned 30° – the same spot of the image
        assertEquals(2f, p.scale)
        assertEquals(30f, p.rotation)
        // A pan moves by exactly the pan.
        val moved = Composition.transform(p, fingers, Offset(40f, -25f), 1f, 0f, screen)
        near(p.offset + Offset(40f, -25f), moved.offset)
    }

    @Test
    fun quarterTurnsAndLimits() {
        var p = Placement(rotation = 170f)
        p = Composition.quarterTurn(p)
        assertEquals(-100f, p.rotation)
        assertEquals(Composition.MAX_SCALE, Composition.transform(Placement(scale = 30f), centre, Offset.Zero, 10f, 0f, screen).scale)
        assertEquals(0.5f, Composition.fitScale(Size(2000f, 1000f), screen))
    }

    @Test
    fun drawModeZoomsBothTogetherWithinLimits() {
        var v = Composition.zoom(DrawView(), centre, Offset.Zero, 0.5f, screen)
        assertEquals(1f, v.zoom)  // never smaller than the whole view
        v = Composition.zoom(DrawView(), Offset(900f, 1900f), Offset.Zero, 100f, screen)
        assertEquals(Composition.MAX_ZOOM, v.zoom)
        // Never past the edge: at 30× the offset stays within (zoom-1)·size/2.
        assert(kotlin.math.abs(v.offset.x) <= 29f * 500f + 0.1f && kotlin.math.abs(v.offset.y) <= 29f * 1000f + 0.1f)
        // Double tap: 3× around the spot, again: back to the whole view.
        val tapped = Composition.doubleTap(DrawView(), Offset(750f, 1000f), screen)
        assertEquals(3f, tapped.zoom)
        near(Offset(-500f, 0f), tapped.offset)  // the tapped spot stays where it was
        assertEquals(DrawView(), Composition.doubleTap(tapped, centre, screen))
    }
}
