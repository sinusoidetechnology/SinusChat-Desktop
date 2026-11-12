package pl.sinusoide.sinuschat.desktop.chat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

object VideoPoster {

    fun of(file: File, maxSide: Int): Pair<ByteArray, ImageBitmap>? = runCatching {
        avutil.av_log_set_level(avutil.AV_LOG_QUIET)
        val g = FFmpegFrameGrabber(file)
        try {
            g.pixelFormat = avutil.AV_PIX_FMT_BGRA
            g.start()
            val srcW = g.imageWidth
            val srcH = g.imageHeight
            if (srcW <= 0 || srcH <= 0) return null
            val k = minOf(1.0, maxSide.toDouble() / maxOf(srcW, srcH))
            g.imageWidth = (srcW * k).roundToInt().coerceAtLeast(2) and 1.inv()
            g.imageHeight = (srcH * k).roundToInt().coerceAtLeast(2) and 1.inv()
            val rotation = ((-g.displayRotation).roundToInt() % 360 + 360) % 360
            val frame = g.grabImage() ?: return null
            val w = frame.imageWidth
            val h = frame.imageHeight
            val src = (frame.image[0] as ByteBuffer).duplicate()
            val stride = frame.imageStride.takeIf { it >= w * 4 } ?: (w * 4)
            val row = w * 4
            val pixels = ByteArray(row * h)
            val base = src.position()
            for (y in 0 until h) {
                src.position(base + y * stride)
                src.get(pixels, y * row, row)
            }
            val bitmap = Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)) }
            try {
                if (!bitmap.installPixels(bitmap.imageInfo, pixels, row)) return null
                val raw = Image.makeFromBitmap(bitmap)
                val turned = rotation == 90 || rotation == 270
                val outW = if (turned) h else w
                val outH = if (turned) w else h
                val surface = Surface.makeRasterN32Premul(outW, outH)
                surface.canvas.apply {
                    translate(outW / 2f, outH / 2f)
                    rotate(rotation.toFloat())
                    drawImage(raw, -w / 2f, -h / 2f)
                }
                val snap = surface.makeImageSnapshot()
                val data = snap.encodeToData(EncodedImageFormat.WEBP, 85) ?: snap.encodeToData(EncodedImageFormat.PNG)
                (data?.bytes ?: return null) to snap.toComposeImageBitmap()
            } finally {
                bitmap.close()
            }
        } finally {
            runCatching { g.stop() }
            runCatching { g.release() }
        }
    }.getOrNull()
}
