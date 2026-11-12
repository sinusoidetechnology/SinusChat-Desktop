package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.ui.chat.AvatarExport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AvatarExportTest {

    @Test
    fun letterAvatarIsPng() {
        val png = AvatarExport.letterPng("Błażej", "#5288C1", 256)
        assertTrue(png.size > 1000)
        assertTrue(png[0] == 0x89.toByte() && png[1] == 'P'.code.toByte())
        if (System.getenv("SINUSCHAT_INSTALLER_ART") != null) {
            File("build/preview").apply { mkdirs() }.resolve("avatar-letter.png").writeBytes(AvatarExport.letterPng("Błażej", "#5288C1", 512))
        }
    }
}
