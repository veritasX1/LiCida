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
    /** Android pictures ↔ the filters' plain pixels. */
    fun pixels(bitmap: Bitmap): Pixels {
        val soft = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return Pixels(soft.width, soft.height).also { soft.getPixels(it.argb, 0, soft.width, 0, 0, soft.width, soft.height) }
    }

    fun bitmap(pixels: Pixels): Bitmap = Bitmap.createBitmap(pixels.argb, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)

    /** A smaller copy (longest side `side`) – the filters run on 2048 px, the previews on a thumbnail. */
    fun scaled(bitmap: Bitmap, side: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= side) return bitmap
        val factor = side.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * factor).toInt()), maxOf(1, (bitmap.height * factor).toInt()), true)
    }

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
            ?: runCatching { android.graphics.BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    /** Read a picture without keeping it (e.g. a photo of one's own paints for the palette). */
    fun decode(context: Context, uri: Uri, side: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > side) decoder.setTargetSampleSize(Math.ceil(longest / side.toDouble()).toInt())
        }
    }.getOrNull()

    /** One's own target picture (card 7): kept privately as ziel.jpg. */
    fun ownTarget(context: Context) = File(context.filesDir, "ziel.jpg")

    fun keepOwnTarget(context: Context, uri: Uri): Bitmap? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1600) decoder.setTargetSampleSize(Math.ceil(longest / 1600.0).toInt())
        }
        ownTarget(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap
    }.getOrNull()

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
    fun saveToPhotos(context: Context, bitmap: Bitmap, name: String): Boolean = savePhoto(context, bitmap, name) != null

    /** The same, giving the picture's address (to share it). */
    fun savePhoto(context: Context, bitmap: Bitmap, name: String): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LiCida")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: return null
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    }.getOrNull()
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

    /** The chosen camera (key of CameraOption), whether the picture fills the screen, and the camera's own view. */
    var cameraKey: String?
        get() = prefs.getString("camera", null)
        set(value) { prefs.edit().putString("camera", value).apply() }

    var fill: Boolean
        get() = prefs.getBoolean("fill", true)
        set(value) { prefs.edit().putBoolean("fill", value).apply() }

    var cameraView: CameraView
        get() = CameraView(prefs.getFloat("camZoom", 1f), Offset(prefs.getFloat("camX", 0f), prefs.getFloat("camY", 0f)),
            prefs.getBoolean("flipH", false), prefs.getBoolean("flipV", false))
        set(value) {
            prefs.edit().putFloat("camZoom", value.zoom).putFloat("camX", value.offset.x).putFloat("camY", value.offset.y)
                .putBoolean("flipH", value.flipH).putBoolean("flipV", value.flipV).apply()
        }

    /** While adjusting the camera: show the reference faintly (handbook p. 14). */
    var ghost: Boolean
        get() = prefs.getBoolean("ghost", true)
        set(value) { prefs.edit().putBoolean("ghost", value).apply() }

    /** The filter history of the current reference (card 10) and the three own sequences. */
    var edits: Edits
        get() = Edits.decode(prefs.getString("edits", null))
        set(value) { prefs.edit().putString("edits", value.encode()).apply() }

    var slots: List<Slot>
        get() = (0 until 3).map { Slot.decode(prefs.getString("slot$it", null), it) }
        set(value) { prefs.edit().apply { value.forEachIndexed { index, slot -> putString("slot$index", slot.encode()) } }.apply() }

    /** The camera's straightening (cards 6/7), a saved one to bring back, and which target AUTO looks for. */
    var correction: Correction
        get() = Correction.decode(prefs.getString("correction", null))
        set(value) { prefs.edit().putString("correction", value.encode()).apply() }

    var savedCorrection: Correction?
        get() = prefs.getString("savedCorrection", null)?.let { Correction.decode(it) }
        set(value) { prefs.edit().putString("savedCorrection", value?.encode()).apply() }

    /** null: LiCida's printed target; else the width/height of one's own target picture (kept as ziel.jpg). */
    var ownTargetAspect: Float?
        get() = prefs.getFloat("ownTarget", 0f).takeIf { it > 0f }
        set(value) { prefs.edit().putFloat("ownTarget", value ?: 0f).apply() }

    var helperGhost: Boolean
        get() = prefs.getBoolean("helperGhost", false)
        set(value) { prefs.edit().putBoolean("helperGhost", value).apply() }

    /** Which extra buttons show while drawing: "Sitzung sichern", Zeitraffer. */
    var sessionButton: Boolean
        get() = prefs.getBoolean("sessionButton", true)
        set(value) { prefs.edit().putBoolean("sessionButton", value).apply() }
    var recordButton: Boolean
        get() = prefs.getBoolean("recordButton", false)
        set(value) { prefs.edit().putBoolean("recordButton", value).apply() }

    var timelapse: TimelapseSettings
        get() = TimelapseSettings.decode(prefs.getString("timelapse", null))
        set(value) { prefs.edit().putString("timelapse", value.encode()).apply() }

    /** The gesture hint shows until the user has moved a reference once. */
    var hintSeen: Boolean
        get() = prefs.getBoolean("hintSeen", false)
        set(value) { prefs.edit().putBoolean("hintSeen", value).apply() }
}
