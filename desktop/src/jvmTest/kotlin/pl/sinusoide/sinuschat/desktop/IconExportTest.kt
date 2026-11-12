package pl.sinusoide.sinuschat.desktop

import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertTrue

class IconExportTest {

    private fun render(svg: ByteArray, px: Int): Surface {
        val dom = SVGDOM(Data.makeFromBytes(svg))
        val surface = Surface.makeRasterN32Premul(px, px)
        surface.canvas.clear(0)
        surface.canvas.save()
        val side = px.toFloat()
        surface.canvas.clipRRect(org.jetbrains.skia.RRect.makeXYWH(0f, 0f, side, side, side * 0.22f), org.jetbrains.skia.ClipMode.INTERSECT, true)
        surface.canvas.drawRect(org.jetbrains.skia.Rect.makeWH(side, side), org.jetbrains.skia.Paint().apply { color = 0xFF000000.toInt() })
        surface.canvas.scale(px / 256f, px / 256f)
        dom.setContainerSize(256f, 256f)
        dom.render(surface.canvas)
        surface.canvas.restore()
        return surface
    }

    private fun macPng(svg: ByteArray, px: Int): ByteArray {
        val tile = Math.round(px * 824f / 1024f)
        val inner = render(svg, tile).makeImageSnapshot()
        val surface = Surface.makeRasterN32Premul(px, px)
        surface.canvas.clear(0)
        val off = (px - tile) / 2f
        surface.canvas.drawImage(inner, off, off)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private fun icns(svg: ByteArray): ByteArray {
        val entries = listOf("icp4" to 16, "icp5" to 32, "ic11" to 32, "ic12" to 64, "ic07" to 128, "ic13" to 256, "ic08" to 256, "ic14" to 512, "ic09" to 512, "ic10" to 1024)
        val body = ByteArrayOutputStream()
        for ((type, px) in entries) {
            val data = macPng(svg, px)
            body.write(type.toByteArray(Charsets.US_ASCII))
            body.write(ByteBuffer.allocate(4).putInt(data.size + 8).array())
            body.write(data)
        }
        val out = ByteArrayOutputStream()
        out.write("icns".toByteArray(Charsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).putInt(body.size() + 8).array())
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    private fun png(svg: ByteArray, px: Int): ByteArray =
        render(svg, px).makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes

    @Test
    fun exportAppIcon() {
        val source = File("icons/sinuschat-logo.svg")
        if (!source.isFile) return
        val svg = source.readBytes()
        val dir = File("build/icons").apply { mkdirs() }
        File(dir, "sinuschat.png").writeBytes(png(svg, 512))

        val sizes = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)
        val pngs = sizes.map { png(svg, it) }
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = 6 + 16 * sizes.size
        sizes.forEachIndexed { i, px ->
            val b = (if (px >= 256) 0 else px).toByte()
            header.put(b).put(b).put(0).put(0).putShort(1).putShort(32)
            header.putInt(pngs[i].size).putInt(offset)
            offset += pngs[i].size
        }
        val ico = ByteArrayOutputStream()
        ico.write(header.array())
        pngs.forEach { ico.write(it) }
        File(dir, "sinuschat.ico").writeBytes(ico.toByteArray())
        File(dir, "sinuschat.icns").writeBytes(icns(svg))
        File(dir, "sinuschat-linux.png").writeBytes(png(svg, 512))
        File(dir, "preview-mac.png").writeBytes(macPng(svg, 256))
        File(dir, "preview-48.png").writeBytes(png(svg, 48))
        File(dir, "preview-16.png").writeBytes(png(svg, 16))
        assertTrue(File(dir, "sinuschat.png").length() > 1000)
    }
}
