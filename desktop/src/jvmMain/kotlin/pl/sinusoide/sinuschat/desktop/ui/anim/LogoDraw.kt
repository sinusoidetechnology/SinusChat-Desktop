package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min

object LogoDraw {
    val CYAN = Color(0xFF2AE0F5)
    const val DRAW_MS = 1900L
    const val TOTAL_MS = 2200f

    private const val BUBBLE =
        "M 49 21 H 206 C 222.0 21 235 34.0 235 50 V 163 C 235 180 223.0 192 207 192 H 91 L 49 233.5 C 42 240.5 31 239.0 25 232 " +
            "C 22.5 229.2 21 226 21 222 V 49 C 21 33.5 33.5 21 49 21 Z M 51 43 H 203 C 208.5 43 213 47.5 213 53 V 161 " +
            "C 213 166.5 208.5 170.5 203 170.5 H 88.5 C 82.5 170.5 78.3 173.2 74 177.3 L 43 208.5 V 51 C 43 46.5 46.5 43 51 43 Z"

    private const val WAVE =
        "M 45,382 C 74.15,473.20 97.69,560.80 133.94,649.29 C 142.08,669.16 150.86,689.08 165.21,705.04 C 191.26,733.99 235.64,749.91 270.50,736.76 " +
            "C 298.08,726.35 319.68,697.74 334.43,669.84 C 343.69,652.31 350.24,635.05 356.59,617.53 C 389.11,527.70 416.10,430.87 444.67,338.82 " +
            "C 456.83,299.65 469.27,261.34 488.82,222.24 C 493.09,213.68 497.71,205.08 503.43,197.29 C 516.34,179.74 534.85,166.31 551.17,167.79 " +
            "C 572.10,169.67 589.40,196.07 602.49,221.49 C 624.24,263.73 634.34,303.26 646.04,344.72 C 660.22,394.99 676.74,448.09 691.37,498.69 " +
            "C 704.46,543.98 716.05,587.27 734.28,632.67 C 752.54,678.12 777.46,725.67 822.93,738.40 C 840.88,743.42 862.03,743.02 879.85,735.73 " +
            "C 898.06,728.28 912.80,713.63 924.27,697.58 C 936.06,681.09 944.42,663.14 952.08,644.99 C 969.22,604.42 982.89,562.92 996.40,521.20 " +
            "C 1025.24,432.18 1053.37,342.18 1084.60,254.35 L 1030,255 C 1000.22,337.30 974.61,423.64 947.11,507.12 C 934.43,545.60 921.35,583.47 906.07,621.95 " +
            "C 902.88,629.98 899.60,638.04 895.46,646.03 C 888.07,660.29 877.96,674.33 864.83,681.30 C 853.37,687.40 839.59,688.10 828.87,682.90 " +
            "C 816.14,676.73 807.71,662.25 800.08,647.66 C 757.53,566.36 739.88,481.96 716.25,396.93 C 703.54,351.19 689.10,305.27 673.08,261.12 " +
            "C 666.62,243.32 659.90,225.80 652.89,208.21 C 646.33,191.74 639.52,175.20 628.68,160.22 C 607.27,130.64 570.17,107.14 536.92,110.95 " +
            "C 507.11,114.36 480.39,139.75 461.16,166.86 C 439.16,197.88 426.95,231.17 415.29,265.06 C 385.50,351.75 359.37,442.46 332.30,530.09 " +
            "C 320.72,567.55 308.98,604.45 290.51,641.71 C 285.60,651.61 280.22,661.55 272.84,669.10 C 260.02,682.21 241.20,688.13 226.14,682.99 " +
            "C 206.99,676.45 193.92,652.03 183.40,629.12 C 165.06,589.20 154.44,553.88 142.24,515.72 C 128.28,472.02 112.24,424.59 101.26,382.95 L 45,382 Z"

    internal val bubbleShape: Path by lazy {
        PathParser().parsePathString(BUBBLE).toPath().apply { fillType = PathFillType.EvenOdd }
    }

    internal val waveShape: Path by lazy {
        PathParser().parsePathString(WAVE).toPath().apply {
            transform(Matrix().apply {
                translate(39.89f, 44.46f)
                scale(0.158f, 0.158f)
            })
        }
    }

    internal val bubbleLine: Path by lazy {
        Path().apply {
            moveTo(32f, 224f)
            lineTo(32f, 50f)
            cubicTo(32f, 39f, 39f, 32f, 50f, 32f)
            lineTo(205f, 32f)
            cubicTo(216f, 32f, 224f, 40f, 224f, 51f)
            lineTo(224f, 162f)
            cubicTo(224f, 174f, 215f, 181.25f, 205f, 181.25f)
            lineTo(90f, 181.25f)
            cubicTo(84f, 181.25f, 80f, 184f, 76f, 188f)
            lineTo(34f, 229f)
        }
    }

    private val wavePoints = listOf(
        73f to 382f, 118f to 520f, 155f to 625f, 190f to 690f, 240f to 712f, 285f to 690f, 320f to 640f,
        360f to 540f, 410f to 390f, 455f to 260f, 500f to 175f, 545f to 140f, 595f to 170f, 640f to 260f,
        680f to 390f, 725f to 560f, 765f to 660f, 815f to 708f, 855f to 712f, 895f to 690f, 930f to 630f,
        965f to 530f, 1010f to 390f, 1057f to 255f
    )

    internal val waveLine: Path by lazy {
        val pts = wavePoints.map { (x, y) -> Offset(39.89f + x * 0.158f, 44.46f + y * 0.158f) }
        Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[maxOf(i - 1, 0)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[minOf(i + 2, pts.size - 1)]
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y
                )
            }
        }
    }

    internal val content = Rect(21f, 21f, 235f, 240f)
}

@Composable
fun LogoDraw(modifier: Modifier = Modifier) {
    val ms by rememberElapsedMs(LogoDraw.TOTAL_MS)
    Spacer(modifier.drawWithCache {
        val c = LogoDraw.content
        val discRadius = min(size.width, size.height) * 0.4f
        val scale = discRadius * 2f * 0.62f / maxOf(c.width, c.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val bubble = TrimmedPath(LogoDraw.bubbleLine)
        val wave = TrimmedPath(LogoDraw.waveLine)
        val bubbleStroke = Stroke(26f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val waveStroke = Stroke(13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val glowRadius = discRadius * 1.25f
        val glow = Brush.radialGradient(
            0f to LogoDraw.CYAN.copy(alpha = 0.22f),
            0.74f to LogoDraw.CYAN.copy(alpha = 0.22f),
            0.86f to LogoDraw.CYAN.copy(alpha = 0.07f),
            1f to Color.Transparent,
            center = center,
            radius = glowRadius
        )

        onDrawBehind {
            val t = ms
            val g = Motion.span(t, 1500f, 2200f)
            if (g > 0f) drawCircle(glow, glowRadius, center, alpha = g)
            val d = Motion.pop.transform(Motion.span(t, 0f, 520f))
            if (d > 0f) drawCircle(Color.Black, discRadius * (0.84f + 0.16f * d), center, alpha = min(1f, d * 1.4f))
            withTransform({
                translate(center.x, center.y)
                scale(scale, scale, Offset.Zero)
                translate(-c.center.x, -c.center.y)
            }) {
                val kb = Motion.ease.transform(Motion.span(t, 200f, 1300f))
                val kw = Motion.ease.transform(Motion.span(t, 1050f, 1850f))
                if (kb >= 1f) drawPath(LogoDraw.bubbleShape, LogoDraw.CYAN)
                else clipPath(LogoDraw.bubbleShape) { bubble.draw(this, kb, LogoDraw.CYAN, bubbleStroke) }
                if (kw >= 1f) drawPath(LogoDraw.waveShape, LogoDraw.CYAN)
                else clipPath(LogoDraw.waveShape) { wave.draw(this, kw, LogoDraw.CYAN, waveStroke) }
            }
        }
    })
}
