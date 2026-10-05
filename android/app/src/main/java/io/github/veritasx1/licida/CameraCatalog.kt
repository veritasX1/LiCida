package io.github.veritasx1.licida

import io.github.veritasx1.licida.i18n.tr

import kotlin.math.atan

/** What the phone reports about one camera (Camera2): enough to name it the way people know it. */
data class CameraFacts(
    val id: String,
    val facing: Facing,
    val focalLength: Float,      // mm
    val sensorWidth: Float,      // mm (physical size)
    val logical: Boolean = false,
    val physicalIds: List<String> = emptyList(),
    val minZoom: Float = 1f,
) {
    /** Horizontal field of view in degrees – what tells ultra-wide, wide and tele apart. */
    val fieldOfView: Double get() = if (focalLength <= 0f) 0.0 else Math.toDegrees(2 * atan(sensorWidth / (2.0 * focalLength)))
}

enum class Facing { Back, Front, External }

/** One entry of the camera list (handbook p. 13–15): which camera, at which zoom, and what to call it. */
data class CameraOption(val key: String, val label: String, val detail: String, val cameraId: String?, val facing: Facing,
                        val zoomRatio: Float = 1f)

/** Turns the phone's cameras into Apple-like choices (tested in CameraCatalogTest): each lens once,
 *  named by its field of view; a combined ("logical") back camera only if its lenses are not offered
 *  on their own – then its ultra-wide as zoom 0.5×. */
object CameraCatalog {
    const val DEFAULT = "back"

    fun options(cameras: List<CameraFacts>): List<CameraOption> {
        val result = mutableListOf<CameraOption>()
        val listed = cameras.map { it.id }.toSet()
        val backs = cameras.filter { it.facing == Facing.Back && !(it.logical && it.physicalIds.isNotEmpty() && it.physicalIds.all { id -> id in listed }) }
        val main = backs.filter { !it.logical }.ifEmpty { backs }.let { list ->
            // The main camera: of the non-combined ones the one closest to a normal wide angle (~70°).
            list.minByOrNull { kotlin.math.abs(it.fieldOfView - 70.0) }
        }
        if (main != null) {
            result += CameraOption(DEFAULT, tr("Rückkamera"), tr("Weitwinkel · Standard"), main.id, Facing.Back)
            if (main.minZoom < 0.99f) result += CameraOption("back-0.5", tr("Rückkamera"), "Ultraweitwinkel · %.1f×".format(main.minZoom).replace('.', ','),
                main.id, Facing.Back, main.minZoom)
        }
        backs.filter { it != main && !it.logical }.sortedByDescending { it.fieldOfView }.forEach { lens ->
            val wider = main != null && lens.fieldOfView > main.fieldOfView + 10
            val narrower = main != null && lens.fieldOfView < main.fieldOfView - 10
            val kind = when { wider -> tr("Ultraweitwinkel"); narrower -> tr("Tele"); else -> tr("Weitere Kamera") }
            result += CameraOption("back-${lens.id}", tr("Rückkamera"), tr("{kind} · {toInt}° Bildwinkel", "kind" to kind, "toInt" to (lens.fieldOfView.toInt())), lens.id, Facing.Back)
        }
        cameras.firstOrNull { it.facing == Facing.Front }?.let {
            result += CameraOption("front", tr("Frontkamera"), tr("Mit Spiegelaufsatz · Bild wird gespiegelt"), it.id, Facing.Front)
        }
        cameras.filter { it.facing == Facing.External }.forEachIndexed { index, usb ->
            result += CameraOption("usb-${usb.id}", if (index == 0) "USB-Kamera" else tr("USB-Kamera {value}", "value" to (index + 1)), tr("Angeschlossen"), usb.id, Facing.External)
        }
        return result
    }

    /** The remembered choice if it still exists (a USB camera may be unplugged), else the back camera. */
    fun pick(options: List<CameraOption>, remembered: String?): CameraOption? =
        options.firstOrNull { it.key == remembered } ?: options.firstOrNull { it.key == DEFAULT } ?: options.firstOrNull()
}
