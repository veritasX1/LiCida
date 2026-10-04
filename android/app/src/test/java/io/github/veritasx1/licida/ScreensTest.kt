package io.github.veritasx1.licida

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun cameraSheet() {
        val studio = Studio(context).apply { hintSeen = true; cameraKey = null; ghost = true }
        val moto = listOf(CameraFacts("0", Facing.Back, 5.56f, 8.16f), CameraFacts("1", Facing.Front, 3.27f, 4.608f),
            CameraFacts("2", Facing.Back, 1.66f, 3.6736f))
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}, cameras = { moto }) }
        compose.onNodeWithContentDescription("Kamera wählen und einstellen").performClick()
        compose.waitUntil(3000) { runCatching { compose.onNodeWithText("Ultraweitwinkel · 95° Bildwinkel").assertExists() }.isSuccess }
        shot("4-kamera-blatt")
        compose.onNodeWithText("Frontkamera").performClick()
        compose.waitForIdle()
        assertEquals("front", studio.cameraKey)
        assertEquals(true, studio.cameraView.flipV)  // the front camera looks through a mirror
        compose.onNodeWithText("Ganzes Bild").performClick()
        assertEquals(false, studio.fill)
        compose.onNodeWithText("Fertig").performClick()
        compose.onNodeWithContentDescription("Zeichnen").assertExists()
    }

    @Test
    fun toolbox() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits(); slots = List(3) { Slot.decode(null, it) } }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Werkzeuge und Filter").performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("Graustufen: Ohne Farbe").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Graustufen: Ohne Farbe").performClick()
        compose.onNodeWithContentDescription("Tontrennung: 2 bis 16 Grautöne").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Graustufen", "Tontrennung (4)"), studio.edits.active.map { it.label })
        compose.onNodeWithText("Grautöne").assertExists()
        Thread.sleep(600); compose.waitForIdle()
        shot("5-werkzeuge")
        compose.onNodeWithContentDescription("Rückgängig").performClick()
        assertEquals(1, studio.edits.position)
        compose.onNodeWithContentDescription("Verlauf").performClick()
        compose.onNodeWithText("2. Tontrennung (4)").assertExists()  // still there to redo
        // Store the sequence in the first slot, then replay it after going back to the original.
        compose.onNodeWithContentDescription("Wiederholen").performClick()
        compose.onNodeWithContentDescription("Platz 1, leer").performScrollTo().performClick()
        compose.onNodeWithText("Aktuelle Filter hier sichern").performClick()
        assertEquals(2, studio.slots[0].steps.size)
        compose.onAllNodesWithText("Original").onLast().performScrollTo().performClick()  // the button (the history lists it too)
        assertEquals(0, studio.edits.position)
        compose.onNodeWithContentDescription("Platz 1, 2 Filter").performScrollTo().performClick()
        compose.onNodeWithText("Graustufen → Tontrennung (4)").assertExists()  // the menu shows what it applies
        compose.onNodeWithText("Auf dieses Bild anwenden").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Graustufen", "Tontrennung (4)"), studio.edits.active.map { it.label })
        compose.onNodeWithText("Fertig").performClick()
    }
}
