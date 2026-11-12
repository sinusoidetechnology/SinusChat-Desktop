package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

private const val PHONE =
    "M6,22q-0.82,0-1.41-0.59T4,20V4Q4,3.17 4.59,2.59T6,2h9q0.83,0 1.41,0.59T17,4V7H15V4H6V20h6.03l1.68,2H6Z" +
        "M11.21,6.71Q11.5,6.43 11.5,6T11.21,5.29T10.5,5Q10.08,5 9.79,5.29T9.5,6T9.79,6.71T10.5,7t0.71-0.29Z"
private const val HAND =
    "M15.93,23q-0.65,0-1.22-0.26T13.7,22L8.65,15.95L9.78,14.8q0.45-0.45 1.1-0.56q0.65-0.11 1.23,0.19L15,15.88V7h2" +
        "q1.65,0 2.83,1.17T21,11v8q0,1.65-1.17,2.82T17,23H15.93Z" +
        "M15.93,21H17q0.82,0 1.41-0.57T19,19V11q0-0.83-0.59-1.41T17,9V19.13l-5.3-2.68l3.45,4.2q0.15,0.18 0.35,0.26T15.93,21Z"

private const val CYCLE_MS = 3200
private const val END = 0.86f

private fun phase(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun ease(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

@Composable
fun PhoneTap(color: Color, accent: Color, modifier: Modifier = Modifier) {
    val phone = remember { PathParser().parsePathString(PHONE).toPath() }
    val hand = remember { PathParser().parsePathString(HAND).toPath() }
    val check = remember {
        Path().apply {
            moveTo(8.1f, 12.2f)
            lineTo(9.9f, 14.0f)
            lineTo(13.2f, 10.2f)
        }
    }
    val checkLength = remember { PathMeasure().apply { setPath(check, false) }.length }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(END, tween((CYCLE_MS * END).toInt(), easing = LinearEasing)) }
    Canvas(modifier) {
        val t = progress.value
        val unit = size.minDimension / 24f
        val enter = ease(phase(t, 0f, 0.22f))
        val press = phase(t, 0.30f, 0.38f)
        val release = phase(t, 0.38f, 0.48f)
        val tap = if (t < 0.38f) press else 1f - release
        val bob = 0f
        val alpha = 1f
        scale(unit, unit, Offset.Zero) {
            translate(0f, bob) {
                drawPath(phone, color.copy(alpha = color.alpha * (0.55f + 0.45f * enter) * alpha))
                val drawn = ease(phase(t, 0.44f, 0.66f))
                if (drawn > 0f) {
                    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(checkLength, checkLength), checkLength * (1f - drawn))
                    drawPath(
                        check, accent.copy(alpha = accent.alpha * alpha),
                        style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash)
                    )
                }
            }
            val ripple = phase(t, 0.36f, 0.80f)
            if (ripple > 0f && ripple < 1f) {
                val r = 0.8f + ripple * 4.2f
                drawCircle(accent.copy(alpha = accent.alpha * (1f - ripple) * 0.85f * alpha), r, Offset(16f, 7.2f + bob), style = Stroke(0.55f))
                if (ripple > 0.25f) {
                    val q = (ripple - 0.25f) / 0.75f
                    drawCircle(accent.copy(alpha = accent.alpha * (1f - q) * 0.6f * alpha), 0.8f + q * 3f, Offset(16f, 7.2f + bob), style = Stroke(0.45f))
                }
            }
            val slide = 1f - enter
            translate(5f * slide - 0.6f * tap, 6f * slide - 0.9f * tap + bob * 0.4f) {
                drawPath(hand, color.copy(alpha = color.alpha * enter * alpha))
            }
        }
    }
}
