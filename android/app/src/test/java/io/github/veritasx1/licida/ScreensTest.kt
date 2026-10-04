package io.github.veritasx1.licida

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cards 1/2/3/8: the two modes in Apple's Camera look – start, composing, drawing. Pictures with -Pshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val out = System.getProperty("licida.shots")
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun shot(name: String) {
        compose.waitForIdle()
        if (out != null) compose.onRoot().captureRoboImage("$out/$name.png")
    }

    /** A drawing-like sample: a simple flower on white. */
    private fun sample(): Bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f; color = Color.rgb(40, 40, 40) }
        for (i in 0 until 6) canvas.drawCircle(450f + 160f * Math.cos(i * Math.PI / 3).toFloat(), 450f + 160f * Math.sin(i * Math.PI / 3).toFloat(), 110f, paint)
        canvas.drawCircle(450f, 450f, 70f, paint.apply { style = Paint.Style.FILL; color = Color.rgb(255, 196, 0) })
        canvas.drawLine(450f, 600f, 450f, 1150f, paint.apply { style = Paint.Style.STROKE; color = Color.rgb(52, 140, 60) })
    }

    @Test
    fun startWithoutImage() {
        compose.setContent { LiCidaApp(Studio(context), null, cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithText("Was möchtest du zeichnen?").assertExists()
        compose.onNodeWithContentDescription("Zeichnen").assertExists()
        shot("1-start")
    }

    @Test
    fun cameraAskedFirst() {
        compose.setContent { LiCidaApp(Studio(context), null, cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithText("LiCida braucht die Kamera").assertExists()
        shot("0-kamera")
    }

    @Test
    fun composeThenDraw() {
        val studio = Studio(context).apply { hintSeen = false; placement = Placement(scale = 0.9f, rotation = -8f) }
        var drawing = false
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = { drawing = it }) }
        compose.onNodeWithText("Mit zwei Fingern verschieben, zoomen und drehen").assertExists()
        shot("2-einrichten")
        compose.onNodeWithContentDescription("Zeichnen").performClick()
        compose.waitForIdle()
        assertEquals(true, drawing)
        compose.onNodeWithContentDescription("Zurück zum Einrichten").assertExists()
        shot("3-zeichnen")
        compose.onNodeWithContentDescription("Zurück zum Einrichten").performClick()
        compose.waitForIdle()
        assertEquals(false, drawing)
    }
}
