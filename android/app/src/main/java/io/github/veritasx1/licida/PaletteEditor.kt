package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The colour palette editor (card 11, handbook p. 34–37), full screen like an iOS editor: the picture on top
 *  (tap a spot to pick its colour; pinch to zoom), the swatches – before above, after below – then the number
 *  of colours, Ersetzen/Angleichen, the pipette, importing one's own colours and the colour wheel. It works on
 *  a smaller copy (as the handbook says); „Anwenden“ maps the full picture. */
@Composable
fun PaletteEditor(source: Bitmap, start: Palette?, onImport: (count: Int, into: (Palette) -> Unit) -> Unit, onWheel: (Palette) -> Unit,
                  onCancel: () -> Unit, onApply: (Palette) -> Unit) {
    val work = remember(source) { Reference.pixels(Reference.scaled(source, 480)) }
    val workBitmap = remember(work) { Reference.bitmap(work) }
    var palette by remember { mutableStateOf(start ?: Palette.find(work, Palette.DEFAULT_COLOURS)) }
    var count by remember { mutableStateOf(palette.size) }
    var selected by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var pipette by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(palette) { preview = withContext(Dispatchers.Default) { Reference.bitmap(palette.render(work)) } }
    fun setCount(next: Int) {
        if (next == count) return
        count = next
        busy = true
    }
    LaunchedEffect(count, busy) {
        if (!busy) return@LaunchedEffect
        palette = withContext(Dispatchers.Default) { Palette.find(work, count) }.copy(replace = palette.replace)
        selected = 0; busy = false
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicText(tr("Abbrechen"), style = style(17f, 400, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onCancel).padding(4.dp))
            BasicText(if (busy) tr("Farben werden gesucht …") else tr("Farbpalette"), style = style(17f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            BasicText(tr("Anwenden"), style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button) { onApply(palette) }.padding(4.dp))
        }
        // The picture: the palette's result – while the pipette is held, the original to pick from.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp))) {
            val boxWidth = constraints.maxWidth.toFloat(); val boxHeight = constraints.maxHeight.toFloat()
            val fit = minOf(boxWidth / work.width, boxHeight / work.height)
            fun pixelAt(at: Offset): Int? {
                // Screen → picture: undo pan and zoom about the centre, then the fit.
                val x = ((at.x - boxWidth / 2 - pan.x) / zoom + work.width * fit / 2) / fit
                val y = ((at.y - boxHeight / 2 - pan.y) / zoom + work.height * fit / 2) / fit
                return if (x in 0f..(work.width - 1f) && y in 0f..(work.height - 1f)) work[x.toInt(), y.toInt()] else null
            }
            val shown = if (pipette) workBitmap else preview ?: workBitmap
            val image = remember(shown) { shown.asImageBitmap() }
            Image(image, contentDescription = if (pipette) tr("Original") else tr("Vorschau der Farbpalette"), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                    .pointerInput(Unit) { detectTransformGestures { _, move, factor, _ -> zoom = (zoom * factor).coerceIn(1f, 8f); pan += move } }
                    .pointerInput(pipette, palette, zoom, pan) {
                        detectTapGestures { at ->
                            val colour = pixelAt(at) ?: return@detectTapGestures
                            if (pipette) {
                                // Pipette: the selected colour's "after" from the original (handbook p. 36).
                                palette = palette.copy(after = palette.after.toMutableList().also { it[selected] = colour })
                                pipette = false
                            } else {
                                // A spot in the picture selects its palette colour.
                                val index = palette.indices(Pixels(1, 1, intArrayOf(colour)))[0]
                                if (index >= 0) selected = index
                            }
                        }
                    })
            if (pipette) BasicText(tr("Tippe ins Original, um die Farbe zu übernehmen"), style = style(13f, 600, Color.Black),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(CircleShape).background(Ink.yellow).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1C1C1E)).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 8.dp)) {
            // Swatches: tap selects (again: colour picker), hold takes out / brings back.
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(palette.before) { index, before ->
                    val out = index in palette.removed
                    Swatch(before, palette.after[index], selected = index == selected && !out, removed = out,
                        onTap = {
                            when {
                                out -> { palette = palette.copy(removed = palette.removed - index); selected = index }
                                index == selected -> picker = true
                                else -> selected = index
                            }
                        },
                        onHold = { palette = palette.copy(removed = if (out) palette.removed - index else palette.removed + index) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Farben"), style = style(15f), modifier = Modifier.width(64.dp))
                IosSlider((count - 2) / 62f, { setCount(2 + (it * 62).toInt()) }, tr("Anzahl der Farben"), Modifier.weight(1f))
                BasicText("$count", style = style(15f, 600, Ink.secondary, tabular = true).copy(textAlign = TextAlign.End), modifier = Modifier.width(32.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Segmented(listOf(tr("Ersetzen"), tr("Angleichen")), if (palette.replace) 0 else 1) { palette = palette.copy(replace = it == 0) }
                Spacer(Modifier.weight(1f))
                PipetteButton(pipette) { pipette = it }
                Spacer(Modifier.width(10.dp))
                GlassButton(Symbol.Import, tr("Farben aus einem Foto übernehmen")) { onImport(count) { imported -> palette = imported; selected = 0 } }
                Spacer(Modifier.width(10.dp))
                GlassButton(Symbol.Wheel, tr("Palette als Farbkreis sichern oder drucken")) { onWheel(palette) }
            }
            BasicText(if (palette.replace) tr("Ersetzen: jede Farbe wird durch ihre neue Farbe (unten im Feld) ersetzt.")
                else tr("Angleichen: jede Stelle nimmt die nächstliegende neue Farbe."),
                style = style(12f, 400, Ink.secondary), modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
    if (picker) ColourPicker(palette.before[selected], palette.after[selected], onClose = { picker = false }) { colour ->
        palette = palette.copy(after = palette.after.toMutableList().also { it[selected] = colour })
    }
}

/** A swatch: before above, after below (handbook p. 35); taken out: smaller, with an ×. */
@Composable
private fun Swatch(before: Int, after: Int, selected: Boolean, removed: Boolean, onTap: () -> Unit, onHold: () -> Unit) {
    Box(Modifier.size(48.dp, 60.dp).scale(if (removed) 0.7f else 1f).clip(RoundedCornerShape(10.dp))
        .border(if (selected) 2.5.dp else 0.5.dp, if (selected) Ink.yellow else Ink.separator, RoundedCornerShape(10.dp))
        .semantics { contentDescription = if (removed) tr("Farbe herausgenommen") else tr("Farbe"); this.selected = selected }
        .pointerInput(removed, selected) { detectTapGestures(onTap = { onTap() }, onLongPress = { onHold() }) }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(before)))
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(after)))
        }
        if (removed) BasicText("×", style = style(22f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.align(Alignment.Center))
    }
}

/** The pipette (handbook p. 36): on – the original shows, a tap there takes the colour, then it is off again. */
@Composable
private fun PipetteButton(active: Boolean, onToggle: (Boolean) -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(if (active) Ink.yellow else Ink.glass)
        .clickable(role = Role.Switch, onClickLabel = tr("Pipette")) { onToggle(!active) }
        .semantics { contentDescription = tr("Pipette: Farbe aus dem Original nehmen"); selected = active }, contentAlignment = Alignment.Center) {
        SymbolIcon(Symbol.Pipette, if (active) Color.Black else Ink.white, size = 22.dp)
    }
}

/** iOS-like colour picker: a grid of colours, or sliders for hue, saturation and brightness; before and after side by side. */
@Composable
fun ColourPicker(before: Int, after: Int, onClose: () -> Unit, onPick: (Int) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf(after) }
    val hsb = remember(current) { Colours.toHsb(current) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1E)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Farben"), style = style(17f, 600), modifier = Modifier.weight(1f))
                BasicText(tr("Fertig"), style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button) { onPick(current); onClose() }.padding(4.dp))
            }
            Box(Modifier.padding(vertical = 10.dp)) { Segmented(listOf(tr("Raster"), tr("Regler")), tab) { tab = it } }
            if (tab == 0) {
                // 12 hues across, light to dark down, a grey row on top – like the iOS grid.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                    for (row in 0 until 10) Row(Modifier.fillMaxWidth()) {
                        for (column in 0 until 12) {
                            val colour = if (row == 0) Colours.fromHsb(0f, 0f, 1f - column / 11f)
                                else Colours.fromHsb(column * 30f, if (row <= 4) row / 4f else 1f, if (row <= 4) 1f else 1f - (row - 4) / 6.5f)
                            Box(Modifier.weight(1f).height(22.dp).background(Color(colour))
                                .border(if (colour == current) 2.dp else 0.dp, Color.White)
                                .clickable(role = Role.Button) { current = colour })
                        }
                    }
                }
            } else {
                PickerSlider(tr("Farbton"), hsb[0] / 360f) { current = Colours.fromHsb(it * 360f, hsb[1], hsb[2]) }
                PickerSlider(tr("Sättigung"), hsb[1]) { current = Colours.fromHsb(hsb[0], it, hsb[2]) }
                PickerSlider(tr("Helligkeit"), hsb[2]) { current = Colours.fromHsb(hsb[0], hsb[1], it) }
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp))) {
                Box(Modifier.weight(1f).fillMaxSize().background(Color(before)), contentAlignment = Alignment.BottomStart) {
                    BasicText(tr("Vorher"), style = style(12f, 600, if (Colours.lab(before)[0] > 60) Color.Black else Color.White), modifier = Modifier.padding(8.dp))
                }
                Box(Modifier.weight(1f).fillMaxSize().background(Color(current)), contentAlignment = Alignment.BottomEnd) {
                    BasicText(tr("Nachher"), style = style(12f, 600, if (Colours.lab(current)[0] > 60) Color.Black else Color.White), modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style(15f), modifier = Modifier.width(86.dp))
        IosSlider(value, onChange, label, Modifier.weight(1f))
    }
}

/** The palette as a colour wheel (handbook p. 37): sorted by hue or brightness; keep it in Fotos or print it to mix paints. */
@Composable
fun PaletteWheel(palette: Palette, onSave: (Bitmap) -> Unit, onPrint: (Bitmap) -> Unit, onClose: () -> Unit) {
    var byHue by remember { mutableStateOf(true) }
    val wheel = remember(palette, byHue) { Reference.bitmap(Palette.wheel(palette.after.filterIndexed { i, _ -> i !in palette.removed }, 900, byHue)) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1E)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(tr("Farbkreis"), style = style(17f, 600), modifier = Modifier.weight(1f))
                BasicText(tr("Fertig"), style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onClose).padding(4.dp))
            }
            val image = remember(wheel) { wheel.asImageBitmap() }
            Image(image, tr("Farbkreis der Palette"), modifier = Modifier.padding(vertical = 12.dp).size(240.dp).clip(CircleShape))
            Segmented(listOf(tr("Nach Farbton"), tr("Nach Helligkeit")), if (byHue) 0 else 1) { byHue = it == 0 }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(tr("In Fotos sichern"), style = style(15f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)).clickable(role = Role.Button) { onSave(wheel) }.padding(vertical = 12.dp))
                BasicText(tr("Drucken …"), style = style(15f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)).clickable(role = Role.Button) { onPrint(wheel) }.padding(vertical = 12.dp))
            }
        }
    }
}

/** While drawing with a palette (handbook p. 39): one button per colour layer – tap shows/hides it, hold shows the
 *  colour large for mixing; the first button switches all on or off, the last reopens the editor. */
@Composable
fun PaletteLayers(palette: Palette, hidden: Set<Int>, onHidden: (Set<Int>) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    var big by remember { mutableStateOf<Int?>(null) }
    val used = palette.before.indices.filter { it !in palette.removed }
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Ink.glass).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val allOn = hidden.isEmpty()
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Ink.glassStrong)
            .clickable(role = Role.Button, onClickLabel = if (allOn) tr("Alle Farben aus") else tr("Alle Farben ein")) { onHidden(if (allOn) used.toSet() else emptySet()) }
            .semantics { contentDescription = if (allOn) tr("Alle Farben aus") else tr("Alle Farben ein") }, contentAlignment = Alignment.Center) {
            SymbolIcon(Symbol.Filters, Ink.white, size = 20.dp)
        }
        LazyRow(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(used) { _, index ->
                val off = index in hidden
                Box(Modifier.size(40.dp).scale(if (off) 0.62f else 1f).clip(RoundedCornerShape(8.dp)).background(Color(palette.after[index]))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(8.dp))
                    .semantics { contentDescription = if (off) tr("Farbebene aus") else tr("Farbebene ein"); selected = !off }
                    .pointerInput(off, hidden) {
                        detectTapGestures(onTap = { onHidden(if (off) hidden - index else hidden + index) }, onLongPress = { big = index })
                    })
            }
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Ink.glassStrong).clickable(role = Role.Button, onClickLabel = tr("Palette bearbeiten"), onClick = onEdit)
            .semantics { contentDescription = tr("Palette bearbeiten") }, contentAlignment = Alignment.Center) {
            SymbolIcon(Symbol.Wheel, Ink.white, size = 20.dp)
        }
    }
    big?.let { index ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { big = null }) {
            Canvas(Modifier.size(260.dp).clip(RoundedCornerShape(20.dp)).clickable { big = null }.semantics { contentDescription = tr("Farbe groß") }) {
                drawRect(Color(palette.after[index]))
            }
        }
    }
}
