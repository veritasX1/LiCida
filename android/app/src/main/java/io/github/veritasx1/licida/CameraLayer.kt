package io.github.veritasx1.licida

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/** The live view of the paper (handbook p. 13): the back camera by default. Bound directly (Preview +
 *  ImageCapture, full 4:3 sensor picture) so that "Ganzes Bild" really shows the camera's whole field –
 *  a CameraController would crop the stream to the screen's shape. All on the phone: no frame is stored
 *  or sent anywhere; only "Vorlage fotografieren" keeps one picture, as the reference. */
class Camera(private val context: android.content.Context?, private val lifecycle: androidx.lifecycle.LifecycleOwner?) {
    val available = context != null && lifecycle != null
    private val aspect = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
        .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    val preview: Preview = Preview.Builder().setResolutionSelector(aspect).build()
    private val still: ImageCapture = ImageCapture.Builder().setResolutionSelector(aspect).build()
    private var bound: androidx.camera.core.Camera? = null

    /** Start (or switch to) a camera; one that is gone falls back to the back camera instead of crashing. */
    suspend fun bind(option: CameraOption?) {
        if (!available) return
        // Waiting for the camera service happens off the main thread; binding then on it.
        val provider = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ProcessCameraProvider.getInstance(context!!).get(5, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
        } ?: return
        val wanted = option?.let(Cameras::selector) ?: CameraSelector.DEFAULT_BACK_CAMERA
        val selector = if (runCatching { provider.hasCamera(wanted) }.getOrDefault(false)) wanted else CameraSelector.DEFAULT_BACK_CAMERA
        if (!lifecycle!!.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) return
        bound = runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycle!!, selector, preview, still)
        }.getOrNull()
        bound?.cameraControl?.setZoomRatio(option?.zoomRatio ?: 1f)
    }

    fun release() {
        if (!available) return
        val future = ProcessCameraProvider.getInstance(context!!)
        if (future.isDone) runCatching { future.get().unbindAll() }
        bound = null
    }

    /** Tap the focus button: focus once on the middle, then hold it (handbook p. 26). */
    fun focus(view: PreviewView?) {
        val point = view?.meteringPointFactory?.createPoint(view.width / 2f, view.height / 2f) ?: return
        bound?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).disableAutoCancel().build())
    }

    /** Two-finger tap: set the exposure for this spot and keep it (handbook p. 26) – for black paper, time-lapses. */
    fun lockExposure(view: PreviewView?, at: Offset) {
        val point = view?.meteringPointFactory?.createPoint(at.x, at.y) ?: return
        bound?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build())
    }

    /** The flashlight (card 12): only where the camera has one. */
    val hasTorch get() = bound?.cameraInfo?.hasFlashUnit() == true

    fun torch(on: Boolean) {
        bound?.cameraControl?.enableTorch(on)
    }

    /** Back to automatic exposure (and focus). */
    fun unlock() {
        bound?.cameraControl?.cancelFocusAndMetering()
    }

    /** What the camera shows right now, in the view's own coordinates (before any straightening) – for finding the target. */
    fun snapshot(view: PreviewView?): Bitmap? = view?.bitmap

    /** One picture as the new reference, upright. */
    fun capture(context: android.content.Context, onDone: (Bitmap?) -> Unit) {
        if (bound == null) return onDone(null)
        still.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmap()
                val turn = image.imageInfo.rotationDegrees
                image.close()
                onDone(if (turn == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(turn.toFloat()) }, true))
            }

            override fun onError(exception: ImageCaptureException) = onDone(null)
        })
    }
}

/** The camera's own view (card 5, handbook p. 14): zoomed and moved on its own while the camera sheet
 *  is open, mirrored for the front camera's mirror; "fill" or the whole camera picture. */
data class CameraView(val zoom: Float = 1f, val offset: Offset = Offset.Zero, val flipH: Boolean = false, val flipV: Boolean = false)

/** The camera picture in its box: "Füllen" crops to the screen, "Ganzes Bild" shows the whole 4:3 field.
 *  In previews and tests (no camera there) a dark grey surface. */
@Composable
fun CameraLayer(camera: Camera, onView: (PreviewView) -> Unit, modifier: Modifier = Modifier, fill: Boolean = true,
                correction: Correction = Correction()) {
    if (LocalInspectionMode.current || !camera.available) {
        Box(modifier.fillMaxSize().background(Color(0xFF3A3A3C)))
        return
    }
    AndroidView(factory = { context ->
        PreviewView(context).apply {
            // A TextureView inside: it zooms and moves with the reference (graphicsLayer) in draw mode.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = if (fill) PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER
            camera.preview.surfaceProvider = surfaceProvider
            // The straightening needs the view's size: applied again whenever it changes.
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> straighten(view, view.tag as? Correction ?: Correction()) }
            onView(this)
        }
    }, update = { view ->
        val wanted = if (fill) PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER
        if (view.scaleType != wanted) view.scaleType = wanted
        view.tag = correction
        straighten(view, correction)
    }, modifier = modifier.fillMaxSize())
}

/** The correction as the view's drawing matrix – a full perspective map, done by the graphics chip (cards 6/7). */
private fun straighten(view: android.view.View, correction: Correction) {
    if (view.width == 0 || view.height == 0) return
    view.animationMatrix = if (correction.isPlain) null
        else Matrix().apply { setValues(correction.matrix(view.width.toFloat(), view.height.toFloat())) }
}

@Composable
fun rememberCamera(enabled: Boolean, option: CameraOption?): Camera {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val inPreview = LocalInspectionMode.current
    val camera = remember(enabled, inPreview) { if (!enabled || inPreview) Camera(null, null) else Camera(context, lifecycle) }
    // The chosen camera (and its zoom, e.g. 0.5× for the ultra-wide of a combined camera).
    androidx.compose.runtime.LaunchedEffect(camera, option) { camera.bind(option) }
    DisposableEffect(camera) { onDispose { camera.release() } }
    return camera
}

/** The phone's cameras as CameraX can use them (Camera2 facts for naming), and how to select one. */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
object Cameras {
    fun facts(context: android.content.Context): List<CameraFacts> = runCatching {
        val provider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context).get(5, java.util.concurrent.TimeUnit.SECONDS)
        provider.availableCameraInfos.mapNotNull { info ->
            val camera2 = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
            val facing = when (camera2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_FACING)) {
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> Facing.Front
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL -> Facing.External
                else -> Facing.Back
            }
            val focal = camera2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 0f
            val sensor = camera2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0f
            val capabilities = camera2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            val logical = android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities
            val physical = if (logical) runCatching {
                (context.getSystemService(android.hardware.camera2.CameraManager::class.java))
                    .getCameraCharacteristics(camera2.cameraId).physicalCameraIds.toList()
            }.getOrDefault(emptyList()) else emptyList()
            CameraFacts(camera2.cameraId, facing, focal, sensor, logical, physical, info.zoomState.value?.minZoomRatio ?: 1f)
        }
    }.getOrDefault(emptyList())

    fun has(context: android.content.Context, selector: CameraSelector): Boolean = runCatching {
        androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context).get(5, java.util.concurrent.TimeUnit.SECONDS).hasCamera(selector)
    }.getOrDefault(false)

    fun selector(option: CameraOption): CameraSelector {
        val id = option.cameraId ?: return CameraSelector.DEFAULT_BACK_CAMERA
        return CameraSelector.Builder().addCameraFilter { infos ->
            infos.filter { androidx.camera.camera2.interop.Camera2CameraInfo.from(it).cameraId == id }
        }.build()
    }
}
