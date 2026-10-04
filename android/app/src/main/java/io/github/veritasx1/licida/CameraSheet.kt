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

/** The camera controls (card 5, handbook p. 13–15) as an iOS sheet at half height: the live picture
 *  above stays visible – and while it is open, two fingers there move and zoom the camera picture. */
@Composable
fun CameraSheet(options: List<CameraOption>, chosen: CameraOption?, fill: Boolean, ghost: Boolean, hasReference: Boolean,
                onChoose: (CameraOption) -> Unit, onFill: (Boolean) -> Unit, onGhost: (Boolean) -> Unit, onReset: () -> Unit,
                onDone: () -> Unit, modifier: Modifier = Modifier) {
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
            BasicText("Mit zwei Fingern oben im Bild zoomst und verschiebst du das Kamerabild. Besser: die Kamera näher oder weiter weg stellen – "
                + "das hält das Bild scharf. „Ganzes Bild“ zeigt das ganze Blickfeld der Kamera und erlaubt die größte Zeichnung.",
                style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
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
