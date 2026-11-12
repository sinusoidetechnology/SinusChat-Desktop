package pl.sinusoide.sinuschat.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.legal.TermsGate
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.Themes
import pl.sinusoide.sinuschat.desktop.ui.TermsUpdateScreen
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class TermsUpdatePreviewTest {

    @Test
    fun renderTermsUpdate() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview").apply { mkdirs() }
        val dir = Files.createTempDirectory("terms-preview").toFile()
        TermsGate.useFile(File(dir, "terms.properties"))
        try {
            TermsGate.accept()
            TermsGate.remember("2026-12-01")
            for ((name, palette) in listOf("dark" to Themes.dark, "light" to Themes.light)) {
                SinusColors.palette = palette
                val scene = ImageComposeScene(1180, 760, Density(1f)) {
                    SinusTheme { TermsUpdateScreen(AppLanguage.PL, {}, {}) }
                }
                try {
                    var t = 0L
                    while (t < 1800L) { scene.render(t * 1_000_000L); t += 16 }
                    scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "terms-update-$name.png").writeBytes(it) }
                } finally {
                    scene.close()
                }
            }
        } finally {
            SinusColors.palette = Themes.dark
            TermsGate.useFile(null)
            dir.deleteRecursively()
        }
    }
}
