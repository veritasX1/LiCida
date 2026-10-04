package io.github.veritasx1.licida

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import java.io.File

/** The reference image (handbook p. 9–12): picked from Fotos or Dateien, or photographed – kept as a
 *  private copy in the app's own folder (never in a backup, never sent anywhere), so it is still
 *  there when LiCida opens again. "Sichern" writes the composed picture to Fotos – only then, and
 *  only because the user asked. */
object Reference {
    private const val FILE = "vorlage.jpg"
    private const val MAX_SIDE = 3200   // sharp enough to draw from, small enough for any phone

    fun file(context: Context) = File(context.filesDir, FILE)

    /** Read an image the user chose (photo picker / file dialog: read access only to that one). */
    fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) decoder.setTargetSampleSize(Math.ceil(longest / MAX_SIDE.toDouble()).toInt())
        }
        keep(context, bitmap)
        bitmap
    }.getOrNull()

    /** A photo taken in LiCida as the reference (already upright). */
    fun keep(context: Context, bitmap: Bitmap) {
        val target = file(context)
        val temporary = File(target.parentFile, "$FILE.tmp")
        temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        temporary.renameTo(target)
    }

    fun restore(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { file ->
        runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE } }.getOrNull()
    }

    fun forget(context: Context) {
        file(context).delete()
    }

    /** The reference as it lies on the screen (zoomed, turned, moved), on white – what "Sichern" keeps. */
    fun compose(reference: Bitmap, placement: Placement, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(reference, matrix(reference.width, reference.height, placement, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Image pixels → screen: fit the screen, then the user's scale and turn about the centre, then the offset. */
    fun matrix(imageWidth: Int, imageHeight: Int, placement: Placement, width: Int, height: Int): Matrix {
        val fit = Composition.fitScale(androidx.compose.ui.geometry.Size(imageWidth.toFloat(), imageHeight.toFloat()),
            androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()))
        return Matrix().apply {
            postTranslate(-imageWidth / 2f, -imageHeight / 2f)
            postScale(fit * placement.scale, fit * placement.scale)
            postRotate(placement.rotation)
            postTranslate(width / 2f + placement.offset.x, height / 2f + placement.offset.y)
        }
    }

    /** Into Fotos, album "LiCida" (Android 10+: no storage permission needed). Returns false if it failed. */
    fun saveToPhotos(context: Context, bitmap: Bitmap, name: String): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LiCida")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: return false
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    }.getOrDefault(false)
}

/** What LiCida remembers on this phone: where the reference lies and how see-through it is. Private
 *  preferences, never backed up. */
class Studio(context: Context) {
    private val prefs = context.getSharedPreferences("studio", Context.MODE_PRIVATE)

    var placement: Placement
        get() = Placement(prefs.getFloat("scale", 1f), prefs.getFloat("rotation", 0f), Offset(prefs.getFloat("x", 0f), prefs.getFloat("y", 0f)))
        set(value) {
            prefs.edit().putFloat("scale", value.scale).putFloat("rotation", value.rotation)
                .putFloat("x", value.offset.x).putFloat("y", value.offset.y).apply()
        }

    var opacity: Float
        get() = prefs.getFloat("opacity", 0.5f)
        set(value) { prefs.edit().putFloat("opacity", value).apply() }

    var drawOpacity: Float
        get() = prefs.getFloat("drawOpacity", 0.5f)
        set(value) { prefs.edit().putFloat("drawOpacity", value).apply() }

    /** The gesture hint shows until the user has moved a reference once. */
    var hintSeen: Boolean
        get() = prefs.getBoolean("hintSeen", false)
        set(value) { prefs.edit().putBoolean("hintSeen", value).apply() }
}
