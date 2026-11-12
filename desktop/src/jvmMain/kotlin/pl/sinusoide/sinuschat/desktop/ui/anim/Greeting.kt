package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.random.Random

private const val HOLD_MS = 1500L
private const val FADE_MS = 850L
private const val GAP_MS = 350L

private class GreetingStroke(val trimmed: TrimmedPath, val startMs: Long, val endMs: Long)

private class GreetingWord(val strokes: List<GreetingStroke>, val writeMs: Long, val separate: Boolean) {
    val cycleMs: Long get() = writeMs + HOLD_MS + FADE_MS + GAP_MS
}

private fun penMotion(x: Float): Float = 0.6f * x + 0.4f * x * x * (3f - 2f * x)

private fun shape(a: FloatArray, scale: Float, dx: Float, dy: Float) = Path().apply {
    moveTo(a[0] * scale + dx, a[1] * scale + dy)
    var i = 2
    while (i + 5 < a.size) {
        cubicTo(
            a[i] * scale + dx, a[i + 1] * scale + dy,
            a[i + 2] * scale + dx, a[i + 3] * scale + dy,
            a[i + 4] * scale + dx, a[i + 5] * scale + dy
        )
        i += 6
    }
}

fun greetingOrder(firstLanguage: String, cycle: Long, seed: Long): List<Int> {
    val all = HandwritingData.WORDS.indices
    val head = all.filter { HandwritingData.WORDS[it].lang == firstLanguage }
    return head + all.filter { it !in head }.shuffled(Random(seed + cycle))
}

@Composable
fun Greeting(color: Color, firstLanguage: String, startAfterMs: Long, modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs()
    val seed = remember { Random.nextLong() }
    val first = HandwritingData.WORDS.firstOrNull { it.lang == firstLanguage }?.text ?: ""
    Spacer(modifier.semantics { contentDescription = first }.drawWithCache {
        val w = size.width
        val h = size.height
        val base = h / (HandwritingData.ASCENT + HandwritingData.DESCENT)
        val baseline = HandwritingData.ASCENT * base
        val room = w - HandwritingData.PEN * base * 2
        val words = HandwritingData.WORDS.map { word ->
            val bounds = word.strokes.map { shape(it, 1f, 0f, 0f).getBounds() }.reduce { a, b ->
                Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            }
            val scale = minOf(base, room / bounds.width)
            val dx = (w - bounds.width * scale) / 2f - bounds.left * scale
            val separate = word.strokes.size > 4
            var t = 0L
            val strokes = word.strokes.mapIndexed { i, raw ->
                val trimmed = TrimmedPath(shape(raw, scale, dx, baseline))
                val units = trimmed.length / scale
                val dur = if (separate) (120 + units * 1.2f).toLong()
                else if (i == 0) (450 + units * 0.66f).toLong().coerceIn(1200, 3200)
                else if (units < 8f) 130L else (190 + units * 1.6f).toLong()
                val start = if (i == 0) 0L else t + if (separate) 90 else 170
                t = start + dur
                GreetingStroke(trimmed, start, t)
            }
            GreetingWord(strokes, t, separate)
        }
        val loopMs = words.sumOf { it.cycleMs }
        val pen = Stroke(HandwritingData.PEN * base, cap = StrokeCap.Round, join = StrokeJoin.Round)

        onDrawBehind {
            val elapsed = ms.toLong() - startAfterMs
            if (elapsed < 0) return@onDrawBehind
            val order = greetingOrder(firstLanguage, elapsed / loopMs, seed)
            var t = elapsed % loopMs
            var index = 0
            while (t >= words[order[index]].cycleMs) {
                t -= words[order[index]].cycleMs
                index++
            }
            val word = words[order[index]]
            val fadeFrom = word.writeMs + HOLD_MS
            val a = when {
                t < fadeFrom -> 1f
                t < fadeFrom + FADE_MS -> 1f - Motion.fade.transform((t - fadeFrom) / FADE_MS.toFloat())
                else -> 0f
            }
            if (a <= 0f) return@onDrawBehind
            val ink = color.copy(alpha = color.alpha * a)
            for ((i, s) in word.strokes.withIndex()) {
                if (t <= s.startMs) break
                val raw = ((t - s.startMs) / (s.endMs - s.startMs).toFloat()).coerceIn(0f, 1f)
                val k = if (i == 0 && !word.separate) penMotion(raw) else Motion.fade.transform(raw)
                s.trimmed.draw(this, k, ink, pen)
            }
        }
    })
}
