package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

object Motion {
    val ease = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    val pop = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
    val fade = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
    val enter = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    fun span(ms: Float, from: Float, to: Float): Float = ((ms - from) / (to - from)).coerceIn(0f, 1f)

    fun overshoot(t: Float, tension: Float): Float {
        val x = t - 1f
        return x * x * ((tension + 1f) * x + tension) + 1f
    }
}

@Composable
fun rememberElapsedMs(untilMs: Float = Float.MAX_VALUE): State<Float> {
    val ms = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (ms.floatValue < untilMs) {
            val now = withFrameNanos { it }
            ms.floatValue = (now - start) / 1_000_000f
        }
    }
    return ms
}

@Composable
fun Modifier.riseIn(delayMs: Int = 0, durationMs: Int = 320, distanceDp: Float = 24f): Modifier {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(durationMs, delayMs, Motion.enter)) }
    val distance = with(LocalDensity.current) { distanceDp.dp.toPx() }
    return graphicsLayer {
        alpha = k.value
        translationY = distance * (1f - k.value)
    }
}

class TrimmedPath(val path: Path) {
    private val measure = PathMeasure().apply { setPath(path, false) }
    val length: Float = measure.length
    private val piece = Path()

    fun draw(scope: DrawScope, k: Float, color: Color, stroke: Stroke) {
        if (k <= 0f) return
        if (k >= 1f) {
            scope.drawPath(path, color, style = stroke)
            return
        }
        piece.reset()
        measure.getSegment(0f, length * k, piece, true)
        scope.drawPath(piece, color, style = stroke)
    }
}

fun Offset.distanceTo(other: Offset): Float = (this - other).getDistance()
