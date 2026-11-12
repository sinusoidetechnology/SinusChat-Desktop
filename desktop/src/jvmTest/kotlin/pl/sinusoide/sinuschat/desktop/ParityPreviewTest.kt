package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.PhoneTap
import pl.sinusoide.sinuschat.desktop.ui.chat.LetterAvatar
import pl.sinusoide.sinuschat.desktop.ui.chat.LetterFont
import pl.sinusoide.sinuschat.desktop.ui.settings.Flag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParityPreviewTest {

    @Test
    fun letterFontLoads() {
        assertNotNull(LetterFont.typeface)
    }

    @Test
    fun renderAvatarsFlagsAndHandoff() {
        val out = File("build/preview").apply { mkdirs() }
        val scene = ImageComposeScene(760, 320, Density(1f)) {
            Column(Modifier.fillMaxSize().background(SinusColors.backgroundSolid).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LetterAvatar("Błażej", "#5288C1", 54.dp)
                    LetterAvatar("Ania", "#E74C3C", 54.dp)
                    LetterAvatar("żaneta", "#2ECC71", 54.dp)
                    LetterAvatar("Grupa", "#9B59B6", 110.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in listOf("pl", "en", "cs", "uk", "de", "zh-TW")) Flag(c, Modifier.size(48.dp, 32.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneTap(SinusColors.textPrimary, SinusColors.primary, Modifier.size(96.dp))
                }
            }
        }
        try {
            scene.render(0L)
            var t = 0L
            while (t < 1500L) {
                t += 16
                scene.render(t * 1_000_000L)
            }
            val png = scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes
            assertNotNull(png)
            File(out, "parity.png").writeBytes(png)
            assertTrue(png.size > 1000)
        } finally {
            scene.close()
        }
    }
}
