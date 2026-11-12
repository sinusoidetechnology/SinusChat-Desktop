package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.installer.BinDraw
import java.io.File
import kotlin.test.Test

class BinFlipPreviewTest {

    @Test
    fun crossDrawsAfterFlip() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview/installer").apply { mkdirs() }
        val done = mutableStateOf(false)
        val scene = ImageComposeScene(240, 240, Density(1f)) {
            BinDraw(Color.White, done.value, Modifier.fillMaxSize().background(Color(0xFF04121A)))
        }
        try {
            var t = 0L
            while (t < 2600L) {
                if (t >= 1200L) done.value = true
                scene.render(t * 1_000_000L)
                t += 16
            }
            scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "bin-flip.png").writeBytes(it) }
        } finally {
            scene.close()
        }
    }
}
