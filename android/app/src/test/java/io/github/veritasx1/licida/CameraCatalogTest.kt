package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Test

/** Card 5: the phone's cameras as Apple-like choices – with the moto g84's real facts. */
class CameraCatalogTest {
    // From dumpsys media.camera on the moto g84: 0 main, 1 front, 2 ultra-wide, 3 = 0+2 combined.
    private val main = CameraFacts("0", Facing.Back, 5.56f, 8.16f)
    private val front = CameraFacts("1", Facing.Front, 3.27f, 4.608f)
    private val wide = CameraFacts("2", Facing.Back, 1.66f, 3.6736f)
    private val logical = CameraFacts("3", Facing.Back, 5.56f, 8.16f, logical = true, physicalIds = listOf("2", "0"), minZoom = 0.5f)

    @Test
    fun motoG84() {
        val options = CameraCatalog.options(listOf(main, front, wide, logical))
        assertEquals(listOf("back", "back-2", "front"), options.map { it.key })
        assertEquals("Ultraweitwinkel · 95° Bildwinkel", options[1].detail)
        assertEquals("0", options[0].cameraId)
    }

    @Test
    fun onlyTheCombinedCameraOffered() {
        // Some phones show only the combined back camera: its ultra-wide comes as 0.5×.
        val options = CameraCatalog.options(listOf(logical.copy(physicalIds = listOf("7", "8")), front))
        assertEquals(listOf("back", "back-0.5", "front"), options.map { it.key })
        assertEquals(0.5f, options[1].zoomRatio)
        assertEquals("Ultraweitwinkel · 0,5×", options[1].detail)
    }

    @Test
    fun usbAndRemembering() {
        val usb = CameraFacts("100", Facing.External, 0f, 0f)
        val options = CameraCatalog.options(listOf(main, front, usb))
        assertEquals("USB-Kamera", options.last().label)
        assertEquals("usb-100", CameraCatalog.pick(options, "usb-100")?.key)
        // Unplugged: back to the back camera.
        assertEquals("back", CameraCatalog.pick(CameraCatalog.options(listOf(main, front)), "usb-100")?.key)
    }
}
