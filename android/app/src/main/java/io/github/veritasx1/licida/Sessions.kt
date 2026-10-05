package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import kotlin.math.roundToInt

/** A kept drawing session (card 14, handbook p. 41–44): what it is, when, and everything needed to carry on –
 *  the reference (copied), the filters, where the reference lay, the camera and its straightening. The snapshot of
 *  the paper (camera only, as seen) is kept beside it to find the paper again. All in LiCida's own folder. */
data class Session(val id: String, val description: String, val created: Long, val placement: Placement, val opacity: Float,
                   val drawOpacity: Float, val cameraKey: String?, val fill: Boolean, val cameraView: CameraView,
                   val edits: Edits, val correction: Correction) {

    fun encode(): Properties = Properties().apply {
        setProperty("beschreibung", description); setProperty("erstellt", created.toString())
        setProperty("platz", "${placement.scale};${placement.rotation};${placement.offset.x};${placement.offset.y}")
        setProperty("deckkraft", "$opacity;$drawOpacity")
        cameraKey?.let { setProperty("kamera", it) }
        setProperty("fuellen", fill.toString())
        setProperty("kameraansicht", "${cameraView.zoom};${cameraView.offset.x};${cameraView.offset.y};${cameraView.flipH};${cameraView.flipV}")
        setProperty("filter", edits.encode()); setProperty("korrektur", correction.encode())
    }

    companion object {
        fun decode(id: String, p: Properties): Session? = runCatching {
            fun floats(key: String) = p.getProperty(key).split(";").map { it.toFloat() }
            val place = floats("platz"); val alpha = floats("deckkraft")
            val view = p.getProperty("kameraansicht").split(";")
            Session(id, p.getProperty("beschreibung", ""), p.getProperty("erstellt").toLong(),
                Placement(place[0], place[1], Offset(place[2], place[3])), alpha[0], alpha[1], p.getProperty("kamera"),
                p.getProperty("fuellen") == "true",
                CameraView(view[0].toFloat(), Offset(view[1].toFloat(), view[2].toFloat()), view[3] == "true", view[4] == "true"),
                Edits.decode(p.getProperty("filter")), Correction.decode(p.getProperty("korrektur")))
        }.getOrNull()

        fun defaultDescription(time: Long = System.currentTimeMillis()) =
            tr("Zeichnung vom {date}", "date" to SimpleDateFormat(tr("d. MMMM yyyy, HH:mm"), Locale.forLanguageTag(io.github.veritasx1.licida.i18n.I18n.language())).format(Date(time)))

        fun dateText(time: Long) = SimpleDateFormat(tr("d. MMM yyyy, HH:mm"), Locale.forLanguageTag(io.github.veritasx1.licida.i18n.I18n.language())).format(Date(time))
    }
}

/** The kept sessions on this phone: filesDir/sitzungen/<id>/ with sitzung.properties, vorlage.jpg and bild.jpg. */
object Sessions {
    fun folder(context: Context) = File(context.filesDir, "sitzungen")
    private fun dir(context: Context, id: String) = File(folder(context), id)
    fun reference(context: Context, id: String) = File(dir(context, id), "vorlage.jpg")
    fun snapshotFile(context: Context, id: String) = File(dir(context, id), "bild.jpg")

    /** Keep a session: the current reference file is copied, the snapshot written beside it. */
    fun save(context: Context, session: Session, reference: File, snapshot: Bitmap): Boolean = runCatching {
        val target = dir(context, session.id)
        target.mkdirs()
        reference.copyTo(reference(context, session.id), overwrite = true)
        snapshotFile(context, session.id).outputStream().use { Reference.scaled(snapshot, 1600).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // Written last: a session without its description file does not count (a half-written one stays invisible).
        val temporary = File(target, "sitzung.properties.tmp")
        temporary.outputStream().use { session.encode().store(it, "LiCida-Sitzung") }
        temporary.renameTo(File(target, "sitzung.properties"))
    }.getOrDefault(false)

    /** Newest first. */
    fun list(context: Context): List<Session> = (folder(context).listFiles() ?: emptyArray()).mapNotNull { dir ->
        val file = File(dir, "sitzung.properties")
        if (!file.exists()) null else Session.decode(dir.name, Properties().apply { file.inputStream().use { load(it) } })
    }.sortedByDescending { it.created }

    fun delete(context: Context, id: String) { dir(context, id).deleteRecursively() }

    fun snapshot(context: Context, id: String, side: Int = 1600): Bitmap? = decode(snapshotFile(context, id), side)

    /** For the list: the reference tells sessions apart, the snapshot of a blank sheet does not. */
    fun thumbnail(context: Context, id: String): Bitmap? = decode(reference(context, id), 200) ?: snapshot(context, id, 200)

    private fun decode(file: File, side: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val sample = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / side).coerceAtLeast(1))
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun newId(time: Long = System.currentTimeMillis()) = time.toString()
}

/** The sheet's frame: grabber, Abbrechen · Title · action (iOS). */
@Composable
private fun SheetFrame(title: String, left: String?, right: String?, rightEnabled: Boolean = true, onLeft: () -> Unit, onRight: () -> Unit,
                       onOutside: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onOutside), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().imePadding().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xFF1C1C1E))
            .keepTouches().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Color(0x66EBEBF5)))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                left?.let { BasicText(it, style = style(17f, 400, Ink.yellow), modifier = Modifier.align(Alignment.CenterStart).clickable(role = Role.Button, onClick = onLeft).padding(4.dp)) }
                BasicText(title, style = style(17f, 600), modifier = Modifier.align(Alignment.Center))
                right?.let { BasicText(it, style = style(17f, 600, if (rightEnabled) Ink.yellow else Ink.secondary),
                    modifier = Modifier.align(Alignment.CenterEnd).clickable(enabled = rightEnabled, role = Role.Button, onClick = onRight).padding(4.dp)) }
            }
            content()
        }
    }
}

/** "Sitzung sichern": the snapshot (with "Neu aufnehmen" – 3, 2, 1, so the hand is out of the picture) and a
 *  description. */
@Composable
fun SaveSessionSheet(snapshot: Bitmap?, description: String, onDescription: (String) -> Unit, onRetake: () -> Unit,
                     onCancel: () -> Unit, onSave: () -> Unit) {
    SheetFrame(tr("Sitzung sichern"), tr("Abbrechen"), tr("Sichern"), snapshot != null, onCancel, onSave, onCancel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val image = remember(snapshot) { snapshot?.asImageBitmap() }
                Box(Modifier.size(96.dp, 150.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image, tr("Schnappschuss der Zeichenfläche"), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else BasicText("…", style = style(17f, 600, Ink.secondary))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    BasicText(tr("Der Schnappschuss zeigt dein Blatt, wie die Kamera es jetzt sieht. Damit findet LiCida es beim Wiederherstellen wieder."),
                        style = style(13f, 400, Ink.secondary))
                    BasicText(tr("Neu aufnehmen"), style = style(15f, 600, Ink.yellow), modifier = Modifier.padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color(0xFF2C2C2E)).clickable(role = Role.Button, onClick = onRetake)
                        .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            BasicText(tr("Beschreibung").uppercase(), style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                BasicTextField(description, onDescription, singleLine = true, textStyle = style(17f), cursorBrush = SolidColor(Ink.yellow),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr("Beschreibung") })
            }
            BasicText(tr("Gesichert werden Vorlage, Filter, Lage der Vorlage, Kamera und Ausrichtung – nur auf diesem Gerät."),
                style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** "Sitzungen": the kept ones, newest first; tap to carry on, swipe left to delete (iOS list). */
@Composable
fun SessionsSheet(sessions: List<Session>, thumbnail: (Session) -> Bitmap?, onOpen: (Session) -> Unit, onDelete: (Session) -> Unit, onClose: () -> Unit) {
    SheetFrame(tr("Sitzungen"), null, tr("Fertig"), true, {}, onClose, onClose) {
        if (sessions.isEmpty()) {
            BasicText(tr("Noch keine Sitzung gesichert.\nBeim Zeichnen oben rechts auf den Pfeil nach unten tippen – dann kannst du später genau dort weitermachen."),
                style = style(15f, 400, Ink.secondary).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp))
        } else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E))) {
            sessions.forEachIndexed { index, session ->
                key(session.id) {
                    if (index > 0) Box(Modifier.padding(start = 84.dp).fillMaxWidth().height(0.5.dp).background(Ink.separator))
                    SwipeToDelete(session.description, onDelete = { onDelete(session) }) {
                        val thumb by produceState<Bitmap?>(null, session.id) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { thumbnail(session) }
                        }
                        Row(Modifier.fillMaxWidth().background(Color(0xFF2C2C2E)).clickable(role = Role.Button, onClickLabel = tr("Weiterzeichnen")) { onOpen(session) }
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A3C))) {
                                thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                BasicText(session.description.ifBlank { tr("Ohne Beschreibung") }, style = style(17f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(Session.dateText(session.created), style = style(13f, 400, Ink.secondary))
                            }
                        }
                    }
                }
            }
        }
        BasicText(tr("Nach links wischen zum Löschen."), style = style(13f, 400, Ink.secondary),
            modifier = Modifier.padding(start = 32.dp, top = 6.dp).then(if (sessions.isEmpty()) Modifier.height(0.dp) else Modifier))
    }
}

/** An iOS list row that slides left to show a red "Löschen"; a full swipe deletes at once. */
@Composable
fun SwipeToDelete(label: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val reveal = with(LocalDensity.current) { 88.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableStateOf(1f) }
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.fillMaxHeight().width(with(LocalDensity.current) { (-offset).coerceAtLeast(0f).toDp() }).background(Ink.red)
                .clickable(role = Role.Button, onClickLabel = tr("Löschen"), onClick = onDelete).semantics { contentDescription = tr("{label} löschen", "label" to label) },
                contentAlignment = Alignment.Center) {
                if (-offset > reveal * 0.6f) BasicText(tr("Löschen"), style = style(15f, 600), maxLines = 1)
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }.pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = {
                offset = when {
                    -offset > width * 0.6f -> { onDelete(); 0f }
                    -offset > reveal / 2 -> -reveal
                    else -> 0f
                }
            }) { change, drag -> change.consume(); offset = (offset + drag).coerceIn(-width, 0f) }
        }) { content() }
    }
}
