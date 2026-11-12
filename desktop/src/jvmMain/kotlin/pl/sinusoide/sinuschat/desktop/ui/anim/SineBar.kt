package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import pl.sinusoide.sinuschat.desktop.ui.SinusColors

@Composable
fun SineBar(progress: Float?, modifier: Modifier = Modifier, color: Color = SinusColors.primary, track: Color = SinusColors.card) {
    val loop = androidx.compose.animation.core.rememberInfiniteTransition()
    val phase by loop.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1000, easing = androidx.compose.animation.core.LinearEasing)))
    val sweep by loop.animateFloat(-0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1300, easing = Motion.ease)))
    val shown = remember { Animatable(0f) }
    LaunchedEffect(progress) { if (progress != null) shown.animateTo(progress.coerceIn(0f, 1f), tween(250)) }
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        val amp = 3.5.dp.toPx()
        val length = 22.dp.toPx()
        val mid = size.height / 2f
        val left = stroke
        val right = size.width - stroke
        fun wave(from: Float, to: Float): androidx.compose.ui.graphics.Path {
            val path = androidx.compose.ui.graphics.Path()
            var x = from
            val shift = phase * 2f * kotlin.math.PI.toFloat()
            path.moveTo(x, mid + amp * kotlin.math.sin(x / length * 2f * kotlin.math.PI.toFloat() - shift))
            while (x < to) {
                x = minOf(to, x + 1.5f)
                path.lineTo(x, mid + amp * kotlin.math.sin(x / length * 2f * kotlin.math.PI.toFloat() - shift))
            }
            return path
        }
        val style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        if (progress == null) {
            drawLine(track, Offset(left, mid), Offset(right, mid), strokeWidth = stroke, cap = StrokeCap.Round)
            val w = (right - left) * 0.35f
            val start = (left + (right - left) * sweep).coerceIn(left, right)
            val end = (start + w).coerceIn(left, right)
            if (end > start) drawPath(wave(start, end), color, style = style)
        } else {
            val split = left + (right - left) * shown.value
            if (split < right) drawLine(track, Offset(split, mid), Offset(right, mid), strokeWidth = stroke, cap = StrokeCap.Round)
            if (split > left) drawPath(wave(left, split), color, style = style)
        }
    }
}
