package io.github.veritasx1.licida

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Setting up (choose and compose the reference) and drawing (camera and reference locked) – the two
 *  modes of Camera Lucida (handbook p. 7–8), in the look of Apple's Camera app. */
enum class Mode { Setup, Draw }

@Composable
fun LiCidaApp(studio: Studio, initial: Bitmap?, cameraAllowed: Boolean, onAskCamera: () -> Unit, onDrawMode: (Boolean) -> Unit,
              startMode: Mode = Mode.Setup, cameras: (android.content.Context) -> List<CameraFacts> = Cameras::facts) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reference by remember { mutableStateOf(initial) }
    var placement by remember { mutableStateOf(studio.placement) }
    var opacity by remember { mutableStateOf(studio.opacity) }
    var drawOpacity by remember { mutableStateOf(studio.drawOpacity) }
    var mode by remember { mutableStateOf(if (initial != null) startMode else Mode.Setup) }
    var view by remember { mutableStateOf(DrawView()) }
    var chrome by remember { mutableStateOf(true) }
    var moving by remember { mutableStateOf(false) }
    var exposureLocked by remember { mutableStateOf(false) }
    // Card 15: hints by help level – the gesture hint counts once per start it shows in, and goes once the user moved the picture.
    var help by remember { mutableStateOf(studio.help) }
    val gestureCount = remember { studio.hintCount("gesten") }
    var gestureDone by remember { mutableStateOf(false) }
    var gestureCounted by remember { mutableStateOf(false) }
    val gestureHint = !gestureDone && help.shows(gestureCount)
    // Card 16: the draw-mode hint (gone at the first tap or after a while), the tour and the guide.
    val drawCount = remember { studio.hintCount("zeichnen") }
    var drawHintDone by remember { mutableStateOf(false) }
    var drawHintCounted by remember { mutableStateOf(false) }
    val anchors = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var tourStep by remember { mutableStateOf<Int?>(null) }
    var guidePage by remember { mutableStateOf(false) }
    var offerTour by remember { mutableStateOf(!studio.tourOffered && studio.help != HelpLevel.Off) }
    var screen by remember { mutableStateOf(Size(1f, 1f)) }
    var message by remember { mutableStateOf<String?>(null) }
    /** A short note in the middle (like iOS's HUD), gone after a moment. */
    fun say(text: String) {
        message = text
        scope.launch { delay(1800); if (message == text) message = null }
    }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    // The camera choice (card 5): the phone's cameras, the remembered one, its own view and fill mode.
    var cameraOptions by remember { mutableStateOf<List<CameraOption>>(emptyList()) }
    var cameraKey by remember { mutableStateOf(studio.cameraKey) }
    var cameraView by remember { mutableStateOf(studio.cameraView) }
    var fill by remember { mutableStateOf(studio.fill) }
    var ghost by remember { mutableStateOf(studio.ghost) }
    var cameraSheet by remember { mutableStateOf(false) }
    val chosenCamera = CameraCatalog.pick(cameraOptions, cameraKey)
    LaunchedEffect(cameraAllowed, cameraSheet) {
        if (cameraAllowed) cameraOptions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CameraCatalog.options(cameras(context)) }
    }
    var cameraQuality by remember { mutableStateOf(studio.cameraQuality) }
    val camera = rememberCamera(cameraAllowed, chosenCamera, cameraQuality)
    var projector by remember { mutableStateOf(studio.projector) }
    var keymap by remember { mutableStateOf(studio.keymap) }
    var settingsPage by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf<KeyAction?>(null) }
    var referenceOff by remember { mutableStateOf(false) }

    // The toolbox (cards 9/10): filters stack on a 2048-px working copy, rendered in the background.
    var edits by remember { mutableStateOf(studio.edits) }
    var slots by remember { mutableStateOf(studio.slots) }
    var filterSheet by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var previews by remember { mutableStateOf<Map<Filter, Bitmap>>(emptyMap()) }
    val work = remember(reference) { reference?.let { Reference.scaled(it, 2048) } }
    // The palette's colour layers while drawing (card 11): which are hidden; reset when the edits change.
    val lastPalette = edits.active.lastOrNull()?.palette
    var hiddenLayers by remember(edits) { mutableStateOf<Set<Int>>(emptySet()) }
    var paletteEditor by remember { mutableStateOf(false) }
    var paletteBackToSheet by remember { mutableStateOf(false) }
    // The draw mode's further tools (card 12).
    var tools by remember { mutableStateOf(Tools(sessionButton = studio.sessionButton, recordButton = studio.recordButton)) }
    LaunchedEffect(tools.sessionButton, tools.recordButton) { studio.sessionButton = tools.sessionButton; studio.recordButton = tools.recordButton }
    var moreSheet by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    LaunchedEffect(tools.torch) { camera.torch(tools.torch) }

    val flickerAlpha = if (tools.flicker && mode == Mode.Draw) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "Flimmern")
        val period = (2400 - tools.flickerSpeed * 2100).toInt()
        transition.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(period, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "Flimmern").value
    } else 1f
    /** A picture of the drawing surface without buttons (handbook p. 32: "Share my work"). */
    fun captureWork(then: (Bitmap) -> Unit) {
        val activity = context as? android.app.Activity ?: return
        capturing = true; moreSheet = false
        scope.launch {
            delay(180)  // the buttons are gone from the screen
            captureScreen(activity) { picture -> capturing = false; picture?.let(then) ?: say("Bild ließ sich nicht aufnehmen") }
        }
    }

    // The colour sliders (card 4): live as a colour matrix on the picture, a step when taken over.
    var effectsSheet by remember { mutableStateOf(false) }
    var effects by remember { mutableStateOf(Effects()) }
    var paletteSource by remember { mutableStateOf<Bitmap?>(null) }
    var wheelOf by remember { mutableStateOf<Palette?>(null) }
    var importInto by remember { mutableStateOf<((Palette) -> Unit)?>(null) }
    var importCount by remember { mutableStateOf(Palette.DEFAULT_COLOURS) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val into = importInto ?: return@rememberLauncherForActivityResult
        uri?.let { Reference.decode(context, it, 480) }?.let { picture -> into(Palette.find(Reference.pixels(picture), importCount)) }
        importInto = null
    }
    LaunchedEffect(work, edits.active, hiddenLayers) {
        val base = work ?: run { shown = null; return@LaunchedEffect }
        if (edits.active.isEmpty()) { shown = base; return@LaunchedEffect }
        busy = true
        shown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val palette = edits.active.last().palette
            if (palette != null && hiddenLayers.isNotEmpty())
                Reference.bitmap(palette.render(Edits.render(Reference.pixels(base), edits.active.dropLast(1)), hiddenLayers))
            else Reference.bitmap(Edits.render(Reference.pixels(base), edits.active))
        }
        busy = false
    }
    /** The editor works on the picture as it is before the palette (when a palette is the last step: before that one). */
    fun openPalette() {
        val base = work ?: return
        scope.launch {
            paletteSource = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val before = if (lastPalette != null) edits.active.dropLast(1) else edits.active
                Reference.bitmap(Edits.render(Reference.pixels(base), before))
            }
            paletteBackToSheet = filterSheet
            filterSheet = false   // the editor takes the whole screen; "Abbrechen" brings the toolbox back
            paletteEditor = true
        }
    }
    LaunchedEffect(shown, filterSheet) {
        val now = shown ?: return@LaunchedEffect
        if (!filterSheet) return@LaunchedEffect
        val last = edits.active.lastOrNull()?.filter
        previews = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val thumb = Reference.pixels(Reference.scaled(now, 160))
            Filter.entries.associateWith { filter -> Reference.bitmap(Filters.apply(thumb, filter, filter.defaultValue, last)) }
        }
    }
    fun edit(next: Edits) { edits = next; studio.edits = next }

    // Straightening the camera (cards 6/7): by hand, or automatically from the target.
    var correction by remember { mutableStateOf(studio.correction) }
    var savedCorrection by remember { mutableStateOf(studio.savedCorrection) }
    var helperGhost by remember { mutableStateOf(studio.helperGhost) }
    var ownAspect by remember { mutableStateOf(studio.ownTargetAspect) }
    var autoBusy by remember { mutableStateOf<String?>(null) }
    var checkRect by remember { mutableStateOf<FloatArray?>(null) }
    var askCheck by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkSpeed by remember { mutableStateOf(0.4f) }
    val helperPicture = remember { Target.bitmap(840) }
    val ownTargetPicture = remember(ownAspect) {
        if (ownAspect == null) null else runCatching { android.graphics.BitmapFactory.decodeFile(Reference.ownTarget(context).path) }.getOrNull()
    }
    val ownPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { Reference.keepOwnTarget(context, it) }?.let { picture ->
            ownAspect = picture.width.toFloat() / picture.height; studio.ownTargetAspect = ownAspect
        }
    }
    fun setCorrection(next: Correction) { correction = next; studio.correction = next }

    // The time-lapse (card 13): a picture at each interval into Android's encoder.
    var timelapse by remember { mutableStateOf(studio.timelapse) }
    var recorder by remember { mutableStateOf<TimelapseRecorder?>(null) }
    var filmFrames by remember { mutableStateOf(0) }
    fun stopRecording() {
        val active = recorder ?: return
        recorder = null
        active.stop { ok -> say(if (ok) "Zeitraffer in Fotos gesichert" else "Zeitraffer ließ sich nicht sichern") }
    }
    fun startRecording() {
        val (w, h) = Timelapse.size(screen.width.toInt().coerceAtLeast(2), screen.height.toInt().coerceAtLeast(2), timelapse.height)
        recorder = TimelapseRecorder.start(context, w, h, timelapse) ?: run { say("Aufnahme lässt sich nicht starten"); return }
        filmFrames = 0
        if (!exposureLocked) say("Tipp: vorher mit zwei Fingern tippen – Belichtung sperren, damit es nicht flackert")
    }
    LaunchedEffect(recorder) {
        val active = recorder ?: return@LaunchedEffect
        val activity = context as? android.app.Activity
        while (recorder === active) {
            if (timelapse.recordUi && activity != null) captureScreen(activity) { picture -> picture?.let { active.add(it); filmFrames++ } }
            else camera.snapshot(preview)?.let { raw ->
                active.add(cameraFrame(raw, correction, cameraView, if (timelapse.ignoreZoom) null else view)); filmFrames++
            }
            delay(timelapse.intervalMillis)
        }
    }
    LaunchedEffect(mode) { if (mode != Mode.Draw) stopRecording() }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val watcher = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) stopRecording() }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }
    fun runAuto() {
        scope.launch {
            for (second in 3 downTo 1) { autoBusy = "Arm aus dem Bild … $second"; delay(1000) }
            autoBusy = "Suche das Zielbild …"
            val shot = camera.snapshot(preview)
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { shot?.let { AutoAlign.find(it, ownAspect) } }
            autoBusy = null
            if (found == null) {
                val text = "Zielbild nicht gefunden – flach hinlegen, gut beleuchten, ganz ins Bild"
                message = text; delay(2600); if (message == text) message = null
                return@launch
            }
            setCorrection(Correction(auto = found.homography))
            cameraView = CameraView(); studio.cameraView = cameraView   // AUTO also undoes a mirror
            checkRect = found.rectangle
            askCheck = true
        }
    }


    // Sessions (card 14): keep everything to carry on later, and find the paper again.
    var saveSheet by remember { mutableStateOf(false) }
    var saveSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var saveDescription by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf<String?>(null) }
    var sessionsSheet by remember { mutableStateOf(false) }
    var sessionList by remember { mutableStateOf<List<Session>>(emptyList()) }
    var aligning by remember { mutableStateOf<Session?>(null) }
    var alignSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var alignAlpha by remember { mutableStateOf(0.5f) }
    var alignBusy by remember { mutableStateOf(false) }
    var setupMenu by remember { mutableStateOf(false) }
    /** The camera picture as seen (straightened, the camera's own view), without the draw zoom and without buttons. */
    fun cameraPicture(): Bitmap? = camera.snapshot(preview)?.let { cameraFrame(it, correction, cameraView, null) }
    fun openSave() {
        saveSnapshot = cameraPicture()
        saveDescription = Session.defaultDescription()
        saveSheet = true
    }
    fun retake() {
        saveSheet = false
        scope.launch {
            for (second in 3 downTo 1) { countdown = "Hand aus dem Bild … $second"; delay(1000) }
            countdown = null; delay(150)
            saveSnapshot = cameraPicture() ?: saveSnapshot
            saveSheet = true
        }
    }
    fun saveSession() {
        val picture = saveSnapshot ?: return
        val session = Session(Sessions.newId(), saveDescription.trim().ifBlank { Session.defaultDescription() }, System.currentTimeMillis(),
            placement, opacity, drawOpacity, cameraKey, fill, cameraView, edits, correction)
        saveSheet = false
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Sessions.save(context, session, Reference.file(context), picture) }
            say(if (ok) "Sitzung gesichert" else "Sitzung ließ sich nicht sichern")
        }
    }
    fun openSessions() {
        setupMenu = false
        scope.launch {
            sessionList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Sessions.list(context) }
            sessionsSheet = true
        }
    }
    fun restore(session: Session) {
        scope.launch {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val file = Sessions.reference(context, session.id)
                runCatching { file.copyTo(Reference.file(context), overwrite = true); Reference.restore(context) }.getOrNull() to Sessions.snapshot(context, session.id)
            }
            val picture = loaded.first ?: run { say("Die Sitzung ließ sich nicht öffnen"); return@launch }
            reference = picture
            placement = session.placement; studio.placement = placement
            opacity = session.opacity; studio.opacity = opacity
            drawOpacity = session.drawOpacity; studio.drawOpacity = drawOpacity
            session.cameraKey?.let { cameraKey = it; studio.cameraKey = it }
            fill = session.fill; studio.fill = fill
            cameraView = session.cameraView; studio.cameraView = cameraView
            edits = session.edits; studio.edits = edits
            setCorrection(session.correction)
            mode = Mode.Setup; view = DrawView()
            sessionsSheet = false
            alignSnapshot = loaded.second
            aligning = if (loaded.second != null) session else null
        }
    }
    /** The paper found again by image matching: the reference is carried along as the paper moved. */
    fun alignAuto() {
        val session = aligning ?: return
        val old = alignSnapshot ?: return
        scope.launch {
            alignBusy = true
            val live = cameraPicture()
            val found = live?.let {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val small = Reference.scaled(it, 800)
                    Registration.find(Reference.pixels(Reference.scaled(old, 800)), Reference.pixels(small))?.let { shift ->
                        val back = it.width.toFloat() / small.width
                        shift.copy(dx = shift.dx * back, dy = shift.dy * back)
                    }
                }
            }
            alignBusy = false
            if (found == null) { say("Blatt nicht wiedergefunden – von Hand verschieben"); return@launch }
            placement = found.carry(session.placement); studio.placement = placement
            say("Ausgerichtet")
        }
    }

    fun use(bitmap: Bitmap?) {
        if (bitmap == null) { say("Das Bild ließ sich nicht öffnen"); return }
        reference = bitmap
        placement = Placement()
        studio.placement = placement
        edits = Edits(); studio.edits = edits
        mode = Mode.Setup
    }

    // Fotos (Android's photo picker: LiCida sees only the one picture chosen) and Dateien (the system's file dialog).
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) use(Reference.load(context, uri))
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) use(Reference.load(context, uri))
    }
    fun pickPhoto() = photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun pickFile() = files.launch(arrayOf("image/*"))
    fun takePhoto() {
        if (!cameraAllowed) { onAskCamera(); return }
        camera.capture(context) { bitmap -> bitmap?.let { Reference.keep(context, it) }; use(bitmap) }
    }

    LaunchedEffect(mode) { onDrawMode(mode == Mode.Draw); if (mode != Mode.Draw) referenceOff = false }
    val gestureShowing = gestureHint && reference != null && mode == Mode.Setup && cameraAllowed
    LaunchedEffect(gestureShowing) { if (gestureShowing && !gestureCounted) { gestureCounted = true; studio.countHint("gesten") } }
    val drawHint = mode == Mode.Draw && chrome && !drawHintDone && help.shows(drawCount)
    LaunchedEffect(drawHint) {
        if (!drawHint) return@LaunchedEffect
        if (!drawHintCounted) { drawHintCounted = true; studio.countHint("zeichnen") }
        delay(6000); drawHintDone = true
    }
    fun startTour() { guidePage = false; setupMenu = false; offerTour = false; studio.tourOffered = true; mode = Mode.Setup; tourStep = 0 }
    fun saveGuide() {
        scope.launch {
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
            val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Guide.savePdf(context, Guide.pdf(context, version)) }
            if (uri == null) { say("Anleitung ließ sich nicht sichern"); return@launch }
            say("Anleitung in Downloads/LiCida gesichert")
            val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(view) }
        }
    }

    /** The largest drawing (handbook p. 25): the camera's whole field at its sharpest. */
    fun maximize() {
        setupMenu = false
        fill = false; studio.fill = false
        cameraQuality = CameraQuality.Sharp; studio.cameraQuality = CameraQuality.Sharp
        say("Ganzes Bild, scharf – jetzt die Vorlage bildschirmfüllend zoomen")
    }
    /** "Kamera zurücksetzen" (handbook p. 25): straightening, mirror and the camera's own view back to the start. */
    fun resetCamera() {
        setupMenu = false
        setCorrection(Correction())
        cameraView = chosenCamera?.let(Composition::cameraViewFor) ?: CameraView(); studio.cameraView = cameraView
        say("Kamera zurückgesetzt")
    }
    fun namedSlots() = slots.mapIndexed { i, slot -> slot.copy(name = slot.name.trim().ifBlank { "Platz ${i + 1}" }) }
    fun settingsState() = SettingsState(help, fill, cameraQuality, timelapse, slots.map { it.name }, keymap, projector)
    fun applySettings(next: SettingsState) {
        if (next.help != help) { help = next.help; studio.help = next.help }
        if (next.fill != fill) { fill = next.fill; studio.fill = next.fill }
        if (next.quality != cameraQuality) { cameraQuality = next.quality; studio.cameraQuality = next.quality }
        if (next.timelapse != timelapse) { timelapse = next.timelapse; studio.timelapse = next.timelapse }
        if (next.slotNames != slots.map { it.name }) {
            // While typing a name may be empty for a moment; kept (and shown elsewhere) as "Platz n" then.
            slots = slots.mapIndexed { i, slot -> slot.copy(name = next.slotNames[i]) }
            studio.slots = namedSlots()
        }
        if (next.keymap != keymap) { keymap = next.keymap; studio.keymap = next.keymap }
        if (next.projector != projector) { projector = next.projector; studio.projector = next.projector }
    }

    // Keys (handbook p. 47): while drawing, the mapped ones steer; on the settings page, the next key is taken for a row.
    KeyListener { event ->
        val code = event.keyCode
        val waiting = listening
        if (settingsPage && waiting != null) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (code == android.view.KeyEvent.KEYCODE_BACK) listening = null
                else if (Keymap.usable(code)) { applySettings(settingsState().copy(keymap = keymap.assign(waiting, code))); listening = null }
            }
            return@KeyListener true
        }
        val sheetOpen = filterSheet || moreSheet || saveSheet || effectsSheet || paletteEditor || settingsPage || wheelOf != null
        if (mode != Mode.Draw || sheetOpen) return@KeyListener false
        val action = keymap.action(code) ?: return@KeyListener false
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return@KeyListener true
        val first = event.repeatCount == 0
        when (action) {
            KeyAction.Fainter, KeyAction.Stronger -> {
                drawOpacity = (drawOpacity + if (action == KeyAction.Stronger) KeyMoves.OPACITY_STEP else -KeyMoves.OPACITY_STEP).coerceIn(0f, 1f)
                studio.drawOpacity = drawOpacity
                if (referenceOff) referenceOff = false
            }
            KeyAction.Toggle -> if (first) { referenceOff = !referenceOff; say(if (referenceOff) "Vorlage aus" else "Vorlage ein") }
            KeyAction.Flicker -> if (first) { tools = tools.copy(flicker = !tools.flicker); say(if (tools.flicker) "Flimmern an" else "Flimmern aus") }
            KeyAction.SplitLeft, KeyAction.SplitRight -> {
                val at = if (tools.split) tools.splitAt + (if (action == KeyAction.SplitRight) KeyMoves.SPLIT_STEP else -KeyMoves.SPLIT_STEP) else tools.splitAt
                tools = tools.copy(split = true, splitAt = at.coerceIn(0.05f, 0.95f))
            }
            else -> view = KeyMoves.view(action, view, screen)
        }
        true
    }
    BackHandler(enabled = cameraSheet) { cameraSheet = false }
    BackHandler(enabled = filterSheet) { filterSheet = false }
    BackHandler(enabled = moreSheet) { moreSheet = false }
    BackHandler(enabled = effectsSheet) { effectsSheet = false; effects = Effects() }
    BackHandler(enabled = paletteEditor) { paletteEditor = false; filterSheet = paletteBackToSheet }
    BackHandler(enabled = saveSheet) { saveSheet = false }
    BackHandler(enabled = sessionsSheet) { sessionsSheet = false }
    BackHandler(enabled = setupMenu) { setupMenu = false }
    BackHandler(enabled = aligning != null) { aligning = null; alignSnapshot = null }
    BackHandler(enabled = tourStep != null) { tourStep = null }
    BackHandler(enabled = guidePage) { guidePage = false }
    BackHandler(enabled = settingsPage) { if (listening != null) listening = null else { settingsPage = false; slots = namedSlots() } }
    BackHandler(enabled = mode == Mode.Draw) { mode = Mode.Setup; view = DrawView(); chrome = true }

    val shownOpacity = when {
        aligning != null -> 0f
        mode == Mode.Draw && tools.split -> 1f
        mode == Mode.Draw && referenceOff -> 0f
        mode == Mode.Draw -> drawOpacity
        cameraSheet -> if (ghost) 0.35f else 0f
        moving -> minOf(opacity, Composition.WHILE_MOVING)
        else -> opacity
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTourAnchors provides anchors) {
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }) {
        // The picture: camera below, reference above – in draw mode zoomed and moved as one.
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (mode == Mode.Draw) {
                scaleX = view.zoom; scaleY = view.zoom
                translationX = view.offset.x; translationY = view.offset.y
            }
        }) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = cameraView.zoom * (if (cameraView.flipH) -1f else 1f)
                scaleY = cameraView.zoom * (if (cameraView.flipV) -1f else 1f)
                translationX = cameraView.offset.x; translationY = cameraView.offset.y
            }) {
                CameraLayer(camera, onView = { preview = it }, fill = fill, correction = correction)
                // Checking the automatic alignment: the target fades in and out where it lies (handbook p. 24).
                if (checking) checkRect?.let { rect -> CheckOverlay(rect, ownTargetPicture ?: helperPicture, ownTargetPicture != null, checkSpeed) }
            }
            // Projector mode (handbook p. 47): the camera keeps running (time-lapse), but the screen stays black under the reference.
            if (projector) Box(Modifier.fillMaxSize().background(Color.Black))
            // Finding the paper again: the session's snapshot, carried along like the reference.
            if (aligning != null) alignSnapshot?.let { snapshot ->
                val saved = aligning!!.placement
                val image = remember(snapshot) { snapshot.asImageBitmap() }
                Image(image, contentDescription = "Schnappschuss der Sitzung", contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val scale = placement.scale / saved.scale
                        val angle = placement.rotation - saved.rotation
                        val a = Math.toRadians(angle.toDouble())
                        val ox = saved.offset.x; val oy = saved.offset.y
                        scaleX = scale; scaleY = scale; rotationZ = angle
                        translationX = placement.offset.x - scale * (ox * kotlin.math.cos(a) - oy * kotlin.math.sin(a)).toFloat()
                        translationY = placement.offset.y - scale * (ox * kotlin.math.sin(a) + oy * kotlin.math.cos(a)).toFloat()
                        alpha = alignAlpha
                    })
            }
            (shown ?: reference)?.let { bitmap ->
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(image, contentDescription = "Vorlage", contentScale = ContentScale.Fit,
                    colorFilter = if (effectsSheet && !effects.isNeutral) androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix(effects.matrix())) else null,
                    modifier = Modifier.fillMaxSize().then(if (mode == Mode.Draw && tools.split) Modifier.drawWithContent {
                        // Split (handbook p. 32): the reference only left of the divider. Clipped before the reference is
                        // placed, so the line stays upright; the shared layer is zoomed, hence screen → layer coordinates.
                        val cx = size.width / 2
                        val local = cx + (tools.splitAt * size.width - cx - view.offset.x) / view.zoom
                        clipRect(right = local) { this@drawWithContent.drawContent() }
                    } else Modifier).graphicsLayer {
                        scaleX = placement.scale; scaleY = placement.scale; rotationZ = placement.rotation
                        translationX = placement.offset.x; translationY = placement.offset.y
                        alpha = shownOpacity * (if (tools.flicker && mode == Mode.Draw) flickerAlpha else 1f)
                    })
            }
        }

        // The helper grid as a ghost over the screen: lay the printed target so that it matches (handbook p. 19).
        if (helperGhost && mode == Mode.Setup) {
            val ghostImage = remember(helperPicture) { helperPicture.asImageBitmap() }
            Image(ghostImage, contentDescription = "Hilfsraster", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f })
        }

        // Gestures – with the camera sheet open they move the camera picture (handbook p. 14).
        if (mode == Mode.Setup && cameraSheet) {
            Box(Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                        studio.cameraView = cameraView
                    }
                }
                .pointerInput(screen) {
                    detectTransformGestures { centroid, pan, zoom, _ -> cameraView = Composition.moveCamera(cameraView, centroid, pan, zoom, screen) }
                })
        } else if (mode == Mode.Setup && reference != null) {
            Box(Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        moving = true
                        do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                        moving = false
                        studio.placement = placement
                        gestureDone = true
                    }
                }
                .pointerInput(screen) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        placement = Composition.transform(placement, centroid, pan, zoom, rotation, screen)
                    }
                })
        }
        if (mode == Mode.Draw) {
            Box(Modifier.fillMaxSize()
                .pointerInput(screen) {
                    detectTransformGestures { centroid, pan, zoom, _ -> view = Composition.zoom(view, centroid, pan, zoom, screen) }
                }
                .pointerInput(screen) {
                    detectTapGestures(onTap = { chrome = !chrome; drawHintDone = true }, onDoubleTap = { view = Composition.doubleTap(view, it, screen) })
                }
                .pointerInput(Unit) {
                    // Two fingers tap together: exposure for that spot, held (handbook p. 26).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var two = false
                        var spot = Offset.Zero
                        val started = System.currentTimeMillis()
                        do {
                            val event = awaitPointerEvent()
                            val down = event.changes.filter { it.pressed }
                            if (down.size == 2) { two = true; spot = (down[0].position + down[1].position) / 2f }
                        } while (event.changes.any { it.pressed })
                        if (two && System.currentTimeMillis() - started < 300) {
                            camera.lockExposure(preview, spot)
                            exposureLocked = true
                            say("Belichtung gesperrt")
                        }
                    }
                })
        }

        when {
            filterSheet && reference != null -> FilterSheet(edits, previews, slots, busy,
                onApply = { step -> edit(edits.add(step)) },
                onValue = { value -> edit(edits.replaceLast(Step(Filter.Posterize, value))) },
                onUndo = { edit(edits.undo()) }, onRedo = { edit(edits.redo()) }, onJump = { edit(edits.jump(it)) },
                onStore = { index -> slots = slots.mapIndexed { i, slot -> if (i == index) slot.copy(steps = edits.active) else slot }; studio.slots = slots },
                onReplay = { index -> edit(edits.replay(slots[index].steps)) },
                onRename = { index, name -> slots = slots.mapIndexed { i, slot -> if (i == index) slot.copy(name = name) else slot }; studio.slots = slots },
                onSave = {
                    val bitmap = shown ?: reference ?: return@FilterSheet
                    val saved = Reference.saveToPhotos(context, Reference.compose(bitmap, placement, screen.width.toInt(), screen.height.toInt()),
                        "LiCida-${System.currentTimeMillis() / 1000}")
                    say(if (saved) "In Fotos gesichert" else "Sichern hat nicht geklappt")
                },
                onDone = { filterSheet = false },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.52f),
                onPalette = ::openPalette,
                onEffects = { effects = Effects(); effectsSheet = true; filterSheet = false })
            mode == Mode.Setup && cameraSheet -> CameraSheet(cameraOptions, chosenCamera, fill, ghost, reference != null,
                onChoose = { option ->
                    if (option.key != chosenCamera?.key) {
                        cameraKey = option.key; studio.cameraKey = option.key
                        cameraView = Composition.cameraViewFor(option); studio.cameraView = cameraView
                    }
                },
                onFill = { fill = it; studio.fill = it },
                onGhost = { ghost = it; studio.ghost = it },
                onReset = { cameraView = chosenCamera?.let(Composition::cameraViewFor) ?: CameraView(); studio.cameraView = cameraView },
                onDone = { cameraSheet = false },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.5f),
                correcting = CorrectionControls(correction, cameraView.flipH, cameraView.flipV, helperGhost, ownAspect != null,
                    savedCorrection != null, autoBusy,
                    onCorrection = { correction = it }, onCorrectionDone = { studio.correction = correction },
                    onFlip = { horizontal ->
                        cameraView = if (horizontal) cameraView.copy(flipH = !cameraView.flipH) else cameraView.copy(flipV = !cameraView.flipV)
                        studio.cameraView = cameraView
                    },
                    onHelperGhost = { helperGhost = it; studio.helperGhost = it },
                    onResetCorrection = { setCorrection(Correction()) },
                    onTargetKind = { own -> if (!own) { ownAspect = null; studio.ownTargetAspect = null } },
                    onPickOwnTarget = { ownPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPrintTarget = {
                        runCatching {
                            androidx.print.PrintHelper(context).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }
                                .printBitmap("LiCida-Zielbild", Target.bitmap(2480))
                        }.onFailure { say("Drucken ist hier nicht möglich") }
                    },
                    onSaveTarget = {
                        say(if (Reference.saveToPhotos(context, Target.bitmap(2480), "LiCida-Zielbild")) "Zielbild in Fotos gesichert" else "Sichern hat nicht geklappt")
                    },
                    onAuto = ::runAuto,
                    onSaveSetting = { savedCorrection = correction; studio.savedCorrection = correction; say("Ausrichtung gesichert") },
                    onRestoreSetting = { savedCorrection?.let { setCorrection(it); say("Ausrichtung wiederhergestellt") } }))
            effectsSheet -> Unit   // while the colour sliders are open, everything else waits (handbook p. 10)
            aligning != null && mode == Mode.Setup -> AlignControls(alignAlpha, { alignAlpha = it }, alignBusy, ::alignAuto,
                Modifier.align(Alignment.BottomCenter)) { aligning = null; alignSnapshot = null }
            mode == Mode.Setup -> SetupChrome(reference, opacity, !gestureHint || tourStep != null, cameraAllowed,
                onOpacity = { opacity = it }, onOpacityDone = { studio.opacity = opacity },
                onPhotos = ::pickPhoto, onFiles = ::pickFile, onCamera = ::takePhoto, onAskCamera = onAskCamera, onMenu = { setupMenu = true },
                onRotate = { placement = Composition.quarterTurn(placement); studio.placement = placement },
                onSave = {
                    val bitmap = shown ?: reference ?: return@SetupChrome
                    val saved = Reference.saveToPhotos(context, Reference.compose(bitmap, placement, screen.width.toInt(), screen.height.toInt()),
                        "LiCida-Vorlage-${System.currentTimeMillis() / 1000}")
                    say(if (saved) "In Fotos gesichert" else "Sichern hat nicht geklappt")
                },
                onDraw = { mode = Mode.Draw; view = DrawView(); chrome = true },
                onCameraSettings = { cameraSheet = true }, onFilters = { filterSheet = true })
            else -> AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                if (!capturing) DrawChrome(view, drawOpacity, exposureLocked, onFilters = { filterSheet = true },
                    torch = tools.torch, onTorchOff = { tools = tools.copy(torch = false) }, onMore = { moreSheet = true },
                    record = if (tools.recordButton || recorder != null) { { RecordButton(recorder != null, filmFrames.let { recorder?.filmSeconds ?: 0 }) {
                        if (recorder != null) stopRecording() else startRecording() } } } else null,
                    onOpacity = { drawOpacity = it }, onOpacityDone = { studio.drawOpacity = drawOpacity },
                    onBack = { mode = Mode.Setup; view = DrawView() },
                    onSaveSession = if (tools.sessionButton) ::openSave else null,
                    onFocus = { camera.focus(preview); say("Scharfgestellt") },
                    onUnlock = { camera.unlock(); exposureLocked = false },
                    onZoomReset = { view = DrawView() })
            }
        }

        if (lastPalette != null && !filterSheet && !cameraSheet && !paletteEditor && !effectsSheet && aligning == null && (mode == Mode.Setup || chrome)) {
            PaletteLayers(lastPalette, hiddenLayers, { hiddenLayers = it }, onEdit = ::openPalette,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = if (mode == Mode.Draw) 84.dp else 176.dp))
        }
        if (mode == Mode.Draw && tools.split && !capturing) SplitHandle(tools.splitAt) { tools = tools.copy(splitAt = it) }
        if (moreSheet && mode == Mode.Draw) MoreToolsSheet(tools, camera.hasTorch, { tools = it },
            extra = { TimelapseRows(tools, timelapse, { tools = it }) { timelapse = it; studio.timelapse = it } },
            onSave = { captureWork { picture -> say(if (Reference.saveToPhotos(context, picture, "LiCida-Zeichnung-${System.currentTimeMillis() / 1000}")) "In Fotos gesichert" else "Sichern hat nicht geklappt") } },
            onShare = {
                captureWork { picture ->
                    val uri = Reference.savePhoto(context, picture, "LiCida-Zeichnung-${System.currentTimeMillis() / 1000}") ?: return@captureWork say("Sichern hat nicht geklappt")
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/jpeg")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(android.content.Intent.createChooser(send, "Zeichnung teilen"))
                }
            },
            onClose = { moreSheet = false })
        if (effectsSheet) EffectsSheet(effects, { effects = it }, onCancel = { effectsSheet = false; effects = Effects(); filterSheet = true },
            onApply = { edit(edits.add(Step.effects(effects))); effectsSheet = false; effects = Effects() },
            modifier = Modifier.align(Alignment.BottomCenter))
        if (paletteEditor) paletteSource?.let { source ->
            PaletteEditor(source, lastPalette,
                onImport = { count, into -> importCount = count; importInto = into; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onWheel = { wheelOf = it },
                onCancel = { paletteEditor = false; filterSheet = paletteBackToSheet },
                onApply = { palette ->
                    edit(if (lastPalette != null) edits.replaceLast(Step.palette(palette)) else edits.add(Step.palette(palette)))
                    paletteEditor = false; filterSheet = false
                })
        }
        wheelOf?.let { palette ->
            PaletteWheel(palette,
                onSave = { say(if (Reference.saveToPhotos(context, it, "LiCida-Farbkreis-${System.currentTimeMillis() / 1000}")) "Farbkreis in Fotos gesichert" else "Sichern hat nicht geklappt") },
                onPrint = { wheel -> runCatching { androidx.print.PrintHelper(context).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }.printBitmap("LiCida-Farbkreis", wheel) } },
                onClose = { wheelOf = null })
        }

        autoBusy?.let { text ->
            if (!cameraSheet) return@let
            BasicText(text, style = style(17f, 600, Color.Black), modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp).clip(CircleShape).background(Ink.yellow).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (askCheck) AskDialog("Kamera ausgerichtet", "Jetzt prüfen, ob Zielbild und Kamerabild genau übereinanderliegen? Das Zielbild dabei liegen lassen.",
            "Prüfen", onYes = { askCheck = false; checking = true; cameraSheet = false }, onNo = { askCheck = false })
        if (checking) CheckControls(checkSpeed, { checkSpeed = it }, Modifier.align(Alignment.BottomCenter)) { checking = false }

        if (setupMenu && mode == Mode.Setup) SetupMenu(onClose = { setupMenu = false }, entries = listOf(
            MenuEntry(Symbol.Folder, "Aus Dateien") { setupMenu = false; pickFile() },
            MenuEntry(Symbol.Sessions, "Sitzungen …") { openSessions() },
            MenuEntry(Symbol.Maximize, "Größtmögliche Zeichnung", ::maximize),
            MenuEntry(Symbol.Reset, "Kamera zurücksetzen", ::resetCamera),
            MenuEntry(Symbol.Gear, "Einstellungen …") { setupMenu = false; settingsPage = true },
            MenuEntry(Symbol.Help, "Hilfe …") { setupMenu = false; guidePage = true }))
        if (drawHint && !capturing && !moreSheet && !filterSheet) HintPill(Symbol.Hand, "Tippen blendet die Knöpfe aus · Doppeltipp zoomt",
            Modifier.align(Alignment.TopCenter))
        if (guidePage) GuidePage(onTour = ::startTour, onPdf = ::saveGuide, onDone = { guidePage = false })
        tourStep?.let { index ->
            TourOverlay(index, anchors, onNext = { tourStep = if (index < Tour.steps.lastIndex) index + 1 else null }, onSkip = { tourStep = null })
        }
        if (offerTour && cameraAllowed && mode == Mode.Setup && tourStep == null && !settingsPage && !guidePage)
            AskDialog("Willkommen bei LiCida", "Ein kurzer Rundgang zeigt dir die Knöpfe – eine Minute. Du findest ihn später unter „Mehr“ → „Hilfe“.",
                "Rundgang", onYes = ::startTour, onNo = { offerTour = false; studio.tourOffered = true })
        if (settingsPage) SettingsPage(settingsState(), listening, ::applySettings, onListen = { listening = it },
            onPermissions = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)))
            },
            onDone = { listening = null; settingsPage = false; slots = namedSlots() })
        if (saveSheet) SaveSessionSheet(saveSnapshot, saveDescription, { saveDescription = it }, ::retake, { saveSheet = false }, ::saveSession)
        if (sessionsSheet) SessionsSheet(sessionList, { Sessions.thumbnail(context, it.id) }, ::restore,
            onDelete = { gone -> Sessions.delete(context, gone.id); sessionList = sessionList - gone; say("Sitzung gelöscht") },
            onClose = { sessionsSheet = false })
        (countdown ?: if (alignBusy) "Suche dein Blatt …" else null)?.let { text ->
            BasicText(text, style = style(17f, 600, Color.Black), modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp).clip(CircleShape).background(Ink.yellow).padding(horizontal = 16.dp, vertical = 8.dp))
        }

        message?.let { text ->
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Ink.glassStrong).padding(horizontal = 18.dp, vertical = 12.dp)) {
                BasicText(text, style = style(15f, 600))
            }
        }
    }
    }
}

@Composable
private fun SetupChrome(reference: Bitmap?, opacity: Float, hintSeen: Boolean, cameraAllowed: Boolean,
                        onOpacity: (Float) -> Unit, onOpacityDone: () -> Unit,
                        onPhotos: () -> Unit, onFiles: () -> Unit, onCamera: () -> Unit, onAskCamera: () -> Unit, onMenu: () -> Unit,
                        onRotate: () -> Unit, onSave: () -> Unit, onDraw: () -> Unit, onCameraSettings: () -> Unit, onFilters: () -> Unit) {
    val ready = reference != null
    Box(Modifier.fillMaxSize()) {
        // Top: files on the left, turn and keep on the right (Camera keeps its top bar this light).
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Symbol.Ellipsis, "Mehr", modifier = Modifier.tourAnchor("mehr"), onClick = onMenu)
            Spacer(Modifier.size(12.dp))
            GlassButton(Symbol.Aperture, "Kamera wählen und einstellen", enabled = cameraAllowed, modifier = Modifier.tourAnchor("kamera"), onClick = onCameraSettings)
            Spacer(Modifier.size(12.dp))
            GlassButton(Symbol.Filters, "Werkzeuge und Filter", enabled = ready, modifier = Modifier.tourAnchor("werkzeuge"), onClick = onFilters)
            Spacer(Modifier.weight(1f))
            GlassButton(Symbol.RotateRight, "Vorlage um 90 Grad drehen", enabled = ready, modifier = Modifier.tourAnchor("drehen"), onClick = onRotate)
            Spacer(Modifier.size(12.dp))
            GlassButton(Symbol.Save, "Vorlage in Fotos sichern", enabled = ready, modifier = Modifier.tourAnchor("sichern"), onClick = onSave)
        }

        if (!cameraAllowed) CameraNeeded(onAskCamera, Modifier.align(Alignment.Center))
        else if (!ready) EmptyStart(onPhotos, onFiles, onCamera, Modifier.align(Alignment.Center))
        else if (!hintSeen) HintPill(Symbol.Hand, "Mit zwei Fingern verschieben, zoomen und drehen", Modifier.align(Alignment.TopCenter))

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (ready) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).tourAnchor("deckkraft"), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Vorlage", style = style(13f, 600), modifier = Modifier.widthIn(min = 64.dp))
                IosSlider(opacity, onOpacity, "Deckkraft der Vorlage", Modifier.weight(1f), onRelease = onOpacityDone)
                BasicText("${(opacity * 100).toInt()} %", style = style(13f, 500, Ink.secondary, tabular = true).copy(textAlign = TextAlign.End), modifier = Modifier.widthIn(min = 48.dp))
            }
            Row(Modifier.fillMaxWidth().background(Ink.bar).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PhotosButton(reference, onPhotos, Modifier.tourAnchor("fotos"))
                DrawButton(enabled = ready, modifier = Modifier.tourAnchor("zeichnen"), onClick = onDraw)
                GlassButton(Symbol.Camera, "Vorlage fotografieren", size = 52.dp, modifier = Modifier.tourAnchor("fotografieren"), onClick = onCamera)
            }
        }
    }
}

/** Like Camera's last-photo thumbnail: the current reference, or the Fotos symbol. */
@Composable
private fun PhotosButton(reference: Bitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Ink.glass).border(1.5.dp, Ink.white.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
        .clickable(role = Role.Button, onClickLabel = "Bild aus Fotos", onClick = onClick).semantics { contentDescription = "Bild aus Fotos" },
        contentAlignment = Alignment.Center) {
        if (reference != null) {
            val thumb = remember(reference) { reference.asImageBitmap() }
            Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else SymbolIcon(Symbol.Photo, Ink.white, size = 26.dp)
    }
}

/** The first start: what to draw – instead of blinking buttons (the original), a clear question. */
@Composable
private fun EmptyStart(onPhotos: () -> Unit, onFiles: () -> Unit, onCamera: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 32.dp).widthIn(max = 360.dp).clip(RoundedCornerShape(22.dp)).background(Ink.card).padding(vertical = 20.dp)) {
        BasicText("Was möchtest du zeichnen?", style = style(20f, 700), modifier = Modifier.padding(horizontal = 20.dp))
        BasicText("Wähle ein Bild aus deinen Fotos oder Dateien – oder fotografiere eine Vorlage. Es bleibt auf deinem Gerät.",
            style = style(15f, 400, Ink.secondary), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp))
        for ((index, entry) in listOf(Triple(Symbol.Photo, "Aus Fotos", onPhotos), Triple(Symbol.Folder, "Aus Dateien", onFiles),
            Triple(Symbol.Camera, "Vorlage fotografieren", onCamera)).withIndex()) {
            val (symbol, label, action) = entry
            if (index > 0) Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(Ink.separator))
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = action).padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(symbol, Ink.yellow, size = 24.dp)
                BasicText(label, style = style(17f, 400), modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun CameraNeeded(onAsk: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 32.dp).widthIn(max = 360.dp).clip(RoundedCornerShape(22.dp)).background(Ink.card).padding(20.dp)) {
        BasicText("LiCida braucht die Kamera", style = style(20f, 700))
        BasicText("Du zeichnest mit Blick auf den Bildschirm: Die Kamera zeigt dein Papier, darüber liegt die Vorlage. Das Kamerabild bleibt auf deinem Gerät – LiCida hat keinen Internetzugang.",
            style = style(15f, 400, Ink.secondary), modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        BasicText("Kamera erlauben", style = style(17f, 600, Color.Black).copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.yellow).clickable(role = Role.Button, onClick = onAsk).padding(vertical = 13.dp))
    }
}

@Composable
private fun DrawChrome(view: DrawView, opacity: Float, exposureLocked: Boolean, onFilters: () -> Unit,
                       torch: Boolean, onTorchOff: () -> Unit, onMore: () -> Unit, record: (@Composable () -> Unit)?, onOpacity: (Float) -> Unit, onOpacityDone: () -> Unit,
                       onBack: () -> Unit, onSaveSession: (() -> Unit)?, onFocus: () -> Unit, onUnlock: () -> Unit, onZoomReset: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Like Camera's "AE/AF-SPERRE": yellow, tap to release.
            if (exposureLocked) BasicText("BELICHTUNG GESPERRT", style = style(12f, 700, Color.Black),
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Ink.yellow).clickable(role = Role.Button, onClickLabel = "Belichtung lösen", onClick = onUnlock)
                    .padding(horizontal = 8.dp, vertical = 4.dp))
            if (view.zoom > 1.01f) BasicText("%.1f×".format(view.zoom).replace('.', ','), style = style(13f, 700, Ink.yellow, tabular = true).copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape).background(Ink.glass)
                    .clickable(role = Role.Button, onClickLabel = "Ganze Ansicht", onClick = onZoomReset).padding(top = 13.dp))
        }
        Row(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
            GlassButton(Symbol.Filters, "Werkzeuge und Filter", onClick = onFilters)
            // Handbook p. 32: with the flashlight on, a button to turn it off.
            if (torch) { Spacer(Modifier.size(12.dp)); GlassButton(Symbol.Flashlight, "Taschenlampe aus", active = true, onClick = onTorchOff) }
        }
        Row(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            record?.let { it(); Spacer(Modifier.size(12.dp)) }
            onSaveSession?.let { GlassButton(Symbol.Sessions, "Sitzung sichern", onClick = it); Spacer(Modifier.size(12.dp)) }
            GlassButton(Symbol.Focus, "Scharfstellen", onClick = onFocus)
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Symbol.ChevronLeft, "Zurück zum Einrichten", onClick = onBack)
            Row(Modifier.weight(1f).padding(start = 12.dp).clip(CircleShape).background(Ink.glass).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbol.Camera, Ink.white, size = 18.dp)
                IosSlider(opacity, onOpacity, "Deckkraft der Vorlage", Modifier.weight(1f).padding(horizontal = 6.dp), onRelease = onOpacityDone)
                SymbolIcon(Symbol.Photo, Ink.white, size = 18.dp)
            }
            Spacer(Modifier.size(12.dp))
            GlassButton(Symbol.More, "Weitere Werkzeuge", onClick = onMore)
        }
    }
}

/** The automatic alignment's result: the straightening and where the target's dots (or corners) now lie. */
class AutoFound(val homography: FloatArray, val rectangle: FloatArray)

/** Finding the target in a camera picture (card 7) – on a smaller copy, the points scaled back to the view. */
object AutoAlign {
    fun find(picture: Bitmap, ownAspect: Float?): AutoFound? {
        val small = Reference.scaled(picture, 720)
        val factor = picture.width.toFloat() / small.width
        val pixels = Reference.pixels(small)
        val found = (if (ownAspect == null) Target.findDots(pixels) else Target.findCorners(pixels)) ?: return null
        val points = FloatArray(8) { found[it] * factor }
        val homography = Target.straighten(points, ownAspect ?: Target.DOT_ASPECT) ?: return null
        val rectangle = FloatArray(8).also { out ->
            for (p in 0 until 4) Homography.apply(homography, points[2 * p], points[2 * p + 1]).let { (x, y) -> out[2 * p] = x; out[2 * p + 1] = y }
        }
        return AutoFound(homography, rectangle)
    }
}

/** The target picture placed where it lies after straightening, fading in and out (handbook p. 24). */
@Composable
private fun CheckOverlay(rect: FloatArray, picture: Bitmap, own: Boolean, speed: Float) {
    val image = remember(picture) { picture.asImageBitmap() }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "Prüfen")
    val period = (3000 - speed * 2700).toInt()
    val alpha by transition.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(period), androidx.compose.animation.core.RepeatMode.Reverse), label = "Überblenden")
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        // Own picture: its corners are the rectangle; LiCida's target: the dots' rectangle → the whole sheet.
        val left: Float; val top: Float; val width: Float; val height: Float
        if (own) { left = rect[0]; top = rect[1]; width = rect[2] - rect[0]; height = rect[5] - rect[1] }
        else {
            val sx = (rect[2] - rect[0]) / (Target.DOTS[2] - Target.DOTS[0])
            val sy = (rect[5] - rect[1]) / (Target.DOTS[5] - Target.DOTS[1])
            left = rect[0] - Target.DOTS[0] * sx; top = rect[1] - Target.DOTS[1] * sy
            width = Target.WIDTH * sx; height = Target.HEIGHT * sy
        }
        drawImage(image, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)), alpha = alpha)
    }
}

@Composable
private fun CheckControls(speed: Float, onSpeed: (Float) -> Unit, modifier: Modifier, onDone: () -> Unit) {
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).clip(RoundedCornerShape(18.dp))
        .background(Ink.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Ausrichtung prüfen", style = style(17f, 600), modifier = Modifier.weight(1f))
            BasicText("Fertig", style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onDone).padding(6.dp))
        }
        BasicText("Liegt alles übereinander, flimmert kaum etwas. Sieht es doppelt aus: Zielbild glätten, Licht prüfen und erneut ausrichten.",
            style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Tempo", style = style(15f), modifier = Modifier.widthIn(min = 56.dp))
            IosSlider(speed, onSpeed, "Tempo des Überblendens", Modifier.weight(1f))
        }
    }
}

/** An iOS alert with two choices. */
@Composable
private fun AskDialog(title: String, body: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onNo) {
        Column(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF22C2C2E)), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(title, style = style(17f, 600).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp))
            BasicText(body, style = style(13f).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.separator))
            Row(Modifier.fillMaxWidth()) {
                BasicText("Später", style = style(17f, 400, Ink.yellow).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onNo).padding(vertical = 12.dp))
                Box(Modifier.size(0.5.dp, 44.dp).background(Ink.separator))
                BasicText(yes, style = style(17f, 600, Ink.yellow).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onYes).padding(vertical = 12.dp))
            }
        }
    }
}

/** Finding a session's paper again (handbook p. 44): the snapshot over the camera, by hand or automatically. */
@Composable
private fun AlignControls(alpha: Float, onAlpha: (Float) -> Unit, busy: Boolean, onAuto: () -> Unit, modifier: Modifier, onDone: () -> Unit) {
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).clip(RoundedCornerShape(18.dp))
        .background(Ink.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Blatt wiederfinden", style = style(17f, 600), modifier = Modifier.weight(1f))
            BasicText("Fertig", style = style(17f, 600, Ink.yellow), modifier = Modifier.clickable(role = Role.Button, onClick = onDone).padding(6.dp))
        }
        BasicText("Der Schnappschuss liegt über dem Kamerabild. Leg dein Blatt so, dass beides übereinanderliegt – oder lass LiCida die Vorlage passend schieben.",
            style = style(13f, 400, Ink.secondary), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Schnappschuss", style = style(15f), modifier = Modifier.widthIn(min = 112.dp))
            IosSlider(alpha, onAlpha, "Deckkraft des Schnappschusses", Modifier.weight(1f))
        }
        BasicText(if (busy) "Suche …" else "Automatisch ausrichten", style = style(17f, 600, Color.Black).copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (busy) Ink.secondary else Ink.yellow)
                .clickable(enabled = !busy, role = Role.Button, onClick = onAuto).padding(vertical = 13.dp))
    }
}

class MenuEntry(val symbol: Symbol, val label: String, val action: () -> Unit)

/** An iOS pull-down menu under the "Mehr" button: rounded, label left, symbol right. */
@Composable
fun SetupMenu(onClose: () -> Unit, entries: List<MenuEntry>) {
    Box(Modifier.fillMaxSize().clickable(onClick = onClose)) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 64.dp).widthIn(min = 230.dp, max = 280.dp)
            .clip(RoundedCornerShape(13.dp)).background(Color(0xFF2C2C2E))) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.separator))
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = entry.action).padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BasicText(entry.label, style = style(17f), modifier = Modifier.weight(1f))
                    SymbolIcon(entry.symbol, Ink.white, size = 20.dp)
                }
            }
        }
    }
}
