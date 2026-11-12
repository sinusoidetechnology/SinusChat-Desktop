package pl.sinusoide.sinuschat.desktop.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.UpdateStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.SineBar
import pl.sinusoide.sinuschat.desktop.ui.anim.SineRing
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.desktop.update.DesktopUpdater
import pl.sinusoide.sinuschat.desktop.update.LocalUpdater
import pl.sinusoide.sinuschat.desktop.update.UpdateState
import java.util.Locale

private val pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private fun mb(bytes: Long, locale: Locale): String = String.format(locale, "%.1f MB", bytes / 1024.0 / 1024.0)

private fun speed(bytesPerSecond: Double, locale: Locale): String =
    if (bytesPerSecond >= 1024 * 1024) String.format(locale, "%.1f MB", bytesPerSecond / 1024.0 / 1024.0)
    else String.format(locale, "%.0f KB", bytesPerSecond / 1024.0)

private fun duration(seconds: Long, us: UpdateStrings): String =
    if (seconds < 60) "${seconds.coerceAtLeast(1)} ${us.unitSec}" else "${(seconds + 59) / 60} ${us.unitMin}"

private fun localeOf(us: UpdateStrings): Locale = when (us) {
    UpdateStrings.PL -> Locale.forLanguageTag("pl")
    UpdateStrings.CS -> Locale.forLanguageTag("cs")
    UpdateStrings.UK -> Locale.forLanguageTag("uk")
    UpdateStrings.DE -> Locale.GERMAN
    UpdateStrings.ZH -> Locale.TRADITIONAL_CHINESE
    else -> Locale.ENGLISH
}

@Composable
internal fun DownloadDetails(us: UpdateStrings, s: UpdateState.Downloading) {
    val locale = localeOf(us)
    SineBar(s.progress, Modifier.fillMaxWidth().height(18.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(us.downloadAmount(mb(s.done, locale), mb(s.total, locale)), color = SinusColors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (s.bytesPerSecond > 0.0) Text(us.downloadSpeed(speed(s.bytesPerSecond, locale)), color = SinusColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Text(
        s.secondsLeft?.let { us.timeLeft(duration(it, us)) } ?: us.timeUnknown,
        color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
    )
}

@Composable
fun UpdateIndicator() {
    val updater = LocalUpdater.current ?: return
    val state by updater.state.collectAsState()
    val chat = LocalChatStrings.current
    val us = remember(chat) { UpdateStrings.of(chat) }
    var details by remember { mutableStateOf(false) }
    val visible = state is UpdateState.Downloading || state is UpdateState.Verifying || state is UpdateState.Ready || state is UpdateState.Installing
    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + scaleIn(tween(260), 0.6f), exit = fadeOut(tween(160)) + scaleOut(tween(160), 0.6f)) {
        Box {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .clickable {
                        when (state) {
                            is UpdateState.Ready -> updater.requestRestart()
                            else -> details = !details
                        }
                    }
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center
            ) {
                when (val s = state) {
                    is UpdateState.Downloading -> SineRing(s.progress, Modifier.size(28.dp), color = SinusColors.primary, disc = Color.Transparent)
                    is UpdateState.Ready -> ReadyIcon()
                    else -> SineRing(null, Modifier.size(28.dp), color = SinusColors.primary, disc = Color.Transparent)
                }
            }
            if (details && (state is UpdateState.Downloading || state is UpdateState.Verifying)) {
                Popup(
                    offset = IntOffset(0, 0),
                    alignment = Alignment.TopEnd,
                    onDismissRequest = { details = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Column(
                        Modifier.padding(top = 48.dp, end = 4.dp).width(300.dp)
                            .shadow(12.dp, RoundedCornerShape(SinusColors.radius(16.dp)))
                            .clip(RoundedCornerShape(SinusColors.radius(16.dp))).background(SinusColors.surfaceHigh)
                            .border(1.dp, SinusColors.border, RoundedCornerShape(SinusColors.radius(16.dp)))
                            .padding(16.dp)
                    ) {
                        when (val s = state) {
                            is UpdateState.Downloading -> {
                                Text(us.downloadTitle(s.manifest.version), color = SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                DownloadDetails(us, s)
                            }
                            is UpdateState.Verifying -> {
                                Text(us.downloadTitle(s.manifest.version), color = SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                SineBar(null, Modifier.fillMaxWidth().height(18.dp))
                                Text(us.verifying, color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyIcon() {
    val scale = remember { Animatable(0.4f) }
    val drop = remember { Animatable(-6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(420, easing = pop)) }
    LaunchedEffect(Unit) { drop.animateTo(0f, tween(520, easing = pop)) }
    Box(
        Modifier.size(34.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(CircleShape)
            .background(SinusColors.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon("ic_ready_to_update", SinusColors.primary, Modifier.size(22.dp).graphicsLayer { translationY = drop.value })
    }
}

@Composable
fun RestartDialog(updater: DesktopUpdater, onExit: () -> Unit) {
    val ask by updater.askRestart.collectAsState()
    val state by updater.state.collectAsState()
    val ready = state as? UpdateState.Ready
    val chat = LocalChatStrings.current
    val us = remember(chat) { UpdateStrings.of(chat) }
    AnimatedVisibility(ask && ready != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        val shown = ready ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { updater.dismissRestart() },
            contentAlignment = Alignment.Center
        ) {
            val scale = remember { Animatable(0.92f) }
            LaunchedEffect(Unit) { scale.animateTo(1f, tween(260, easing = pop)) }
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(RoundedCornerShape(SinusColors.radius(22.dp))).background(SinusColors.surfaceHigh)
                    .border(1.dp, SinusColors.border, RoundedCornerShape(SinusColors.radius(22.dp)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(SinusColors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    SinusIcon("ic_ready_to_update", SinusColors.primary, Modifier.size(32.dp))
                }
                Text(us.readyTitle, color = SinusColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                Text(
                    us.readyDesc(shown.manifest.version), color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    us.readyNote, color = SinusColors.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogButton(us.later, primary = false, Modifier.weight(1f)) { updater.dismissRestart() }
                    DialogButton(us.restart, primary = true, Modifier.weight(1f)) { updater.apply(onExit) }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Box(
        modifier.clip(shape).background(if (primary) SinusColors.primary else Color.Transparent)
            .then(if (primary) Modifier else Modifier.border(1.dp, SinusColors.border, shape))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) Color.White else SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}
