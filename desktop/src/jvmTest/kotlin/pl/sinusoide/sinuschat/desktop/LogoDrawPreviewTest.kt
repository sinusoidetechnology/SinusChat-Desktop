package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.ui.anim.LogoDraw
import java.io.File
import kotlin.test.Test

class LogoDrawPreviewTest {

    @Test
    fun renderFrames() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview/logo").apply { mkdirs() }
        for (at in listOf(500L, 900L, 1300L, 1600L, 2400L)) {
            val scene = ImageComposeScene(260, 260, Density(1f)) {
                LogoDraw(Modifier.fillMaxSize().background(Color(0xFF0E1621)))
            }
            try {
                var t = 0L
                while (t < at) { scene.render(t * 1_000_000L); t += 16 }
                scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "logo-$at.png").writeBytes(it) }
            } finally {
                scene.close()
            }
        }
    }
}
