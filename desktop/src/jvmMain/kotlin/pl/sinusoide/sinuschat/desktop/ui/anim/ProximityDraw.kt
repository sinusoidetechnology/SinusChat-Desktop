package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import pl.sinusoide.sinuschat.desktop.ui.icons.VectorIcons
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

object ProximityDrawings {

    private fun poly(vararg xy: Float) = Path().apply {
        moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
    }

    fun bluetooth(): IconDrawing = IconDrawing(
        Rect(2f, 2f, 22f, 22f),
        listOf(
            DrawPart(poly(5.7f, 5.7f, 12.4f, 12.4f, 16.4f, 16.3f, 11.6f, 21.4f, 11.5f, 12.2f), 2.4f, 120f, 1500f),
            DrawPart(poly(5.7f, 18.3f, 12.4f, 11.6f, 16.4f, 7.7f, 11.6f, 2.6f, 11.5f, 11.8f), 2.4f, 120f, 1500f)
        ),
        VectorIcons.path("ic_bluetooth"),
        clip = true
    )

    fun nearby(): IconDrawing = IconDrawing(
        Rect(2f, 2f, 22f, 22f),
        listOf(
            DrawPart(Path().apply { moveTo(12f, 2.6f); lineTo(21.4f, 12f); lineTo(12f, 21.4f); lineTo(2.6f, 12f); close() }, 2.8f, 120f, 1150f),
            DrawPart(poly(7.2f, 12f, 16.8f, 12f), 9.4f, 1100f, 1500f, StrokeCap.Butt)
        ),
        VectorIcons.path("ic_nearby"),
        clip = true
    )

    fun connected(): IconDrawing = IconDrawing(
        Rect(1f, 3f, 23f, 21f),
        listOf(
            DrawPart(poly(12.2f, 16f, 4f, 16f, 4f, 5f, 20f, 5f, 20f, 7.2f), 2.6f, 120f, 1000f),
            DrawPart(poly(1.6f, 19f, 12.4f, 19f), 2.6f, 900f, 1200f),
            DrawPart(poly(15f, 10f, 15f, 19f, 21f, 19f, 21f, 9f, 15f, 9f, 15f, 10.5f), 2.6f, 1150f, 1850f),
            DrawPart(poly(18f, 11.75f, 18f, 11.8f), 2.4f, 1850f, 2000f)
        ),
        VectorIcons.path("ic_connected"),
        clip = true
    )

    fun distance(): IconDrawing = IconDrawing(
        Rect(1f, 6f, 23f, 18f),
        listOf(
            DrawPart(poly(12f, 12f, 4.6f, 12f), 2.6f, 120f, 620f, StrokeCap.Butt),
            DrawPart(poly(12f, 12f, 19.4f, 12f), 2.6f, 120f, 620f, StrokeCap.Butt),
            DrawPart(poly(7.7f, 7.6f, 3.3f, 12f, 7.7f, 16.4f), 2.8f, 560f, 1060f),
            DrawPart(poly(16.3f, 7.6f, 20.7f, 12f, 16.3f, 16.4f), 2.8f, 560f, 1060f)
        ),
        VectorIcons.path("ic_distance"),
        clip = true
    )
}

@Composable
private fun rememberSeconds(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    return t
}

private fun DrawScope.icon(name: String, center: Offset, size: Float, color: Color) {
    val vector = VectorIcons.vector(name)
    val viewport = vector.viewportWidth
    val path = VectorIcons.path(name)
    val s = size / viewport
    withTransform({
        translate(center.x - size / 2f, center.y - size / 2f)
        scale(s, s, Offset.Zero)
    }) {
        drawPath(path, color)
    }
}

@Composable
fun DistanceWave(strength: Float, locked: Boolean, ink: Color, accent: Color, modifier: Modifier = Modifier) {
    val t = rememberSeconds()
    val state = remember { floatArrayOf(1f, 0f, -1f) }
    Canvas(modifier) {
        val dt = if (state[1] == 0f) 0f else (t - state[1]).coerceIn(0f, 0.1f)
        state[1] = t
        state[0] += (strength.coerceIn(0f, 1f) - state[0]) * (1f - exp(-dt * 3f))
        if (locked && state[2] < 0f) state[2] = t
        if (!locked) state[2] = -1f
        val shown = state[0]
        val lockK = if (state[2] >= 0f) ((t - state[2]) / 0.45f).coerceIn(0f, 1f) else 0f
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val iconSize = min(h * 0.34f, w * 0.16f)
        val left = w * 0.14f
        val far = 1f - shown
        val right = w * (0.62f + 0.24f * far)

        val pulse = (t % 1.6f) / 1.6f
        drawCircle(
            accent.copy(alpha = 0.43f * (1f - pulse) * (1f - lockK)), iconSize * (0.7f + 0.9f * pulse), Offset(left, cy),
            style = Stroke(iconSize * 0.05f)
        )
        val x0 = left + iconSize * 0.75f
        val x1 = right - iconSize * 0.6f
        if (x1 > x0 && lockK < 1f) {
            val amp = h * (0.05f + 0.13f * shown)
            val waves = 2.5f + 2.5f * far
            val path = Path()
            val steps = 80
            for (i in 0..steps) {
                val k = i / steps.toFloat()
                val x = x0 + (x1 - x0) * k
                val env = sin(PI * k).toFloat()
                val y = cy + amp * env * sin(2f * PI.toFloat() * (waves * k - t * 0.9f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val dash = iconSize * 0.18f
            drawPath(
                path, accent.copy(alpha = (0.25f + 0.75f * shown) * (1f - lockK)),
                style = Stroke(
                    iconSize * (0.06f + 0.06f * shown), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (shown < 0.35f) PathEffect.dashPathEffect(floatArrayOf(dash, dash * (1.5f - shown * 2f)), t * 40f) else null
                )
            )
        }
        icon("ic_pc", Offset(left, cy), iconSize, ink)
        icon("ic_new_phone", Offset(right, cy), iconSize * 0.9f, ink.copy(alpha = 1f - 0.4f * lockK))
        if (lockK > 0f) icon("ic_lock", Offset((left + right) / 2f, cy), iconSize * (0.4f + 0.6f * lockK), accent.copy(alpha = lockK))
    }
}

@Composable
fun ConnectedHop(ink: Color, accent: Color, modifier: Modifier = Modifier) {
    val t = rememberSeconds()
    val icon = remember { VectorIcons.path("ic_connected") }
    Canvas(modifier) {
        val ms = t * 1000f
        val content = Rect(1f, 3f, 23f, 21f)
        val s = min(size.width, size.height) * 0.6f / content.width
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        val glowK = Motion.span(ms, 500f, 1100f)
        if (glowK > 0f) {
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to accent.copy(alpha = 0.12f), 0.55f to accent.copy(alpha = 0.035f), 1f to Color.Transparent,
                    center = center, radius = radius
                ),
                radius, center, alpha = glowK
            )
        }
        val monitorK = Motion.ease.transform(Motion.span(ms, 0f, 450f))
        val drop = Motion.span(ms, 250f, 900f)
        val landing = if (drop < 1f) (1f - Motion.overshoot(drop, 2.2f)) * -7f else 0f
        val cycle = 1600f
        val since = ms - 1300f
        val hop = if (since > 0f) {
            val k = (since % cycle) / 450f
            if (k < 1f) -2.4f * sin(PI.toFloat() * k) else 0f
        } else 0f
        val phone = Rect(13.4f, 7.4f, 23f, 21f)
        withTransform({
            translate(center.x, center.y)
            scale(s, s, Offset.Zero)
            translate(-content.center.x, -content.center.y)
        }) {
            withTransform({ scale(0.9f + 0.1f * monitorK, 0.9f + 0.1f * monitorK, Offset(8f, 12f)) }) {
                clipRect(phone.left, phone.top, phone.right, phone.bottom, androidx.compose.ui.graphics.ClipOp.Difference) {
                    drawPath(icon, ink.copy(alpha = ink.alpha * monitorK))
                }
            }
            if (drop > 0f) {
                withTransform({ translate(0f, landing + hop) }) {
                    clipRect(phone.left, phone.top, phone.right, phone.bottom) {
                        drawPath(icon, ink.copy(alpha = ink.alpha * Motion.span(ms, 250f, 450f)))
                    }
                }
            }
        }
    }
}
