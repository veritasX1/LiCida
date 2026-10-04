package io.github.veritasx1.licida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Card 16: the guide's text and the tour's stops (the PDF needs a real phone – Robolectric has no PdfDocument). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpTest {
    @Test
    fun guideIsComplete() {
        assertEquals(11, Guide.chapters.size)
        Guide.chapters.forEach { assertTrue(it.title, it.paragraphs.isNotEmpty() && it.paragraphs.all { p -> p.length > 20 }) }
        // What the app no longer has must not be in the guide.
        val all = Guide.chapters.joinToString(" ") { it.paragraphs.joinToString(" ") }
        listOf("YouTube", "E-Mail", "Kauf ", "Abo ").forEach { assertTrue(it, it !in all) }
    }

    @Test
    fun tourStops() {
        val anchors = Tour.steps.mapNotNull { it.anchor }
        assertEquals(anchors.size, anchors.toSet().size)
        assertTrue(Tour.steps.all { it.text.endsWith(".") })
    }
}
