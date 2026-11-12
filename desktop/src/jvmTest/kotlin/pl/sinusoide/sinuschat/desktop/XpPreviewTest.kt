package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.Themes
import pl.sinusoide.sinuschat.desktop.ui.xp.XpCaption
import pl.sinusoide.sinuschat.desktop.ui.xp.XpFrameBorder
import java.io.File
import kotlin.test.Test

class XpPreviewTest {

    @Test
    fun renderXpFrame() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview/xp").apply { mkdirs() }
        val before = SinusColors.palette
        SinusColors.palette = Themes.retro
        try {
            for ((name, active, maximized) in listOf(Triple("active", true, false), Triple("inactive", false, false), Triple("maximized", true, true))) {
                val scene = ImageComposeScene(520, 220, Density(1f)) {
                    SinusTheme {
                        XpFrameBorder(active, maximized, caption = { XpCaption("SinusChat", appIcon(), active, maximized, {}, {}, {}) }) {
                            Box(Modifier.fillMaxSize().background(SinusColors.backgroundSolid).padding(16.dp)) {
                                Text("Zażółć gęślą jaźń 123 — Tahoma", color = SinusColors.textPrimary, fontSize = 15.sp)
                            }
                        }
                    }
                }
                try {
                    scene.render(0L)
                    scene.render(16_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "$name.png").writeBytes(it) }
                } finally {
                    scene.close()
                }
            }
        } finally {
            SinusColors.palette = before
        }
    }
}
