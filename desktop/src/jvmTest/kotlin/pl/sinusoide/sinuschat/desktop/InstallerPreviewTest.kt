package pl.sinusoide.sinuschat.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.installer.InstallerPreview
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test

class InstallerPreviewTest {

    @Test
    fun renderInstallerScreens() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview/installer").apply { mkdirs() }
        val shots = listOf("WELCOME", "TERMS", "LANGUAGE", "OPTIONS", "INSTALLING", "DONE", "CHOICE", "CONFIRM", "REMOVING", "REMOVED")
        for (name in shots + "BIN_DONE") {
            val step = if (name == "BIN_DONE") "REMOVING" else name
            val scene = ImageComposeScene(820, 540, Density(1f)) { InstallerPreview(step, AppLanguage.PL, binDone = name == "BIN_DONE") }
            try {
                var t = 0L
                while (t < 3200L) {
                    scene.render(t * 1_000_000L)
                    t += 16
                }
                scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "$name.png").writeBytes(it) }
            } finally {
                scene.close()
            }
        }
        for ((name, at) in listOf("QUESTION_MID" to 800L, "QUESTION_END" to 2600L)) {
            val scene = ImageComposeScene(240, 240, Density(1f)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF04121A))) {
                    pl.sinusoide.sinuschat.desktop.installer.QuestionDraw(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF2AE0F5), androidx.compose.ui.Modifier.fillMaxSize())
                }
            }
            try {
                var t = 0L
                while (t < at) { scene.render(t * 1_000_000L); t += 16 }
                scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "$name.png").writeBytes(it) }
            } finally {
                scene.close()
            }
        }
        val manifest = pl.sinusoide.sinuschat.desktop.update.UpdateManifest("SinusChat-Desktop", "1.1")
        val pkg = pl.sinusoide.sinuschat.desktop.update.UpdatePackage("https://sinusoide.pl/a.exe", "a".repeat(64), 104_300_000L)
        val updater = pl.sinusoide.sinuschat.desktop.update.DesktopUpdater({ NotifyPrefs() }, {})
        for ((name, state, ask) in listOf(
            Triple("BAR_DOWNLOADING", pl.sinusoide.sinuschat.desktop.update.UpdateState.Downloading(manifest, pkg, 42, 43_800_000L, 3_400_000.0), false),
            Triple("BAR_READY", pl.sinusoide.sinuschat.desktop.update.UpdateState.Ready(manifest, pkg, File("x.exe")), true)
        )) {
            updater.preview(state, ask)
            val scene = ImageComposeScene(720, 460, Density(1f)) {
                pl.sinusoide.sinuschat.desktop.ui.SinusTheme {
                    androidx.compose.runtime.CompositionLocalProvider(
                        pl.sinusoide.sinuschat.desktop.update.LocalUpdater provides updater,
                        pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings provides AppLanguage.PL.chat
                    ) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(pl.sinusoide.sinuschat.desktop.ui.SinusColors.backgroundSolid)) {
                            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().padding(16.dp)) {
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    androidx.compose.material3.Text("Czaty", color = pl.sinusoide.sinuschat.desktop.ui.SinusColors.textPrimary, fontSize = 22.sp, modifier = androidx.compose.ui.Modifier.weight(1f))
                                    pl.sinusoide.sinuschat.desktop.ui.update.UpdateIndicator()
                                    pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon("ic_search", pl.sinusoide.sinuschat.desktop.ui.SinusColors.textSecondary, androidx.compose.ui.Modifier.padding(10.dp).size(24.dp))
                                }
                                if (state is pl.sinusoide.sinuschat.desktop.update.UpdateState.Downloading) {
                                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 20.dp).size(300.dp, 140.dp).background(pl.sinusoide.sinuschat.desktop.ui.SinusColors.surfaceHigh).padding(16.dp)) {
                                        val us = pl.sinusoide.sinuschat.desktop.i18n.UpdateStrings.PL
                                        androidx.compose.material3.Text(us.downloadTitle("1.1"), color = pl.sinusoide.sinuschat.desktop.ui.SinusColors.textPrimary, fontSize = 14.5.sp)
                                        pl.sinusoide.sinuschat.desktop.ui.update.DownloadDetails(us, state)
                                    }
                                }
                            }
                            pl.sinusoide.sinuschat.desktop.ui.update.RestartDialog(updater) {}
                        }
                    }
                }
            }
            try {
                var t = 0L
                while (t < 1200L) { scene.render(t * 1_000_000L); t += 16 }
                scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "$name.png").writeBytes(it) }
            } finally {
                scene.close()
            }
        }
        val update = ImageComposeScene(460, 250, Density(1f)) { pl.sinusoide.sinuschat.desktop.installer.UpdatePreview(AppLanguage.PL) }
        try {
            var t = 0L
            while (t < 900L) { update.render(t * 1_000_000L); t += 16 }
            update.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "UPDATE.png").writeBytes(it) }
        } finally {
            update.close()
        }
    }
}
