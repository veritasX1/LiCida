package io.github.veritasx1.licida

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
    var hintSeen by remember { mutableStateOf(studio.hintSeen) }
    var screen by remember { mutableStateOf(Size(1f, 1f)) }
    var message by remember { mutableStateOf<String?>(null) }
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
    val camera = rememberCamera(cameraAllowed, chosenCamera)

    fun say(text: String) {
        message = text
        scope.launch { delay(1800); if (message == text) message = null }
    }

    fun use(bitmap: Bitmap?) {
        if (bitmap == null) { say("Das Bild ließ sich nicht öffnen"); return }
        reference = bitmap
        placement = Placement()
        studio.placement = placement
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

    LaunchedEffect(mode) { onDrawMode(mode == Mode.Draw) }
    BackHandler(enabled = cameraSheet) { cameraSheet = false }
    BackHandler(enabled = mode == Mode.Draw) { mode = Mode.Setup; view = DrawView(); chrome = true }

    val shownOpacity = when {
        mode == Mode.Draw -> drawOpacity
        cameraSheet -> if (ghost) 0.35f else 0f
        moving -> minOf(opacity, Composition.WHILE_MOVING)
        else -> opacity
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }) {
        // The picture: camera below, reference above – in draw mode zoomed and moved as one.
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (mode == Mode.Draw) {
                scaleX = view.zoom; scaleY = view.zoom
                translationX = view.offset.x; translationY = view.offset.y
            }
        }) {
            CameraLayer(camera, onView = { preview = it }, fill = fill, modifier = Modifier.graphicsLayer {
                scaleX = cameraView.zoom * (if (cameraView.flipH) -1f else 1f)
                scaleY = cameraView.zoom * (if (cameraView.flipV) -1f else 1f)
                translationX = cameraView.offset.x; translationY = cameraView.offset.y
            })
            reference?.let { bitmap ->
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(image, contentDescription = "Vorlage", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = placement.scale; scaleY = placement.scale; rotationZ = placement.rotation
                    translationX = placement.offset.x; translationY = placement.offset.y
                    alpha = shownOpacity
                })
            }
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
                        if (!hintSeen) { hintSeen = true; studio.hintSeen = true }
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
                    detectTapGestures(onTap = { chrome = !chrome }, onDoubleTap = { view = Composition.doubleTap(view, it, screen) })
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
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.5f))
            mode == Mode.Setup -> SetupChrome(reference, opacity, hintSeen, cameraAllowed,
                onOpacity = { opacity = it }, onOpacityDone = { studio.opacity = opacity },
                onPhotos = ::pickPhoto, onFiles = ::pickFile, onCamera = ::takePhoto, onAskCamera = onAskCamera,
                onRotate = { placement = Composition.quarterTurn(placement); studio.placement = placement },
                onSave = {
                    val bitmap = reference ?: return@SetupChrome
                    val saved = Reference.saveToPhotos(context, Reference.compose(bitmap, placement, screen.width.toInt(), screen.height.toInt()),
                        "LiCida-Vorlage-${System.currentTimeMillis() / 1000}")
                    say(if (saved) "In Fotos gesichert" else "Sichern hat nicht geklappt")
                },
                onDraw = { mode = Mode.Draw; view = DrawView(); chrome = true },
                onCameraSettings = { cameraSheet = true })
            else -> AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                DrawChrome(view, drawOpacity, exposureLocked,
                    onOpacity = { drawOpacity = it }, onOpacityDone = { studio.drawOpacity = drawOpacity },
                    onBack = { mode = Mode.Setup; view = DrawView() },
                    onFocus = { camera.focus(preview); say("Scharfgestellt") },
                    onUnlock = { camera.unlock(); exposureLocked = false },
                    onZoomReset = { view = DrawView() })
            }
        }

        message?.let { text ->
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(Ink.glassStrong).padding(horizontal = 18.dp, vertical = 12.dp)) {
                BasicText(text, style = style(15f, 600))
            }
        }
    }
}

@Composable
private fun SetupChrome(reference: Bitmap?, opacity: Float, hintSeen: Boolean, cameraAllowed: Boolean,
                        onOpacity: (Float) -> Unit, onOpacityDone: () -> Unit,
                        onPhotos: () -> Unit, onFiles: () -> Unit, onCamera: () -> Unit, onAskCamera: () -> Unit,
                        onRotate: () -> Unit, onSave: () -> Unit, onDraw: () -> Unit, onCameraSettings: () -> Unit) {
    val ready = reference != null
    Box(Modifier.fillMaxSize()) {
        // Top: files on the left, turn and keep on the right (Camera keeps its top bar this light).
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Symbol.Folder, "Bild aus Dateien", onClick = onFiles)
            Spacer(Modifier.size(12.dp))
            GlassButton(Symbol.Aperture, "Kamera wählen und einstellen", enabled = cameraAllowed, onClick = onCameraSettings)
            Spacer(Modifier.weight(1f))
            GlassButton(Symbol.RotateRight, "Vorlage um 90 Grad drehen", enabled = ready, onClick = onRotate)
            Spacer(Modifier.size(12.dp))
            GlassButton(Symbol.Save, "Vorlage in Fotos sichern", enabled = ready, onClick = onSave)
        }

        if (!cameraAllowed) CameraNeeded(onAskCamera, Modifier.align(Alignment.Center))
        else if (!ready) EmptyStart(onPhotos, onFiles, onCamera, Modifier.align(Alignment.Center))
        else if (!hintSeen) Row(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp)
            .clip(CircleShape).background(Ink.glassStrong).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon(Symbol.Hand, Ink.white, size = 18.dp)
            BasicText("Mit zwei Fingern verschieben, zoomen und drehen", style = style(13f, 500), modifier = Modifier.padding(start = 8.dp))
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (ready) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Vorlage", style = style(13f, 600), modifier = Modifier.widthIn(min = 64.dp))
                IosSlider(opacity, onOpacity, "Deckkraft der Vorlage", Modifier.weight(1f), onRelease = onOpacityDone)
                BasicText("${(opacity * 100).toInt()} %", style = style(13f, 500, Ink.secondary, tabular = true).copy(textAlign = TextAlign.End), modifier = Modifier.widthIn(min = 48.dp))
            }
            Row(Modifier.fillMaxWidth().background(Ink.bar).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PhotosButton(reference, onPhotos)
                DrawButton(enabled = ready, onClick = onDraw)
                GlassButton(Symbol.Camera, "Vorlage fotografieren", size = 52.dp, onClick = onCamera)
            }
        }
    }
}

/** Like Camera's last-photo thumbnail: the current reference, or the Fotos symbol. */
@Composable
private fun PhotosButton(reference: Bitmap?, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Ink.glass).border(1.5.dp, Ink.white.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
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
private fun DrawChrome(view: DrawView, opacity: Float, exposureLocked: Boolean, onOpacity: (Float) -> Unit, onOpacityDone: () -> Unit,
                       onBack: () -> Unit, onFocus: () -> Unit, onUnlock: () -> Unit, onZoomReset: () -> Unit) {
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
        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
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
        }
    }
}
