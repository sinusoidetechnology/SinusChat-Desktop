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

class FaviconExportTest {

    @Test
    fun exportFavicon() {
        val target = System.getenv("SINUSCHAT_FAVICON") ?: return
        val svg = File(target, "favicon.svg").readBytes()
        val sizes = listOf(16, 32, 48)
        val pngs = sizes.map { px ->
            val dom = SVGDOM(Data.makeFromBytes(svg))
            val surface = Surface.makeRasterN32Premul(px, px)
            surface.canvas.clear(0)
            surface.canvas.scale(px / 64f, px / 64f)
            dom.setContainerSize(64f, 64f)
            dom.render(surface.canvas)
            surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = 6 + 16 * sizes.size
        sizes.forEachIndexed { i, px ->
            header.put(px.toByte()).put(px.toByte()).put(0).put(0).putShort(1).putShort(32)
            header.putInt(pngs[i].size).putInt(offset)
            offset += pngs[i].size
        }
        val ico = ByteArrayOutputStream()
        ico.write(header.array())
        pngs.forEach { ico.write(it) }
        File(target, "favicon.ico").writeBytes(ico.toByteArray())
        File("build/preview").mkdirs()
        File("build/preview/favicon-32.png").writeBytes(pngs[1])
    }
}
