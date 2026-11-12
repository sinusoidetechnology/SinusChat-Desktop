package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val SWEEP = 0.55f
private const val JITTER = 0.12f

class DustBurst(
    val image: ImageBitmap,
    val left: Float,
    val top: Float,
    val reverse: Boolean,
    val tag: String?,
    val durationNs: Long,
    val onFinish: () -> Unit,
    density: Float,
    budget: Int
) {
    var startNs = 0L
    var progress = 0f
    val count: Int
    val xs: FloatArray
    val ys: FloatArray
    val colors: Array<Color>
    val vx: FloatArray
    val vy: FloatArray
    val wind: FloatArray
    val lift: FloatArray
    val size: FloatArray
    val delay: FloatArray
    val phase: FloatArray

    init {
        val w = image.width
        val h = image.height
        val pixels = image.toPixelMap()
        val step = max(3, ceil(sqrt(w.toDouble() * h / budget)).toInt())
        val cap = (w / step + 1) * (h / step + 1)
        xs = FloatArray(cap); ys = FloatArray(cap); colors = Array(cap) { Color.Transparent }
        vx = FloatArray(cap); vy = FloatArray(cap); wind = FloatArray(cap); lift = FloatArray(cap)
        size = FloatArray(cap); delay = FloatArray(cap); phase = FloatArray(cap)
        val d = density
        var n = 0
        var j = 0
        while (j < h) {
            var i = 0
            while (i < w) {
                val c = pixels[i, j]
                if (c.alpha >= 10f / 255f && n < cap) {
                    colors[n] = c.copy(alpha = max(c.alpha, 170f / 255f))
                    xs[n] = left + i + step / 2f
                    ys[n] = top + j + step / 2f
                    vx[n] = (-25f + Random.nextFloat() * 80f) * d
                    vy[n] = (-50f + Random.nextFloat() * 60f) * d
                    wind[n] = (40f + Random.nextFloat() * 70f) * d
                    lift[n] = (20f + Random.nextFloat() * 50f) * d
                    size[n] = step * 0.55f * (0.8f + Random.nextFloat() * 0.4f)
                    delay[n] = (i.toFloat() / w) * SWEEP + Random.nextFloat() * JITTER
                    phase[n] = Random.nextFloat() * 6.2832f
                    n++
                }
                i += step
            }
            j += step
        }
        count = n
    }
}

class DustState {
    val bursts = mutableStateListOf<DustBurst>()
    var frame by mutableLongStateOf(0L)

    fun add(
        image: ImageBitmap,
        topLeft: Offset,
        density: Float,
        reverse: Boolean = false,
        tag: String? = null,
        onFinish: () -> Unit = {}
    ) {
        if (image.width <= 0 || image.height <= 0) {
            onFinish()
            return
        }
        val budget = (2200 / (1 + bursts.size)).coerceAtLeast(500)
        val duration = if (reverse) 1_000_000_000L else 1_250_000_000L
        bursts += DustBurst(image, topLeft.x, topLeft.y, reverse, tag, duration, onFinish, density, budget)
    }

    fun cancel(tag: String) {
        bursts.removeAll { it.tag == tag }
    }
}

@Composable
fun DustOverlay(state: DustState, modifier: Modifier = Modifier) {
    val active = state.bursts.isNotEmpty()
    LaunchedEffect(active) {
        while (state.bursts.isNotEmpty()) {
            withFrameNanos { now ->
                val done = ArrayList<DustBurst>()
                for (b in state.bursts) {
                    if (b.startNs == 0L) b.startNs = now
                    b.progress = ((now - b.startNs).toFloat() / b.durationNs).coerceIn(0f, 1f)
                    if (b.progress >= 1f) done += b
                }
                state.frame = now
                if (done.isNotEmpty()) {
                    state.bursts.removeAll(done)
                    done.forEach { it.onFinish() }
                }
            }
        }
    }
    if (!active) return
    val paint = Paint()
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = state.frame
        val wobble = 6f * density
        for (b in state.bursts) {
            val p = if (b.reverse) 1f - b.progress else b.progress
            val w = b.image.width.toFloat()
            val h = b.image.height.toFloat()
            val solidFrom = b.left + (p / SWEEP) * w
            val goneTo = b.left + ((p - JITTER) / SWEEP) * w
            if (goneTo < b.left + w) {
                if (solidFrom <= b.left) {
                    drawImage(b.image, Offset(b.left, b.top))
                } else {
                    val canvas = drawContext.canvas
                    canvas.saveLayer(Rect(b.left, b.top, b.left + w, b.top + h), paint)
                    drawImage(b.image, Offset(b.left, b.top))
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 1f to Color.Black,
                            startX = goneTo, endX = max(solidFrom, goneTo + 1f)
                        ),
                        topLeft = Offset(b.left, b.top), size = Size(w, h), blendMode = BlendMode.DstIn
                    )
                    canvas.restore()
                }
            }
            for (k in 0 until b.count) {
                val start = b.delay[k]
                if (p <= start) continue
                val t = ((p - start) / (1f - start)).coerceIn(0f, 1f)
                if (t >= 1f) continue
                val x = b.xs[k] + b.vx[k] * t + b.wind[k] * t * t
                val y = b.ys[k] + b.vy[k] * t - b.lift[k] * t * t + sin(b.phase[k] + t * 7f) * wobble * t
                val s = b.size[k] * (1f - 0.65f * t)
                val c = b.colors[k]
                drawRect(
                    c.copy(alpha = c.alpha * (1f - t).pow(1.3f)),
                    topLeft = Offset(x - s, y - s), size = Size(s * 2f, s * 2f)
                )
            }
        }
    }
}
