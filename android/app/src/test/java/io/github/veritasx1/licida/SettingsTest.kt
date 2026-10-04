package io.github.veritasx1.licida

import android.view.KeyEvent
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Card 15: help levels, the keymap and what a key does to the draw view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTest {
    private val screen = Size(1080f, 2400f)

    @Test
    fun helpLevels() {
        assertFalse(HelpLevel.Off.shows(0))
        assertTrue(HelpLevel.Once.shows(0)); assertFalse(HelpLevel.Once.shows(1))
        assertTrue(HelpLevel.Thrice.shows(2)); assertFalse(HelpLevel.Thrice.shows(3))
        assertTrue(HelpLevel.Always.shows(1000))
    }

    @Test
    fun defaultKeysAsInTheHandbook() {
        val map = Keymap()
        assertEquals(KeyAction.ZoomIn, map.action(KeyEvent.KEYCODE_E))
        assertEquals(KeyAction.ZoomOut, map.action(KeyEvent.KEYCODE_Q))
        assertEquals(KeyAction.Toggle, map.action(KeyEvent.KEYCODE_R))
        assertEquals(KeyAction.SplitRight, map.action(KeyEvent.KEYCODE_3))
        assertNull(map.action(KeyEvent.KEYCODE_X))
        assertEquals(12, map.keys.values.toSet().size)   // no key twice
    }

    @Test
    fun aKeyBelongsToOneActionOnly() {
        val map = Keymap().assign(KeyAction.Flicker, KeyEvent.KEYCODE_E)
        assertEquals(KeyAction.Flicker, map.action(KeyEvent.KEYCODE_E))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, map.keys[KeyAction.ZoomIn])
        assertNull(map.action(KeyEvent.KEYCODE_F))
    }

    @Test
    fun keymapKeptAndRead() {
        val map = Keymap().assign(KeyAction.Toggle, KeyEvent.KEYCODE_VOLUME_UP).assign(KeyAction.ZoomIn, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(map, Keymap.decode(map.encode()))
        assertEquals(Keymap(), Keymap.decode(null))
        assertEquals(Keymap(), Keymap.decode("Unsinn"))
        // A newer LiCida with an action the stored map does not know: that one gets its default key.
        assertEquals(KeyEvent.KEYCODE_D, Keymap.decode("ZoomIn=33").keys[KeyAction.PanRight])
    }

    @Test
    fun keyLabels() {
        assertEquals("E", Keymap.label(KeyEvent.KEYCODE_E))
        assertEquals("3", Keymap.label(KeyEvent.KEYCODE_3))
        assertEquals("Lauter", Keymap.label(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals("Taste A", Keymap.label(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("–", Keymap.label(KeyEvent.KEYCODE_UNKNOWN))
        assertFalse(Keymap.usable(KeyEvent.KEYCODE_BACK))
        assertTrue(Keymap.usable(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun keysMoveTheView() {
        val near = KeyMoves.view(KeyAction.ZoomIn, DrawView(), screen)
        assertEquals(1.25f, near.zoom, 1e-4f)
        assertEquals(1f, KeyMoves.view(KeyAction.ZoomOut, DrawView(), screen).zoom, 1e-4f)   // never below the whole view
        // Not zoomed in, there is nothing to pan to.
        assertEquals(DrawView(), KeyMoves.view(KeyAction.PanLeft, DrawView(), screen))
        val zoomed = DrawView(3f)
        val left = KeyMoves.view(KeyAction.PanLeft, zoomed, screen)
        assertEquals(108f, left.offset.x, 1e-3f)   // a tenth of the short side; the picture moves right
        assertEquals(-108f, KeyMoves.view(KeyAction.PanDown, zoomed, screen).offset.y, 1e-3f)
        assertEquals(zoomed, KeyMoves.view(KeyAction.Toggle, zoomed, screen))
    }
}
