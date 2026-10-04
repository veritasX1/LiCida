package io.github.veritasx1.licida

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What a slot action asks before it acts (handbook p. 30–31: every slot action is confirmed). */
private sealed interface SlotQuestion {
    data class Menu(val index: Int) : SlotQuestion
    data class Store(val index: Int) : SlotQuestion
    data class Replay(val index: Int) : SlotQuestion
    data class Rename(val index: Int) : SlotQuestion
}

/** The toolbox (cards 9/10) as an iOS sheet: undo, redo and the history on top, the filters as a strip
 *  of live previews like the Photos app, the value of Tontrennung, the three own sequences, and the
 *  original / save at the bottom. Filters stack: each works on what you see now. */
@Composable
fun FilterSheet(edits: Edits, previews: Map<Filter, Bitmap>, slots: List<Slot>, busy: Boolean,
                onApply: (Step) -> Unit, onValue: (Int) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onJump: (Int) -> Unit,
                onStore: (Int) -> Unit, onReplay: (Int) -> Unit, onRename: (Int, String) -> Unit, onSave: () -> Unit, onDone: () -> Unit,
                modifier: Modifier = Modifier, onPalette: () -> Unit = {}, onEffects: () -> Unit = {}) {
    var history by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf<SlotQuestion?>(null) }
    val last = edits.active.lastOrNull()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xF21C1C1E))
        .windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Color(0x66EBEBF5)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Symbol.Undo, "Rückgängig", edits.canUndo, onUndo)
            ToolButton(Symbol.Redo, "Wiederholen", edits.canRedo, onRedo)
            ToolButton(Symbol.History, "Verlauf", edits.steps.isNotEmpty(), active = history) { history = !history }
            BasicText(if (busy) "Wird angewendet …" else "Werkzeuge", style = style(17f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            BasicText("Fertig", style = style(17f, 600, Ink.yellow).copy(textAlign = TextAlign.End),
                modifier = Modifier.width(72.dp).clickable(role = Role.Button, onClick = onDone).padding(vertical = 8.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            if (history) History(edits, onJump)
            // The filters: previews of what each would make of the picture now (handbook p. 27: live thumbnails).
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp)) {
                itemsIndexed(Filter.entries) { _, filter ->
                    Column(Modifier.width(76.dp).clickable(role = Role.Button) {
                        // The palette opens its editor (handbook p. 34); the others apply at once.
                        if (filter == Filter.ColourPalette) onPalette()
                        else if (filter == Filter.ColourEffects) onEffects()
                        else onApply(Step(filter, if (filter.takesValue && last?.filter == filter) last.value else filter.defaultValue))
                    }.semantics { contentDescription = "${filter.label}: ${filter.hint}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Checker)
                            .border(if (last?.filter == filter) 2.dp else 0.dp, Ink.yellow, RoundedCornerShape(10.dp))) {
                            previews[filter]?.let { bitmap ->
                                val image = remember(bitmap) { bitmap.asImageBitmap() }
                                Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
                            }
                        }
                        BasicText(filter.label, style = style(12f, 500, if (last?.filter == filter) Ink.yellow else Ink.white).copy(textAlign = TextAlign.Center),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
            // Tontrennung's shades, like a slider under Photos' filters: changes the last step.
            if (last?.filter == Filter.Posterize) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Grautöne", style = style(15f), modifier = Modifier.width(84.dp))
                IosSlider((last.value - 2) / 14f, { onValue(2 + (it * 14).toInt()) }, "Anzahl der Grautöne", Modifier.weight(1f))
                BasicText("${last.value}", style = style(15f, 600, Ink.secondary, tabular = true).copy(textAlign = TextAlign.End), modifier = Modifier.width(32.dp))
            }
            BasicText("EIGENE FOLGEN", style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 32.dp, top = 14.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEachIndexed { index, slot ->
                    SlotCard(slot, Modifier.weight(1f)) { question = SlotQuestion.Menu(index) }
                }
            }
            BasicText("Eine Folge merkt sich die Filter von jetzt und wendet sie später auf andere Bilder an.",
                style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Wide("Original", Ink.white, edits.position > 0, Modifier.weight(1f)) { onJump(0) }
                Wide("In Fotos sichern", Ink.yellow, true, Modifier.weight(1f), onClick = onSave)
            }
        }
    }
    question?.let { asked ->
        SlotDialog(asked, slots, edits, onClose = { question = null }, onStore = onStore, onReplay = onReplay, onRename = onRename,
            onAskRename = { index -> question = SlotQuestion.Rename(index) })
    }
}

private val Checker = Color(0xFF3A3A3C)

@Composable
private fun ToolButton(symbol: Symbol, description: String, enabled: Boolean, onClick: () -> Unit) = ToolButton(symbol, description, enabled, false, onClick)

@Composable
private fun ToolButton(symbol: Symbol, description: String, enabled: Boolean, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description }.alpha(if (enabled) 1f else 0.3f), contentAlignment = Alignment.Center) {
        SymbolIcon(symbol, if (active) Ink.yellow else Ink.white, size = 22.dp)
    }
}

/** The history (handbook p. 29): every step, the current one marked; tap to go back to it. */
@Composable
private fun History(edits: Edits, onJump: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E))) {
        (listOf("Original") + edits.steps.map { it.label }).forEachIndexed { index, label ->
            if (index > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Ink.separator))
            val current = index == edits.position
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onJump(index) }.semantics { selected = current }
                .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(if (index == 0) label else "$index. $label", style = style(16f, 400, if (index > edits.position) Ink.secondary else Ink.white),
                    modifier = Modifier.weight(1f))
                if (current) SymbolIcon(Symbol.Checkmark, Ink.yellow, size = 18.dp, weight = 2.2f)
            }
        }
    }
}

/** A slot like an iOS tile: name and how many filters; a tap opens its menu. */
@Composable
private fun SlotCard(slot: Slot, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "${slot.name}, ${if (slot.steps.isEmpty()) "leer" else "${slot.steps.size} Filter"}" }
        .padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(slot.name, style = style(15f, 600).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(if (slot.steps.isEmpty()) "leer" else "${slot.steps.size} Filter", style = style(12f, 400, Ink.secondary), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Wide(label: String, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    BasicText(label, style = style(17f, 600, if (enabled) color else Ink.secondary).copy(textAlign = TextAlign.Center),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp))
}

/** iOS alert: the question, the filters concerned, Abbrechen / the action. */
@Composable
private fun SlotDialog(question: SlotQuestion, slots: List<Slot>, edits: Edits, onClose: () -> Unit,
                       onStore: (Int) -> Unit, onReplay: (Int) -> Unit, onRename: (Int, String) -> Unit, onAskRename: (Int) -> Unit) {
    var name by remember(question) { mutableStateOf((question as? SlotQuestion.Rename)?.let { slots[it.index].name } ?: "") }
    if (question is SlotQuestion.Menu) return SlotMenu(question.index, slots[question.index], edits, onClose, onStore, onReplay) {
        onClose(); onAskRename(question.index)
    }
    val (title, body, action) = when (question) {
        is SlotQuestion.Menu -> Triple("", "", "")
        is SlotQuestion.Store -> Triple(if (slots[question.index].steps.isEmpty()) "In „${slots[question.index].name}“ sichern?" else "„${slots[question.index].name}“ überschreiben?",
            edits.active.joinToString(" → ") { it.label }, "Sichern")
        is SlotQuestion.Replay -> Triple("„${slots[question.index].name}“ anwenden?", slots[question.index].steps.joinToString(" → ") { it.label }, "Anwenden")
        is SlotQuestion.Rename -> Triple("Folge umbenennen", "", "Sichern")
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(270.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF22C2C2E)).clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(title, style = style(17f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp))
            if (body.isNotEmpty()) BasicText(body, style = style(13f, 400).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
            if (question is SlotQuestion.Rename) BasicTextField(name, { name = it.take(24) }, singleLine = true, textStyle = style(15f),
                cursorBrush = SolidColor(Ink.yellow), modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF1C1C1E)).padding(horizontal = 10.dp, vertical = 8.dp).semantics { contentDescription = "Name der Folge" })
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.separator))
            Row(Modifier.fillMaxWidth()) {
                BasicText("Abbrechen", style = style(17f, 400, Ink.yellow).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onClose).padding(vertical = 12.dp))
                Box(Modifier.width(0.5.dp).height(44.dp).background(Ink.separator))
                BasicText(action, style = style(17f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).clickable(role = Role.Button) {
                        when (question) {
                            is SlotQuestion.Store -> onStore(question.index)
                            is SlotQuestion.Replay -> onReplay(question.index)
                            is SlotQuestion.Rename -> if (name.isNotBlank()) onRename(question.index, name.trim())
                            is SlotQuestion.Menu -> Unit
                        }
                        onClose()
                    }.padding(vertical = 12.dp))
            }
        }
    }
}

/** The slot's menu, iOS action sheet style: what it holds, then Anwenden / Hier sichern / Umbenennen / Abbrechen.
 *  The filters are listed in the menu itself – it is the confirmation the handbook asks for. */
@Composable
private fun SlotMenu(index: Int, slot: Slot, edits: Edits, onClose: () -> Unit, onStore: (Int) -> Unit, onReplay: (Int) -> Unit, onRename: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xF22C2C2E)), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(slot.name, style = style(13f, 600, Ink.secondary).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 14.dp))
                BasicText(if (slot.steps.isEmpty()) "Noch keine Filter gesichert" else slot.steps.joinToString(" → ") { it.label },
                    style = style(13f, 400, Ink.secondary).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp))
                MenuOption("Auf dieses Bild anwenden", slot.steps.isNotEmpty()) { onReplay(index); onClose() }
                MenuOption(if (slot.steps.isEmpty()) "Aktuelle Filter hier sichern" else "Mit aktuellen Filtern ersetzen", edits.active.isNotEmpty()) { onStore(index); onClose() }
                MenuOption("Umbenennen", true, onRename)
            }
            Spacer(Modifier.height(8.dp))
            BasicText("Abbrechen", style = style(19f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xF22C2C2E)).clickable(role = Role.Button, onClick = onClose)
                    .padding(vertical = 15.dp))
        }
    }
}

@Composable
private fun MenuOption(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.separator))
    BasicText(label, style = style(19f, 400, if (enabled) Ink.yellow else Ink.secondary).copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 15.dp))
}
