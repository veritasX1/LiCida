package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** How often LiCida's hints appear (handbook p. 25): never ("Alter Meister"), once, three times or always. */
enum class HelpLevel(val label: String, val times: Int) {
    Off(tr("Aus"), 0), Once("1×", 1), Thrice("3×", 3), Always(tr("Immer"), Int.MAX_VALUE);

    fun shows(alreadyShown: Int) = alreadyShown < times
}

/** The camera picture against delay (handbook p. 46): fewer pixels are quicker on slow phones and long cables. */
enum class CameraQuality(val label: String) { Smooth(tr("Flüssig")), Sharp(tr("Scharf")) }

/** What a key (Bluetooth keyboard, game controller, selfie remote) does while drawing (handbook p. 47). */
enum class KeyAction(val label: String, val defaultKey: Int) {
    ZoomIn(tr("Näher heran"), KeyEvent.KEYCODE_E),
    ZoomOut(tr("Weiter weg"), KeyEvent.KEYCODE_Q),
    PanLeft(tr("Nach links"), KeyEvent.KEYCODE_A),
    PanRight(tr("Nach rechts"), KeyEvent.KEYCODE_D),
    PanUp(tr("Nach oben"), KeyEvent.KEYCODE_W),
    PanDown(tr("Nach unten"), KeyEvent.KEYCODE_S),
    Fainter(tr("Vorlage schwächer"), KeyEvent.KEYCODE_Z),
    Stronger(tr("Vorlage stärker"), KeyEvent.KEYCODE_C),
    Toggle(tr("Vorlage aus/ein"), KeyEvent.KEYCODE_R),
    Flicker(tr("Flimmern aus/ein"), KeyEvent.KEYCODE_F),
    SplitLeft(tr("Teiler nach links"), KeyEvent.KEYCODE_1),
    SplitRight(tr("Teiler nach rechts"), KeyEvent.KEYCODE_3),
}

/** The keys as the user set them; a key belongs to one action at most. */
data class Keymap(val keys: Map<KeyAction, Int> = KeyAction.entries.associateWith { it.defaultKey }) {
    fun action(code: Int): KeyAction? = keys.entries.firstOrNull { it.value == code }?.key

    /** Giving a key to an action takes it away from the one that had it (that one is then without a key). */
    fun assign(action: KeyAction, code: Int): Keymap =
        Keymap(keys.mapValues { (a, c) -> if (a == action) code else if (c == code) KeyEvent.KEYCODE_UNKNOWN else c })

    fun encode() = KeyAction.entries.joinToString(";") { "${it.name}=${keys[it] ?: KeyEvent.KEYCODE_UNKNOWN}" }

    companion object {
        fun decode(text: String?): Keymap {
            if (text.isNullOrBlank()) return Keymap()
            val read = text.split(";").mapNotNull { part ->
                val (name, code) = part.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                val action = KeyAction.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                code.toIntOrNull()?.let { action to it }
            }.toMap()
            return Keymap(KeyAction.entries.associateWith { read[it] ?: it.defaultKey })
        }

        /** "E", "1", "Lauter" – what is printed on the key, in words where nothing is. */
        fun label(code: Int): String = when (code) {
            KeyEvent.KEYCODE_UNKNOWN -> "–"
            KeyEvent.KEYCODE_SPACE -> tr("Leertaste")
            KeyEvent.KEYCODE_ENTER -> tr("Eingabe")
            KeyEvent.KEYCODE_TAB -> tr("Tab")
            KeyEvent.KEYCODE_DPAD_LEFT -> "←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
            KeyEvent.KEYCODE_DPAD_UP -> "↑"
            KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
            KeyEvent.KEYCODE_VOLUME_UP -> tr("Lauter")
            KeyEvent.KEYCODE_VOLUME_DOWN -> tr("Leiser")
            KeyEvent.KEYCODE_PAGE_UP -> tr("Bild ↑")
            KeyEvent.KEYCODE_PAGE_DOWN -> tr("Bild ↓")
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> tr("Wiedergabe")
            KeyEvent.KEYCODE_MEDIA_NEXT -> tr("Weiter")
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tr("Zurück")
            else -> when {
                code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
                code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (code - KeyEvent.KEYCODE_0)).toString()
                code in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 -> tr("Taste {value}", "value" to (code - KeyEvent.KEYCODE_BUTTON_1 + 1))
                code in GAMEPAD -> tr("Taste {value}", "value" to GAMEPAD.getValue(code))
                else -> tr("Taste {code}", "code" to code)
            }
        }

        /** A controller's buttons by the names printed on them. */
        private val GAMEPAD = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to "A", KeyEvent.KEYCODE_BUTTON_B to "B", KeyEvent.KEYCODE_BUTTON_C to "C",
            KeyEvent.KEYCODE_BUTTON_X to "X", KeyEvent.KEYCODE_BUTTON_Y to "Y", KeyEvent.KEYCODE_BUTTON_Z to "Z",
            KeyEvent.KEYCODE_BUTTON_L1 to "L1", KeyEvent.KEYCODE_BUTTON_R1 to "R1", KeyEvent.KEYCODE_BUTTON_L2 to "L2",
            KeyEvent.KEYCODE_BUTTON_R2 to "R2", KeyEvent.KEYCODE_BUTTON_THUMBL to "L3", KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
            KeyEvent.KEYCODE_BUTTON_START to tr("Start"), KeyEvent.KEYCODE_BUTTON_SELECT to tr("Select"), KeyEvent.KEYCODE_BUTTON_MODE to tr("Mode"))

        /** Keys that stay Android's: going back and home are never taken. */
        fun usable(code: Int) = code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_UNKNOWN &&
            code != KeyEvent.KEYCODE_POWER && code != KeyEvent.KEYCODE_ESCAPE
    }
}

/** The draw view after a key: zoom around the middle, pan by a tenth of the screen (stays inside the picture). */
object KeyMoves {
    const val ZOOM_STEP = 1.25f
    const val OPACITY_STEP = 0.1f
    const val SPLIT_STEP = 0.05f

    fun view(action: KeyAction, view: DrawView, screen: Size): DrawView {
        val middle = Offset(screen.width / 2, screen.height / 2)
        val step = minOf(screen.width, screen.height) / 10f
        return when (action) {
            KeyAction.ZoomIn -> Composition.zoom(view, middle, Offset.Zero, ZOOM_STEP, screen)
            KeyAction.ZoomOut -> Composition.zoom(view, middle, Offset.Zero, 1f / ZOOM_STEP, screen)
            // "Nach links" looks further left: the picture moves right.
            KeyAction.PanLeft -> Composition.zoom(view, middle, Offset(step, 0f), 1f, screen)
            KeyAction.PanRight -> Composition.zoom(view, middle, Offset(-step, 0f), 1f, screen)
            KeyAction.PanUp -> Composition.zoom(view, middle, Offset(0f, step), 1f, screen)
            KeyAction.PanDown -> Composition.zoom(view, middle, Offset(0f, -step), 1f, screen)
            else -> view
        }
    }
}

/** Keys reach Compose only with focus; the activity hands every key here first, and LiCida takes the ones it uses. */
object KeyHub {
    var listener: ((KeyEvent) -> Boolean)? = null
    fun dispatch(event: KeyEvent): Boolean = listener?.invoke(event) ?: false
}

@Composable
fun KeyListener(onKey: (KeyEvent) -> Boolean) {
    val current = androidx.compose.runtime.rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val listener: (KeyEvent) -> Boolean = { current.value(it) }
        KeyHub.listener = listener
        onDispose { if (KeyHub.listener === listener) KeyHub.listener = null }
    }
}

/** Everything the settings page shows and changes. */
data class SettingsState(val help: HelpLevel, val fill: Boolean, val quality: CameraQuality, val timelapse: TimelapseSettings,
                         val slotNames: List<String>, val keymap: Keymap, val projector: Boolean)

/** The settings page (handbook p. 45–47) as iOS Settings: a large title, grouped lists with a caption above and a note below. */
@Composable
fun SettingsPage(state: SettingsState, listening: KeyAction?, onChange: (SettingsState) -> Unit, onListen: (KeyAction?) -> Unit,
                 onPermissions: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).keepTouches().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
            BasicText(tr("Fertig"), style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onDone).padding(4.dp))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 32.dp)) {
            BasicText(tr("Einstellungen"), style = style(34f, 700), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // Language: like the system or chosen here – takes effect at the next start.
            run {
                val i18n = io.github.veritasx1.licida.i18n.I18n
                val codes = listOf<String?>(null) + i18n.LANGUAGES.keys
                var chosen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(i18n.chosen) }
                Group(tr("Sprache"), tr("Die Sprache wechselt beim nächsten Start von LiCida.")) {
                    Stacked(tr("Sprache")) {
                        Segmented(listOf(tr("System")) + i18n.LANGUAGES.values, codes.indexOf(chosen).coerceAtLeast(0)) { chosen = codes[it]; i18n.chosen = codes[it] }
                    }
                }
            }

            Group(tr("Hilfe"), tr("„Aus“ ist für alte Meister: keine Hinweise mehr. Gezählt wird je Hinweis.")) {
                Stacked(tr("Hinweise zeigen")) {
                    Segmented(HelpLevel.entries.map { it.label }, state.help.ordinal) { onChange(state.copy(help = HelpLevel.entries[it])) }
                }
            }

            Group(tr("Kamera"), tr("„Ganzes Bild“ zeigt das ganze Blickfeld und erlaubt die größte Zeichnung. „Flüssig“ nimmt weniger Bildpunkte – hilft, wenn das Kamerabild nachzieht; „Scharf“ nimmt so viele, wie die Vorschau der Kamera hergibt.")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tr("Bildausschnitt"), style = style(17f), modifier = Modifier.weight(1f))
                    Segmented(listOf(tr("Füllen"), tr("Ganzes Bild")), if (state.fill) 0 else 1) { onChange(state.copy(fill = it == 0)) }
                }
                Line()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tr("Kamerabild"), style = style(17f), modifier = Modifier.weight(1f))
                    Segmented(CameraQuality.entries.map { it.label }, state.quality.ordinal) { onChange(state.copy(quality = CameraQuality.entries[it])) }
                }
            }

            Group(tr("Zeitraffer"), tr("Die Höhe gilt für die kurze Seite; die lange passt sich dem Bildschirm an.")) {
                SwitchRow(tr("Oberfläche mitfilmen"), tr("Knöpfe und Regler sind im Video zu sehen"), state.timelapse.recordUi, true) {
                    onChange(state.copy(timelapse = state.timelapse.copy(recordUi = it)))
                }
                Line()
                SwitchRow(tr("Zoomen ausblenden"), tr("Das Video zeigt immer das ganze Blatt – als hättest du nie hineingezoomt"),
                    state.timelapse.ignoreZoom, !state.timelapse.recordUi) { onChange(state.copy(timelapse = state.timelapse.copy(ignoreZoom = it))) }
                Line()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tr("Auflösung"), style = style(17f), modifier = Modifier.weight(1f))
                    Segmented(TimelapseSettings.HEIGHTS.map { "${it}p" }, TimelapseSettings.HEIGHTS.indexOf(state.timelapse.height).coerceAtLeast(0)) {
                        onChange(state.copy(timelapse = state.timelapse.copy(height = TimelapseSettings.HEIGHTS[it])))
                    }
                }
                Line()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tr("Qualität"), style = style(17f), modifier = Modifier.weight(1f))
                    Segmented(TimelapseSettings.QUALITIES, state.timelapse.quality) { onChange(state.copy(timelapse = state.timelapse.copy(quality = it))) }
                }
            }

            Group(tr("Eigene Filterfolgen"), tr("Die Namen der drei Plätze im Werkzeugkasten.")) {
                state.slotNames.forEachIndexed { index, name ->
                    if (index > 0) Line()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(tr("Platz {value}", "value" to (index + 1)), style = style(17f), modifier = Modifier.widthIn(min = 88.dp))
                        BasicTextField(name, { text -> onChange(state.copy(slotNames = state.slotNames.mapIndexed { i, old -> if (i == index) text.take(24) else old })) },
                            singleLine = true, textStyle = style(17f, 400, Ink.secondary).copy(textAlign = TextAlign.End), cursorBrush = SolidColor(Ink.yellow),
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            Group(tr("Tastatur und Fernbedienung"), tr("Beim Zeichnen steuern eine Bluetooth-Tastatur, ein Controller oder ein Selfie-Auslöser (sendet meist „Lauter“) LiCida aus der Ferne – praktisch, wenn das Handy weit weg über einem großen Blatt hängt. Zum Ändern eine Zeile antippen und die neue Taste drücken.")) {
                KeyAction.entries.forEachIndexed { index, action ->
                    if (index > 0) Line()
                    val code = state.keymap.keys[action] ?: KeyEvent.KEYCODE_UNKNOWN
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = tr("Taste ändern")) { onListen(if (listening == action) null else action) }
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(action.label, style = style(17f), modifier = Modifier.weight(1f))
                        if (listening == action) BasicText(tr("Taste drücken …"), style = style(17f, 600, Ink.yellow))
                        else BasicText(Keymap.label(code), style = style(17f, 400, Ink.secondary, tabular = true))
                    }
                }
                Line()
                BasicText(tr("Standardtasten"), style = style(17f, 400, Ink.yellow),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onListen(null); onChange(state.copy(keymap = Keymap())) }
                        .padding(horizontal = 16.dp, vertical = 12.dp))
            }

            Group(tr("Experte"), tr("Für einen Projektor: Das Kamerabild bleibt schwarz, nur die Vorlage leuchtet; Flimmern blendet zwischen Vorlage und Schwarz. Die Kamera läuft weiter – der Zeitraffer filmt also trotzdem.")) {
                SwitchRow(tr("Projektor-Modus"), tr("Kamerabild ausblenden"), state.projector, true) { onChange(state.copy(projector = it)) }
            }

            Group(tr("Datenschutz"), tr("LiCida hat keinen Internetzugang. Bilder, Sitzungen und Einstellungen bleiben auf diesem Gerät.")) {
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onPermissions).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BasicText(tr("Berechtigungen"), style = style(17f), modifier = Modifier.weight(1f))
                    BasicText(tr("Android-Einstellungen ›"), style = style(17f, 400, Ink.secondary))
                }
            }
        }
    }
}

/** A row whose choice is too wide beside its label: the label above, the choice below. */
@Composable
private fun Stacked(label: String, choice: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        BasicText(label, style = style(17f), modifier = Modifier.padding(bottom = 8.dp))
        choice()
    }
}

@Composable
private fun Group(caption: String, note: String?, content: @Composable () -> Unit) {
    BasicText(caption.uppercase(), style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 6.dp))
    Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E))) { content() }
    note?.let { BasicText(it, style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 6.dp)) }
}
