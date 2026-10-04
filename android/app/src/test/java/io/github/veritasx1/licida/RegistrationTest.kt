package io.github.veritasx1.licida

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Card 14: finding a session's paper again after it moved. */
class RegistrationTest {
    /** A drawing on paper: light ground, some dark strokes and blobs (fixed seed). */
    private fun paper(width: Int, height: Int, seed: Long): Pixels {
        val random = java.util.Random(seed)
        val shapes = List(40) { floatArrayOf(random.nextFloat() * width, random.nextFloat() * height, 6 + random.nextFloat() * 40, random.nextFloat() * 160) }
        return Pixels(width, height, IntArray(width * height) { i ->
            val x = i % width; val y = i / width
            var v = 235f
            for (s in shapes) { val dx = x - s[0]; val dy = y - s[1]; if (dx * dx + dy * dy < s[2] * s[2]) v = s[3] }
            val g = v.toInt().coerceIn(0, 255); Filters.argb(255, g, g, g)
        })
    }

    /** The same paper seen after it moved by `shift` – and in other light. */
    private fun moved(old: Pixels, shift: Shift): Pixels {
        val a = Math.toRadians(-shift.angle.toDouble())
        val cx = old.width / 2f; val cy = old.height / 2f
        return Pixels(old.width, old.height, IntArray(old.argb.size) { i ->
            val px = i % old.width - cx - shift.dx; val py = i / old.width - cy - shift.dy
            val qx = (cx + (cos(a) * px - sin(a) * py) / shift.scale).toInt(); val qy = (cy + (sin(a) * px + cos(a) * py) / shift.scale).toInt()
            val g = if (qx in 0 until old.width && qy in 0 until old.height) (old.argb[qy * old.width + qx] and 255) * 0.8f + 20 else 60f
            Filters.argb(255, g.toInt(), g.toInt(), g.toInt())
        })
    }

    @Test
    fun findsTheMovedPaper() {
        val old = paper(360, 800, 7)
        val truth = Shift(28f, -41f, 1.06f, 7f)
        val found = Registration.find(old, moved(old, truth))!!
        assertEquals(truth.angle, found.angle, 0.8f)
        assertEquals(truth.scale, found.scale, 0.02f)
        assertEquals(truth.dx, found.dx, 5f)
        assertEquals(truth.dy, found.dy, 5f)
        assertTrue(found.score > 0.8f)
    }

    @Test
    fun anotherPictureIsNotGuessed() {
        assertNull(Registration.find(paper(360, 800, 7), paper(360, 800, 99)))
    }

    @Test
    fun theReferenceIsCarriedAlong() {
        // A point of the reference at the screen centre + offset moves exactly like the paper.
        val placement = Placement(1.5f, 10f, Offset(40f, -20f))
        val shift = Shift(30f, 12f, 1.1f, 90f)
        val carried = shift.carry(placement)
        assertEquals(1.65f, carried.scale, 1e-4f)
        assertEquals(100f, carried.rotation, 1e-4f)
        // offset (40,-20) turned by 90° → (20, 40), ×1.1 → (22, 44), + (30, 12)
        assertEquals(52f, carried.offset.x, 1e-3f)
        assertEquals(56f, carried.offset.y, 1e-3f)
    }
}
