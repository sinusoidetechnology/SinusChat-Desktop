package pl.sinusoide.sinuschat.desktop.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.UpdateStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.SineBar
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.desktop.update.DesktopUpdater
import pl.sinusoide.sinuschat.desktop.update.UpdateManifest
import pl.sinusoide.sinuschat.desktop.update.UpdateState

private val spinEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
fun UpdateOverlay(updater: DesktopUpdater, onExit: () -> Unit) {
    val open by updater.dialog.collectAsState()
    AnimatedVisibility(open, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        UpdateScreen(updater, onExit)
    }
}

@Composable
private fun UpdateScreen(updater: DesktopUpdater, onExit: () -> Unit) {
    val chat = LocalChatStrings.current
    val us = remember(chat) { UpdateStrings.of(chat) }
    val state by updater.state.collectAsState()
    val uri = LocalUriHandler.current
    val current = updater.version
    Box(
        Modifier.fillMaxSize().background(SinusColors.backgroundSolid)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        if (state !is UpdateState.Installing) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(44.dp).clip(CircleShape)
                    .clickable { updater.close() }.pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center
            ) { SinusIcon("ic_close", SinusColors.textPrimary, Modifier.size(24.dp)) }
        }
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                UpdateState.Idle, UpdateState.Checking, UpdateState.Disabled -> {
                    CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                    Text(us.checking, color = SinusColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                }
                UpdateState.Unknown -> {
                    Hero("ic_warning", SinusColors.textSecondary, false, us.unknownTitle, us.unknownDesc)
                    Button(us.retry, primary = false, top = 28) { updater.check(force = true) }
                }
                is UpdateState.Latest -> {
                    Hero("ic_check", SinusColors.primary, false, us.latestTitle, us.latestDesc(current))
                    Button(us.ok, primary = true, top = 28) { updater.close() }
                }
                is UpdateState.Available -> {
                    Hero("ic_update", SinusColors.primary, true, us.title, us.desc)
                    Compare(us, current, s.manifest.version)
                    WhatsNew(us, s.manifest) { runCatching { uri.openUri(it) } }
                    if (s.pkg == null) {
                        Note(us.noPackage)
                        Button(us.ok, primary = true, top = 24) { updater.later() }
                    } else {
                        Button(us.update, primary = true, top = 24) { updater.install(onExit) }
                        Button(us.later, primary = false, top = 6) { updater.later() }
                    }
                }
                is UpdateState.Downloading -> {
                    Hero("ic_update", SinusColors.primary, true, us.title, us.desc, spinning = true)
                    Compare(us, current, s.manifest.version)
                    Spacer(Modifier.height(24.dp))
                    DownloadDetails(us, s)
                }
                is UpdateState.Verifying -> {
                    Hero("ic_update", SinusColors.primary, true, us.title, us.desc, spinning = true)
                    Compare(us, current, s.manifest.version)
                    Spacer(Modifier.height(24.dp))
                    SineBar(null, Modifier.fillMaxWidth().height(18.dp))
                    Text(us.verifying, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                }
                is UpdateState.Ready -> {
                    Hero("ic_ready_to_update", SinusColors.primary, false, us.readyTitle, us.readyDesc(s.manifest.version))
                    Note(us.readyNote)
                    Button(us.restart, primary = true, top = 24) { updater.apply(onExit) }
                    Button(us.later, primary = false, top = 6) { updater.close() }
                }
                is UpdateState.Installing -> {
                    Hero("ic_update", SinusColors.primary, true, us.title, if (s.restarting) us.installing else us.opened, spinning = s.restarting)
                    if (!s.restarting) Button(us.ok, primary = true, top = 28) { updater.close() }
                }
                is UpdateState.Failed -> {
                    val text = when (s.reason) {
                        UpdateState.Reason.NETWORK -> us.failed
                        UpdateState.Reason.REJECTED -> us.rejected
                        UpdateState.Reason.INSTALL -> us.installFailed
                        UpdateState.Reason.DEV_BUILD -> us.devBuild
                    }
                    Hero("ic_warning", SinusColors.danger, false, us.title, text)
                    Compare(us, current, s.manifest.version)
                    if (s.reason == UpdateState.Reason.NETWORK || s.reason == UpdateState.Reason.REJECTED) {
                        Button(us.retry, primary = true, top = 24) { updater.install(onExit) }
                        Button(us.later, primary = false, top = 6) { updater.later() }
                    } else {
                        Button(us.ok, primary = true, top = 24) { updater.close() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(icon: String, color: Color, spin: Boolean, title: String, desc: String, spinning: Boolean = false) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(420, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))) }
    LaunchedEffect(spin) {
        if (!spin) return@LaunchedEffect
        kotlinx.coroutines.delay(260)
        rotation.snapTo(0f)
        rotation.animateTo(360f, tween(1100, easing = spinEase))
    }
    val loop = rememberInfiniteTransition()
    val endless by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart))
    Box(
        Modifier.size(104.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape).background(color.copy(alpha = 0x26 / 255f))
            .then(
                if (spin && !spinning) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch {
                        rotation.snapTo(0f)
                        rotation.animateTo(360f, tween(1100, easing = spinEase))
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon(icon, color, Modifier.size(52.dp).graphicsLayer { rotationZ = if (spinning) endless else rotation.value })
    }
    Text(
        title, color = SinusColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 22.dp)
    )
    Text(
        desc, color = SinusColors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun Compare(us: UpdateStrings, current: String, latest: String) {
    Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Chip(us.yours, current, false)
        SinusIcon("ic_chevron_right", SinusColors.textSecondary, Modifier.padding(horizontal = 10.dp).size(28.dp))
        Chip(us.newVersion, latest, true)
    }
}

@Composable
private fun Chip(label: String, version: String, highlight: Boolean) {
    val shape = RoundedCornerShape(SinusColors.radius(16.dp))
    Column(
        Modifier.clip(shape)
            .background(if (highlight) SinusColors.primary.copy(alpha = 0x2E / 255f) else SinusColors.card)
            .then(if (highlight) Modifier.border(1.5.dp, SinusColors.primary.copy(alpha = 0x99 / 255f), shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = SinusColors.textSecondary, fontSize = 12.sp)
        Text(version, color = if (highlight) SinusColors.primary else SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WhatsNew(us: UpdateStrings, manifest: UpdateManifest, open: (String) -> Unit) {
    val link = manifest.changelog?.takeIf { it.startsWith("https://") } ?: return
    Text(
        us.whatsNew(manifest.version), color = SinusColors.primary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(12.dp)).clickable { open(link) }
            .pointerHoverIcon(PointerIcon.Hand).padding(10.dp)
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 18.dp)
    )
}

@Composable
private fun Progress(value: Float?, label: String) {
    Spacer(Modifier.height(28.dp))
    if (value == null) {
        LinearProgressIndicator(
            color = SinusColors.primary, trackColor = SinusColors.card, strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    } else {
        LinearProgressIndicator(
            progress = { value }, color = SinusColors.primary, trackColor = SinusColors.card, strokeCap = StrokeCap.Round,
            gapSize = 0.dp, drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
    Text(label, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Button(label: String, primary: Boolean, top: Int, onClick: () -> Unit) {
    Text(
        label, color = if (primary) Color.White else SinusColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = top.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(26.dp)))
            .background(if (primary) SinusColors.primary else Color.Transparent)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 20.dp, vertical = 15.dp)
    )
}
