package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One chapter of LiCida's own German guide (card 16): shown in the app and printed into the PDF from the same text. */
data class Chapter(val title: String, val paragraphs: List<String>)

/** The guide – written for someone who has never used such an app ("Tante Erna"), without support mail or videos. */
object Guide {
    val chapters = listOf(
        Chapter(tr("Was LiCida macht"), listOf(
            tr("LiCida legt eine Vorlage über das Kamerabild deines Papiers. Du schaust beim Zeichnen auf den Bildschirm und ziehst die Linien der Vorlage auf dem Papier nach – wie mit einer Camera lucida, dem Zeichengerät der alten Meister."),
            tr("Alles bleibt auf deinem Gerät. LiCida hat keinen Internetzugang, sammelt nichts und kostet nichts."))),
        Chapter(tr("Aufbau"), listOf(
            tr("Das Handy muss fest über dem Papier stehen, die Kamera nach unten – auf einem Handyhalter, einem Stativ oder einem Stapel Bücher. Es darf sich beim Zeichnen nicht bewegen."),
            tr("Klebe das Papier fest. Gutes, gleichmäßiges Licht hilft; die Taschenlampe unter „Weitere Werkzeuge“ hellt auf."),
            tr("Zwischen Kamera und Papier brauchst du Platz für deine Hand. Je höher die Kamera, desto größer kann die Zeichnung werden."))),
        Chapter(tr("Vorlage wählen"), listOf(
            tr("Beim ersten Öffnen fragt LiCida: „Was möchtest du zeichnen?“ Wähle ein Bild aus deinen Fotos, aus deinen Dateien (auch Cloud-Ordner, die Android kennt) oder fotografiere etwas."),
            tr("LiCida sieht nur das eine Bild, das du auswählst. Später wechselst du die Vorlage über das kleine Bild unten links, über „Mehr“ (der Knopf mit den drei Punkten) oder die Kamera unten rechts."))),
        Chapter(tr("Vorlage einrichten"), listOf(
            tr("Jetzt bestimmst du, wie groß und wo die Zeichnung wird: Mit zwei Fingern verschiebst, zoomst und drehst du die Vorlage. Dabei wird sie durchsichtig, damit du das Papier darunter siehst."),
            tr("Der Regler „Vorlage“ stellt ein, wie kräftig sie über dem Papier liegt. Der Knopf mit dem Pfeil dreht sie um 90 Grad, der Knopf daneben sichert sie so, wie du sie eingerichtet hast, in deinen Fotos."),
            tr("Tipp: Füll den Bildschirm mit dem, was du zeichnen willst – dann wird die Zeichnung am größten."))),
        Chapter(tr("Kamera wählen und ausrichten"), listOf(
            tr("Der Knopf mit der Blende öffnet die Kamera-Einstellungen. Oben wählst du die Kamera, darunter „Füllen“ oder „Ganzes Bild“. „Ganzes Bild“ zeigt das ganze Blickfeld und erlaubt die größte Zeichnung. Mit zwei Fingern verschiebst und zoomst du das Kamerabild."),
            tr("Steht die Kamera schräg über dem Papier, wirkt das Bild verzerrt. Unter „Korrektur von Hand“ gleichst du Neigung, Höhe und Seitenverhältnis aus, mit dem Hilfsraster als Maß. Spiegeln braucht man für einen Spiegelaufsatz vor der Frontkamera."),
            tr("Bequemer geht es „Automatisch“: Drucke das Zielbild aus (oder nimm eine Zeitschrift als eigenes Bild), leg es flach unter die Kamera und tippe auf „Automatisch ausrichten“. LiCida richtet das Bild aus, als schaue die Kamera senkrecht von oben. Eine gelungene Ausrichtung kannst du sichern und später wiederherstellen."))),
        Chapter(tr("Werkzeuge und Filter"), listOf(
            tr("Der Knopf mit den drei Kreisen öffnet den Werkzeugkasten. Filter verändern die Vorlage und wirken aufeinander: Graustufen, Sepia, Tontrennung (2 bis 16 Grautöne), Lasurwerte, Comic, Neon, Kanten, Schwellwert, Raster, Eckmarken und mehr."),
            tr("Rückgängig, Wiederholen und der Verlauf bringen jeden früheren Schritt zurück. Unter „Eigene Folgen“ merkst du dir Filter und wendest sie später auf andere Bilder an."),
            tr("Die Farbpalette fasst die Vorlage zu wenigen Farben zusammen. Du kannst Farben ersetzen oder angleichen, mit der Pipette aus dem Original nehmen, aus einem Foto übernehmen und als Farbkreis sichern. Beim Zeichnen blendest du einzelne Farben aus."),
            tr("„Farbeffekte“ ändern Helligkeit, Kontrast, Sättigung und Farbton."))),
        Chapter(tr("Zeichnen"), listOf(
            tr("Tippe auf den großen Stift unten in der Mitte. Jetzt sind Kamera und Vorlage gekoppelt: Zoomst oder schiebst du mit zwei Fingern, bewegt sich beides zusammen und bleibt deckungsgleich. Doppeltipp zoomt dreifach heran und wieder zurück."),
            tr("Ein Tipp blendet alle Knöpfe aus und wieder ein. Der Regler unten verschiebt zwischen Kamera und Vorlage."),
            tr("Der Knopf oben rechts stellt scharf. Tippst du mit zwei Fingern gleichzeitig, wird die Belichtung für diese Stelle gesperrt – gut bei dunklem Papier und für den Zeitraffer. Ein Tipp auf „Belichtung gesperrt“ löst sie wieder."),
            tr("Der Pfeil unten links führt zurück zum Einrichten."))),
        Chapter(tr("Weitere Werkzeuge beim Zeichnen"), listOf(
            tr("Der Pfeil unten rechts öffnet weitere Werkzeuge: Taschenlampe, geteilte Ansicht (links Vorlage, rechts Kamera – zum Farbvergleich) und Flimmern (die Vorlage blendet ein und aus; stimmt die Farbe, verschwindet das Flimmern)."),
            tr("„In Fotos sichern“ und „Teilen …“ nehmen ein Bild deiner Zeichenfläche ohne Knöpfe auf. Geteilt wird nur, wohin du es selbst schickst."),
            tr("Mit dem Schalter „Zeitraffer“ erscheint ein Aufnahmeknopf. Das Video landet in deinen Fotos; das Tempo bestimmt, wie kurz es wird."))),
        Chapter(tr("Sitzungen"), listOf(
            tr("Beim Zeichnen sichert der Knopf oben rechts eine Sitzung: Vorlage, Filter, Lage, Kamera und Ausrichtung, dazu ein Schnappschuss deines Blatts. „Neu aufnehmen“ zählt 3, 2, 1, damit deine Hand aus dem Bild ist."),
            tr("Später öffnest du sie über „Mehr“ → „Sitzungen …“. Der Schnappschuss liegt dann über dem Kamerabild: Leg dein Blatt passend hin oder tippe auf „Automatisch ausrichten“. Nach links wischen löscht eine Sitzung."))),
        Chapter(tr("Einstellungen und Tasten"), listOf(
            tr("„Mehr“ → „Einstellungen …“: wie oft Hinweise erscheinen (Aus ist für alte Meister), Bildausschnitt und Schärfe der Kamera, Zeitraffer, die Namen deiner Filterfolgen und der Projektor-Modus, in dem das Kamerabild schwarz bleibt."),
            tr("Beim Zeichnen steuert auch eine Bluetooth-Tastatur, ein Controller oder ein Selfie-Auslöser LiCida: E und Q zoomen, W A S D schieben, Z und C ändern die Deckkraft, R schaltet die Vorlage aus und ein, F das Flimmern, 1 und 3 bewegen den Teiler. In den Einstellungen belegst du jede Taste neu."),
            tr("„Größtmögliche Zeichnung“ im Menü stellt die Kamera auf das ganze, scharfe Bild; „Kamera zurücksetzen“ nimmt alle Korrekturen zurück."))),
        Chapter(tr("Deine Daten"), listOf(
            tr("LiCida hat keinen Internetzugang und keine Werbung, keine Käufe, keine Abos. Bilder, Sitzungen und Einstellungen liegen nur auf diesem Gerät. Fotos, Videos und diese Anleitung landen nur dort, wo du sie sicherst."),
            tr("LiCida ist freie Software (GPL-3.0): Jeder darf sie nutzen, ansehen und verbessern – für immer kostenlos."))),
    )

    /** The guide as a PDF (A4, Inter), for printing or reading elsewhere. */
    fun pdf(context: Context, version: String): ByteArray {
        val document = PdfDocument()
        val width = 595; val height = 842; val margin = 64f
        val inter = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter) }.getOrNull() ?: Typeface.DEFAULT
        fun paint(size: Float, bold: Boolean, grey: Boolean = false) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(inter, if (bold) Typeface.BOLD else Typeface.NORMAL); textSize = size
            color = if (grey) 0xFF6E6E73.toInt() else 0xFF1C1C1E.toInt()
        }
        fun layout(text: String, paint: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, paint, (width - 2 * margin).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()
        var number = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun finish() { page?.let { p ->
            if (number > 1) p.canvas.drawText(tr("LiCida – Anleitung · Seite {number}", "number" to number), margin, height - 32f, paint(8f, false, grey = true))
            document.finishPage(p) } }
        fun newPage() { finish(); number++; page = document.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()); y = margin }
        fun place(text: String, paint: TextPaint, before: Float) {
            val block = layout(text, paint)
            if (y + before + block.height > height - margin) newPage() else y += before
            page!!.canvas.apply { save(); translate(margin, y); block.draw(this); restore() }
            y += block.height
        }
        newPage()
        y = 260f
        place("LiCida", paint(40f, true), 0f)
        place(tr("Zeichnen mit Kamera und Vorlage"), paint(18f, false), 8f)
        place(tr("Anleitung · Version {version}", "version" to version), paint(11f, false, grey = true), 24f)
        place(tr("Ohne Internet, ohne Käufe, ohne Datensammlung. Alles bleibt auf deinem Gerät."), paint(11f, false, grey = true), 6f)
        chapters.forEachIndexed { index, chapter ->
            if (index == 0) newPage()
            place("${index + 1}  ${chapter.title}", paint(16f, true), if (y > margin) 26f else 0f)
            chapter.paragraphs.forEach { place(it, paint(11f, false), 8f) }
        }
        finish()
        return java.io.ByteArrayOutputStream().also { document.writeTo(it); document.close() }.toByteArray()
    }

    /** Into Downloads/LiCida (MediaStore – no storage permission). */
    fun savePdf(context: Context, bytes: ByteArray): Uri? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "LiCida-Anleitung.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/LiCida")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        uri
    }.getOrNull()
}

/** The guide page (handbook p. 11): the tour first, then the chapters as an iOS list; a chapter opens as its own page. */
@Composable
fun GuidePage(onTour: () -> Unit, onPdf: () -> Unit, onDone: () -> Unit) {
    var open by remember { mutableStateOf<Int?>(null) }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    Column(Modifier.fillMaxSize().background(Color.Black).keepTouches().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (open != null) BasicText(tr("‹ Anleitung"), style = style(17f, 400, Ink.yellow),
                modifier = Modifier.clickable(role = Role.Button) { open = null }.padding(4.dp))
            Box(Modifier.weight(1f))
            BasicText(tr("Fertig"), style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onDone).padding(4.dp))
        }
        val chapter = open?.let { Guide.chapters[it] }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 32.dp)) {
            if (chapter != null) {
                BasicText(chapter.title, style = style(28f, 700), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() })
                chapter.paragraphs.forEach { BasicText(it, style = style(17f), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) }
                val next = open!! + 1
                if (next < Guide.chapters.size) BasicText(tr("Weiter: {value} ›", "value" to (Guide.chapters[next].title)), style = style(17f, 400, Ink.yellow),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp).clickable(role = Role.Button) { open = next }.padding(vertical = 6.dp))
            } else {
                BasicText(tr("Anleitung"), style = style(34f, 700), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() })
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E))) {
                    ListRow(tr("Rundgang starten"), Ink.yellow, onTour)
                }
                BasicText(tr("Zeigt dir die Knöpfe beim Einrichten – eine Minute."), style = style(13f, 400, Ink.secondary),
                    modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 6.dp))
                BasicText(tr("Kapitel").uppercase(), style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 36.dp, top = 28.dp, bottom = 6.dp))
                Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E))) {
                    Guide.chapters.forEachIndexed { index, item ->
                        if (index > 0) Line()
                        ListRow("${index + 1}. ${item.title}", Ink.white) { open = index }
                    }
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E))) {
                    ListRow(tr("Anleitung als PDF sichern"), Ink.yellow, onPdf)
                }
                BasicText(tr("Zum Ausdrucken oder Lesen am Computer – landet in „Downloads/LiCida“."), style = style(13f, 400, Ink.secondary),
                    modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 6.dp))
            }
        }
    }
}

@Composable
private fun ListRow(label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style(17f, 400, color), modifier = Modifier.weight(1f))
        if (color == Ink.white) BasicText("›", style = style(20f, 400, Ink.secondary))
    }
}

/** Where the tour's buttons are on screen: each one reports its bounds under a name. */
val LocalTourAnchors = staticCompositionLocalOf<SnapshotStateMap<String, Rect>?> { null }

@Composable
fun Modifier.tourAnchor(name: String): Modifier {
    val anchors = LocalTourAnchors.current ?: return this
    return this.onGloballyPositioned { anchors[name] = it.boundsInRoot() }
}

/** One stop of the guided tour (handbook p. 11): which button, and what it does. No anchor: a note in the middle. */
data class TourStep(val anchor: String?, val title: String, val text: String)

object Tour {
    val steps = listOf(
        TourStep("mehr", tr("Mehr"), tr("Bilder aus Dateien, gesicherte Sitzungen, die größtmögliche Zeichnung, Einstellungen und diese Hilfe.")),
        TourStep("kamera", tr("Kamera"), tr("Welche Kamera, wie viel vom Bild – und die Ausrichtung, damit das Papier wie von oben gesehen wirkt.")),
        TourStep("werkzeuge", tr("Werkzeuge und Filter"), tr("Graustufen, Tontrennung, Kanten, Raster und mehr; dazu Farbpalette und Farbeffekte.")),
        TourStep("drehen", tr("Drehen"), tr("Dreht die Vorlage um 90 Grad.")),
        TourStep("sichern", tr("Vorlage sichern"), tr("Legt die Vorlage so, wie du sie eingerichtet hast, in deine Fotos.")),
        TourStep(null, tr("Größe und Lage"), tr("Mit zwei Fingern verschiebst, zoomst und drehst du die Vorlage. So bestimmst du, wie groß und wo deine Zeichnung auf dem Papier wird.")),
        TourStep("deckkraft", tr("Deckkraft"), tr("Wie kräftig die Vorlage über dem Papier liegt.")),
        TourStep("fotos", tr("Vorlage aus Fotos"), tr("Ein Bild aus deinen Fotos. LiCida sieht nur das, welches du auswählst.")),
        TourStep("zeichnen", tr("Zeichnen"), tr("Koppelt Kamera und Vorlage. Zoomen und Schieben bewegt dann beides – das Handy bleibt ab jetzt ruhig stehen.")),
        TourStep("fotografieren", tr("Vorlage fotografieren"), tr("Fotografiere etwas als Vorlage.")),
    )
}

/** The tour: everything dimmed except the button it is about, a card beside it (iOS-style coach mark). */
@Composable
fun TourOverlay(index: Int, anchors: Map<String, Rect>, onNext: () -> Unit, onSkip: () -> Unit) {
    val step = Tour.steps[index]
    val hole = step.anchor?.let { anchors[it] }
    val density = LocalDensity.current
    var screenHeight by remember { mutableStateOf(0) }
    var cardHeight by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().keepTouches().onSizeChanged { screenHeight = it.height }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            hole?.let { r ->
                val pad = 8.dp.toPx()
                val radius = CornerRadius(minOf(r.width, r.height) / 2 + pad)
                drawRoundRect(Color.Black, Offset(r.left - pad, r.top - pad), androidx.compose.ui.geometry.Size(r.width + 2 * pad, r.height + 2 * pad),
                    radius, blendMode = BlendMode.Clear)
                drawRoundRect(Ink.yellow, Offset(r.left - pad, r.top - pad), androidx.compose.ui.geometry.Size(r.width + 2 * pad, r.height + 2 * pad),
                    radius, style = Stroke(2.dp.toPx()))
            }
        }
        val gap = with(density) { 20.dp.toPx() }
        // Below the button when it is in the upper half, else above it; no button: in the middle.
        val top = when {
            hole == null -> (screenHeight - cardHeight) / 2f
            hole.center.y < screenHeight / 2f -> hole.bottom + gap
            else -> hole.top - gap - cardHeight
        }
        Column(Modifier.offset { IntOffset(0, top.roundToInt().coerceAtLeast(0)) }.fillMaxWidth().padding(horizontal = 24.dp)
            .onSizeChanged { cardHeight = it.height }.widthIn(max = 420.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2C2C2E))
            .padding(16.dp)) {
            BasicText(step.title, style = style(17f, 600), modifier = Modifier.semantics { heading() })
            BasicText(step.text, style = style(15f, 400, Ink.secondary), modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Überspringen"), style = style(15f, 400, Ink.secondary), modifier = Modifier.clickable(role = Role.Button, onClick = onSkip).padding(4.dp))
                BasicText(tr("{value} von {size}", "value" to (index + 1), "size" to Tour.steps.size), style = style(13f, 400, Ink.secondary, tabular = true),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                BasicText(if (index == Tour.steps.lastIndex) tr("Fertig") else tr("Weiter"), style = style(17f, 600, Ink.yellow),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onNext).padding(4.dp))
            }
        }
    }
}

/** A short hint at the top (one per help level, card 15/16), like the gesture hint. */
@Composable
fun HintPill(symbol: Symbol, text: String, modifier: Modifier = Modifier) {
    Row(modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, start = 12.dp, end = 12.dp)
        .clip(RoundedCornerShape(50)).background(Ink.glassStrong).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(symbol, Ink.white, size = 18.dp)
        BasicText(text, style = style(13f, 500), modifier = Modifier.padding(start = 8.dp))
    }
}
