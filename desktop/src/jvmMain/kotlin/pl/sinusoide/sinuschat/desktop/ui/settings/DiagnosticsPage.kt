package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.diag.DesktopDiagnostics
import pl.sinusoide.sinuschat.desktop.diag.DesktopDiagnostics.Id
import pl.sinusoide.sinuschat.desktop.diag.DesktopDiagnostics.State
import pl.sinusoide.sinuschat.desktop.i18n.DiagStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.net.FirewallMonitor
import pl.sinusoide.sinuschat.desktop.security.LockManager
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HELP_URL = "https://sinusoide.pl/pomoc.html"
private val dGreen = Color(0xFF3FB97A)
private val dRed = Color(0xFFE5484D)
private val dOrange = Color(0xFFE67E22)

internal fun problemsLabel(ds: DiagStrings, lang: String, n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when (lang) {
        "pl" -> when {
            n == 1 -> ds.problemsOne(n)
            mod10 in 2..4 && mod100 !in 12..14 -> ds.problemsFew(n)
            else -> ds.problemsMany(n)
        }
        "uk" -> when {
            mod10 == 1 && mod100 != 11 -> ds.problemsOne(n)
            mod10 in 2..4 && mod100 !in 12..14 -> ds.problemsFew(n)
            else -> ds.problemsMany(n)
        }
        "cs" -> when (n) {
            1 -> ds.problemsOne(n)
            in 2..4 -> ds.problemsFew(n)
            else -> ds.problemsMany(n)
        }
        else -> if (n == 1) ds.problemsOne(n) else ds.problemsMany(n)
    }
}

private class CheckItem(val title: String, val icon: String, val state: State?, val text: String, val fix: String? = null, val busy: Boolean = false, val onFix: (() -> Unit)? = null)

@Composable
internal fun DiagnosticsPage(link: PhoneLink, store: ChatStore?, lock: LockManager, langCode: String) {
    val chat = LocalChatStrings.current
    val ds = remember(chat) { DiagStrings.of(chat) }
    val uri = LocalUriHandler.current
    var run by remember { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf<List<DesktopDiagnostics.Check>?>(null) }
    var device by remember { mutableStateOf("") }
    var os by remember { mutableStateOf("") }
    var egg by remember { mutableStateOf(false) }
    val firewall = remember { FirewallMonitor() }
    val fwState by firewall.state.collectAsState()
    val updater = pl.sinusoide.sinuschat.desktop.update.LocalUpdater.current
    val us = remember(chat) { pl.sinusoide.sinuschat.desktop.i18n.UpdateStrings.of(chat) }
    val upd = updater?.state?.collectAsState()?.value
    LaunchedEffect(run) {
        checks = null
        updater?.check()
        checks = withContext(Dispatchers.IO) { DesktopDiagnostics.run(link, store, lock) }
        device = withContext(Dispatchers.IO) { DesktopDiagnostics.deviceName() }
        os = withContext(Dispatchers.IO) { DesktopDiagnostics.osName() }
    }
    val list = checks
    fun row(c: DesktopDiagnostics.Check): CheckItem = when (c.id) {
        Id.PHONE -> CheckItem(ds.phone, "ic_qr_code", c.state, when (c.state) {
            State.OK -> ds.phoneOk(c.value)
            State.PROBLEM -> ds.phoneProblem
            State.INFO -> ds.phonePairing
        }, if (c.state == State.PROBLEM) ds.retry else null) { run++ }
        Id.PHONE_LOCKED -> CheckItem(ds.locked, "ic_lock", c.state, if (c.state == State.OK) ds.lockedOk else ds.lockedProblem)
        Id.LOCAL_NETWORK -> CheckItem(ds.lan, "ic_wifi", c.state, if (c.state == State.OK) ds.lanOk else ds.lanProblem)
        Id.FIREWALL -> CheckItem(ds.fw, "ic_shield", c.state, when {
            c.state == State.OK -> ds.fwOk
            c.value == "blocked" -> ds.fwBlocked
            c.value == "public" -> ds.fwPublic
            else -> ds.fwUnknown
        }, if (c.state == State.PROBLEM) ds.fix else null, busy = fwState is FirewallMonitor.Ui.Fixing) { firewall.fix { run++ } }
        Id.NOTIFICATIONS -> CheckItem(ds.notif, "ic_notifications", c.state, when (c.value) {
            "tray" -> ds.notifTray
            "off" -> ds.notifOff
            else -> ds.notifOk
        })
        Id.MICROPHONE -> CheckItem(ds.mic, "ic_mic", c.state, if (c.state == State.OK) ds.okAvailable else ds.micProblem,
            if (c.state == State.PROBLEM && pl.sinusoide.sinuschat.desktop.system.Platform.canOpenSettings(pl.sinusoide.sinuschat.desktop.system.Platform.Settings.MICROPHONE)) ds.openSettings else null) { pl.sinusoide.sinuschat.desktop.system.Platform.openSettings(pl.sinusoide.sinuschat.desktop.system.Platform.Settings.MICROPHONE) }
        Id.SPEAKERS -> CheckItem(ds.spk, "ic_call_speaker_on", c.state, if (c.state == State.OK) ds.okAvailable else ds.spkProblem)
        Id.ENCRYPTION -> CheckItem(ds.enc, "ic_encrypted", c.state, if (c.state == State.OK) ds.encOk else ds.encProblem)
        Id.LOCK -> CheckItem(ds.lock, "ic_lock", c.state, if (c.state == State.OK) ds.lockOk else ds.lockOff)
        Id.DISK -> CheckItem(ds.disk, "ic_harddrive", c.state, if (c.state == State.OK) ds.diskOk(c.value) else ds.diskProblem(c.value))
    }
    fun versionRow(): CheckItem? {
        val u = updater ?: return null
        val cur = u.version
        return when (val s = upd) {
            pl.sinusoide.sinuschat.desktop.update.UpdateState.Disabled -> CheckItem(ds.version, "ic_update", State.INFO, us.versionOff(cur))
            pl.sinusoide.sinuschat.desktop.update.UpdateState.Unknown ->
                CheckItem(ds.version, "ic_update", State.INFO, us.versionUnknown(cur), us.checkAgain) { u.check(force = true) }
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Latest -> CheckItem(ds.version, "ic_update", State.OK, us.versionOk(cur))
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Available -> CheckItem(ds.version, "ic_update", State.PROBLEM, us.versionOld(cur, s.manifest.version), us.update) { u.open() }
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Downloading -> CheckItem(ds.version, "ic_update", State.PROBLEM, us.versionOld(cur, s.manifest.version), us.update) { u.open() }
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Verifying -> CheckItem(ds.version, "ic_update", State.PROBLEM, us.versionOld(cur, s.manifest.version), us.update) { u.open() }
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Ready -> CheckItem(ds.version, "ic_ready_to_update", State.PROBLEM, us.readyDesc(s.manifest.version), us.restart) { u.requestRestart() }
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Installing -> CheckItem(ds.version, "ic_update", State.PROBLEM, us.versionOld(cur, s.manifest.version), us.update) { u.open() }
            is pl.sinusoide.sinuschat.desktop.update.UpdateState.Failed -> CheckItem(ds.version, "ic_update", State.PROBLEM, us.versionOld(cur, s.manifest.version), us.update) { u.open() }
            else -> CheckItem(ds.version, "ic_update", null, ds.checking)
        }
    }
    val version = if (list != null) versionRow() else null
    val problems = (list?.count { it.state == State.PROBLEM } ?: 0) + (if (version?.state == State.PROBLEM) 1 else 0)
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(20.dp))).background(SinusColors.card)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val color = when {
            list == null -> SinusColors.primary
            problems > 0 -> dOrange
            else -> dGreen
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(color.copy(alpha = 0x26 / 255f)), contentAlignment = Alignment.Center) {
            when {
                list == null -> CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                problems > 0 -> SinusIcon("ic_warning", dOrange, Modifier.size(32.dp))
                else -> SinusIcon("ic_check", dGreen, Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                list == null -> ds.checking
                problems > 0 -> problemsLabel(ds, langCode, problems)
                else -> ds.heroOk
            },
            color = SinusColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            when {
                list == null -> ds.heroChecking
                problems > 0 -> ds.heroProblemsDesc
                else -> ds.okDesc
            },
            color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
        )
    }
    if (list != null) {
        val groups = listOf(
            ds.secConnection to listOf(Id.PHONE, Id.PHONE_LOCKED, Id.LOCAL_NETWORK, Id.FIREWALL),
            ds.sound to listOf(Id.MICROPHONE, Id.SPEAKERS),
            ds.secApp to listOf(Id.NOTIFICATIONS, Id.DISK),
            ds.secSecurity to listOf(Id.ENCRYPTION, Id.LOCK)
        )
        for ((title, ids) in groups) {
            val rows = (if (title == ds.secApp) listOfNotNull(version) else emptyList()) + ids.mapNotNull { id -> list.firstOrNull { it.id == id } }.map(::row)
            if (rows.isEmpty()) continue
            SectionTitle(title)
            Card {
                rows.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    CheckRow(r)
                }
            }
        }
    }
    SectionTitle(ds.secMore)
    Card {
        NavRow("ic_help", SinusColors.primary, ds.moreTitle, ds.moreDesc) { runCatching { uri.openUri(HELP_URL) } }
    }
    SectionTitle(ds.secInfo)
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) { if (hint != null) { delay(700); hint = null } }
    Card {
        InfoRow("ic_dolphin", ds.version, ds.codename(DesktopDiagnostics.version, DesktopDiagnostics.CODENAME), hint) {
            val now = System.currentTimeMillis()
            if (now - lastTap > 2000) taps = 0
            lastTap = now
            taps++
            if (taps >= 5) {
                taps = 0
                egg = true
            } else if (taps > 2) hint = (5 - taps).toString()
        }
        RowDivider()
        InfoRow("ic_settings", ds.device, if (device.isEmpty()) ds.checking else ds.deviceValue(device, os), null, null)
    }
    if (egg) DolphinEgg { egg = false }
}

@Composable
private fun CheckRow(r: CheckItem) {
    val color = when (r.state) {
        State.OK -> dGreen
        State.PROBLEM -> dRed
        State.INFO -> SinusColors.textSecondary
        null -> SinusColors.primary
    }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 13.dp)) {
        Box(
            Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(color.copy(alpha = 0x24 / 255f)),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon(
                when (r.state) {
                    State.OK -> "ic_check"
                    State.PROBLEM -> "ic_warning"
                    else -> "ic_info"
                }, color, Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(r.title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            Text(
                r.text, color = if (r.state == State.PROBLEM) dRed else SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (r.fix != null && r.onFix != null && r.busy) {
                CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.5.dp, modifier = Modifier.padding(top = 10.dp).size(24.dp))
            } else if (r.fix != null && r.onFix != null) {
                Text(
                    r.fix, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(SinusColors.radius(16.dp))).background(SinusColors.primary)
                        .clickable(onClick = r.onFix).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: String, title: String, value: String, badge: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, SinusColors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            Text(value, color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (badge != null) {
            Text(
                badge, color = SinusColors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(SinusColors.primary).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

private class Dolphin(val sizePx: Float, val color: Color, x: Float, y: Float, vx: Float, vy: Float) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var vx = vx
    var vy = vy
    var dragging by mutableStateOf(false)
}

private class Tilt {
    @Volatile var gx = 0f
    @Volatile var gy = 1800f
    private var process: Process? = null

    fun start() {
        if (!pl.sinusoide.sinuschat.desktop.net.FirewallCheck.isWindows) return
        val script = "\$null = [Windows.Devices.Sensors.Accelerometer, Windows.Devices.Sensors, ContentType = WindowsRuntime]\n" +
            "\$a = [Windows.Devices.Sensors.Accelerometer]::GetDefault()\n" +
            "if (\$a -eq \$null) { exit }\n" +
            "\$c = [System.Globalization.CultureInfo]::InvariantCulture\n" +
            "while (\$true) { \$r = \$a.GetCurrentReading(); if (\$r) { [Console]::Out.WriteLine([string]::Format(\$c, '{0:F3} {1:F3}', \$r.AccelerationX, \$r.AccelerationY)); [Console]::Out.Flush() }; Start-Sleep -Milliseconds 40 }"
        val encoded = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val p = runCatching {
            ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).redirectErrorStream(true).start()
        }.getOrNull() ?: return
        process = p
        Thread {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    val parts = line.trim().split(' ')
                    val ax = parts.getOrNull(0)?.toFloatOrNull()
                    val ay = parts.getOrNull(1)?.toFloatOrNull()
                    if (ax != null && ay != null) {
                        gx = ax * 9.81f * 380f
                        gy = -ay * 9.81f * 380f
                    }
                }
            }
        }.apply { isDaemon = true; name = "dolphin-tilt" }.start()
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }
}

@Composable
private fun DolphinEgg(onClose: () -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dolphins = remember { mutableStateListOf<Dolphin>() }
    val tilt = remember { Tilt() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val intro = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    val focus = remember { FocusRequester() }
    DisposableEffect(Unit) {
        tilt.start()
        onDispose { tilt.stop() }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        intro.animateTo(1f, tween(1000))
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.033f)
                last = now
                val w = area.width.toFloat()
                val h = area.height.toFloat()
                if (w > 0f && h > 0f) for (d in dolphins) {
                    if (d.dragging) continue
                    d.vx += tilt.gx * dt
                    d.vy += tilt.gy * dt
                    var nx = d.x + d.vx * dt
                    var ny = d.y + d.vy * dt
                    if (nx < 0f) { nx = 0f; d.vx = -d.vx * 0.5f }
                    if (nx > w - d.sizePx) { nx = w - d.sizePx; d.vx = -d.vx * 0.5f }
                    if (ny < 0f) { ny = 0f; d.vy = -d.vy * 0.5f }
                    if (ny > h - d.sizePx) { ny = h - d.sizePx; d.vy = -d.vy * 0.4f; d.vx *= 0.95f }
                    d.x = nx
                    d.y = ny
                }
            }
        }
    }
    val colors = listOf(0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF4CD964, 0xFF5AC8FA, 0xFF007AFF, 0xFF5856D6, 0xFFFF2D55, 0xFF00E5FF, 0xFFFF00FF)
    Popup(popupPositionProvider = FullWindow, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier.requiredSize(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })
                .background(SinusColors.backgroundSolid)
                .onSizeChanged { area = it }
                .focusRequester(focus).focusable()
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        onClose()
                        true
                    } else false
                }
        ) {
            for (d in dolphins) {
                val sizeDp = with(density) { d.sizePx.toDp() }
                SinusIcon(
                    "ic_dolphin", d.color,
                    Modifier.offset { IntOffset(d.x.roundToInt(), d.y.roundToInt()) }.size(sizeDp)
                        .graphicsLayer {
                            rotationZ = (d.vx * 0.04f).coerceIn(-60f, 60f)
                            val s = if (d.dragging) 1.3f else 1f
                            scaleX = s
                            scaleY = s
                        }
                        .pointerInput(d) {
                            detectDragGestures(
                                onDragStart = { d.dragging = true; d.vx = 0f; d.vy = 0f },
                                onDragEnd = { d.dragging = false },
                                onDragCancel = { d.dragging = false }
                            ) { change, drag ->
                                change.consume()
                                d.x += drag.x
                                d.y += drag.y
                                d.vx = drag.x / 0.016f
                                d.vy = drag.y / 0.016f
                            }
                        }
                )
            }
            Box(
                Modifier.align(Alignment.Center).size(220.dp)
                    .graphicsLayer {
                        val s = intro.value * press.value
                        scaleX = s
                        scaleY = s
                        alpha = intro.value
                        rotationZ = 360f * intro.value
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val cx = area.width / 2f
                        val cy = area.height / 2f
                        repeat(13) {
                            val size = with(density) { (50 + kotlin.random.Random.nextInt(40)).dp.toPx() }
                            val angle = kotlin.random.Random.nextDouble() * Math.PI * 2
                            val speed = 800f + kotlin.random.Random.nextFloat() * 1200f
                            dolphins += Dolphin(
                                size, Color(colors[kotlin.random.Random.nextInt(colors.size)]),
                                cx - size / 2f, cy - size / 2f,
                                (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat()
                            )
                        }
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                SinusIcon("ic_dolphin", Color(0xFF007AFF), Modifier.fillMaxSize())
            }
            LaunchedEffect(dolphins.size) {
                if (dolphins.isEmpty()) return@LaunchedEffect
                press.snapTo(0.8f)
                press.animateTo(1f, tween(200))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp).clip(CircleShape)
                    .clickable(onClick = onClose).pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center
            ) {
                SinusIcon("ic_close", SinusColors.textSecondary, Modifier.size(24.dp))
            }
        }
    }
}
