package pl.sinusoide.sinuschat.desktop.installer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import pl.sinusoide.sinuschat.desktop.ui.anim.TrimmedPath
import pl.sinusoide.sinuschat.desktop.ui.anim.rememberElapsedMs
import pl.sinusoide.sinuschat.desktop.ui.icons.VectorIcons
import kotlin.math.max
import kotlin.math.min

private val pen = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val fall = CubicBezierEasing(0.55f, 0f, 0.9f, 0.6f)

private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()

private fun DrawScope.glow(ink: Color, alpha: Float) {
    if (alpha <= 0f) return
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f
    drawCircle(
        Brush.radialGradient(0f to ink.copy(alpha = 0.12f), 0.55f to ink.copy(alpha = 0.035f), 1f to Color.Transparent, center = center, radius = radius),
        radius, center, alpha = alpha
    )
}

private inline fun DrawScope.inIcon(content: Rect, extra: Float = 1f, block: DrawScope.() -> Unit) {
    val s = min(size.width, size.height) * 0.6f / max(content.width, content.height) * extra
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s, s, Offset.Zero)
        translate(-content.center.x, -content.center.y)
    }) { block() }
}

private fun pop(ms: Float): Float = 0.86f + 0.14f * Motion.overshoot((ms / 420f).coerceIn(0f, 1f), 2.2f)

private fun appear(ms: Float): Float = (ms / 420f * 2.5f).coerceIn(0f, 1f)

@Composable
fun DocumentDraw(ink: Color, modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs(1500f)
    Spacer(modifier.drawWithCache {
        val lines = Rect(300f, 460f, 660f, 740f)
        val page = Path()
        for (p in VectorIcons.shapes("ic_document")) {
            val b = p.getBounds()
            if (!(b.left >= lines.left && b.right <= lines.right && b.top >= lines.top && b.bottom <= lines.bottom)) page.addPath(p)
        }
        val content = page.getBounds()
        onDrawBehind {
            val t = ms
            glow(ink, Motion.span(t, 700f, 1300f))
            val top = pen.transform(Motion.span(t, 480f, 1000f))
            val bottom = pen.transform(Motion.span(t, 860f, 1380f))
            var shape = page
            if (top > 0f) shape = Path.combine(PathOperation.Difference, shape, Path().apply { addRect(Rect(320f, 480f, 320f + 320f * top, 560f)) })
            if (bottom > 0f) shape = Path.combine(PathOperation.Difference, shape, Path().apply { addRect(Rect(320f, 640f, 320f + 320f * bottom, 720f)) })
            val s = pop(t)
            inIcon(content) {
                withTransform({ scale(s, s, content.center) }) {
                    drawPath(shape, ink.copy(alpha = ink.alpha * appear(t)))
                }
            }
        }
    })
}

private const val MONITOR = "M8,21V19H4q-0.82,0-1.41-0.59T2,17V5Q2,4.17 2.59,3.59T4,3h8V5H4V17H20V14h2v3q0,0.82-0.59,1.41T20,19H16v2H8Z"
private const val ARROW = "M17,14L12,9l1.4-1.4L16,10.17V3h2v7.17L20.6,7.6L22,9l-5,5Z"
private const val CYCLE = 2400f

@Composable
fun InstallDraw(ink: Color, looping: Boolean, modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs()
    Spacer(modifier.drawWithCache {
        val monitor = parse(MONITOR)
        val arrow = parse(ARROW)
        val content = Rect(2f, 3f, 22f, 21f)
        onDrawBehind {
            val t = ms
            glow(ink, Motion.span(t, 700f, 1300f))
            val s = pop(t)
            val local = t - 300f
            val cycle = if (local < 0f) -1f else if (looping) local % CYCLE else min(local, 1000f)
            inIcon(content) {
                withTransform({ scale(s, s, content.center) }) {
                    drawPath(monitor, ink.copy(alpha = ink.alpha * appear(t)))
                }
                if (cycle >= 0f) {
                    val drop = fall.transform(Motion.span(cycle, 0f, 620f))
                    val bounce = if (cycle in 620f..900f) kotlin.math.sin((cycle - 620f) / 280f * Math.PI.toFloat()) * 0.7f else 0f
                    val leave = if (looping) Motion.span(cycle, 1750f, 2150f) else 0f
                    val dy = -12f * (1f - drop) + bounce + 2.5f * leave
                    val alpha = Motion.span(cycle, 0f, 200f) * (1f - leave)
                    if (alpha > 0f) withTransform({ translate(0f, dy) }) {
                        drawPath(arrow, ink.copy(alpha = ink.alpha * alpha))
                    }
                }
            }
        }
    })
}

private val QUESTION_HOOK = Path().apply {
    moveTo(7.84f, 7.5f)
    cubicTo(8.4f, 5.3f, 10f, 4.3f, 12f, 4.3f)
    cubicTo(14.6f, 4.3f, 16.1f, 6f, 16.1f, 8.1f)
    cubicTo(16.1f, 10.2f, 13.6f, 11.3f, 12.7f, 12.5f)
    cubicTo(12.15f, 13.3f, 12.05f, 14.4f, 12.05f, 16.8f)
}

private val QUESTION_DOT = Path().apply {
    moveTo(12f, 18.6f)
    cubicTo(12.8f, 18.6f, 13.4f, 19.2f, 13.4f, 20f)
    cubicTo(13.4f, 20.8f, 12.8f, 21.4f, 12f, 21.4f)
    cubicTo(11.2f, 21.4f, 10.6f, 20.8f, 10.6f, 20f)
    cubicTo(10.6f, 19.2f, 11.2f, 18.6f, 12f, 18.6f)
}

@Composable
fun QuestionDraw(ink: Color, accent: Color, modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs(2200f)
    Spacer(modifier.drawWithCache {
        val shape = VectorIcons.path("ic_question")
        val hook = TrimmedPath(QUESTION_HOOK)
        val dot = TrimmedPath(QUESTION_DOT)
        val hookStroke = Stroke(4.6f, cap = StrokeCap.Round)
        val dotStroke = Stroke(3.2f, cap = StrokeCap.Round)
        val content = Rect(2f, 2f, 22f, 22f)
        onDrawBehind {
            val t = ms
            glow(accent, Motion.span(t, 1300f, 2000f))
            inIcon(content) {
                if (t >= 1700f) {
                    drawPath(shape, ink)
                } else {
                    clipPath(shape) {
                        hook.draw(this, Motion.ease.transform(Motion.span(t, 150f, 1250f)), ink, hookStroke)
                        dot.draw(this, Motion.ease.transform(Motion.span(t, 1300f, 1650f)), ink, dotStroke)
                    }
                }
            }
        }
    })
}

private const val BIN = "M7,21q-0.82,0-1.41-0.59T5,19V6H4V4H9V3h6V4h5V6H19V19q0,0.82-0.59,1.41T17,21H7ZM17,6H7V19H17V6Z"
private const val CROSS = "M9.4,16.5L12,13.9l2.6,2.6L16,15.1l-2.6-2.6L16,9.9L14.6,8.5L12,11.1L9.4,8.5L8,9.9l2.6,2.6L8,15.1l1.4,1.4Z"

@Composable
fun BinDraw(ink: Color, done: Boolean, modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs()
    val doneAt = remember { floatArrayOf(-1f) }
    Spacer(modifier.drawWithCache {
        val bin = parse(BIN)
        val cross = parse(CROSS)
        val arm1 = TrimmedPath(Path().apply { moveTo(8.6f, 9.1f); lineTo(15.4f, 15.9f) })
        val arm2 = TrimmedPath(Path().apply { moveTo(15.4f, 9.1f); lineTo(8.6f, 15.9f) })
        val stroke = Stroke(3f, cap = StrokeCap.Square)
        val content = Rect(4f, 3f, 20f, 21f)
        onDrawBehind {
            val t = ms
            glow(ink, Motion.span(t, 700f, 1300f))
            val s = pop(t)
            inIcon(content) {
                withTransform({ scale(s, s, content.center) }) {
                    drawPath(bin, ink.copy(alpha = ink.alpha * appear(t)))
                }
                if (done && doneAt[0] < 0f) doneAt[0] = t
                val at = doneAt[0]
                if (at >= 0f) {
                    val k1 = pen.transform(Motion.span(t - at, 0f, 450f))
                    val k2 = pen.transform(Motion.span(t - at, 350f, 800f))
                    clipPath(cross) {
                        arm1.draw(this, k1, ink, stroke)
                        arm2.draw(this, k2, ink, stroke)
                    }
                }
            }
        }
    })
}
