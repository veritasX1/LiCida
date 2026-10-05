package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

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

    /** The welcome tour is offered at a first start – in these tests only where it is the subject. */
    @org.junit.Before
    fun tourAlreadyOffered() { Studio(context).tourOffered = true }

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
        compose.onNodeWithText(tr("Was möchtest du zeichnen?")).assertExists()
        compose.onNodeWithContentDescription(tr("Zeichnen")).assertExists()
        shot("1-start")
    }

    @Test
    fun cameraAskedFirst() {
        compose.setContent { LiCidaApp(Studio(context), null, cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithText(tr("LiCida braucht die Kamera")).assertExists()
        shot("0-kamera")
    }

    @Test
    fun composeThenDraw() {
        val studio = Studio(context).apply { hintSeen = false; placement = Placement(scale = 0.9f, rotation = -8f) }
        var drawing = false
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = { drawing = it }) }
        compose.onNodeWithText(tr("Mit zwei Fingern verschieben, zoomen und drehen")).assertExists()
        shot("2-einrichten")
        compose.onNodeWithContentDescription(tr("Zeichnen")).performClick()
        compose.waitForIdle()
        assertEquals(true, drawing)
        compose.onNodeWithContentDescription(tr("Zurück zum Einrichten")).assertExists()
        shot("3-zeichnen")
        compose.onNodeWithContentDescription(tr("Zurück zum Einrichten")).performClick()
        compose.waitForIdle()
        assertEquals(false, drawing)
    }

    @Test
    fun cameraSheet() {
        val studio = Studio(context).apply { hintSeen = true; cameraKey = null; ghost = true }
        val moto = listOf(CameraFacts("0", Facing.Back, 5.56f, 8.16f), CameraFacts("1", Facing.Front, 3.27f, 4.608f),
            CameraFacts("2", Facing.Back, 1.66f, 3.6736f))
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}, cameras = { moto }) }
        compose.onNodeWithContentDescription(tr("Kamera wählen und einstellen")).performClick()
        compose.waitUntil(3000) { runCatching { compose.onNodeWithText(tr("{kind} · {toInt}° Bildwinkel", "kind" to tr("Ultraweitwinkel"), "toInt" to 95)).assertExists() }.isSuccess }
        shot("4-kamera-blatt")
        compose.onNodeWithText(tr("Frontkamera")).performClick()
        compose.waitForIdle()
        assertEquals("front", studio.cameraKey)
        assertEquals(true, studio.cameraView.flipV)  // the front camera looks through a mirror
        compose.onNodeWithText(tr("Ganzes Bild")).performClick()
        assertEquals(false, studio.fill)
        // Correction by hand (card 6): stretch + and the helper grid; the sheet shows the automatic part (card 7).
        compose.onNodeWithContentDescription(tr("{value} vergrößern", "value" to tr("Breite"))).performScrollTo().performClick()
        compose.waitForIdle()
        assert(studio.correction.stretchX > 1f) { studio.correction.encode() }
        compose.onNodeWithContentDescription(tr("Hilfsraster einblenden")).performScrollTo().performClick()
        assertEquals(true, studio.helperGhost)
        compose.onNodeWithText(tr("Automatisch ausrichten")).performScrollTo().assertExists()
        shot("6-korrektur")
        compose.onNodeWithText(tr("Korrektur zurücksetzen")).performScrollTo().performClick()
        assertEquals(true, studio.correction.isPlain)
        compose.onNodeWithText(tr("Fertig")).performClick()
        compose.onNodeWithContentDescription(tr("Zeichnen")).assertExists()
    }

    @Test
    fun toolbox() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits(); slots = List(3) { Slot.decode(null, it) } }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Werkzeuge und Filter")).performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("${tr("Graustufen")}: ${tr("Ohne Farbe")}").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("${tr("Graustufen")}: ${tr("Ohne Farbe")}").performClick()
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription("${tr("Tontrennung")}: ${tr("2 bis 16 Grautöne")}"))
        compose.onNodeWithContentDescription("${tr("Tontrennung")}: ${tr("2 bis 16 Grautöne")}").performClick()
        compose.waitForIdle()
        assertEquals(listOf(tr("Graustufen"), "${tr("Tontrennung")} (4)"), studio.edits.active.map { it.label })
        compose.onNodeWithText(tr("Grautöne")).assertExists()
        Thread.sleep(600); compose.waitForIdle()
        shot("5-werkzeuge")
        compose.onNodeWithContentDescription(tr("Rückgängig")).performClick()
        assertEquals(1, studio.edits.position)
        compose.onNodeWithContentDescription(tr("Verlauf")).performClick()
        compose.onNodeWithText("2. ${tr("Tontrennung")} (4)").assertExists()  // still there to redo
        // Store the sequence in the first slot, then replay it after going back to the original.
        compose.onNodeWithContentDescription(tr("Wiederholen")).performClick()
        compose.onNodeWithContentDescription("${tr("Platz {value}", "value" to 1)}, ${tr("leer")}").performScrollTo().performClick()
        compose.onNodeWithText(tr("Aktuelle Filter hier sichern")).performClick()
        assertEquals(2, studio.slots[0].steps.size)
        compose.onAllNodesWithText(tr("Original")).onLast().performScrollTo().performClick()  // the button (the history lists it too)
        assertEquals(0, studio.edits.position)
        compose.onNodeWithContentDescription("${tr("Platz {value}", "value" to 1)}, ${tr("{size} Filter", "size" to 2)}").performScrollTo().performClick()
        compose.onNodeWithText("${tr("Graustufen")} → ${tr("Tontrennung")} (4)").assertExists()  // the menu shows what it applies
        compose.onNodeWithText(tr("Auf dieses Bild anwenden")).performClick()
        compose.waitForIdle()
        assertEquals(listOf(tr("Graustufen"), "${tr("Tontrennung")} (4)"), studio.edits.active.map { it.label })
        compose.onNodeWithText(tr("Fertig")).performClick()
    }

    @Test
    fun colourPalette() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Werkzeuge und Filter")).performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("${tr("Farbpalette")}: ${tr("Wenige Farben, einzeln ein- und ausblendbar")}").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("${tr("Farbpalette")}: ${tr("Wenige Farben, einzeln ein- und ausblendbar")}").performClick()
        compose.waitUntil(8000) { runCatching { compose.onNodeWithText(tr("Anwenden")).assertExists() }.isSuccess }
        compose.waitUntil(8000) { runCatching { compose.onNodeWithText(tr("Farbpalette")).assertExists() }.isSuccess }  // found
        Thread.sleep(500); compose.waitForIdle()
        shot("7-palette")
        // The selected swatch tapped again: the colour picker.
        compose.onAllNodesWithContentDescription(tr("Farbe"))[0].performClick()
        compose.onAllNodesWithContentDescription(tr("Farbe"))[0].performClick()
        compose.onNodeWithText(tr("Vorher")).assertExists()
        compose.onNodeWithText(tr("Regler")).performClick()
        shot("8-farbwahl")
        compose.onAllNodesWithText(tr("Fertig")).onLast().performClick()
        compose.onNodeWithText(tr("Anwenden")).performClick()
        compose.waitForIdle()
        val step = studio.edits.active.single()
        assertEquals(Filter.ColourPalette, step.filter)
        // Drawing with colour layers: one off – the layer row is there, the button shrinks.
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription(tr("Palette bearbeiten")).assertExists() }.isSuccess }
        compose.onAllNodesWithContentDescription(tr("Farbebene ein"))[0].performClick()
        compose.onNodeWithContentDescription(tr("Farbebene aus")).assertExists()
        Thread.sleep(500); compose.waitForIdle()
        shot("9-farbebenen")
        compose.onNodeWithContentDescription(tr("Alle Farben ein")).performClick()
        compose.onAllNodesWithContentDescription(tr("Farbebene aus")).assertCountEquals(0)
    }

    @Test
    fun colourEffects() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = false, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Werkzeuge und Filter")).performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithContentDescription("${tr("Farbeffekte")}: ${tr("Helligkeit, Kontrast, Sättigung, Farbton")}").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("${tr("Farbeffekte")}: ${tr("Helligkeit, Kontrast, Sättigung, Farbton")}").performClick()
        compose.onNodeWithContentDescription(tr("Zeichnen")).assertDoesNotExist()      // everything else waits
        compose.onNodeWithContentDescription(tr("Sättigung")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.9f) }
        compose.onNodeWithContentDescription(tr("Farbton")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.8f) }
        shot("10-farbeffekte")
        compose.onNodeWithText(tr("Übernehmen")).performClick()
        compose.waitForIdle()
        val step = studio.edits.active.single()
        assertEquals(Filter.ColourEffects, step.filter)
        assertEquals(1.8f, step.effects!!.saturation, 0.01f)
        compose.onNodeWithContentDescription(tr("Zeichnen")).assertExists()
    }

    @Test
    fun moreToolsWhileDrawing() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits() }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Zeichnen")).performClick()
        compose.onNodeWithContentDescription(tr("Weitere Werkzeuge")).performClick()
        compose.onNodeWithText(tr("Werkzeuge beim Zeichnen")).assertExists()
        compose.onNodeWithText(tr("Diese Kamera hat kein Licht")).assertExists()        // no flashlight here: switch off
        compose.onNodeWithContentDescription(tr("Geteilte Ansicht")).performClick()
        compose.onNodeWithContentDescription(tr("Flimmern")).performClick()
        compose.onNodeWithContentDescription(tr("Tempo des Flimmerns")).assertExists()
        shot("11-werkzeuge-zeichnen")
        compose.onNodeWithText(tr("Fertig")).performClick()
        compose.onNodeWithContentDescription(tr("Teiler verschieben")).assertExists()
        compose.onNodeWithContentDescription(tr("Weitere Werkzeuge")).performClick()
        compose.onNodeWithContentDescription(tr("Flimmern")).performClick()   // off: the split alone
        compose.onNodeWithText(tr("Fertig")).performClick()
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
        compose.onNodeWithContentDescription(tr("Mehr")).performClick()
        compose.onNodeWithText(tr("Aus Dateien")).assertExists()
        shot("14-mehr")
        compose.onNodeWithText(tr("Sitzungen …")).performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText(tr("Blume im Garten")).fetchSemanticsNodes().isNotEmpty() }
        shot("15-sitzungen")
        compose.onNodeWithText(tr("Blume im Garten")).performClick()
        compose.waitUntil(3000) { compose.onAllNodesWithText(tr("Blatt wiederfinden")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(tr("Schnappschuss der Sitzung")).assertExists()
        assertEquals(Placement(0.8f, 5f), studio.placement)
        assertEquals(1, studio.edits.active.size)
        assertEquals(4f, studio.correction.tilt)
        shot("16-wiederfinden")
        compose.onNodeWithText(tr("Fertig")).performClick()
        compose.onNodeWithContentDescription(tr("Schnappschuss der Sitzung")).assertDoesNotExist()

        Sessions.delete(context, kept.id)
        assertEquals(0, Sessions.list(context).size)
    }

    @Test
    fun saveSessionSheetWhileDrawing() {
        val studio = Studio(context).apply { hintSeen = true; edits = Edits(); sessionButton = true }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Zeichnen")).performClick()
        compose.onNodeWithContentDescription(tr("Sitzung sichern")).performClick()
        compose.onNodeWithText(tr("Neu aufnehmen")).assertExists()
        compose.onNodeWithContentDescription(tr("Beschreibung")).assertExists()
        shot("13-sitzung-sichern")
        compose.onNodeWithText(tr("Abbrechen")).performClick()
        compose.onNodeWithText(tr("Neu aufnehmen")).assertDoesNotExist()
    }

    @Test
    fun settingsPage() {
        // Card 15: from the Mehr menu; a key row takes the next key pressed.
        val studio = Studio(context)
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Mehr")).performClick()
        shot("17-mehr-menue")
        compose.onNodeWithText(tr("Einstellungen …")).performClick()
        compose.onNodeWithText(tr("Einstellungen")).assertExists()
        shot("18-einstellungen")
        compose.onNodeWithText(tr("Vorlage aus/ein")).performScrollTo().performClick()
        compose.onNodeWithText(tr("Taste drücken …")).assertExists()
        KeyHub.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP))
        compose.waitForIdle()
        compose.onNodeWithText(tr("Lauter")).assertExists()
        assertEquals(KeyAction.Toggle, studio.keymap.action(android.view.KeyEvent.KEYCODE_VOLUME_UP))
        shot("19-tasten")
        compose.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText(tr("Projektor-Modus")))
        compose.onNodeWithContentDescription(tr("Projektor-Modus")).performClick()
        assertEquals(true, studio.projector)
        compose.onNodeWithText(tr("Fertig")).performClick()
        compose.onNodeWithText(tr("Einstellungen")).assertDoesNotExist()
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
        compose.onNodeWithText(tr("1,3×")).assertExists()
        press(android.view.KeyEvent.KEYCODE_C)
        assertEquals(0.6f, studio.drawOpacity, 1e-4f)
        press(android.view.KeyEvent.KEYCODE_R)
        compose.onNodeWithText(tr("Vorlage aus")).assertExists()
        // A key nobody has is left to Android.
        assertEquals(false, KeyHub.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_X)))
    }

    @Test
    fun projectorHidesTheCamera() {
        val studio = Studio(context).apply { projector = true }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}, startMode = Mode.Draw) }
        shot("20-projektor")
    }

    @Test
    fun firstStartOffersTheTour() {
        // Card 16: once, at the first start; the tour walks over every button of the setup screen.
        val studio = Studio(context).apply { tourOffered = false }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithText(tr("Willkommen bei LiCida")).assertExists()
        shot("21-willkommen")
        compose.onNodeWithText(tr("Rundgang")).performClick()
        assertEquals(true, studio.tourOffered)
        compose.onNodeWithText(tr("{value} von {size}", "value" to 1, "size" to Tour.steps.size)).assertExists()
        shot("22-rundgang-mehr")
        repeat(8) { compose.onNodeWithText(tr("Weiter")).performClick() }
        compose.onNodeWithText(tr("{value} von {size}", "value" to 9, "size" to Tour.steps.size)).assertExists()
        shot("23-rundgang-zeichnen")
        compose.onNodeWithText(tr("Weiter")).performClick()
        compose.onNodeWithText(tr("Fertig")).performClick()
        compose.onNodeWithText(tr("Fertig")).assertDoesNotExist()
        compose.onNodeWithText(tr("Willkommen bei LiCida")).assertDoesNotExist()
    }

    @Test
    fun noTourOfferForOldMasters() {
        val studio = Studio(context).apply { tourOffered = false; help = HelpLevel.Off }
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithText(tr("Willkommen bei LiCida")).assertDoesNotExist()
        compose.onNodeWithText(tr("Mit zwei Fingern verschieben, zoomen und drehen")).assertDoesNotExist()
    }

    @Test
    fun guideFromTheMenu() {
        compose.setContent { LiCidaApp(Studio(context), sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}) }
        compose.onNodeWithContentDescription(tr("Mehr")).performClick()
        compose.onNodeWithText(tr("Hilfe …")).performClick()
        compose.onNodeWithText(tr("Anleitung")).assertExists()
        shot("24-anleitung")
        compose.onNodeWithText("7. ${tr("Zeichnen")}").performClick()
        compose.onNodeWithText(tr("‹ Anleitung")).assertExists()
        shot("25-kapitel")
        compose.onNodeWithText(tr("Weiter: {value} ›", "value" to tr("Weitere Werkzeuge beim Zeichnen"))).performClick()
        compose.onNodeWithText(tr("Weitere Werkzeuge beim Zeichnen")).assertExists()
        compose.onNodeWithText(tr("‹ Anleitung")).performClick()
        compose.onNodeWithText(tr("Rundgang starten")).performClick()
        compose.onNodeWithText(tr("{value} von {size}", "value" to 1, "size" to Tour.steps.size)).assertExists()
    }

    @Test
    fun drawHintGoesAtTheFirstTap() {
        val studio = Studio(context)
        compose.setContent { LiCidaApp(studio, sample(), cameraAllowed = true, onAskCamera = {}, onDrawMode = {}, startMode = Mode.Draw) }
        compose.onNodeWithText(tr("Tippen blendet die Knöpfe aus · Doppeltipp zoomt")).assertExists()
        assertEquals(1, studio.hintCount("zeichnen"))
    }
}
