package io.github.veritasx1.licida

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import kotlin.math.roundToInt

/** The time-lapse settings (card 13, handbook p. 33/45): playback speed (60× = an hour becomes a minute), the
 *  height of the film, the quality, whether the buttons are filmed and whether zooming is left out. */
data class TimelapseSettings(val speed: Int = 60, val height: Int = 1080, val quality: Int = 1, val recordUi: Boolean = false,
                             val ignoreZoom: Boolean = true) {
    /** Real seconds between two pictures: the film plays at 30 pictures a second, `speed` times faster. */
    val intervalMillis get() = (speed * 1000L / Timelapse.FPS).coerceAtLeast(33)
    val choppy get() = speed < 5   // handbook: below 5× the warning about choppy playback

    /** Bits per second for this size: Gut / Besser / Beste. */
    fun bitrate(width: Int, height: Int): Int = (width * height * Timelapse.FPS * listOf(0.06, 0.12, 0.22)[quality.coerceIn(0, 2)]).roundToInt()

    fun encode() = "$speed;$height;$quality;$recordUi;$ignoreZoom"

    companion object {
        val QUALITIES = listOf("Gut", "Besser", "Beste")
        val HEIGHTS = listOf(720, 1080, 1440)
        val SPEEDS = listOf(2, 5, 10, 30, 60, 120, 300, 600)

        fun decode(text: String?): TimelapseSettings {
            val p = text?.split(";") ?: return TimelapseSettings()
            if (p.size < 5) return TimelapseSettings()
            return TimelapseSettings(p[0].toIntOrNull() ?: 60, p[1].toIntOrNull() ?: 1080, p[2].toIntOrNull() ?: 1, p[3] == "true", p[4] != "false")
        }
    }
}

object Timelapse {
    const val FPS = 30

    /** The film size for the screen's shape: `height` lines on the short side (720p, 1080p …, upright or across),
     *  the long side to match but at most 1920 (more than most phone encoders take) – both multiples of 16. */
    fun size(screenWidth: Int, screenHeight: Int, height: Int): Pair<Int, Int> {
        val short = minOf(screenWidth, screenHeight).toDouble(); val long = maxOf(screenWidth, screenHeight).toDouble()
        val scale = minOf(height / short, 1.0, LONGEST / long)
        fun even(v: Double) = ((v * scale).roundToInt() / 16).coerceAtLeast(1) * 16
        return if (screenWidth <= screenHeight) Pair(even(short), even(long)) else Pair(even(long), even(short))
    }
    const val LONGEST = 1920

    /** ARGB pixels → YUV 4:2:0 planes (BT.601, video range), as the encoder takes them. */
    fun toYuv(argb: IntArray, width: Int, height: Int, y: ByteArray, u: ByteArray, v: ByteArray) {
        for (row in 0 until height) for (col in 0 until width) {
            val p = argb[row * width + col]
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            y[row * width + col] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
            if (row % 2 == 0 && col % 2 == 0) {
                val i = (row / 2) * (width / 2) + col / 2
                u[i] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                v[i] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
            }
        }
    }
}

/** Records a time-lapse: pictures handed in at the interval, encoded with Android's own H.264 encoder (no extra
 *  library) with exact timestamps – 30 a second – straight into Fotos (Movies/LiCida). Encoding on its own thread. */
class TimelapseRecorder private constructor(private val context: Context, private val uri: Uri, val width: Int, val height: Int,
                                            settings: TimelapseSettings) {
    private val thread = HandlerThread("Zeitraffer").apply { start() }
    private val handler = Handler(thread.looper)
    private val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")!!
    private val muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val codec: MediaCodec
    private var track = -1
    private var frames = 0
    private val y = ByteArray(width * height)
    private val u = ByteArray(width * height / 4)
    private val v = ByteArray(width * height / 4)
    private val scratch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(width * height)
    @Volatile var framesWritten = 0
        private set

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrate(width, height))
            setInteger(MediaFormat.KEY_FRAME_RATE, Timelapse.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    private val speed = settings.speed
    private var started = -1L
    @Volatile private var lastTimeUs = -1L
    /** How long the film is so far. */
    val filmSeconds get() = (maxOf(lastTimeUs, 0L) / 1_000_000L).toInt()

    /** One picture (any size: fitted into the film, black around), stamped by the real time since the first
     *  divided by the speed – the film keeps its tempo even when a picture comes late. */
    fun add(picture: Bitmap, nowMillis: Long = android.os.SystemClock.elapsedRealtime()) {
        if (started < 0) started = nowMillis
        val timeUs = maxOf((nowMillis - started) * 1000L / speed, lastTimeUs + 1)
        lastTimeUs = timeUs
        handler.post {
            val canvas = Canvas(scratch)
            canvas.drawColor(android.graphics.Color.BLACK)
            val scale = minOf(width.toFloat() / picture.width, height.toFloat() / picture.height)
            val w = picture.width * scale; val h = picture.height * scale
            canvas.drawBitmap(picture, null, android.graphics.RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2),
                Paint(Paint.FILTER_BITMAP_FLAG))
            scratch.getPixels(pixels, 0, width, 0, 0, width, height)
            Timelapse.toYuv(pixels, width, height, y, u, v)
            frames++
            feed(timeUs, false)
            drain(false)
        }
    }

    private fun feed(timeUs: Long, last: Boolean) {
        val index = codec.dequeueInputBuffer(10_000)
        if (index < 0) return
        if (last) { codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); return }
        val image = codec.getInputImage(index) ?: return
        val planes = image.planes
        fun put(plane: android.media.Image.Plane, data: ByteArray, w: Int, h: Int) {
            val buffer = plane.buffer
            for (row in 0 until h) for (col in 0 until w) buffer.put(row * plane.rowStride + col * plane.pixelStride, data[row * w + col])
        }
        put(planes[0], y, width, height)
        put(planes[1], u, width / 2, height / 2)
        put(planes[2], v, width / 2, height / 2)
        codec.queueInputBuffer(index, 0, width * height * 3 / 2, timeUs, 0)
    }

    private fun drain(end: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && track >= 0) { muxer.writeSampleData(track, buffer, info); framesWritten++ }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Finish the film and make it visible in Fotos; `onDone(true)` when there is one. */
    fun stop(onDone: (Boolean) -> Unit) {
        handler.post {
            val ok = runCatching {
                feed(lastTimeUs + 1_000_000L / Timelapse.FPS, true)
                drain(true)
                codec.stop(); codec.release()
                if (track >= 0) { muxer.stop() }
                muxer.release(); descriptor.close()
                framesWritten > 0
            }.getOrDefault(false)
            if (ok) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            else context.contentResolver.delete(uri, null, null)
            thread.quitSafely()
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(ok) }
        }
    }

    companion object {
        /** A new film in Fotos (album LiCida) – null if it cannot be made. */
        fun start(context: Context, width: Int, height: Int, settings: TimelapseSettings): TimelapseRecorder? = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "LiCida-Zeitraffer-${System.currentTimeMillis() / 1000}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LiCida")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            runCatching { TimelapseRecorder(context, uri, width, height, settings) }.onFailure { context.contentResolver.delete(uri, null, null) }.getOrNull()
        }.getOrNull()
    }
}
