package io.github.veritasx1.licida

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** The camera controls (card 5, handbook p. 13–15) as an iOS sheet at half height: the live picture
 *  above stays visible – and while it is open, two fingers there move and zoom the camera picture. */
@Composable
fun CameraSheet(options: List<CameraOption>, chosen: CameraOption?, fill: Boolean, ghost: Boolean, hasReference: Boolean,
                onChoose: (CameraOption) -> Unit, onFill: (Boolean) -> Unit, onGhost: (Boolean) -> Unit, onReset: () -> Unit,
                onDone: () -> Unit, modifier: Modifier = Modifier, correcting: CorrectionControls? = null) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Color(0xF21C1C1E))
        .windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Color(0x66EBEBF5)))  // grabber
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(56.dp))
            BasicText("Kamera", style = style(17f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            BasicText("Fertig", style = style(17f, 600, Ink.yellow).copy(textAlign = TextAlign.End),
                modifier = Modifier.width(56.dp).clickable(role = Role.Button, onClick = onDone).padding(vertical = 6.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Section("KAMERA")
            Group {
                options.forEachIndexed { index, option ->
                    if (index > 0) Separator()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoose(option) }
                        .semantics { selected = option.key == chosen?.key; contentDescription = "${option.label}, ${option.detail}" }
                        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(option.label, style = style(17f))
                            BasicText(option.detail, style = style(13f, 400, Ink.secondary))
                        }
                        if (option.key == chosen?.key) SymbolIcon(Symbol.Checkmark, Ink.yellow, size = 20.dp, weight = 2.2f)
                    }
                }
                if (options.isEmpty()) BasicText("Keine Kamera gefunden", style = style(17f, 400, Ink.secondary), modifier = Modifier.padding(16.dp))
            }
            Section("BILD")
            Group {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Ausschnitt", style = style(17f), modifier = Modifier.weight(1f))
                    Segmented(listOf("Füllen", "Ganzes Bild"), if (fill) 0 else 1) { onFill(it == 0) }
                }
                Separator()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Vorlage einblenden", style = style(17f, 400, if (hasReference) Ink.white else Ink.secondary), modifier = Modifier.weight(1f))
                    IosSwitch(ghost && hasReference, enabled = hasReference, description = "Vorlage einblenden", onChange = onGhost)
                }
                Separator()
                BasicText("Kamerabild zurücksetzen", style = style(17f, 400, Ink.yellow),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onReset).padding(horizontal = 16.dp, vertical = 12.dp))
            }
            correcting?.let { CorrectionSections(it) }
            BasicText("Mit zwei Fingern oben im Bild zoomst und verschiebst du das Kamerabild. Besser: die Kamera näher oder weiter weg stellen – "
                + "das hält das Bild scharf. „Ganzes Bild“ zeigt das ganze Blickfeld der Kamera und erlaubt die größte Zeichnung.",
                style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

/** Everything the correction sections show and do (cards 6/7). */
class CorrectionControls(
    val correction: Correction, val flipH: Boolean, val flipV: Boolean, val helperGhost: Boolean, val ownTarget: Boolean,
    val hasSaved: Boolean, val busy: String?,
    val onCorrection: (Correction) -> Unit, val onCorrectionDone: () -> Unit, val onFlip: (horizontal: Boolean) -> Unit,
    val onHelperGhost: (Boolean) -> Unit, val onResetCorrection: () -> Unit,
    val onTargetKind: (own: Boolean) -> Unit, val onPickOwnTarget: () -> Unit, val onPrintTarget: () -> Unit, val onSaveTarget: () -> Unit,
    val onAuto: () -> Unit, val onSaveSetting: () -> Unit, val onRestoreSetting: () -> Unit,
)

@Composable
private fun CorrectionSections(c: CorrectionControls) {
    Section("KORREKTUR VON HAND")
    Group {
        val tilt = c.correction.tilt
        SliderRow("Neigung", "${tilt.toInt()}°", (tilt + Correction.MAX_TILT) / (2 * Correction.MAX_TILT), "Neigung der Kamera",
            { c.onCorrection(c.correction.copy(tilt = (it * 2 - 1) * Correction.MAX_TILT)) }, c.onCorrectionDone)
        Separator()
        SliderRow("Höhe", "%.1f".format(c.correction.height).replace('.', ','), (c.correction.height - 1f) / 9f, "Höhe über der Zeichenfläche",
            { c.onCorrection(c.correction.copy(height = 1f + it * 9f)) }, c.onCorrectionDone)
        Separator()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Spiegeln", style = style(17f), modifier = Modifier.weight(1f))
            Toggle("↔", "Waagerecht spiegeln", c.flipH) { c.onFlip(true) }
            Spacer(Modifier.width(8.dp))
            Toggle("↕", "Senkrecht spiegeln", c.flipV) { c.onFlip(false) }
        }
        Separator()
        StretchRow("Breite", c.correction.stretchX, { c.onCorrection(c.correction.copy(stretchX = it)) }, c.onCorrectionDone)
        Separator()
        StretchRow("Länge", c.correction.stretchY, { c.onCorrection(c.correction.copy(stretchY = it)) }, c.onCorrectionDone)
        Separator()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Hilfsraster einblenden", style = style(17f), modifier = Modifier.weight(1f))
            IosSwitch(c.helperGhost, description = "Hilfsraster einblenden", onChange = c.onHelperGhost)
        }
        Separator()
        BasicText("Korrektur zurücksetzen", style = style(17f, 400, Ink.yellow),
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = c.onResetCorrection).padding(horizontal = 16.dp, vertical = 12.dp))
    }
    BasicText("Leg das gedruckte Zielbild auf und blende das Hilfsraster ein: Neigung und Strecken so wählen, dass sich beide decken.",
        style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))

    Section("AUTOMATISCH")
    Group {
        CheckRow("LiCida-Zielbild", "Ausdrucken oder auf einem zweiten Bildschirm zeigen", !c.ownTarget) { c.onTargetKind(false) }
        Separator()
        CheckRow("Eigenes Bild", "Etwas Flaches mit klaren Kanten, z. B. eine Zeitschrift", c.ownTarget) { c.onPickOwnTarget() }
        Separator()
        ActionRow("Zielbild drucken …", onClick = c.onPrintTarget)
        Separator()
        ActionRow("Zielbild in Fotos sichern", onClick = c.onSaveTarget)
    }
    BasicText(c.busy ?: "Automatisch ausrichten", style = style(17f, 600, androidx.compose.ui.graphics.Color.Black).copy(textAlign = TextAlign.Center),
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (c.busy == null) Ink.yellow else Ink.secondary)
            .clickable(enabled = c.busy == null, role = Role.Button, onClick = c.onAuto).padding(vertical = 14.dp))
    BasicText("Zielbild flach in den Blick der Kamera legen, Arm aus dem Bild – LiCida richtet das Kamerabild so aus, als schaue die Kamera senkrecht von oben.",
        style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
    Section("EINSTELLUNG")
    Group {
        ActionRow("Diese Ausrichtung sichern", onClick = c.onSaveSetting)
        Separator()
        ActionRow("Gesicherte wiederherstellen", enabled = c.hasSaved, onClick = c.onRestoreSetting)
    }
}

@Composable
private fun SliderRow(label: String, value: String, fraction: Float, description: String, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style(17f), modifier = Modifier.width(82.dp))
        IosSlider(fraction.coerceIn(0f, 1f), onChange, description, Modifier.weight(1f), onRelease = onDone)
        BasicText(value, style = style(15f, 500, Ink.secondary, tabular = true).copy(textAlign = TextAlign.End), modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun Toggle(symbol: String, description: String, on: Boolean, onClick: () -> Unit) {
    BasicText(symbol, style = style(18f, 600, if (on) androidx.compose.ui.graphics.Color.Black else Ink.white).copy(textAlign = TextAlign.Center),
        modifier = Modifier.size(width = 52.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Ink.yellow else Color(0x3D767680))
            .clickable(role = Role.Switch, onClick = onClick).semantics { contentDescription = description; selected = on }.padding(top = 5.dp))
}

/** Stretch with − and +: slow at first, faster the longer they are held (handbook p. 18); 1:1 resets. */
@Composable
private fun StretchRow(label: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val current = androidx.compose.runtime.rememberUpdatedState(value)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = style(17f), modifier = Modifier.weight(1f))
        BasicText("${(value * 100).toInt()} %", style = style(15f, 500, Ink.secondary, tabular = true), modifier = Modifier.padding(end = 10.dp))
        for ((sign, text) in listOf(-1 to "−", 1 to "+")) {
            BasicText(text, style = style(20f, 500).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(start = 6.dp).size(width = 44.dp, height = 34.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0x3D767680))
                .semantics { contentDescription = "$label ${if (sign < 0) "verringern" else "vergrößern"}" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val started = System.currentTimeMillis()
                        onChange((current.value + sign * Correction.stretchStep(0)).coerceIn(0.5f, 2f))
                        while (true) {
                            val released = withTimeoutOrNull(60) { waitForUpOrCancellation() }
                            if (released != null) break
                            if (System.currentTimeMillis() - started > 350)
                                onChange((current.value + sign * Correction.stretchStep(System.currentTimeMillis() - started)).coerceIn(0.5f, 2f))
                        }
                        onDone()
                    }
                }.padding(top = 3.dp))
        }
        BasicText("1:1", style = style(15f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(start = 6.dp).size(width = 40.dp, height = 34.dp).clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "$label zurücksetzen") { onChange(1f); onDone() }.padding(top = 7.dp))
    }
}

@Composable
private fun CheckRow(label: String, detail: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics { selected = checked }
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = style(17f))
            BasicText(detail, style = style(13f, 400, Ink.secondary))
        }
        if (checked) SymbolIcon(Symbol.Checkmark, Ink.yellow, size = 20.dp, weight = 2.2f)
    }
}

@Composable
private fun ActionRow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    BasicText(label, style = style(17f, 400, if (enabled) Ink.yellow else Ink.secondary),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun Section(title: String) = BasicText(title, style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E))) { content() }
}

@Composable
private fun Separator() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Ink.separator))

/** iOS segmented control (dark): a grey track, the chosen segment raised. */
@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0x3D767680)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            BasicText(label, style = style(13f, if (index == selected) 600 else 500).copy(textAlign = TextAlign.Center),
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (index == selected) Color(0xFF636366) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }.semantics { this.selected = index == selected }
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** iOS switch: green track when on. */
@Composable
fun IosSwitch(on: Boolean, enabled: Boolean = true, description: String, onChange: (Boolean) -> Unit) {
    Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(if (on) Color(0xFF30D158) else Color(0x52787880))
        .clickable(enabled = enabled, role = Role.Switch) { onChange(!on) }.semantics { contentDescription = description; selected = on }
        .padding(2.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(27.dp).clip(CircleShape).background(if (enabled) Color.White else Color(0xFF8E8E93)))
    }
}
