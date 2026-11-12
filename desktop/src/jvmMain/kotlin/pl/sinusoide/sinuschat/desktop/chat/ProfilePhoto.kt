package pl.sinusoide.sinuschat.desktop.chat

import pl.sinusoide.sinuschat.link.LinkFrame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ProfilePhoto {

    private const val MAX_SOURCE_BYTES = 40L * 1024 * 1024
    private const val SIDE = 1024
    private const val QUALITY = 0.9f

    val extensions = setOf("jpg", "jpeg", "png", "bmp", "gif")

    fun prepare(file: File): ByteArray? = runCatching {
        if (!file.isFile || file.length() !in 1..MAX_SOURCE_BYTES) return null
        val src = ImageIO.read(file) ?: return null
        if (src.width <= 0 || src.height <= 0) return null
        val scale = minOf(1.0, SIDE.toDouble() / minOf(src.width, src.height))
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, w, h)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val bytes = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(bytes).use { stream ->
            writer.output = stream
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = QUALITY
            }
            writer.write(null, IIOImage(out, null, null), param)
            writer.dispose()
        }
        bytes.toByteArray().takeIf { it.isNotEmpty() && it.size <= LinkFrame.AVATAR_MAX_BYTES }
    }.getOrNull()
}
