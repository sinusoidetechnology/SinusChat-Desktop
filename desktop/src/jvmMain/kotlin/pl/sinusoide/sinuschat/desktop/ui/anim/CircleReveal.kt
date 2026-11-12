package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

class RevealOrigin(val center: Offset, val startRadius: Float)

fun Modifier.circleReveal(origin: RevealOrigin?, progress: () -> Float): Modifier =
    if (origin == null) this
    else drawWithContent {
        val k = progress()
        if (k >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val c = origin.center
        val far = maxOf(
            c.distanceTo(Offset.Zero),
            c.distanceTo(Offset(size.width, 0f)),
            c.distanceTo(Offset(0f, size.height)),
            c.distanceTo(Offset(size.width, size.height))
        )
        val r = origin.startRadius + (far - origin.startRadius) * k
        val clip = Path().apply { addOval(Rect(c, r)) }
        clipPath(clip) { this@drawWithContent.drawContent() }
    }
