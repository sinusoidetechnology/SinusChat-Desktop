package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import pl.sinusoide.sinuschat.desktop.ui.icons.VectorIcons
import kotlin.math.max
import kotlin.math.min

private val wifiBars by lazy {
    VectorIcons.shapes("ic_wifi").sortedByDescending { it.getBounds().top }
}

@Composable
fun WifiDraw(ink: Color, accent: Color, modifier: Modifier = Modifier) {
    val bars = wifiBars
    val end = 620f + (bars.size - 1) * 280f
    val ms by rememberElapsedMs(end + 400f)
    Spacer(modifier.drawWithCache {
        val contentW = 960f
        val contentH = 560f
        val contentCx = 480f
        val contentCy = 560f
        val s = min(size.width, size.height) * 0.6f / max(contentW, contentH)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        val glow = Brush.radialGradient(
            0f to accent.copy(alpha = 0.12f),
            0.55f to accent.copy(alpha = 0.035f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        )
        val pivots = bars.map { it.getBounds().center }

        onDrawBehind {
            val t = ms
            val g = Motion.span(t, end - 350f, end + 350f)
            if (g > 0f) drawCircle(glow, radius, center, alpha = g)
            withTransform({
                translate(center.x, center.y)
                scale(s, s, Offset.Zero)
                translate(-contentCx, -contentCy)
            }) {
                for ((i, bar) in bars.withIndex()) {
                    val k = Motion.span(t, 140f + i * 280f, 620f + i * 280f)
                    if (k <= 0f) continue
                    if (k >= 1f) {
                        drawPath(bar, ink)
                        continue
                    }
                    val sc = 0.55f + 0.45f * Motion.overshoot(k, 3f)
                    scale(sc, sc, pivots[i]) {
                        drawPath(bar, ink, alpha = min(1f, k * 3f))
                    }
                }
            }
        }
    })
}
