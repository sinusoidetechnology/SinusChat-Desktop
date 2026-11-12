package pl.sinusoide.sinuschat.desktop.ui.xp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Path2D

object XpStyle {
    const val BAR = 30
    const val FRAME = 4
    const val RADIUS = 8

    val activeBar = arrayOf(
        0.00f to Color(0xFF0058EE), 0.04f to Color(0xFF3593FF), 0.06f to Color(0xFF288EFF), 0.08f to Color(0xFF127DFF),
        0.10f to Color(0xFF036FFC), 0.14f to Color(0xFF0262EE), 0.20f to Color(0xFF0057E5), 0.24f to Color(0xFF0054E3),
        0.56f to Color(0xFF0055EB), 0.66f to Color(0xFF005BF5), 0.76f to Color(0xFF026AFE), 0.86f to Color(0xFF0062EF),
        0.92f to Color(0xFF0052D6), 0.94f to Color(0xFF0040AB), 1.00f to Color(0xFF003092)
    )
    val inactiveBar = arrayOf(
        0.00f to Color(0xFF7697E7), 0.03f to Color(0xFF7E9EE3), 0.06f to Color(0xFF94AFE8), 0.08f to Color(0xFF97B4E9),
        0.14f to Color(0xFF82A5E4), 0.17f to Color(0xFF7C9FE2), 0.25f to Color(0xFF7996DE), 0.56f to Color(0xFF7B99E1),
        0.81f to Color(0xFF82A9E9), 0.89f to Color(0xFF80A5E7), 0.94f to Color(0xFF7B96E1), 0.97f to Color(0xFF7A93DF),
        1.00f to Color(0xFFABBAE3)
    )
    val frameActive = listOf(Color(0xFF0019CF), Color(0xFF0831D9), Color(0xFF166AEE), Color(0xFF0855DD))
    val frameInactive = listOf(Color(0xFF5B6FB5), Color(0xFF7A93DF), Color(0xFF8CA5E6), Color(0xFF7B96E1))

    val titleFont: FontFamily by lazy { systemFamily("Trebuchet MS") }
    val appFont: FontFamily by lazy { systemFamily("Tahoma") }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun systemFamily(name: String): FontFamily = runCatching {
        FontFamily(
            androidx.compose.ui.text.platform.SystemFont(name, FontWeight.Normal),
            androidx.compose.ui.text.platform.SystemFont(name, FontWeight.Bold)
        )
    }.getOrDefault(FontFamily.SansSerif)
}

private enum class Glyph { MINIMIZE, MAXIMIZE, RESTORE, CLOSE }

@Composable
fun XpCaption(
    title: String,
    icon: Painter,
    active: Boolean,
    maximized: Boolean,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stops = if (active) XpStyle.activeBar else XpStyle.inactiveBar
    Row(
        modifier.fillMaxWidth().height(XpStyle.BAR.dp).background(Brush.verticalGradient(*stops)).padding(start = 6.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            style = TextStyle(
                color = if (active) Color.White else Color(0xFFD8E4F8), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = XpStyle.titleFont, shadow = Shadow(Color(0xFF0A1883), Offset(1f, 1f), 0f)
            )
        )
        CaptionButton(Glyph.MINIMIZE, active, onMinimize)
        Spacer(Modifier.width(2.dp))
        CaptionButton(if (maximized) Glyph.RESTORE else Glyph.MAXIMIZE, active, onMaximize)
        Spacer(Modifier.width(2.dp))
        CaptionButton(Glyph.CLOSE, active, onClose)
    }
}

@Composable
private fun CaptionButton(glyph: Glyph, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val close = glyph == Glyph.CLOSE
    Canvas(Modifier.size(21.dp).clickable(interactionSource = source, indication = null, onClick = onClick)) {
        val u = size.width / 21f
        val colors = if (close) {
            arrayOf(0f to Color(0xFFCC4600), 0.55f to Color(0xFFDC6527), 0.70f to Color(0xFFCD7546), 0.90f to Color(0xFFFFCCB2), 1f to Color.White)
        } else {
            arrayOf(0f to Color(0xFF0054E9), 0.55f to Color(0xFF2263D5), 0.70f to Color(0xFF4479E4), 0.90f to Color(0xFFA3BBEC), 1f to Color.White)
        }
        val corner = CornerRadius(3f * u)
        drawRoundRect(Color.White, cornerRadius = corner)
        drawRoundRect(
            Brush.radialGradient(*colors, center = Offset(size.width * 0.9f, size.height * 0.9f), radius = size.width * 1.25f),
            topLeft = Offset(u, u), size = Size(size.width - 2f * u, size.height - 2f * u), cornerRadius = CornerRadius(2f * u)
        )
        if (pressed) drawRoundRect(Color.Black.copy(alpha = 0.28f), topLeft = Offset(u, u), size = Size(size.width - 2f * u, size.height - 2f * u), cornerRadius = CornerRadius(2f * u))
        else if (hovered) drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(u, u), size = Size(size.width - 2f * u, size.height - 2f * u), cornerRadius = CornerRadius(2f * u))
        glyph(glyph, u, if (close) Color(0xFFDC6527) else Color(0xFF2263D5))
        if (!active) drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = corner)
    }
}

private fun DrawScope.glyph(glyph: Glyph, u: Float, fill: Color) {
    val white = Color.White
    when (glyph) {
        Glyph.MINIMIZE -> drawRect(white, Offset(5f * u, 13f * u), Size(7f * u, 3f * u))
        Glyph.MAXIMIZE -> {
            drawRect(white, Offset(5f * u, 5f * u), Size(11f * u, 11f * u), style = Stroke(u))
            drawRect(white, Offset(5f * u, 5f * u), Size(11f * u, 3f * u))
        }
        Glyph.RESTORE -> {
            drawRect(white, Offset(8f * u, 4.5f * u), Size(8f * u, 7f * u), style = Stroke(u))
            drawRect(white, Offset(8f * u, 4.5f * u), Size(8f * u, 2f * u))
            drawRect(fill, Offset(5f * u, 8.5f * u), Size(8f * u, 7f * u))
            drawRect(white, Offset(5f * u, 8.5f * u), Size(8f * u, 7f * u), style = Stroke(u))
            drawRect(white, Offset(5f * u, 8.5f * u), Size(8f * u, 2f * u))
        }
        Glyph.CLOSE -> {
            drawLine(white, Offset(6f * u, 6f * u), Offset(15f * u, 15f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Square)
            drawLine(white, Offset(15f * u, 6f * u), Offset(6f * u, 15f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Square)
        }
    }
}

@Composable
fun XpFrameBorder(active: Boolean, maximized: Boolean, caption: @Composable () -> Unit, content: @Composable () -> Unit) {
    val colors = if (active) XpStyle.frameActive else XpStyle.frameInactive
    val frame = if (maximized) 0.dp else XpStyle.FRAME.dp
    Column(
        Modifier.fillMaxSize().drawBehind {
            if (maximized) return@drawBehind
            val px = 1.dp.toPx()
            for ((i, c) in colors.withIndex()) {
                val inset = i * px + px / 2f
                drawRect(c, Offset(inset, inset), Size(size.width - 2f * inset, size.height - 2f * inset), style = Stroke(px))
            }
        }
    ) {
        caption()
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = frame, end = frame, bottom = frame)) { content() }
    }
}

internal fun usableBounds(window: java.awt.Window): java.awt.Rectangle {
    val gc = window.graphicsConfiguration
    val b = gc.bounds
    val ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return java.awt.Rectangle(b.x + ins.left, b.y + ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FrameWindowScope.XpWindow(
    title: String,
    icon: Painter,
    state: WindowState,
    active: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    val maximized = state.placement == WindowPlacement.Maximized
    fun toggle() {
        if (state.placement == WindowPlacement.Maximized) {
            state.placement = WindowPlacement.Floating
        } else {
            runCatching { window.maximizedBounds = usableBounds(window) }
            state.placement = WindowPlacement.Maximized
        }
    }
    DisposableEffect(window, maximized) {
        fun reshape() {
            runCatching {
                if (maximized) {
                    window.shape = null
                } else {
                    val w = window.width.toDouble()
                    val h = window.height.toDouble()
                    val r = XpStyle.RADIUS.toDouble()
                    window.shape = Path2D.Double().apply {
                        moveTo(0.0, h)
                        lineTo(0.0, r)
                        quadTo(0.0, 0.0, r, 0.0)
                        lineTo(w - r, 0.0)
                        quadTo(w, 0.0, w, r)
                        lineTo(w, h)
                        closePath()
                    }
                }
            }
        }
        val listener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = reshape()
        }
        window.addComponentListener(listener)
        reshape()
        onDispose {
            window.removeComponentListener(listener)
            runCatching { window.shape = null }
        }
    }
    XpFrameBorder(
        active, maximized,
        caption = {
            WindowDraggableArea(
                Modifier.onPointerEvent(PointerEventType.Press) { if (it.awtEventOrNull?.clickCount == 2) toggle() }
            ) {
                XpCaption(title, icon, active, maximized, { state.isMinimized = true }, ::toggle, onClose)
            }
        },
        content = content
    )
}
