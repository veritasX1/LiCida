package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Cards 6/7: perspective maps, the manual correction and finding the target in a tilted view. */
class CorrectionTest {
    private fun near(expected: Float, actual: Float, tolerance: Float = 0.05f) = assertEquals(expected, actual, tolerance)

    @Test
    fun fourPointsMapExactly() {
        val from = floatArrayOf(10f, 20f, 300f, 40f, 0f, 400f, 330f, 380f)
        val to = floatArrayOf(0f, 0f, 200f, 0f, 0f, 300f, 200f, 300f)
        val h = Homography.fromPoints(from, to)!!
        for (p in 0 until 4) {
            val (x, y) = Homography.apply(h, from[2 * p], from[2 * p + 1])
            near(to[2 * p], x, 0.01f); near(to[2 * p + 1], y, 0.01f)
        }
        assertNull(Homography.fromPoints(floatArrayOf(0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f), to))  // all on one line
    }

    @Test
    fun manualTiltKeepsTheCentreAndNarrowsOneEnd() {
        val plain = Correction()
        assertTrue(plain.isPlain)
        val m = Correction(tilt = 20f).matrix(1000f, 2000f)
        val (cx, cy) = Homography.apply(m, 500f, 1000f)
        near(500f, cx); near(1000f, cy)                     // the centre stays
        val topWidth = Homography.apply(m, 1000f, 0f).first - Homography.apply(m, 0f, 0f).first
        val bottomWidth = Homography.apply(m, 1000f, 2000f).first - Homography.apply(m, 0f, 2000f).first
        assertTrue("top $topWidth, bottom $bottomWidth", topWidth > bottomWidth)  // one end farther away: narrower
        // More "height" = less stretch (the handbook: the height slider exaggerates when lower).
        val far = Correction(tilt = 20f, height = 8f).matrix(1000f, 2000f)
        val farBottom = Homography.apply(far, 1000f, 2000f).first - Homography.apply(far, 0f, 2000f).first
        assertTrue(farBottom > bottomWidth)
        // Stretch: wider by the factor, about the centre.
        val s = Correction(stretchX = 1.2f).matrix(1000f, 2000f)
        near(1100f, Homography.apply(s, 1000f, 1000f).first, 0.5f)
        assertEquals(Correction(tilt = 12f, stretchY = 0.9f, auto = Homography.IDENTITY), Correction.decode(Correction(tilt = 12f, stretchY = 0.9f, auto = Homography.IDENTITY).encode()))
        assertTrue(Correction.stretchStep(2000) > Correction.stretchStep(100))
    }

    /** The target seen through a tilted camera: pixels of `view` come from the target by an inverse map. */
    private fun tilted(width: Int, height: Int, seenCorners: FloatArray): Pixels {
        val target = Target.pixels(420, 594)
        val paper = floatArrayOf(0f, 0f, 420f, 0f, 0f, 594f, 420f, 594f)
        val back = Homography.fromPoints(seenCorners, paper)!!   // view → paper
        val grey = Filters.argb(255, 120, 110, 100)
        return Pixels(width, height, IntArray(width * height) { i ->
            val (u, v) = Homography.apply(back, (i % width).toFloat(), (i / width).toFloat())
            if (u < 0 || v < 0 || u >= 420 || v >= 594) grey else target[u.toInt(), v.toInt()]
        })
    }

    @Test
    fun findsTheDotsThroughATiltedCameraAndStraightens() {
        // The sheet seen in perspective: the top edge narrower (camera tilted), a bit turned.
        val seen = floatArrayOf(180f, 120f, 460f, 140f, 90f, 600f, 560f, 590f)
        val view = tilted(640, 720, seen)
        val dots = Target.findDots(view)
        assertNotNull(dots)
        // Where the dots should be: the target's dot positions taken through the same perspective.
        val paperToView = Homography.fromPoints(floatArrayOf(0f, 0f, 420f, 0f, 0f, 594f, 420f, 594f), seen)!!
        for (p in 0 until 4) {
            val (ex, ey) = Homography.apply(paperToView, Target.DOTS[2 * p] * 2, Target.DOTS[2 * p + 1] * 2)
            assertTrue("dot $p: ${dots!![2 * p]},${dots[2 * p + 1]} vs $ex,$ey", abs(dots[2 * p] - ex) < 4 && abs(dots[2 * p + 1] - ey) < 4)
        }
        // Straightened: the four dots form a true rectangle with the target's shape.
        val h = Target.straighten(dots!!, Target.DOT_ASPECT)!!
        val p = (0 until 4).map { Homography.apply(h, dots[2 * it], dots[2 * it + 1]) }
        near(p[0].first, p[2].first, 0.5f); near(p[1].first, p[3].first, 0.5f)   // left and right edges upright
        near(p[0].second, p[1].second, 0.5f); near(p[2].second, p[3].second, 0.5f) // top and bottom level
        near(Target.DOT_ASPECT, (p[1].first - p[0].first) / (p[2].second - p[0].second), 0.01f)
    }

    @Test
    fun aMirrorAndATurnAreUndoneByTheMarker() {
        // Seen through a mirror (left and right swapped) and upside down – as with the front camera's mirror.
        val seen = floatArrayOf(560f, 590f, 90f, 600f, 460f, 140f, 180f, 120f)   // paper TL,TR,BL,BR land here
        val view = tilted(640, 720, seen)
        val dots = Target.findDots(view)!!
        val paperToView = Homography.fromPoints(floatArrayOf(0f, 0f, 420f, 0f, 0f, 594f, 420f, 594f), seen)!!
        // The returned TL must be where the paper's top-left dot is seen (not merely the top-left of the picture).
        for (p in 0 until 4) {
            val (ex, ey) = Homography.apply(paperToView, Target.DOTS[2 * p] * 2, Target.DOTS[2 * p + 1] * 2)
            assertTrue("dot $p", abs(dots[2 * p] - ex) < 4 && abs(dots[2 * p + 1] - ey) < 4)
        }
        // Straightened, the marker sits top left again, beside the first dot: the picture is upright and unmirrored.
        val h = Target.straighten(dots, Target.DOT_ASPECT)!!
        val (mx, my) = Homography.apply(h, Homography.apply(paperToView, Target.MARKER[0] * 2, Target.MARKER[1] * 2).first,
            Homography.apply(paperToView, Target.MARKER[0] * 2, Target.MARKER[1] * 2).second)
        val (tlx, tly) = Homography.apply(h, dots[0], dots[1])
        val (trx, _) = Homography.apply(h, dots[2], dots[3])
        assertTrue(mx > tlx && mx < trx && abs(my - tly) < 3)
    }

    @Test
    fun ownPictureCornersAndNothingFound() {
        // A dark magazine on a light table, in perspective.
        val width = 400; val height = 500
        val corners = floatArrayOf(110f, 90f, 300f, 110f, 80f, 420f, 330f, 400f)
        val back = Homography.fromPoints(corners, floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))!!
        val image = Pixels(width, height, IntArray(width * height) { i ->
            val (u, v) = Homography.apply(back, (i % width).toFloat(), (i / width).toFloat())
            if (u in 0f..1f && v in 0f..1f) Filters.argb(255, 40, 60, 90) else Filters.argb(255, 230, 225, 215)
        })
        val found = Target.findCorners(image)!!
        for (p in 0 until 4) assertTrue("corner $p", abs(found[2 * p] - corners[2 * p]) < 4 && abs(found[2 * p + 1] - corners[2 * p + 1]) < 4)
        // A plain grey picture: no target – LiCida says so instead of guessing.
        assertNull(Target.findDots(Pixels(200, 200, IntArray(40000) { Filters.argb(255, 128, 128, 128) })))
    }
}
