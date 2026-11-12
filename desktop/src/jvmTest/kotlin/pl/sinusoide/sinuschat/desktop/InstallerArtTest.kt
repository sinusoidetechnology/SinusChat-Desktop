package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.TextLine
import pl.sinusoide.sinuschat.desktop.ui.chat.LetterFont
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class InstallerArtTest {

    private val cyan = 0xFF2AE0F5.toInt()
    private val scales = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f)

    private val logo: Image by lazy {
        Image.makeFromEncoded(PhoneLink::class.java.getResourceAsStream("/sinuschat.png")!!.use { it.readBytes() })
    }

    private fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    private fun drawLogo(c: Canvas, x: Float, y: Float, size: Float) {
        c.drawImageRect(
            logo, Rect.makeWH(logo.width.toFloat(), logo.height.toFloat()), Rect.makeXYWH(x, y, size, size),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true
        )
    }

    private fun art(w: Int, h: Int, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): Image {
        val scene = androidx.compose.ui.ImageComposeScene(w, h, androidx.compose.ui.unit.Density(1f)) {
            androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) { draw() }
        }
        try {
            return scene.render()
        } finally {
            scene.close()
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.wave(baseY: Float, amp: Float, length: Float, phase: Float, stroke: Float, color: Int) {
        val path = androidx.compose.ui.graphics.Path()
        var x = -stroke
        path.moveTo(x, baseY + amp * sin(phase))
        while (x <= size.width + stroke) {
            x += 1f
            path.lineTo(x, baseY + amp * sin(phase + x / length * 2f * PI.toFloat()))
        }
        drawPath(path, ComposeColor(color), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }

    private fun large(s: Float): Image {
        val w = (164 * s).roundToInt()
        val h = (314 * s).roundToInt()
        return art(w, h) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to ComposeColor(0xFF071D26), 0.55f to ComposeColor(0xFF04121A), 1f to ComposeColor(0xFF000000)))
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(ComposeColor(alpha(cyan, 0.22f)), ComposeColor(alpha(cyan, 0f))), Offset(w * 0.5f, h * 0.26f), w * 0.85f))
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(ComposeColor(alpha(0xFF5288C1.toInt(), 0.20f)), ComposeColor(alpha(0xFF5288C1.toInt(), 0f))), Offset(w * 0.15f, h * 0.92f), w * 0.9f))
            val base = h * 0.80f
            wave(base, 15f * s, 70f * s, 0f, 2.6f * s, alpha(cyan, 0.95f))
            wave(base + 6f * s, 11f * s, 54f * s, 1.3f, 1.6f * s, alpha(cyan, 0.45f))
            wave(base - 5f * s, 20f * s, 96f * s, 2.4f, 1.2f * s, alpha(0xFF5288C1.toInt(), 0.7f))
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                val logoSize = 92f * s
                drawLogo(c, (w - logoSize) / 2f, 46f * s, logoSize)
                val title = TextLine.make("SinusChat", Font(LetterFont.typeface, 21f * s))
                c.drawTextLine(title, (w - title.width) / 2f, 46f * s + logoSize + 34f * s, Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true })
                val sub = TextLine.make("DESKTOP", Font(LetterFont.typeface, 11.5f * s))
                c.drawTextLine(sub, (w - sub.width) / 2f, 46f * s + logoSize + 54f * s, Paint().apply { color = cyan; isAntiAlias = true })
            }
        }
    }

    private fun splash(s: Float): Image {
        val w = (360 * s).roundToInt()
        val h = (200 * s).roundToInt()
        return art(w, h) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to ComposeColor(0xFF071D26), 0.6f to ComposeColor(0xFF04121A), 1f to ComposeColor(0xFF000000)))
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(ComposeColor(alpha(cyan, 0.24f)), ComposeColor(alpha(cyan, 0f))), Offset(w * 0.5f, h * 0.35f), w * 0.55f))
            val base = h * 0.84f
            wave(base, 9f * s, 70f * s, 0f, 2.2f * s, alpha(cyan, 0.95f))
            wave(base + 4f * s, 7f * s, 54f * s, 1.3f, 1.3f * s, alpha(cyan, 0.45f))
            wave(base - 3f * s, 12f * s, 96f * s, 2.4f, 1.0f * s, alpha(0xFF5288C1.toInt(), 0.7f))
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                val logoSize = 64f * s
                drawLogo(c, (w - logoSize) / 2f, 30f * s, logoSize)
                val title = TextLine.make("SinusChat", Font(LetterFont.typeface, 19f * s))
                c.drawTextLine(title, (w - title.width) / 2f, 30f * s + logoSize + 30f * s, Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true })
            }
        }
    }

    private fun small(size: Int): Image = art(size, size) {
        drawRect(ComposeColor.White)
        drawIntoCanvas { cv -> drawLogo(cv.nativeCanvas, 0f, 0f, size.toFloat()) }
    }

    private fun bmp(image: Image): ByteArray {
        val w = image.width
        val h = image.height
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        image.readPixels(bitmap)
        val px = bitmap.readPixels()!!
        val row = (w * 3 + 3) and 3.inv()
        val size = 54 + row * h
        val out = ByteArrayOutputStream(size)
        fun le(v: Int, n: Int) = repeat(n) { out.write((v shr (8 * it)) and 0xFF) }
        out.write('B'.code)
        out.write('M'.code)
        le(size, 4); le(0, 4); le(54, 4)
        le(40, 4); le(w, 4); le(h, 4); le(1, 2); le(24, 2); le(0, 4); le(row * h, 4); le(2835, 4); le(2835, 4); le(0, 4); le(0, 4)
        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                out.write(px[i].toInt() and 0xFF)
                out.write(px[i + 1].toInt() and 0xFF)
                out.write(px[i + 2].toInt() and 0xFF)
            }
            repeat(row - w * 3) { out.write(0) }
        }
        return out.toByteArray()
    }

    private fun iconSplash(size: Int): ByteArray {
        val image = art(size, size) { drawIntoCanvas { cv -> drawLogo(cv.nativeCanvas, 0f, 0f, size.toFloat()) } }
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        image.readPixels(bitmap)
        val px = bitmap.readPixels()!!
        for (i in 0 until size * size) {
            val o = i * 4
            val alpha = px[o + 3].toInt() and 0xFF
            if (alpha < 128) {
                px[o] = 0xFF.toByte(); px[o + 1] = 0; px[o + 2] = 0xFF.toByte()
            } else if (alpha < 255) {
                for (k in 0..2) px[o + k] = ((px[o + k].toInt() and 0xFF) * 255 / alpha).coerceAtMost(255).toByte()
            }
            px[o + 3] = 0xFF.toByte()
        }
        val opaque = Bitmap()
        opaque.allocPixels(ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
        opaque.installPixels(px)
        return bmp(Image.makeFromBitmap(opaque))
    }

    @Test
    fun renderInstallerArt() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/installer-art").apply { mkdirs() }
        for (s in scales) {
            val pct = (s * 100).roundToInt()
            File(out, "wizard-$pct.bmp").writeBytes(bmp(large(s)))
            File(out, "wizard-small-$pct.bmp").writeBytes(bmp(small((55 * s).roundToInt())))
        }
        File(out, "splash.bmp").writeBytes(bmp(splash(2f)))
        for ((pct, px) in listOf(100 to 160, 125 to 200, 150 to 240, 175 to 280, 200 to 320)) File(out, "splash-$pct.bmp").writeBytes(iconSplash(px))
        splash(2f).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes?.let { File(out, "splash.png").writeBytes(it) }
        large(2f).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes?.let { File(out, "preview.png").writeBytes(it) }
        assertTrue(File(out, "wizard-100.bmp").length() > 1000)
    }
}
