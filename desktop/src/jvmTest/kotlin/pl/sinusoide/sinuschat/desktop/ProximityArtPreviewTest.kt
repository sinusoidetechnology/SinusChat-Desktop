package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.anim.DistanceWave
import pl.sinusoide.sinuschat.desktop.ui.anim.IconDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.PhoneTap
import pl.sinusoide.sinuschat.desktop.ui.anim.ProximityDrawings
import java.io.File
import kotlin.test.Test

class ProximityArtPreviewTest {

    @Test
    fun renderArt() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview").apply { mkdirs() }
        for (at in listOf(700L, 3000L)) {
            val scene = ImageComposeScene(1080, 460, Density(1f)) {
                SinusTheme {
                    val ink = SinusColors.textPrimary
                    val accent = SinusColors.primary
                    val m = Modifier.size(270.dp, 210.dp)
                    Column(Modifier.background(SinusColors.backgroundSolid)) {
                        Row {
                            PhoneTap(ink, accent, m)
                            IconDraw(ProximityDrawings.bluetooth(), ink, accent, m)
                            IconDraw(ProximityDrawings.nearby(), ink, accent, m)
                            pl.sinusoide.sinuschat.desktop.ui.anim.ConnectedHop(ink, accent, m)
                        }
                        Row {
                            IconDraw(ProximityDrawings.distance(), ink, accent, m)
                            DistanceWave(1f, false, ink, accent, m)
                            DistanceWave(0.2f, false, ink, accent, m)
                            DistanceWave(0.2f, true, ink, accent, m)
                        }
                    }
                }
            }
            try {
                var t = 0L
                while (t < at) { scene.render(t * 1_000_000L); t += 16 }
                scene.render(t * 1_000_000L).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(out, "proximity-art-$at.png").writeBytes(it) }
            } finally {
                scene.close()
            }
        }
    }
}
