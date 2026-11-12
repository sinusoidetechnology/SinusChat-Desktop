package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.link.ChatSummary
import kotlin.math.min
import kotlin.math.roundToInt

object AvatarColors {

    val PALETTE = listOf(
        "#5288C1", "#E74C3C", "#2ECC71", "#9B59B6", "#E67E22",
        "#1ABC9C", "#E91E63", "#FF5722", "#607D8B", "#795548"
    )

    fun forHash(hash: String): String {
        val h = Math.abs(hash.hashCode())
        return PALETTE[if (h < 0) 0 else h % PALETTE.size]
    }

    fun initialOf(name: String): String {
        val t = name.trim()
        if (t.isEmpty()) return "?"
        return String(Character.toChars(t.codePointAt(0))).uppercase()
    }

    fun shades(base: Color): Pair<Color, Color> {
        val hsb = java.awt.Color.RGBtoHSB(
            (base.red * 255).roundToInt(), (base.green * 255).roundToInt(), (base.blue * 255).roundToInt(), null
        )
        val light = Color(java.awt.Color.HSBtoRGB(hsb[0], hsb[1], min(1f, hsb[2] * 1.3f)))
        val dark = Color(java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2] * 0.9f))
        return light to dark
    }
}

val ChatSummary.avatarKey: String get() = if (isGroup || peer.isEmpty()) id else peer

@Composable
fun rememberAvatar(store: ChatStore, conv: String, key: String): ImageBitmap? {
    val avatars by store.media.avatars.collectAsState()
    val generation by store.media.generation.collectAsState()
    LaunchedEffect(conv, key, generation) {
        if (key.isNotEmpty()) store.media.requestAvatar(conv, key)
    }
    return avatars[key]
}

internal object LetterFont {
    val typeface: org.jetbrains.skia.Typeface? by lazy {
        runCatching {
            val bytes = LetterFont::class.java.getResourceAsStream("/fonts/Roboto.ttf")?.use { it.readBytes() } ?: return@runCatching null
            org.jetbrains.skia.FontMgr.default.makeFromData(org.jetbrains.skia.Data.makeFromBytes(bytes))
                ?.makeClone(org.jetbrains.skia.FontVariation("wght", 500f))
        }.getOrNull()
    }
}

@Composable
fun LetterAvatar(name: String, colorHex: String?, size: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val letter = remember(name) { AvatarColors.initialOf(name) }
    val (light, dark) = remember(colorHex) { AvatarColors.shades(ChatFormat.color(colorHex)) }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val r = s / 2f
        drawCircle(Brush.verticalGradient(listOf(light, dark), 0f, s), r)
        drawCircle(dark.copy(alpha = 150f / 255f), r - 0.5f, style = Stroke(1f))
        val sw = s * 0.012f
        drawCircle(Color.White.copy(alpha = 40f / 255f), r - sw / 2f - 1f, style = Stroke(sw))
        val face = LetterFont.typeface
        if (face != null) {
            val font = org.jetbrains.skia.Font(face, s * 0.46f)
            val paint = org.jetbrains.skia.Paint().apply {
                color = 0xFFFFFFFF.toInt()
                isAntiAlias = true
                val blur = s * 0.015f
                imageFilter = org.jetbrains.skia.ImageFilter.makeDropShadow(
                    0f, s * 0.008f, blur * 0.57735f + 0.5f, blur * 0.57735f + 0.5f, 0x50000000
                )
            }
            val m = font.metrics
            val width = font.measureTextWidth(letter, paint)
            val y = r - (m.ascent + m.descent) / 2f
            drawIntoCanvas { it.nativeCanvas.drawString(letter, r - width / 2f, y, font, paint) }
        } else {
            val layout = measurer.measure(
                letter,
                TextStyle(
                    color = Color.White,
                    fontSize = (s * 0.46f).toSp(),
                    fontWeight = FontWeight.Medium,
                    shadow = Shadow(Color(0x50000000), Offset(0f, s * 0.008f), s * 0.015f)
                )
            )
            drawText(layout, topLeft = Offset(r - layout.size.width / 2f, r - layout.size.height / 2f))
        }
    }
}

@Composable
fun Avatar(
    name: String,
    colorHex: String?,
    size: Dp,
    image: ImageBitmap? = null,
    online: Boolean = false,
    ring: Color = SinusColors.surface
) {
    Box(Modifier.size(size)) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            LetterAvatar(name, colorHex, size)
        }
        if (online) {
            val dot = size * (15f / 54f)
            Box(
                Modifier.align(Alignment.BottomEnd).size(dot).clip(CircleShape).background(ring)
                    .padding(dot * (2.5f / 15f)).clip(CircleShape).background(SinusColors.online)
            )
        }
    }
}
