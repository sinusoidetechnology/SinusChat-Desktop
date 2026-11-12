package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import pl.sinusoide.sinuschat.desktop.ui.anim.Anim
import pl.sinusoide.sinuschat.desktop.ui.anim.rememberLast
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.ChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings
import pl.sinusoide.sinuschat.desktop.ui.MainScreen
import pl.sinusoide.sinuschat.desktop.ui.NoticeBar
import pl.sinusoide.sinuschat.desktop.ui.SetupActions
import pl.sinusoide.sinuschat.desktop.ui.SetupFlow
import pl.sinusoide.sinuschat.desktop.ui.SetupStep
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.ThemeBackdrop
import pl.sinusoide.sinuschat.desktop.ui.Themes
import pl.sinusoide.sinuschat.desktop.ui.xp.XpWindow
import pl.sinusoide.sinuschat.desktop.ui.lock.PinMode
import pl.sinusoide.sinuschat.desktop.ui.lock.PinScreen
import pl.sinusoide.sinuschat.desktop.ui.settings.SettingsScreen
import pl.sinusoide.sinuschat.desktop.security.LockManager
import pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import pl.sinusoide.sinuschat.desktop.ui.anim.RevealOrigin
import pl.sinusoide.sinuschat.desktop.ui.anim.circleReveal
import pl.sinusoide.sinuschat.desktop.ui.chat.ChatFormat
import pl.sinusoide.sinuschat.link.LinkFrame

private const val IDLE_MS = 5 * 60_000L
private const val BLUR_GRACE_MS = 30_000L
private const val ACTIVITY_CHECK_MS = 2_000L
private const val INPUT_EVENTS = java.awt.AWTEvent.KEY_EVENT_MASK or java.awt.AWTEvent.MOUSE_EVENT_MASK or
    java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK or java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK

private fun screenBounds(): List<java.awt.Rectangle> = runCatching {
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
}.getOrDefault(emptyList())

fun appIcon(): BitmapPainter {
    val bytes = PhoneLink::class.java.getResourceAsStream("/sinuschat.png")!!.use { it.readBytes() }
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

fun main(args: Array<String>) {
    if (pl.sinusoide.sinuschat.desktop.installer.InstallerApp.handles(args)) {
        pl.sinusoide.sinuschat.desktop.installer.InstallerApp.run(args)
        return
    }
    pl.sinusoide.sinuschat.desktop.system.AppMode.configure()
    val instance = try {
        pl.sinusoide.sinuschat.desktop.system.SingleInstance.acquire(SecureStore.dir) ?: return
    } catch (_: Exception) {
        null
    }
    if (applyPendingUpdate()) {
        instance?.release()
        return
    }
    runApp(
        args.firstOrNull { it.startsWith("--language=") }?.substringAfter('='),
        pl.sinusoide.sinuschat.desktop.system.Autostart.ARG in args,
        instance
    )
}

private fun applyPendingUpdate(): Boolean = runCatching {
    if (pl.sinusoide.sinuschat.desktop.system.AppMode.portable) return false
    val device = DeviceState.load()
    if (!pl.sinusoide.sinuschat.desktop.update.DesktopUpdater.shouldApplyAtStart(device.notify)) return false
    val file = pl.sinusoide.sinuschat.desktop.update.DesktopUpdater.pendingFile(device.notify) ?: return false
    device.copy(notify = device.notify.copy(pendingTries = device.notify.pendingTries + 1)).save()
    pl.sinusoide.sinuschat.desktop.update.DesktopUpdater.launchInstaller(file) == true
}.getOrDefault(false)

private fun runApp(
    language: String?,
    startHidden: Boolean,
    instance: pl.sinusoide.sinuschat.desktop.system.SingleInstance?
) = application {
    remember { pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions.cleanTemp() }
    val link = remember {
        PhoneLink().also {
            it.start()
            if (language != null && it.setup.value.language == null && AppLanguage.fromCode(language) != null) it.setLanguage(language)
            SinusColors.palette = Themes.of(it.theme.value)
        }
    }
    val store = remember { ChatStore(link) }
    val updater = remember {
        if (pl.sinusoide.sinuschat.desktop.system.AppMode.portable) null
        else pl.sinusoide.sinuschat.desktop.update.DesktopUpdater({ link.notifyPrefs.value }, { change -> link.updateNotify(change) }).also { it.cleanDownloads() }
    }
    val lock = remember { LockManager().also { l -> link.onUnpaired = { l.wipe() } } }
    val background = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    val proximity = remember { pl.sinusoide.sinuschat.desktop.security.ProximityGuard(background, lock, link::proximityKey) }
    val proximitySetup = remember { pl.sinusoide.sinuschat.desktop.security.ProximitySetupFlow(background, link, lock, proximity) }
    val lockConfig by lock.config.collectAsState()
    val setupStep by proximitySetup.step.collectAsState()
    LaunchedEffect(lockConfig.proximity, lockConfig.proximityRange, lockConfig.proximityCalibration, lockConfig.hasPin, setupStep) {
        val scanning = proximitySetup.scanning
        proximity.dryRun = scanning
        val on = lockConfig.proximity && lockConfig.hasPin
        proximity.update(on || scanning, lockConfig.proximityRange, lockConfig.proximityCalibration)
        link.setProximity(on)
    }
    val icon = remember { appIcon() }
    val tray = rememberTrayState()
    val savedWindow = remember { link.savedWindow?.takeIf { it.visibleOn(screenBounds()) } }
    val windowState = rememberWindowState(
        placement = if (savedWindow?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = savedWindow?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        size = savedWindow?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1180.dp, 760.dp)
    )
    var openRequest by remember { mutableStateOf<String?>(null) }
    var lastNotified by remember { mutableStateOf<String?>(null) }
    val canHide = remember {
        !pl.sinusoide.sinuschat.desktop.system.AppMode.session &&
            (runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false) || pl.sinusoide.sinuschat.desktop.system.Platform.isMac)
    }
    var shown by remember {
        mutableStateOf(!(startHidden && canHide && link.setup.value.done && link.notifyPrefs.value.background))
    }
    var raise by remember { mutableStateOf(0) }
    val setupNow by link.setup.collectAsState()
    val runStrings = pl.sinusoide.sinuschat.desktop.i18n.RunStrings.of(AppLanguage.effective(setupNow.language).chat)
    fun reveal() {
        shown = true
        windowState.isMinimized = false
        raise++
    }
    remember {
        instance?.listen { javax.swing.SwingUtilities.invokeLater { reveal() } }
        pl.sinusoide.sinuschat.desktop.system.Autostart.refresh()
        if (pl.sinusoide.sinuschat.desktop.system.Platform.isMac) runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_REOPENED)) {
                desktop.addAppEventListener(java.awt.desktop.AppReopenedListener { javax.swing.SwingUtilities.invokeLater { reveal() } })
            }
        }
    }

    val quit = {
        store.shutdown()
        link.stop()
        pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions.cleanTemp()
        instance?.release()
        pl.sinusoide.sinuschat.desktop.system.AppMode.wipeSession()
        exitApplication()
    }

    Tray(
        icon = icon,
        state = tray,
        tooltip = "SinusChat",
        onAction = {
            reveal()
            openRequest = lastNotified
        },
        menu = {
            Item(runStrings.trayOpen, onClick = { reveal() })
            Item(runStrings.trayQuit, onClick = quit)
        }
    )

    val closeRequest = {
        if (canHide && link.notifyPrefs.value.background && link.setup.value.done) {
            shown = false
            lock.onHidden()
            if (!link.notifyPrefs.value.backgroundHint) {
                tray.sendNotification(Notification(runStrings.hintTitle, runStrings.hintText, Notification.Type.None))
                link.updateNotify { it.copy(backgroundHint = true) }
            }
        } else {
            quit()
        }
    }
    val themeNow by link.theme.collectAsState()
    val xp = Themes.of(themeNow).key == Themes.RETRO
    var settingsMemo by remember { mutableStateOf(false) }

    androidx.compose.runtime.key(xp) {
    Window(
        onCloseRequest = closeRequest,
        visible = shown,
        title = "SinusChat",
        icon = icon,
        undecorated = xp,
        state = windowState
    ) {
        LaunchedEffect(raise) {
            if (raise == 0) return@LaunchedEffect
            window.toFront()
            window.requestFocus()
        }
        val palette = SinusColors.palette
        LaunchedEffect(palette, shown) {
            if (!shown || xp) return@LaunchedEffect
            delay(30)
            pl.sinusoide.sinuschat.desktop.system.WindowChrome.apply(window, palette.surface, palette.backgroundSolid, palette.dark)
        }
        DisposableEffect(window) {
            window.minimumSize = java.awt.Dimension(900, 600)
            pl.sinusoide.sinuschat.desktop.security.CaptureShield.window = window
            onDispose { }
        }
        LaunchedEffect(windowState) {
            var floating = savedWindow?.takeIf { !it.maximized } ?: savedWindow
            snapshotFlow { Triple(windowState.position, windowState.size, windowState.placement) }
                .collectLatest { (position, size, placement) ->
                    delay(700)
                    if (windowState.isMinimized) return@collectLatest
                    val maximized = placement == WindowPlacement.Maximized
                    if (placement == WindowPlacement.Floating && position is WindowPosition.Absolute) {
                        floating = WindowBounds(position.x.value, position.y.value, size.width.value, size.height.value)
                    }
                    val base = floating ?: return@collectLatest
                    link.saveWindow(base.copy(maximized = maximized))
                }
        }
        val focused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(focused, windowState.isMinimized) {
            if (focused && !windowState.isMinimized) lock.onForeground() else lock.onBackground()
            if (windowState.isMinimized) lock.onHidden()
        }
        LaunchedEffect(Unit) {
            var connected = link.state.value is LinkState.Connected
            link.state.collect { st ->
                val now = st is LinkState.Connected
                if (connected && !now) lock.onPhoneLost()
                connected = now
            }
        }
        val lastInput = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }
        DisposableEffect(Unit) {
            val toolkit = java.awt.Toolkit.getDefaultToolkit()
            val listener = java.awt.event.AWTEventListener { lastInput.set(System.currentTimeMillis()) }
            toolkit.addAWTEventListener(listener, INPUT_EVENTS)
            onDispose { toolkit.removeAWTEventListener(listener) }
        }
        val appLocked by lock.locked.collectAsState()
        LaunchedEffect(focused, windowState.isMinimized, appLocked) {
            val blurredAt = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val present = focused || now - blurredAt < BLUR_GRACE_MS
                link.setActive(present && !windowState.isMinimized && !appLocked && now - lastInput.get() < IDLE_MS)
                delay(ACTIVITY_CHECK_MS)
            }
        }
        LaunchedEffect(Unit) {
            var last = System.currentTimeMillis()
            while (true) {
                delay(5_000)
                val now = System.currentTimeMillis()
                lock.onClockGap(now - last - 5_000)
                last = now
            }
        }
        LaunchedEffect(openRequest) {
            val conv = openRequest ?: return@LaunchedEffect
            if (windowState.placement == WindowPlacement.Floating) window.toFront()
            window.requestFocus()
            if (store.openConv.value != conv) store.open(conv)
            openRequest = null
        }
        val call by store.calls.state.collectAsState()
        val ringing = call?.phase == pl.sinusoide.sinuschat.desktop.call.CallManager.Phase.INCOMING
        LaunchedEffect(ringing) {
            val c = call ?: return@LaunchedEffect
            if (!ringing) return@LaunchedEffect
            if (!link.notifyPrefs.value.allowsCallAlert(pl.sinusoide.sinuschat.desktop.ui.settings.minutesNow())) return@LaunchedEffect
            shown = true
            windowState.isMinimized = false
            window.toFront()
            window.requestFocus()
            val cs = pl.sinusoide.sinuschat.desktop.i18n.CallStrings.of(AppLanguage.effective(link.setup.value.language).chat)
            if (!focused) tray.sendNotification(Notification(c.name, if (c.video) cs.incomingVideo else cs.incomingVoice, Notification.Type.None))
        }
        val startInSettings = remember { settingsMemo }
        val body: @Composable () -> Unit = {
            App(link, store, lock, focused, updater, quit, startInSettings, { settingsMemo = it }, proximity = proximity, proximitySetup = proximitySetup, testNotify = { title, body ->
                tray.sendNotification(Notification(title, body, Notification.Type.None))
            }) { conv, title, body ->
                lastNotified = conv
                tray.sendNotification(Notification(title, body, Notification.Type.None))
            }
        }
        SinusTheme {
            if (xp) {
                XpWindow("SinusChat", icon, windowState, focused, closeRequest, body)
            } else {
                body()
            }
        }
    }
    }
}

fun setupStep(setup: SetupProgress, state: LinkState): SetupStep = when {
    !setup.welcomed -> SetupStep.WELCOME
    !setup.languageChosen -> SetupStep.LANGUAGE
    state !is LinkState.Pairing -> SetupStep.DONE
    else -> SetupStep.PAIR
}

fun notificationText(frame: LinkFrame.Incoming, s: ChatStrings): Pair<String, String> {
    val isGroup = frame.conv.startsWith("g_") || frame.conv.startsWith("group:")
    return when (frame.preview) {
        LinkFrame.PREVIEW_HIDDEN -> "SinusChat" to s.newMessage
        LinkFrame.PREVIEW_NAME -> frame.title.ifBlank { frame.sender.ifBlank { "SinusChat" } } to s.newMessage
        else -> {
            val body = ChatFormat.preview(frame.kind, frame.text, null, s).ifBlank { s.newMessage }
            val title = frame.title.ifBlank { frame.sender.ifBlank { "SinusChat" } }
            title to if (isGroup && frame.sender.isNotBlank()) "${frame.sender}: $body" else body
        }
    }
}

fun sendErrorText(error: String, s: ChatStrings): String = when (error) {
    LinkFrame.ERROR_BLOCKED -> s.errBlocked
    LinkFrame.ERROR_DELETED -> s.errDeleted
    LinkFrame.ERROR_READ_ONLY -> s.errReadOnly
    LinkFrame.ERROR_NO_KEY -> s.errNoKey
    LinkFrame.ERROR_LOCKED -> s.phoneLockedTitle
    ChatStore.ERROR_OFFLINE -> s.errOffline
    else -> s.errFailed
}

fun mediaErrorText(error: String, s: ChatStrings, m: pl.sinusoide.sinuschat.desktop.i18n.MediaStrings): String = when (error) {
    LinkFrame.ERROR_GONE -> m.gone
    LinkFrame.ERROR_TOO_BIG -> m.tooBig
    LinkFrame.ERROR_LOCKED -> s.phoneLockedTitle
    ChatStore.ERROR_OFFLINE -> s.errOffline
    pl.sinusoide.sinuschat.desktop.chat.MediaStore.ERROR_WRITE -> m.voiceFailed
    else -> m.failed
}

@Composable
fun App(
    link: PhoneLink,
    store: ChatStore,
    lock: LockManager,
    focused: Boolean,
    updater: pl.sinusoide.sinuschat.desktop.update.DesktopUpdater? = null,
    quit: () -> Unit = {},
    startInSettings: Boolean = false,
    onSettingsOpen: (Boolean) -> Unit = {},
    proximity: pl.sinusoide.sinuschat.desktop.security.ProximityGuard? = null,
    proximitySetup: pl.sinusoide.sinuschat.desktop.security.ProximitySetupFlow? = null,
    testNotify: (title: String, body: String) -> Unit = { _, _ -> },
    notify: (conv: String, title: String, body: String) -> Unit
) {
    val state by link.state.collectAsState()
    val theme by link.theme.collectAsState()
    val locked by lock.locked.collectAsState()
    var settingsOpen by remember { mutableStateOf(startInSettings) }
    LaunchedEffect(settingsOpen) { onSettingsOpen(settingsOpen) }
    LaunchedEffect(theme) { SinusColors.palette = Themes.of(theme) }
    val safetyPrompts by link.safetyPrompts.collectAsState()
    LaunchedEffect(safetyPrompts) { pl.sinusoide.sinuschat.desktop.security.SafeEngine.enabled = safetyPrompts }
    val systemUri = androidx.compose.ui.platform.LocalUriHandler.current
    val safeUri = remember(systemUri) {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                pl.sinusoide.sinuschat.desktop.security.SafeEngine.openLink(uri) { systemUri.openUri(it) }
            }
        }
    }
    val setup by link.setup.collectAsState()
    val notice by link.notice.collectAsState()
    val termsRequired by pl.sinusoide.sinuschat.desktop.legal.TermsGate.required.collectAsState()
    LaunchedEffect(Unit) {
        pl.sinusoide.sinuschat.desktop.legal.TermsGate.update()
        while (true) {
            if (pl.sinusoide.sinuschat.desktop.legal.TermsGate.due()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pl.sinusoide.sinuschat.desktop.legal.TermsGate.refresh() }
            }
            delay(30 * 60_000L)
        }
    }
    val language = AppLanguage.effective(setup.language)
    val chatStrings = language.chat

    val firewall = remember { pl.sinusoide.sinuschat.desktop.net.FirewallMonitor() }
    var showHelp by remember { mutableStateOf(false) }
    val searchingSince by link.searchingSince.collectAsState()
    val connected = state is LinkState.Connected
    val wantsPhone = setup.done || setupStep(setup, state) == SetupStep.PAIR
    LaunchedEffect(connected, searchingSince, wantsPhone) {
        if (connected || !wantsPhone || !pl.sinusoide.sinuschat.desktop.net.FirewallCheck.isWindows) return@LaunchedEffect
        delay(20_000)
        if (link.phonesNearby() == 0) firewall.check()
    }
    LaunchedEffect(showHelp) {
        if (showHelp && pl.sinusoide.sinuschat.desktop.net.FirewallCheck.isWindows) firewall.check()
    }

    var reveal by remember { mutableStateOf<RevealOrigin?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(reveal) {
        if (reveal == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1250, easing = Motion.ease))
        reveal = null
    }

    var toast by remember { mutableStateOf<String?>(null) }
    var toastFile by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(if (toastFile != null) 6_000 else 4_000)
            toast = null
        }
    }

    LaunchedEffect(updater) {
        val u = updater ?: return@LaunchedEffect
        delay(15_000)
        while (true) {
            u.check(showIfFound = true)
            delay(pl.sinusoide.sinuschat.desktop.update.DesktopUpdater.CHECK_EVERY_MS)
        }
    }
    LaunchedEffect(store, lock) {
        store.diagnostics = { pl.sinusoide.sinuschat.desktop.diag.DesktopDiagnostics.summary(link, store, lock) }
    }
    val currentFocused by rememberUpdatedState(focused)
    val currentStrings by rememberUpdatedState(chatStrings)
    val currentMedia by rememberUpdatedState(language.media)
    val currentSetup by rememberUpdatedState(setup)
    LaunchedEffect(store) {
        store.events.collect { e ->
            when (e) {
                is ChatEvent.Incoming -> {
                    val visible = currentFocused && store.openConv.value == e.frame.conv
                    val prefs = link.notifyPrefs.value
                    val muted = store.chats.value.firstOrNull { it.id == e.frame.conv }?.muted == true
                    val allowed = prefs.allowsMessage(e.frame.conv, muted, currentFocused, pl.sinusoide.sinuschat.desktop.ui.settings.minutesNow())
                    if (currentSetup.done && !visible && allowed) {
                        if (lock.locked.value || pl.sinusoide.sinuschat.desktop.legal.TermsGate.required.value) {
                            notify(e.frame.conv, "SinusChat", currentStrings.newMessage)
                        } else {
                            val (title, body) = notificationText(e.frame.copy(preview = prefs.previewFor(e.frame.preview)), currentStrings)
                            notify(e.frame.conv, title, body)
                        }
                    }
                }
                is ChatEvent.SendFailed -> {
                    toastFile = null
                    toast = if (e.error == LinkFrame.ERROR_TOO_BIG) currentMedia.tooBig else sendErrorText(e.error, currentStrings)
                }
                is ChatEvent.MediaFailed -> {
                    toastFile = null
                    toast = mediaErrorText(e.error, currentStrings, currentMedia)
                }
                is ChatEvent.Saved -> {
                    toastFile = e.file
                    toast = pl.sinusoide.sinuschat.desktop.i18n.FileStrings.of(currentStrings).savedIn(e.file.name)
                }
                is ChatEvent.Info -> {
                    toastFile = null
                    toast = e.text
                }
                is ChatEvent.VoiceFailed -> {
                    val vs = pl.sinusoide.sinuschat.desktop.i18n.VoiceStrings.of(currentStrings)
                    toastFile = null
                    toast = when (e.error) {
                        LinkFrame.ERROR_TOO_SHORT -> vs.tooShort
                        LinkFrame.ERROR_FAILED, LinkFrame.ERROR_GROUP_VOICE, LinkFrame.ERROR_TOO_BIG -> vs.sendFailed
                        else -> sendErrorText(e.error, currentStrings)
                    }
                }
                is ChatEvent.Profile -> {
                    val ps = pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings.of(currentStrings)
                    toastFile = null
                    toast = when {
                        e.field == LinkFrame.PROFILE_NAME -> if (e.ok) ps.nameChanged else ps.nameFailed
                        e.field == LinkFrame.PROFILE_COLOR -> if (e.ok) ps.colorChanged else ps.photoFailed
                        e.ok && e.removed -> ps.photoRemoved
                        e.ok -> ps.photoChanged
                        else -> ps.photoFailed
                    }
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalStrings provides language.strings,
        LocalChatStrings provides chatStrings,
        pl.sinusoide.sinuschat.desktop.i18n.LocalConnStrings provides language.conn,
        pl.sinusoide.sinuschat.desktop.i18n.LocalMediaStrings provides language.media,
        LocalSettingsStrings provides language.settings,
        androidx.compose.ui.platform.LocalUriHandler provides safeUri,
        pl.sinusoide.sinuschat.desktop.update.LocalUpdater provides updater
    ) {
        val issue = pl.sinusoide.sinuschat.desktop.ui.issueText(firewall)
        val wipe: (Boolean) -> Unit = { exceeded ->
            lock.wipe()
            settingsOpen = false
            link.unpair()
            pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions.cleanTemp()
            if (exceeded) toast = language.settings.wipeExceeded
        }
        Box(Modifier.fillMaxSize().background(SinusColors.backgroundSolid)) {
            if (SinusColors.palette.backdrop) ThemeBackdrop(Modifier.fillMaxSize())
            if (!setup.done || reveal != null) {
                SetupFlow(
                    issue = issue,
                    step = setupStep(setup, state),
                    link = state,
                    language = language,
                    actions = SetupActions(
                        welcomed = link::markWelcomed,
                        backToWelcome = link::backToWelcome,
                        chooseLanguage = { link.setLanguage(it.code) },
                        languageDone = {
                            link.setLanguage(language.code)
                            link.markLanguageChosen()
                        },
                        backToLanguage = link::backToLanguage,
                        finish = { origin ->
                            reveal = origin
                            link.finishSetup()
                        },
                        help = { showHelp = true }
                    )
                )
            }
            if (setup.done) {
                Box(Modifier.fillMaxSize().circleReveal(reveal) { progress.value }) {
                    MainScreen(
                        state = state,
                        store = store,
                        language = language,
                        focused = focused && !locked && !settingsOpen,
                        onLanguage = { link.setLanguage(it.code) },
                        onUnpair = link::unpair,
                        onHelp = { showHelp = true },
                        issue = issue,
                        onSettings = { settingsOpen = true }
                    )
                }
            }
            AnimatedVisibility(
                visible = settingsOpen && setup.done,
                enter = fadeIn(tween(220)) + slideInHorizontally(tween(280, easing = Motion.enter)) { it / 12 },
                exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 12 }
            ) {
                SettingsScreen(
                    link = link,
                    lock = lock,
                    language = language,
                    onWipe = wipe,
                    onUnpair = {
                        settingsOpen = false
                        link.unpair()
                    },
                    onClose = { settingsOpen = false },
                    store = store,
                    onTestNotification = testNotify,
                    startThemes = startInSettings,
                    proximity = proximity,
                    onProximitySetup = { proximitySetup?.begin() }
                )
            }
            if (setup.done) pl.sinusoide.sinuschat.desktop.ui.call.CallOverlay(store)
            notice?.let {
                val text = when (it) {
                    Notice.PHONE_UNPAIRED -> language.strings.noticeUnpaired
                    Notice.PHONE_PAUSED -> language.conn.noticePaused
                }
                NoticeBar(text, link::dismissNotice)
            }
            if (showHelp && !connected) {
                pl.sinusoide.sinuschat.desktop.ui.ConnectionHelpDialog(link, firewall) { showHelp = false }
            }
            if (setup.done && updater != null) pl.sinusoide.sinuschat.desktop.ui.update.UpdateOverlay(updater, quit)
            if (setup.done && updater != null) pl.sinusoide.sinuschat.desktop.ui.update.RestartDialog(updater, quit)
            pl.sinusoide.sinuschat.desktop.ui.SafeEngineDialog()
            AnimatedVisibility(
                visible = termsRequired && (setup.done || setup.welcomed),
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.04f)
            ) {
                pl.sinusoide.sinuschat.desktop.ui.TermsUpdateScreen(language, onAccept = {}, onQuit = quit)
            }
            if (proximitySetup != null) pl.sinusoide.sinuschat.desktop.ui.ProximitySetupScreen(proximitySetup, link, language)
            AnimatedVisibility(visible = locked, enter = fadeIn(tween(200)), exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.04f)) {
                val me by link.profile.collectAsState()
                val myPhoto = pl.sinusoide.sinuschat.desktop.ui.chat.rememberAvatar(
                    store, "", if (me?.avatar?.isNotEmpty() == true) LinkFrame.AVATAR_ME else ""
                )
                PinScreen(
                    lock, PinMode.UNLOCK, onDone = { lock.unlock() }, onWipe = wipe,
                    user = me?.let { pl.sinusoide.sinuschat.desktop.ui.lock.PinUser(it.name, it.color.ifBlank { null }, myPhoto) }
                )
            }
            val shownToast = rememberLast(toast)
            AnimatedVisibility(
                visible = toast != null && !locked,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = Anim.toastIn,
                exit = Anim.toastOut
            ) {
                val shownFile = toastFile
                Row(
                    Modifier
                        .padding(bottom = 96.dp)
                        .widthIn(max = 600.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SinusColors.surfaceHigh)
                        .padding(start = 18.dp, end = if (shownFile != null) 8.dp else 18.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        shownToast.orEmpty(),
                        color = SinusColors.text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (shownFile != null) {
                        Text(
                            pl.sinusoide.sinuschat.desktop.i18n.FileStrings.of(chatStrings).showInFolder,
                            color = SinusColors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { pl.sinusoide.sinuschat.desktop.chat.Downloads.reveal(shownFile) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
