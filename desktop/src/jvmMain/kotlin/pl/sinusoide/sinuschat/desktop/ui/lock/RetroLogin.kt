package pl.sinusoide.sinuschat.desktop.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.appIcon
import pl.sinusoide.sinuschat.desktop.ui.chat.Avatar
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon

data class PinUser(val name: String, val color: String?, val image: ImageBitmap?)

private val band = Color(0xFF00309C)
private val skyLight = Color(0xFF7AA1E6)
private val sky = Color(0xFF5A7EDC)
private val tileBlue = Color(0xFF1E4FC2)
private val frame = Color(0xFF7F9DB9)
private val orange = Color(0xFFF99736)
private val soft = Color(0xFFDCE6F8)

@Composable
internal fun RetroLogin(
    user: PinUser?,
    hint: String,
    placeholder: String,
    pinLength: Int,
    showDots: Boolean,
    message: String,
    error: Boolean,
    shake: Int,
    canSubmit: Boolean,
    active: Boolean,
    showHello: Boolean,
    helloLabel: String,
    onFieldClick: () -> Unit,
    onNext: () -> Unit,
    onHello: () -> Unit
) {
    val icon = remember { appIcon() }
    val name = user?.name?.ifBlank { null } ?: "SinusChat"
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        for (x in listOf(10f, -9f, 7f, -5f, 3f, 0f)) offset.animateTo(x, tween(45))
    }
    val shadow = TextStyle(shadow = Shadow(Color(0x66000000), Offset(2f, 2f), 3f))
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(72.dp).background(band))
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(Brush.horizontalGradient(listOf(sky, Color.White, sky)))
        )
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .background(Brush.linearGradient(listOf(skyLight, sky, sky), start = Offset.Zero, end = Offset(1400f, 900f))),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(330.dp), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(icon, null, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("SinusChat", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, style = shadow)
                    }
                    Text(
                        hint, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.End,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
                Spacer(Modifier.width(30.dp))
                Box(
                    Modifier.width(1.dp).height(280.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color.White, Color(0x00FFFFFF))))
                )
                Spacer(Modifier.width(30.dp))
                Column(Modifier.width(380.dp).graphicsLayer { translationX = offset.value.dp.toPx() }) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(tileBlue, Color(0x335A7EDC))))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(3.dp)).background(Color.White).padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Avatar(name, user?.color, 58.dp, user?.image, ring = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (active) {
                                Text(placeholder, color = soft, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp, bottom = 4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Field(pinLength, showDots, Modifier.weight(1f), onFieldClick)
                                    Spacer(Modifier.width(6.dp))
                                    Arrow(canSubmit, onNext)
                                }
                            }
                        }
                    }
                    Text(
                        message, color = if (error) Color(0xFFFFE8A3) else Color.White, fontSize = 13.5.sp,
                        fontWeight = if (error) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.heightIn(min = 20.dp).padding(top = 10.dp, start = 4.dp)
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color(0x00F99736), orange, Color(0x00F99736))))
        )
        Box(Modifier.fillMaxWidth().height(64.dp).background(band).padding(horizontal = 22.dp)) {
            if (showHello && active) {
                Row(
                    Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(3.dp)).clickable(onClick = onHello)
                        .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF6CC56C), Color(0xFF3A9E3A), Color(0xFF2E8B2E))))
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) { SinusIcon("ic_fingerprint", Color.White, Modifier.size(18.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text(helloLabel, color = Color.White, fontSize = 14.sp)
                }
            }
            Text(
                "SinusChat-Desktop", color = soft, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun Field(length: Int, showDots: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val blink = rememberInfiniteTransition()
    val caret by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse))
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(2.dp)).background(Color.White).border(1.dp, frame, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Text).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (showDots && length > 0) {
            Text("●".repeat(length), color = Color(0xFF10203A), fontSize = 12.sp, letterSpacing = 2.sp, maxLines = 1)
        }
        Box(Modifier.padding(start = 1.dp).width(1.dp).fillMaxHeight(0.6f).graphicsLayer { alpha = if (caret > 0.5f) 1f else 0f }.background(Color(0xFF10203A)))
    }
}

@Composable
private fun Arrow(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).graphicsLayer { alpha = if (enabled) 1f else 0.55f }.clip(RoundedCornerShape(3.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF6CC56C), Color(0xFF3A9E3A), Color(0xFF2E8B2E))))
            .border(1.dp, Color.White, RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon("ic_chevron_right", Color.White, Modifier.size(20.dp))
    }
}
