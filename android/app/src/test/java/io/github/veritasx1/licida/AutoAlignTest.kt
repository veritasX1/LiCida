package io.github.veritasx1.licida

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Card 7 end to end: the printable target (Android-drawn, smoothed, with its caption) seen through a
 *  tilted, mirrored camera – found and straightened into a true rectangle of the right shape. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoAlignTest {
    private fun view(corners: FloatArray): Bitmap {
        val target = Target.bitmap(840)
        val out = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.rgb(150, 140, 125))   // the table
        val matrix = Matrix().apply {
            setPolyToPoly(floatArrayOf(0f, 0f, target.width.toFloat(), 0f, 0f, target.height.toFloat(), target.width.toFloat(), target.height.toFloat()), 0,
                corners, 0, 4)
        }
        canvas.drawBitmap(target, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun checkRectangle(found: AutoFound) {
        val r = found.rectangle
        assertTrue("links senkrecht", abs(r[0] - r[4]) < 2f && abs(r[2] - r[6]) < 2f)
        assertTrue("oben waagerecht", abs(r[1] - r[3]) < 2f && abs(r[5] - r[7]) < 2f)
        val aspect = (r[2] - r[0]) / (r[5] - r[1])
        assertTrue("Seitenverhältnis $aspect", abs(aspect - Target.DOT_ASPECT) < 0.01f)
    }

    @Test
    fun tiltedStand() {
        // Top edge farther away (narrower), slightly turned – a tablet stand leaning back.
        val found = AutoAlign.find(view(floatArrayOf(330f, 260f, 760f, 290f, 180f, 1250f, 930f, 1220f)), null)
        assertNotNull(found)
        checkRectangle(found!!)
    }

    @Test
    fun frontCameraMirror() {
        // Mirrored (left and right swapped) and upside down, as with a mirror over the front camera.
        val found = AutoAlign.find(view(floatArrayOf(930f, 1220f, 180f, 1250f, 760f, 290f, 330f, 260f)), null)!!
        checkRectangle(found)
        // The marker (beside the top-left dot) must end up top left after straightening: it is upright again.
        val r = found.rectangle
        assertTrue(r[0] < r[2] && r[1] < r[5])
    }

    @Test
    fun nothingThere() {
        val empty = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(150, 140, 125)) }
        assertNull(AutoAlign.find(empty, null))
    }
}
