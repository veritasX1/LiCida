package io.github.veritasx1.licida

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
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

/** The live view of the paper (handbook p. 13): the back camera by default. All on the phone – no
 *  frame is stored or sent anywhere; only "Foto aufnehmen" keeps one picture, as the reference. */
class Camera(val controller: LifecycleCameraController?) {
    /** Tap the focus button: focus once on the middle, then hold it (handbook p. 26). */
    fun focus(view: PreviewView?) {
        val point = view?.meteringPointFactory?.createPoint(view.width / 2f, view.height / 2f) ?: return
        controller?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).disableAutoCancel().build())
    }

    /** Two-finger tap: set the exposure for this spot and keep it (handbook p. 26) – for black paper, time-lapses. */
    fun lockExposure(view: PreviewView?, at: Offset) {
        val point = view?.meteringPointFactory?.createPoint(at.x, at.y) ?: return
        controller?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
            .disableAutoCancel().build())
    }

    /** Back to automatic exposure (and focus). */
    fun unlock() {
        controller?.cameraControl?.cancelFocusAndMetering()
    }

    /** One picture as the new reference, upright. */
    fun capture(context: android.content.Context, onDone: (Bitmap?) -> Unit) {
        val controller = controller ?: return onDone(null)
        controller.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
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

/** The camera picture filling its box. In previews and tests (no camera there) a dark grey surface. */
@Composable
fun CameraLayer(camera: Camera, onView: (PreviewView) -> Unit, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current || camera.controller == null) {
        Box(modifier.fillMaxSize().background(Color(0xFF3A3A3C)))
        return
    }
    AndroidView(factory = { context ->
        PreviewView(context).apply {
            // A TextureView inside: it zooms and moves with the reference (graphicsLayer) in draw mode.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            controller = camera.controller
            onView(this)
        }
    }, modifier = modifier.fillMaxSize())
}

@Composable
fun rememberCamera(enabled: Boolean): Camera {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val inPreview = LocalInspectionMode.current
    val camera = remember(enabled, inPreview) {
        Camera(if (!enabled || inPreview) null else LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            isPinchToZoomEnabled = false   // LiCida's own gestures
            isTapToFocusEnabled = false
        })
    }
    DisposableEffect(camera, lifecycle) {
        camera.controller?.bindToLifecycle(lifecycle)
        onDispose { camera.controller?.unbind() }
    }
    return camera
}
