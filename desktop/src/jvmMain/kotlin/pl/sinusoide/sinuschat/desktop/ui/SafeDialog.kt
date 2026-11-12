package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.i18n.FileStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.SafeStrings
import pl.sinusoide.sinuschat.desktop.security.SafeEngine
import pl.sinusoide.sinuschat.desktop.ui.anim.rememberLast
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon

private val decelerate16 = Easing { t -> 1f - Math.pow((1f - t).toDouble(), 3.2).toFloat() }

@Composable
fun SafeEngineDialog() {
    val ask by SafeEngine.pending.collectAsState()
    val last = rememberLast(ask)
    AnimatedVisibility(visible = ask != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        last?.let { SafeCard(it) }
    }
}

@Composable
private fun SafeCard(ask: SafeEngine.Ask) {
    val chat = LocalChatStrings.current
    val ss = SafeStrings.of(chat)
    val fs = FileStrings.of(chat)
    val danger = ask.risk == SafeEngine.Risk.DANGEROUS_FILE
    val accent = if (danger) SinusColors.danger else SinusColors.primary
    val subject = ask.subject.ifBlank { ss.thisFile }
    val title = when (ask.risk) {
        SafeEngine.Risk.OPEN_LINK -> ss.linkTitle(subject)
        SafeEngine.Risk.OPEN_FILE -> ss.openFileTitle(subject)
        SafeEngine.Risk.SAVE_FILE -> ss.saveFileTitle(subject)
        SafeEngine.Risk.DANGEROUS_FILE -> fs.riskyTitle(subject)
    }
    val message = when (ask.risk) {
        SafeEngine.Risk.OPEN_LINK -> ss.linkMsg
        SafeEngine.Risk.OPEN_FILE -> ss.openFileMsg
        SafeEngine.Risk.SAVE_FILE -> ss.saveFileMsg
        SafeEngine.Risk.DANGEROUS_FILE -> if (ask.onSandbox != null) fs.riskyDesc + "\n\n" + fs.riskySandboxHint else fs.riskyDesc
    }
    val action = when (ask.risk) {
        SafeEngine.Risk.OPEN_LINK -> ss.linkAction
        SafeEngine.Risk.OPEN_FILE -> ss.openFileAction
        SafeEngine.Risk.SAVE_FILE -> ss.saveFileAction
        SafeEngine.Risk.DANGEROUS_FILE -> fs.saveAnyway
    }
    var why by remember(ask) { mutableStateOf(false) }
    val k = remember(ask) { Animatable(0f) }
    LaunchedEffect(ask) { k.animateTo(1f, tween(220, easing = decelerate16)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(ask) { runCatching { focus.requestFocus() } }

    val primary = SinusColors.textPrimary
    val neutral = primary.copy(alpha = 0x1F / 255f)
    val sheet = if (SinusColors.palette.backdrop) Color(0xFF16202C) else SinusColors.card
    val shape = RoundedCornerShape(SinusColors.radius(24.dp))
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, null) { SafeEngine.cancel(ask) }
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    SafeEngine.cancel(ask)
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val sc = 0.92f + 0.08f * k.value
                    scaleX = sc
                    scaleY = sc
                    alpha = k.value
                }
                .clip(shape)
                .background(sheet)
                .border(1.dp, SinusColors.border, shape)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth()) {
                val tileShape = RoundedCornerShape(SinusColors.radius(18.dp))
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(74.dp)
                        .shadow(4.dp, tileShape)
                        .clip(tileShape)
                        .background(Brush.verticalGradient(listOf(lighten(accent), accent))),
                    contentAlignment = Alignment.Center
                ) {
                    SinusIcon(if (danger) "ic_danger" else "ic_privacy_warning", Color.White, Modifier.size(42.dp))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).size(30.dp).clip(CircleShape)
                        .background(if (why) accent else neutral)
                        .clickable { why = !why }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = if (why) Color.White else primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                if (why) ss.whyTitle else title,
                color = primary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 22.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                if (why) ss.whyMsg else message,
                color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(22.dp))
            if (ask.onSandbox != null) {
                SafeButton(fs.openSandbox, SinusColors.primary, Color.White, Modifier.fillMaxWidth()) { SafeEngine.sandbox(ask) }
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                SafeButton(chat.cancel, neutral, primary, Modifier.weight(1f)) { SafeEngine.cancel(ask) }
                Spacer(Modifier.width(10.dp))
                SafeButton(
                    action,
                    if (danger) neutral else accent,
                    if (danger) SinusColors.danger else Color.White,
                    Modifier.weight(1f)
                ) { SafeEngine.proceed(ask) }
            }
        }
    }
}

@Composable
private fun SafeButton(label: String, fill: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(fill)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun lighten(color: Color): Color {
    val hsb = java.awt.Color.RGBtoHSB((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), null)
    val rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1] * 0.75f, minOf(1f, hsb[2] * 1.2f))
    return Color(rgb or (0xFF shl 24))
}
