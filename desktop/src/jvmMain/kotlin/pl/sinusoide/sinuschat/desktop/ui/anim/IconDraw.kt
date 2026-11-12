package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import pl.sinusoide.sinuschat.desktop.ui.icons.VectorIcons
import kotlin.math.max
import kotlin.math.min

class DrawPart(
    val path: Path,
    val width: Float,
    val fromMs: Float,
    val toMs: Float,
    val cap: StrokeCap = StrokeCap.Round
)

class IconDrawing(val content: Rect, val parts: List<DrawPart>, val fill: Path?, val clip: Boolean = false) {
    val endMs: Float = parts.maxOf { it.toMs }

    companion object {
        private const val EDGE = 6f

        fun language(): IconDrawing {
            val o = Rect(120f, 120f, 840f, 840f)
            val ring = Path().apply {
                arcTo(o, -90f, 180f, true)
                arcTo(o, 90f, 180f, false)
                close()
            }
            val r = 597.3f
            fun meridian(cx: Float, start: Float, sweep: Float) = Path().apply {
                arcTo(Rect(cx - r, 480f - r, cx + r, 480f + r), start, sweep, true)
            }
            fun parallel(y: Float) = Path().apply { moveTo(130f, y); lineTo(830f, y) }
            val w = 80f + 2 * EDGE
            return IconDrawing(
                Rect(80f, 80f, 880f, 880f),
                listOf(
                    DrawPart(ring, w, 150f, 1150f),
                    DrawPart(meridian(937.3f, -142.94f, -74.12f), w, 1050f, 1500f),
                    DrawPart(meridian(22.7f, -37.06f, 74.12f), w, 1200f, 1650f),
                    DrawPart(parallel(360f), w, 1500f, 1850f, StrokeCap.Butt),
                    DrawPart(parallel(600f), w, 1700f, 2050f, StrokeCap.Butt)
                ),
                VectorIcons.path("ic_language")
            )
        }
    }
}

@Composable
fun IconDraw(drawing: IconDrawing, ink: Color, accent: Color, modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs(drawing.endMs + 400f)
    Spacer(modifier.drawWithCache {
        val c = drawing.content
        val s = min(size.width, size.height) * 0.6f / max(c.width, c.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        val glow = Brush.radialGradient(
            0f to accent.copy(alpha = 0.12f),
            0.55f to accent.copy(alpha = 0.035f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        )
        val parts = drawing.parts.map { TrimmedPath(it.path) }
        val strokes = drawing.parts.map { Stroke(it.width, cap = it.cap, join = StrokeJoin.Miter, miter = 4f) }
        val bounds = Rect(Offset.Zero, size)

        onDrawBehind {
            val t = ms
            val g = Motion.span(t, drawing.endMs - 350f, drawing.endMs + 350f)
            if (g > 0f) drawCircle(glow, radius, center, alpha = g)
            drawIntoCanvas { it.saveLayer(bounds, Paint()) }
            withTransform({
                translate(center.x, center.y)
                scale(s, s, Offset.Zero)
                translate(-c.center.x, -c.center.y)
            }) {
                val shape = drawing.fill
                if (drawing.clip && shape != null) {
                    if (t >= drawing.endMs) {
                        drawPath(shape, ink)
                    } else {
                        clipPath(shape) {
                            for ((i, p) in drawing.parts.withIndex()) {
                                val k = Motion.ease.transform(Motion.span(t, p.fromMs, p.toMs))
                                parts[i].draw(this, k, ink, strokes[i])
                            }
                        }
                    }
                } else {
                    for ((i, p) in drawing.parts.withIndex()) {
                        val k = Motion.ease.transform(Motion.span(t, p.fromMs, p.toMs))
                        parts[i].draw(this, k, ink, strokes[i])
                    }
                    shape?.let { drawPath(it, Color.Black, blendMode = BlendMode.DstIn) }
                }
            }
            drawIntoCanvas { it.restore() }
        }
    })
}
