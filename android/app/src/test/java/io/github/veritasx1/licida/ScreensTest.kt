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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
        // With a dialog open there are two windows: the dialog's is the last one.
        if (out != null) compose.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().captureRoboImage("$out/$name.png")
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
        // Correction by hand (card 6): stretch + and the helper grid; the sheet shows the automatic part (card 7).
        compose.onNodeWithContentDescription("Breite vergrößern").performScrollTo().performClick()
        compose.waitForIdle()
        assert(studio.correction.stretchX > 1f) { studio.correction.encode() }
        compose.onNodeWithContentDescription("Hilfsraster einblenden").performScrollTo().performClick()
        assertEquals(true, studio.helperGhost)
        compose.onNodeWithText("Automatisch ausrichten").performScrollTo().assertExists()
        shot("6-korrektur")
        compose.onNodeWithText("Korrektur zurücksetzen").performScrollTo().performClick()
        assertEquals(true, studio.correction.isPlain)
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
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription("Tontrennung: 2 bis 16 Grautöne"))
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

    @Test
    fun colourPalette() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Werkzeuge und Filter").performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("Farbpalette: Wenige Farben, einzeln ein- und ausblendbar").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Farbpalette: Wenige Farben, einzeln ein- und ausblendbar").performClick()
        compose.waitUntil(8000) { runCatching { compose.onNodeWithText("Anwenden").assertExists() }.isSuccess }
        compose.waitUntil(8000) { runCatching { compose.onNodeWithText("Farbpalette").assertExists() }.isSuccess }  // found
        Thread.sleep(500); compose.waitForIdle()
        shot("7-palette")
        // The selected swatch tapped again: the colour picker.
        compose.onAllNodesWithContentDescription("Farbe")[0].performClick()
        compose.onAllNodesWithContentDescription("Farbe")[0].performClick()
        compose.onNodeWithText("Vorher").assertExists()
        compose.onNodeWithText("Regler").performClick()
        shot("8-farbwahl")
        compose.onAllNodesWithText("Fertig").onLast().performClick()
        compose.onNodeWithText("Anwenden").performClick()
        compose.waitForIdle()
        val step = studio.edits.active.single()
        assertEquals(Filter.ColourPalette, step.filter)
        // Drawing with colour layers: one off – the layer row is there, the button shrinks.
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("Palette bearbeiten").assertExists() }.isSuccess }
        compose.onAllNodesWithContentDescription("Farbebene ein")[0].performClick()
        compose.onNodeWithContentDescription("Farbebene aus").assertExists()
        Thread.sleep(500); compose.waitForIdle()
        shot("9-farbebenen")
        compose.onNodeWithContentDescription("Alle Farben ein").performClick()
        compose.onAllNodesWithContentDescription("Farbebene aus").assertCountEquals(0)
    }

    @Test
    fun colourEffects() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Werkzeuge und Filter").performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("Farbeffekte: Helligkeit, Kontrast, Sättigung, Farbton").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Farbeffekte: Helligkeit, Kontrast, Sättigung, Farbton").performClick()
        compose.onNodeWithContentDescription("Zeichnen").assertDoesNotExist()      // everything else waits
        compose.onNodeWithContentDescription("Sättigung").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.9f) }
        compose.onNodeWithContentDescription("Farbton").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.8f) }
        shot("10-farbeffekte")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.waitForIdle()
        val step = studio.edits.active.single()
        assertEquals(Filter.ColourEffects, step.filter)
        assertEquals(1.8f, step.effects!!.saturation, 0.01f)
        compose.onNodeWithContentDescription("Zeichnen").assertExists()
    }

    @Test
    fun moreToolsWhileDrawing() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Zeichnen").performClick()
        compose.onNodeWithContentDescription("Weitere Werkzeuge").performClick()
        compose.onNodeWithText("Werkzeuge beim Zeichnen").assertExists()
        compose.onNodeWithText("Diese Kamera hat kein Licht").assertExists()        // no flashlight here: switch off
        compose.onNodeWithContentDescription("Geteilte Ansicht").performClick()
        compose.onNodeWithContentDescription("Flimmern").performClick()
        compose.onNodeWithContentDescription("Tempo des Flimmerns").assertExists()
        shot("11-werkzeuge-zeichnen")
        compose.onNodeWithText("Fertig").performClick()
        compose.onNodeWithContentDescription("Teiler verschieben").assertExists()
        compose.onNodeWithContentDescription("Weitere Werkzeuge").performClick()
        compose.onNodeWithContentDescription("Flimmern").performClick()   // off: the split alone
        compose.onNodeWithText("Fertig").performClick()
        shot("12-geteilt")
    }

    @Test
    fun sessionsKeptAndRestored() {
        // Card 14: a session on disk – the list shows it, opening it brings back the reference and the alignment step.
        Sessions.folder(context).deleteRecursively()
        Reference.keep(context, sample())
        val session = Session("1791100000000", "Blume im Garten", 1791100000000, Placement(0.8f, 5f), 0.6f, 0.4f, null, true,
            CameraView(), Edits().add(Step(Filter.Gray)), Correction(tilt = 4f))
        assertEquals(true, Sessions.save(context, session, Reference.file(context), sample()))
        val kept = Sessions.list(context).single()
        assertEquals(session, kept)
        org.junit.Assert.assertNotNull("Schnappschuss", Sessions.snapshot(context, kept.id))
        org.junit.Assert.assertNotNull("Vorlage", Reference.restore(context))

        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Mehr").performClick()
        compose.onNodeWithText("Aus Dateien").assertExists()
        shot("14-mehr")
        compose.onNodeWithText("Sitzungen …").performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("Blume im Garten").fetchSemanticsNodes().isNotEmpty() }
        shot("15-sitzungen")
        compose.onNodeWithText("Blume im Garten").performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText("Blatt wiederfinden").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Schnappschuss der Sitzung").assertExists()
        assertEquals(Placement(0.8f, 5f), studio.placement)
        assertEquals(1, studio.edits.active.size)
        assertEquals(4f, studio.correction.tilt)
        shot("16-wiederfinden")
        compose.onNodeWithText("Fertig").performClick()
        compose.onNodeWithContentDescription("Schnappschuss der Sitzung").assertDoesNotExist()

        Sessions.delete(context, kept.id)
        assertEquals(0, Sessions.list(context).size)
    }

    @Test
    fun saveSessionSheetWhileDrawing() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits(); sessionButton = true }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Zeichnen").performClick()
        compose.onNodeWithContentDescription("Sitzung sichern").performClick()
        compose.onNodeWithText("Neu aufnehmen").assertExists()
        compose.onNodeWithContentDescription("Beschreibung").assertExists()
        shot("13-sitzung-sichern")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithText("Neu aufnehmen").assertDoesNotExist()
    }

    @Test
    fun settingsPage() {
        // Card 15: from the Mehr menu; a key row takes the next key pressed.
        val studio = Studio(context)
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription("Mehr").performClick()
        shot("17-mehr-menue")
        compose.onNodeWithText("Einstellungen …").performClick()
        compose.onNodeWithText("Einstellungen").assertExists()
        shot("18-einstellungen")
        compose.onNodeWithText("Vorlage aus/ein").performScrollTo().performClick()
        compose.onNodeWithText("Taste drücken …").assertExists()
        KeyHub.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP))
        compose.waitForIdle()
        compose.onNodeWithText("Lauter").assertExists()
        assertEquals(KeyAction.Toggle, studio.keymap.action(android.view.KeyEvent.KEYCODE_VOLUME_UP))
        shot("19-tasten")
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText("Projektor-Modus"))
        compose.onNodeWithContentDescription("Projektor-Modus").performClick()
        assertEquals(true, studio.projector)
        compose.onNodeWithText("Fertig").performClick()
        compose.onNodeWithText("Einstellungen").assertDoesNotExist()
    }

    @Test
    fun keysWhileDrawing() {
        val studio = Studio(context).apply { drawOpacity = 0.5f }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}, startMode = Mode.Draw) }
        fun press(code: Int) {
            KeyHub.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
            KeyHub.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
            compose.waitForIdle()
        }
        press(android.view.KeyEvent.KEYCODE_E)
        compose.onNodeWithText("1,3×").assertExists()
        press(android.view.KeyEvent.KEYCODE_C)
        assertEquals(0.6f, studio.drawOpacity, 1e-4f)
        press(android.view.KeyEvent.KEYCODE_R)
        compose.onNodeWithText("Vorlage aus").assertExists()
        // A key nobody has is left to Android.
        assertEquals(false, KeyHub.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_X)))
    }

    @Test
    fun projectorHidesTheCamera() {
        val studio = Studio(context).apply { projector = true }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}, startMode = Mode.Draw) }
        shot("20-projektor")
    }
}
