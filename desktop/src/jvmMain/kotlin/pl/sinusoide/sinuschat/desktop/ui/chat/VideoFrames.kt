package pl.sinusoide.sinuschat.desktop.ui.chat

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import pl.sinusoide.sinuschat.desktop.chat.VideoPlayback

class VideoFrames {
    private var bitmaps: List<Bitmap> = emptyList()
    private var width = 0
    private var height = 0
    private var next = 0

    fun fill(picture: VideoPlayback.Picture): Bitmap? {
        if (picture.width != width || picture.height != height || bitmaps.isEmpty()) {
            close()
            val info = ImageInfo(picture.width, picture.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bitmaps = List(3) { Bitmap().apply { allocPixels(info) } }
            width = picture.width
            height = picture.height
        }
        val bitmap = bitmaps[next]
        next = (next + 1) % bitmaps.size
        if (!bitmap.installPixels(bitmap.imageInfo, picture.pixels, picture.width * 4)) return null
        bitmap.notifyPixelsChanged()
        return bitmap
    }

    fun close() {
        bitmaps.forEach { runCatching { it.close() } }
        bitmaps = emptyList()
        width = 0
        height = 0
        next = 0
    }
}
