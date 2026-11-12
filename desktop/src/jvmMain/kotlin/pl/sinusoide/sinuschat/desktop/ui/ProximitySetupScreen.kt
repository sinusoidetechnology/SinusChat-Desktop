package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.ProximitySetupStrings
import pl.sinusoide.sinuschat.desktop.security.ProximitySetupFlow
import pl.sinusoide.sinuschat.desktop.security.ProximitySetupFlow.Step
import pl.sinusoide.sinuschat.desktop.security.ProximityStatus
import pl.sinusoide.sinuschat.desktop.ui.anim.ConnectedHop
import pl.sinusoide.sinuschat.desktop.ui.anim.DistanceWave
import pl.sinusoide.sinuschat.desktop.ui.anim.IconDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.LogoDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.PhoneTap
import pl.sinusoide.sinuschat.desktop.ui.anim.ProximityDrawings
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon

@Composable
fun ProximitySetupScreen(flow: ProximitySetupFlow, link: PhoneLink, language: AppLanguage) {
    val s = remember(language) { ProximitySetupStrings.of(language.chat) }
    val step by flow.step.collectAsState()
    val stale by flow.stale.collectAsState()
    val state by link.state.collectAsState()
    val reading by flow.guard.reading.collectAsState()
    val current = step ?: return
    val connected = state is LinkState.Connected
    var offline by remember { mutableStateOf(!connected) }
    LaunchedEffect(connected) {
        if (connected) offline = false
        else {
            delay(OFFLINE_GRACE_MS)
            offline = true
        }
    }
    val ink = SinusColors.textPrimary
    val accent = SinusColors.primary

    Box(
        Modifier.fillMaxSize().background(SinusColors.backgroundSolid)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        if (SinusColors.palette.backdrop) ThemeBackdrop(Modifier.fillMaxSize())
        if (current != Step.DONE) {
            Box(
                Modifier.align(Alignment.TopStart).padding(14.dp).size(38.dp).clip(CircleShape)
                    .clickable { flow.close() }.pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center
            ) { SinusIcon("ic_close", SinusColors.textSecondary, Modifier.size(20.dp)) }
        }
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            contentKey = { when (it) { Step.BLUETOOTH, Step.SEARCH -> Step.BLUETOOTH; Step.TEST, Step.LOCKED -> Step.TEST; else -> it } },
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
        ) { st ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val art = Modifier.size(width = 320.dp, height = 210.dp)
                when (st) {
                    Step.WAIT_PHONE -> Box(Modifier.size(width = 320.dp, height = 210.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(196.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) { PhoneTap(ink, accent, Modifier.size(132.dp)) }
                    }
                    Step.BLUETOOTH, Step.SEARCH -> IconDraw(remember { ProximityDrawings.bluetooth() }, ink, accent, art)
                    Step.NEARBY -> IconDraw(remember { ProximityDrawings.nearby() }, ink, accent, art)
                    Step.CONNECTED -> ConnectedHop(ink, accent, art)
                    Step.TEST, Step.LOCKED -> {
                        val rssi = reading.rssi
                        val strength = if (reading.status == ProximityStatus.NEAR || reading.status == ProximityStatus.FAR) {
                            (((rssi ?: -100) + 95f) / 45f).coerceIn(0f, 1f)
                        } else 0f
                        DistanceWave(strength, st == Step.LOCKED, ink, accent, art)
                    }
                    Step.DISTANCE -> IconDraw(remember { ProximityDrawings.distance() }, ink, accent, art)
                    Step.DONE -> LogoDraw(Modifier.size(200.dp))
                }
                val (title, desc) = when (st) {
                    Step.WAIT_PHONE -> if (offline) s.offlineTitle to s.offlineDesc
                        else s.waitTitle to if (stale) s.waitStale else s.waitDesc
                    Step.BLUETOOTH -> s.btTitle to s.followPhone
                    Step.NEARBY -> s.nearbyTitle to s.followPhone
                    Step.SEARCH -> s.searchTitle to when (reading.status) {
                        ProximityStatus.BLUETOOTH_OFF, ProximityStatus.UNSUPPORTED -> s.pcBluetoothOff
                        else -> s.searchDesc
                    }
                    Step.CONNECTED -> s.connectedTitle to s.connectedDesc
                    Step.TEST -> s.testTitle to s.testDesc
                    Step.LOCKED -> s.lockedTitle to s.lockedDesc
                    Step.DISTANCE -> s.distanceTitle to s.distanceDesc
                    Step.DONE -> s.doneTitle to s.doneDesc
                }
                Text(
                    title, color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    desc, color = SinusColors.textSecondary, fontSize = 14.5.sp, lineHeight = 21.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
                )
                if (st == Step.TEST) {
                    val seen = reading.status == ProximityStatus.NEAR || reading.status == ProximityStatus.FAR
                    val meters = reading.rssi?.takeIf { seen }?.let { flow.distanceTo(it) }
                    val label = when {
                        !seen -> s.noSignal
                        meters != null -> s.distance(String.format(java.util.Locale.forLanguageTag(language.code), "%.1f", meters)) + " · " + s.signal(reading.rssi ?: 0)
                        else -> s.signal(reading.rssi ?: 0)
                    }
                    Text(
                        label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.13f)).padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    when (st) {
                        Step.CONNECTED -> {
                            SetupButton(s.testNo, primary = false) { flow.skip() }
                            SetupButton(s.testYes, primary = true) { flow.test() }
                        }
                        Step.DISTANCE -> SetupButton(s.finish, primary = true) { flow.finish() }
                        Step.DONE -> SetupButton(s.close, primary = true) { flow.close() }
                        Step.SEARCH -> {
                            SetupButton(s.cancel, primary = false) { flow.close() }
                            if (reading.status == ProximityStatus.BLUETOOTH_OFF) {
                                SetupButton(s.pcBluetoothSettings, primary = true) {
                                    pl.sinusoide.sinuschat.desktop.system.Platform.openSettings(pl.sinusoide.sinuschat.desktop.system.Platform.Settings.BLUETOOTH)
                                }
                            }
                        }
                        else -> SetupButton(s.cancel, primary = false) { flow.close() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Text(
        label, color = if (primary) Color.White else SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(shape)
            .background(if (primary) SinusColors.primary else Color.Transparent)
            .then(if (!primary) Modifier.border(1.dp, SinusColors.border, shape) else Modifier)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

private const val OFFLINE_GRACE_MS = 3_000L
