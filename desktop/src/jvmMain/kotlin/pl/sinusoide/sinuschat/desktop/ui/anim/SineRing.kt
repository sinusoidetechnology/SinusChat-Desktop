package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun SineRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    disc: Color = Color(0x73000000),
    showCancel: Boolean = false
) {
    val target by rememberUpdatedState(progress)
    var shown by remember { mutableFloatStateOf(progress?.coerceIn(0f, 1f) ?: 0f) }
    var last by remember { mutableLongStateOf(0L) }
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val p = target
                if (p != null) {
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.25f)
                    val k = 1f - (1f - 0.12f).pow(dt * 60f)
                    shown += (p.coerceIn(0f, 1f) - shown) * k
                }
                last = now
                value = (now - start) / 1e9f
            }
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val c = Offset(w / 2f, h / 2f)
        val side = min(w, h)
        val stroke = max(2.dp.toPx(), side * 0.055f)
        val amp0 = side * 0.05f
        val r = side / 2f - stroke - amp0 - 1f
        val t = time

        if (disc.alpha > 0f) drawCircle(disc, side / 2f, c)
        drawCircle(color.copy(alpha = 0x38 / 255f), r, c, style = Stroke(stroke * 0.6f))

        val startAngle: Float
        val sweep: Float
        val amp: Float
        if (target == null) {
            startAngle = (t * 280f) % 360f - 90f
            sweep = 80f
            amp = amp0
        } else {
            startAngle = -90f
            sweep = max(14f, 360f * shown)
            amp = amp0 * (1f - 0.6f * shown)
        }
        val waves = 9f
        val phase = t * 7f
        val steps = max(28, (sweep / 3f).toInt())
        val path = Path()
        for (i in 0..steps) {
            val rad = Math.toRadians((startAngle + sweep * i / steps).toDouble()).toFloat()
            val rr = r + amp * sin(rad * waves - phase)
            val x = c.x + rr * cos(rad)
            val y = c.y + rr * sin(rad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (showCancel) {
            val k = side * 0.13f
            val crossStroke = stroke * 0.9f
            drawLine(color, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), crossStroke, StrokeCap.Round)
            drawLine(color, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), crossStroke, StrokeCap.Round)
        }
    }
}
