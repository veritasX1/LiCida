package io.github.veritasx1.licida

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.sin

/** Where the reference image lies over the camera picture while setting up (handbook p. 7, 12):
 *  scale, rotation (degrees) and offset of its centre from the screen centre, in pixels. */
data class Placement(val scale: Float = 1f, val rotation: Float = 0f, val offset: Offset = Offset.Zero)

/** The draw mode's view: camera and reference zoomed and moved together (handbook p. 31). */
data class DrawView(val zoom: Float = 1f, val offset: Offset = Offset.Zero)

/** The geometry of composing and drawing – plain functions, tested in CompositionTest. */
object Composition {
    const val MIN_SCALE = 0.05f
    const val MAX_SCALE = 40f
    const val MAX_ZOOM = 30f       // handbook: "zoom 30x"
    const val DOUBLE_TAP_ZOOM = 3f // handbook: "zooms in to 3x"

    private fun rotate(point: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return Offset(point.x * c - point.y * s, point.x * s + point.y * c)
    }

    /** One step of a two-finger gesture on the reference: the point under the fingers stays under them. */
    fun transform(placement: Placement, centroid: Offset, pan: Offset, zoom: Float, rotation: Float, size: Size): Placement {
        val scale = (placement.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val factor = scale / placement.scale
        val fingers = centroid - Offset(size.width / 2, size.height / 2)
        val offset = fingers + pan + rotate(placement.offset - fingers, rotation) * factor
        return Placement(scale, normalize(placement.rotation + rotation), offset)
    }

    /** The rotate button: a quarter turn to the right (handbook p. 9). */
    fun quarterTurn(placement: Placement) = placement.copy(rotation = normalize(placement.rotation + 90f))

    private fun normalize(degrees: Float): Float {
        var value = degrees % 360f
        if (value > 180f) value -= 360f
        if (value <= -180f) value += 360f
        return value
    }

    /** How big the image is to fill the screen at scale 1 ("fit": the whole image visible). */
    fun fitScale(image: Size, screen: Size): Float =
        if (image.width <= 0f || image.height <= 0f) 1f else minOf(screen.width / image.width, screen.height / image.height)

    /** Draw mode: zoom/pan both layers together, 1×…30×, never showing past the edges. */
    fun zoom(view: DrawView, centroid: Offset, pan: Offset, zoom: Float, size: Size): DrawView {
        val newZoom = (view.zoom * zoom).coerceIn(1f, MAX_ZOOM)
        val factor = newZoom / view.zoom
        val fingers = centroid - Offset(size.width / 2, size.height / 2)
        return clamp(DrawView(newZoom, fingers + pan + (view.offset - fingers) * factor), size)
    }

    /** Double tap: zoomed in → back to the whole view; else 3× around the tapped point. */
    fun doubleTap(view: DrawView, at: Offset, size: Size): DrawView {
        if (view.zoom > 1.01f) return DrawView()
        return zoom(view, at, Offset.Zero, DOUBLE_TAP_ZOOM, size)
    }

    fun clamp(view: DrawView, size: Size): DrawView {
        val limitX = (view.zoom - 1f) * size.width / 2
        val limitY = (view.zoom - 1f) * size.height / 2
        return view.copy(offset = Offset(view.offset.x.coerceIn(-limitX, limitX), view.offset.y.coerceIn(-limitY, limitY)))
    }

    /** The camera's own view while the camera sheet is open: zoom 0.3×…8× (digital), moved freely. */
    fun moveCamera(view: CameraView, centroid: Offset, pan: Offset, zoom: Float, size: Size): CameraView {
        val newZoom = (view.zoom * zoom).coerceIn(0.3f, 8f)
        val factor = newZoom / view.zoom
        val fingers = centroid - Offset(size.width / 2, size.height / 2)
        return view.copy(zoom = newZoom, offset = fingers + pan + (view.offset - fingers) * factor)
    }

    /** Choosing a camera starts its view afresh; the front camera looks through a mirror (handbook p. 13, 16). */
    fun cameraViewFor(option: CameraOption) = CameraView(flipV = option.facing == Facing.Front)

    /** While the reference is moved, it turns see-through so the paper shows (handbook p. 7) – never more opaque than this. */
    const val WHILE_MOVING = 0.55f
}
