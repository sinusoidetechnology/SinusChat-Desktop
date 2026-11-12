package pl.sinusoide.sinuschat.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings
import pl.sinusoide.sinuschat.desktop.security.LockManager
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.Themes
import pl.sinusoide.sinuschat.desktop.ui.lock.PinMode
import pl.sinusoide.sinuschat.desktop.ui.lock.PinScreen
import pl.sinusoide.sinuschat.desktop.ui.lock.PinUser
import java.io.File
import kotlin.test.Test

class RetroLockPreviewTest {

    @Test
    fun renderRetroLogin() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview").apply { mkdirs() }
        val lock = LockManager("lock-preview-retro.bin")
        lock.setPin("482916")
        SinusColors.palette = Themes.retro
        val lang = AppLanguage.PL
        val scene = ImageComposeScene(1180, 760, Density(1f)) {
            SinusTheme {
                CompositionLocalProvider(
                    LocalStrings provides lang.strings,
                    LocalChatStrings provides lang.chat,
                    LocalSettingsStrings provides lang.settings
                ) {
                    PinScreen(lock, PinMode.UNLOCK, {}, user = PinUser("Błażej Stosio", "#5288C1", null))
                }
            }
        }
        try {
            var t = 0L
            while (t < 900L) { scene.render(t * 1_000_000L); t += 16 }
            scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "retro-lock.png").writeBytes(it) }
        } finally {
            scene.close()
            SinusColors.palette = Themes.dark
            lock.wipe()
        }
    }
}
