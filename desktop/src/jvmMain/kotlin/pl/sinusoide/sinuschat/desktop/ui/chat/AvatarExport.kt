package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.Downloads
import pl.sinusoide.sinuschat.desktop.chat.MediaStore
import kotlin.concurrent.thread

object AvatarExport {

    private const val SIZE = 1024

    fun letterPng(name: String, colorHex: String?, size: Int = SIZE): ByteArray {
        val surface = Surface.makeRasterN32Premul(size, size)
        val c = surface.canvas
        val s = size.toFloat()
        val r = s / 2f
        val (light, dark) = AvatarColors.shades(ChatFormat.color(colorHex))
        c.save()
        c.clipRRect(org.jetbrains.skia.RRect.makeXYWH(0f, 0f, s, s, r), true)
        val row = Paint()
        for (y in 0 until size) {
            row.color = androidx.compose.ui.graphics.lerp(light, dark, y / s).toArgb()
            c.drawRect(org.jetbrains.skia.Rect.makeXYWH(0f, y.toFloat(), s, 1f), row)
        }
        c.restore()
        val sw = s * 0.012f
        c.drawCircle(r, r, r - sw / 2f - 1f, Paint().apply {
            isAntiAlias = true
            mode = PaintMode.STROKE
            strokeWidth = sw
            color = 0x28FFFFFF
        })
        val face = LetterFont.typeface
        val letter = AvatarColors.initialOf(name)
        val font = if (face != null) Font(face, s * 0.46f) else Font(null, s * 0.46f)
        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
            val blur = s * 0.015f
            imageFilter = ImageFilter.makeDropShadow(0f, s * 0.008f, blur * 0.57735f + 0.5f, blur * 0.57735f + 0.5f, 0x50000000)
        }
        val m = font.metrics
        val width = font.measureTextWidth(letter, paint)
        c.drawString(letter, r - width / 2f, r - (m.ascent + m.descent) / 2f, font, paint)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    }

    fun photoPng(image: ImageBitmap): ByteArray =
        Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)

    fun save(store: ChatStore, name: String, colorHex: String?, image: ImageBitmap?) {
        thread(isDaemon = true, name = "avatar-export") {
            val bytes = runCatching { if (image != null) photoPng(image) else letterPng(name, colorHex) }.getOrDefault(ByteArray(0))
            val base = Downloads.cleanName(name).ifEmpty { "SinusChat" }
            val file = runCatching { Downloads.reserve("$base.png") }.getOrNull()
            if (bytes.isEmpty() || file == null || runCatching { file.writeBytes(bytes) }.isFailure) {
                file?.takeIf { it.length() == 0L }?.delete()
                store.emit(ChatEvent.MediaFailed(MediaStore.ERROR_WRITE))
            } else {
                store.emit(ChatEvent.Saved(file))
            }
        }
    }
}
