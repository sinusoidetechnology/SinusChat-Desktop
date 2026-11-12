package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings
import pl.sinusoide.sinuschat.desktop.security.LockManager
import pl.sinusoide.sinuschat.desktop.security.WindowsHello
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.Palette
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.ThemeBackdrop
import pl.sinusoide.sinuschat.desktop.ui.Themes
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.desktop.ui.anim.Anim
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import pl.sinusoide.sinuschat.desktop.ui.anim.dialogIn
import pl.sinusoide.sinuschat.desktop.ui.anim.rememberLast
import pl.sinusoide.sinuschat.desktop.ui.lock.PinMode
import pl.sinusoide.sinuschat.desktop.ui.lock.PinScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Page {
    MAIN, PRIVACY, LOCK, DEVICES, NOTIFY, STORAGE, THEMES, LANGUAGE,
    STORAGE_ITEMS, ACCOUNT, PRIVACY_ACCOUNT, SOUND, TRANSLATE, INTERNET, BACKUP, DIAGNOSTICS, REPORT, SUPPORT, WHATS_NEW, DOCS
}

private class Entry(val page: Page, val icon: String, val color: Color, val title: String, val sub: String)

private val orange = Color(0xFFE67E22)
private val mint = Color(0xFF2FA88F)
private val red = Color(0xFFE5484D)
private val green = Color(0xFF3FB97A)

@Composable
private fun placeholders(ps: pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings): Map<Page, Entry> = listOf(
    Entry(Page.ACCOUNT, "ic_key", orange, ps.account, ps.accountSub),
    Entry(Page.SOUND, "ic_call_speaker_on", orange, ps.sound, ps.soundSub),
    Entry(Page.TRANSLATE, "ic_translate", mint, ps.translate, ps.translateSub),
    Entry(Page.INTERNET, "ic_internet", teal, ps.internet, ps.internetSub),
    Entry(Page.BACKUP, "ic_backup", SinusColors.primary, ps.backup, ps.backupSub),
    Entry(Page.DIAGNOSTICS, "ic_question_exchange", green, ps.diagnostics, ps.diagnosticsSub),
    Entry(Page.REPORT, "ic_report", orange, ps.report, ps.reportSub),
    Entry(Page.SUPPORT, "ic_heart", red, ps.support, ps.supportSub),
    Entry(Page.WHATS_NEW, "ic_lightbulb", SinusColors.primary, ps.whatsNew, ps.whatsNewSub(desktopVersion)),
    Entry(Page.DOCS, "ic_document", SinusColors.textSecondary, ps.docs, ps.docsSub)
).associateBy { it.page }

private val desktopVersion: String get() = PhoneLink.APP_NAME.substringAfterLast(' ')

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')

private fun depth(p: Page): Int = when (p) {
    Page.MAIN -> 0
    Page.LOCK, Page.PRIVACY_ACCOUNT, Page.STORAGE_ITEMS -> 2
    else -> 1
}

private val violet = Color(0xFF9B6BE0)
private val teal = Color(0xFF26A5B8)
private val pink = Color(0xFFE0578B)

@Composable
fun SettingsScreen(
    link: PhoneLink,
    lock: LockManager,
    language: AppLanguage,
    onWipe: (exceeded: Boolean) -> Unit,
    onUnpair: () -> Unit,
    onClose: () -> Unit,
    store: pl.sinusoide.sinuschat.desktop.chat.ChatStore? = null,
    onTestNotification: ((String, String) -> Unit)? = null,
    startThemes: Boolean = false,
    proximity: pl.sinusoide.sinuschat.desktop.security.ProximityGuard? = null,
    onProximitySetup: () -> Unit = {}
) {
    val s = LocalSettingsStrings.current
    val chatStrings = LocalChatStrings.current
    val ps = remember(chatStrings) { pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings.of(chatStrings) }
    val hs = remember(chatStrings) { pl.sinusoide.sinuschat.desktop.i18n.HandoffStrings.of(chatStrings) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val extra = placeholders(ps)
    var page by remember { mutableStateOf(if (startThemes) Page.THEMES else Page.MAIN) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var photoOpen by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var storageFilter by remember { mutableStateOf<StorageFilter?>(null) }
    var flow by remember { mutableStateOf<List<PinMode>>(emptyList()) }
    var flowStep by remember { mutableIntStateOf(0) }
    var afterFlow by remember { mutableStateOf<() -> Unit>({}) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3_000)
            toast = null
        }
    }

    fun runPin(modes: List<PinMode>, then: () -> Unit) {
        afterFlow = then
        flowStep++
        flow = modes
    }

    fun closeSearch() {
        searching = false
        query = ""
    }

    fun back() {
        if (searching) {
            closeSearch()
            return
        }
        page = when (page) {
            Page.MAIN -> {
                onClose()
                Page.MAIN
            }
            Page.LOCK, Page.PRIVACY_ACCOUNT -> Page.PRIVACY
            Page.STORAGE_ITEMS -> Page.STORAGE
            else -> Page.MAIN
        }
    }

    fun openLock() {
        if (lock.config.value.hasPin) runPin(listOf(PinMode.CONFIRM)) { page = Page.LOCK }
        else runPin(listOf(PinMode.SET)) {
            page = Page.LOCK
            toast = s.lockSet
        }
    }

    Box(Modifier.fillMaxSize().background(SinusColors.backgroundSolid)) {
        if (SinusColors.palette.backdrop) ThemeBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(SinusColors.surface).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconAction("ic_back", s.back, SinusColors.textPrimary) { back() }
                Spacer(Modifier.width(6.dp))
                if (searching && page == Page.MAIN) {
                    SettingsSearchField(query, { query = it }, ps.searchHint, ::closeSearch, Modifier.weight(1f))
                    if (query.isNotEmpty()) IconAction("ic_close", ps.searchClear, SinusColors.textSecondary) { query = "" }
                } else {
                    Text(
                        when (page) {
                            Page.MAIN -> s.settingsTitle
                            Page.PRIVACY -> s.privacy
                            Page.PRIVACY_ACCOUNT -> hs.privacyRow
                            Page.STORAGE_ITEMS -> storageFilter?.title.orEmpty()
                            Page.LOCK -> s.lockTitle
                            Page.DEVICES -> s.devices
                            Page.NOTIFY -> pl.sinusoide.sinuschat.desktop.i18n.NotifyStrings.of(LocalChatStrings.current).title
                            Page.STORAGE -> pl.sinusoide.sinuschat.desktop.i18n.StorageStrings.of(LocalChatStrings.current).title
                            Page.THEMES -> s.themesTitle
                            Page.LANGUAGE -> s.language
                            else -> extra[page]?.title.orEmpty()
                        },
                        color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (page == Page.MAIN) IconAction("ic_search", ps.searchHint, SinusColors.textSecondary) { searching = true }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SinusColors.border))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val forward = depth(targetState) >= depth(initialState)
                            (fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(tween(280, easing = Motion.enter)) { if (forward) it / 8 else -it / 8 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { if (forward) -it / 12 else it / 12 })
                        }
                    ) { shown ->
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)) {
                        when (shown) {
                            Page.MAIN -> if (searching) {
                                SearchResults(searchEntries(s, extra), query, ps) { target ->
                                    closeSearch()
                                    if (target == Page.DOCS) runCatching { uri.openUri(docsUrl(language.code)) } else page = target
                                }
                            } else {
                                MainPage(link, lock, language, store, ps, extra, { renaming = true }, { photoOpen = true }, { toast = it }) { target ->
                                    if (target == Page.DOCS) runCatching { uri.openUri(docsUrl(language.code)) } else page = target
                                }
                            }
                            Page.PRIVACY -> PrivacyPage(link, lock, hs.privacyRow, { page = Page.PRIVACY_ACCOUNT }) { openLock() }
                            Page.PRIVACY_ACCOUNT -> HandoffPage(link, store, pl.sinusoide.sinuschat.link.LinkFrame.SECTION_PRIVACY)
                            Page.ACCOUNT -> HandoffPage(link, store, pl.sinusoide.sinuschat.link.LinkFrame.SECTION_ACCOUNT)
                            Page.INTERNET -> HandoffPage(link, store, pl.sinusoide.sinuschat.link.LinkFrame.SECTION_INTERNET)
                            Page.BACKUP -> HandoffPage(link, store, pl.sinusoide.sinuschat.link.LinkFrame.SECTION_BACKUP)
                            Page.REPORT -> HandoffPage(link, store, pl.sinusoide.sinuschat.link.LinkFrame.SECTION_REPORT_DESKTOP)
                            Page.SUPPORT -> SupportPage { toast = it }
                            Page.TRANSLATE -> if (store != null) TranslatePage(link, store) { toast = it }
                            Page.DIAGNOSTICS -> DiagnosticsPage(link, store, lock, language.code ?: "pl")
                            Page.NOTIFY -> NotificationsPage(link, onTestNotification, hs.notifyLocal) { toast = it }
                            Page.STORAGE -> StoragePage(store) { storageFilter = it; page = Page.STORAGE_ITEMS }
                            Page.STORAGE_ITEMS -> {
                                val f = storageFilter
                                if (store != null && f != null) StoredItemsPage(store, f) { toast = it }
                            }
                            Page.LOCK -> LockPage(
                                lock,
                                link,
                                proximity,
                                onProximitySetup,
                                onPin = ::runPin,
                                onToast = { toast = it },
                                onDisabled = { page = Page.PRIVACY }
                            )
                            Page.DEVICES -> DevicesPage(link, language, onUnpair)
                            Page.THEMES -> ThemesPage(link)
                            Page.LANGUAGE -> LanguagePage(link) { overlay = it }
                            else -> extra[shown]?.let { EmptySectionPage(it, ps) }
                        }
                    }
                    }
                }
            }
        }
        val shownToast = rememberLast(toast)
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = Anim.toastIn,
            exit = Anim.toastOut
        ) {
            Text(
                shownToast.orEmpty(), color = SinusColors.text, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 40.dp).widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(SinusColors.radius(14.dp))).background(SinusColors.card)
                    .border(1.dp, SinusColors.border, RoundedCornerShape(SinusColors.radius(14.dp)))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
        overlay?.invoke()
        val photoStore = store
        if (photoOpen && photoStore != null) PhotoDialog(link, photoStore, ps, { photoOpen = false }) { toast = it }
        val renameStore = store
        if (renaming && renameStore != null) {
            RenameDialog(link.profile.value?.name.orEmpty(), ps, onDismiss = { renaming = false }) { name ->
                renaming = false
                renameStore.renameProfile(name)
            }
        }
        if (flow.isNotEmpty()) {
            key(flowStep, flow.size) {
                PinScreen(
                    lock = lock,
                    mode = flow.first(),
                    onDone = {
                        val rest = flow.drop(1)
                        if (rest.isEmpty()) {
                            flow = emptyList()
                            afterFlow.invoke()
                        } else {
                            flow = rest
                        }
                    },
                    onCancel = { flow = emptyList() },
                    onWipe = onWipe,
                    modifier = Modifier.dialogIn()
                )
            }
        }
    }
}

@Composable
private fun MainPage(
    link: PhoneLink,
    lock: LockManager,
    language: AppLanguage,
    store: pl.sinusoide.sinuschat.desktop.chat.ChatStore?,
    ps: pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings,
    extra: Map<Page, Entry>,
    onRename: () -> Unit,
    onPhoto: () -> Unit,
    onToast: (String) -> Unit,
    open: (Page) -> Unit
) {
    val s = LocalSettingsStrings.current
    val config by lock.config.collectAsState()
    val state by link.state.collectAsState()
    val phoneName = when (val st = state) {
        is LinkState.Connected -> st.phoneName
        is LinkState.Searching -> st.phoneName
        is LinkState.Pairing -> ""
    }
    @Composable
    fun Extra(p: Page) {
        val e = extra[p] ?: return
        NavRow(e.icon, e.color, e.title, e.sub) { open(p) }
    }
    if (store != null) ProfileSection(link, store, ps, onRename, onPhoto, onToast)
    SectionTitle(s.sectionAccount)
    Card {
        Extra(Page.ACCOUNT)
        RowDivider()
        NavRow("ic_lock", violet, s.privacy, lockStatus(config.hasPin, config.biometric)) { open(Page.PRIVACY) }
        RowDivider()
        NavRow("ic_qr_code", teal, s.devices, phoneName.ifBlank { s.devicesSub }) { open(Page.DEVICES) }
    }
    SectionTitle(s.sectionApp)
    Card {
        val notifyPrefs by link.notifyPrefs.collectAsState()
        val ns = pl.sinusoide.sinuschat.desktop.i18n.NotifyStrings.of(LocalChatStrings.current)
        NavRow("ic_notifications", notifyAmber, ns.title, notifySummary(notifyPrefs)) { open(Page.NOTIFY) }
        RowDivider()
        Extra(Page.SOUND)
        RowDivider()
        NavRow("ic_palette", pink, s.themes, s.themeSub) { open(Page.THEMES) }
        RowDivider()
        NavRow("ic_language", SinusColors.primary, s.language, language.displayName) { open(Page.LANGUAGE) }
        RowDivider()
        Extra(Page.TRANSLATE)
        if (store != null) {
            RowDivider()
            val used by store.media.vault.usage.collectAsState()
            LaunchedEffect(store) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.media.vault.refreshUsage() }
            }
            val ss = pl.sinusoide.sinuschat.desktop.i18n.StorageStrings.of(LocalChatStrings.current)
            NavRow(
                "ic_storage", storageGreen, ss.title,
                pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions.formatSize(used.coerceAtLeast(0L)).ifEmpty { "0 B" }
            ) { open(Page.STORAGE) }
        }
        RowDivider()
        Extra(Page.INTERNET)
    }
    SectionTitle(ps.sectionBackup)
    Card { Extra(Page.BACKUP) }
    SectionTitle(ps.sectionHelp)
    Card {
        Extra(Page.DIAGNOSTICS)
        RowDivider()
        Extra(Page.REPORT)
    }
    SectionTitle(ps.sectionAbout)
    Card {
        Extra(Page.SUPPORT)
        RowDivider()
        Extra(Page.WHATS_NEW)
        RowDivider()
        Extra(Page.DOCS)
    }
}

@Composable
private fun searchEntries(s: pl.sinusoide.sinuschat.desktop.i18n.SettingsStrings, extra: Map<Page, Entry>): List<Entry> {
    val chat = LocalChatStrings.current
    val ns = pl.sinusoide.sinuschat.desktop.i18n.NotifyStrings.of(chat)
    val ss = pl.sinusoide.sinuschat.desktop.i18n.StorageStrings.of(chat)
    val base = listOf(
        Entry(Page.PRIVACY, "ic_lock", violet, s.privacy, s.privacySub),
        Entry(Page.DEVICES, "ic_qr_code", teal, s.devices, s.devicesSub),
        Entry(Page.NOTIFY, "ic_notifications", notifyAmber, ns.title, ""),
        Entry(Page.STORAGE, "ic_storage", storageGreen, ss.title, ""),
        Entry(Page.THEMES, "ic_palette", pink, s.themes, s.themeSub),
        Entry(Page.LANGUAGE, "ic_language", SinusColors.primary, s.language, "")
    )
    val order = listOf(Page.ACCOUNT, Page.PRIVACY, Page.DEVICES, Page.NOTIFY, Page.SOUND, Page.THEMES, Page.LANGUAGE, Page.TRANSLATE,
        Page.STORAGE, Page.INTERNET, Page.BACKUP, Page.DIAGNOSTICS, Page.REPORT, Page.SUPPORT, Page.WHATS_NEW, Page.DOCS)
    val all = (base + extra.values).associateBy { it.page }
    return order.mapNotNull { all[it] }
}

@Composable
private fun SearchResults(entries: List<Entry>, query: String, ps: pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings, open: (Page) -> Unit) {
    val q = fold(query.trim())
    val shown = if (q.isEmpty()) entries else entries.filter { fold(it.title).contains(q) || fold(it.sub).contains(q) }
    Spacer(Modifier.height(10.dp))
    if (shown.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SinusIcon("ic_search", SinusColors.textMuted, Modifier.size(40.dp))
            Text(
                ps.searchEmpty(query.trim()), color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        return
    }
    Card {
        shown.forEachIndexed { i, e ->
            if (i > 0) RowDivider()
            NavRow(e.icon, e.color, e.title, e.sub) { open(e.page) }
        }
    }
}

@Composable
private fun SettingsSearchField(value: String, onChange: (String) -> Unit, hint: String, onClose: () -> Unit, modifier: Modifier) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(SinusColors.radius(20.dp))
    Row(
        modifier.height(40.dp).clip(shape).background(SinusColors.surfaceVariant).border(1.dp, SinusColors.border, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon("ic_search", SinusColors.textSecondary, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = SinusColors.textMuted, fontSize = 15.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = SinusColors.textPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(SinusColors.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        onClose()
                        true
                    } else false
                }
            )
        }
    }
}

@Composable
private fun EmptySectionPage(e: Entry, ps: pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings) {
    Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(SinusColors.radius(24.dp))).background(e.color.copy(alpha = 0x24 / 255f)),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon(e.icon, e.color, Modifier.size(38.dp))
        }
        Text(e.title, color = SinusColors.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        if (e.sub.isNotEmpty()) {
            Text(
                e.sub, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp)
            )
        }
        Text(
            ps.emptyPage, color = SinusColors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 22.dp)
        )
    }
}

@Composable
private fun lockStatus(hasPin: Boolean, hello: Boolean): String {
    val s = LocalSettingsStrings.current
    return when {
        hasPin && hello -> s.lockOnHello
        hasPin -> s.lockOnPin
        else -> s.lockOff
    }
}

private fun docsUrl(code: String?): String = "https://sinusoide.pl/dokumentacja.html?lang=" + (if (code == "pl") "pl" else "en")

@Composable
private fun PrivacyPage(link: PhoneLink, lock: LockManager, accountLabel: String, openAccount: () -> Unit, openLock: () -> Unit) {
    val s = LocalSettingsStrings.current
    val config by lock.config.collectAsState()
    val color = if (config.hasPin) SinusColors.online else Color(0xFFE67E22)
    Spacer(Modifier.height(8.dp))
    Hero(
        "ic_shield", color,
        if (config.hasPin) s.privHeroProtected else s.privHeroUnprotected,
        s.privHeroDesc
    ) {
        Pill(if (config.hasPin) s.pillLockOn else s.pillLockOff, color)
    }
    SectionTitle(s.privSectionLock)
    Card {
        NavRow("ic_lock", SinusColors.primary, s.lockTitle, lockStatus(config.hasPin, config.biometric), onClick = openLock)
    }
    SectionTitle(s.sectionAccount)
    Card {
        NavRow("ic_privacy_warning", violet, accountLabel, "", onClick = openAccount)
    }
    val rs = pl.sinusoide.sinuschat.desktop.i18n.RunStrings.of(LocalChatStrings.current)
    val prefs by link.notifyPrefs.collectAsState()
    var autostart by remember { mutableStateOf(pl.sinusoide.sinuschat.desktop.system.Autostart.isEnabled()) }
    val canAutostart = pl.sinusoide.sinuschat.desktop.system.Autostart.available
    SectionTitle(rs.section)
    Card {
        SwitchRow(
            "ic_pc", teal, rs.autostart, if (canAutostart) rs.autostartDesc else rs.autostartUnavailable,
            autostart, enabled = canAutostart
        ) { on ->
            if (pl.sinusoide.sinuschat.desktop.system.Autostart.set(on)) autostart = on
        }
        RowDivider()
        SwitchRow("ic_chat", SinusColors.primary, rs.background, rs.backgroundDesc, prefs.background && !pl.sinusoide.sinuschat.desktop.system.AppMode.session,
            enabled = !pl.sinusoide.sinuschat.desktop.system.AppMode.session) { on ->
            link.updateNotify { it.copy(background = on) }
        }
    }
}

@Composable
private fun LockPage(
    lock: LockManager,
    link: PhoneLink,
    proximity: pl.sinusoide.sinuschat.desktop.security.ProximityGuard?,
    onProximitySetup: () -> Unit,
    onPin: (List<PinMode>, () -> Unit) -> Unit,
    onToast: (String) -> Unit,
    onDisabled: () -> Unit
) {
    val s = LocalSettingsStrings.current
    val config by lock.config.collectAsState()
    val hello by produceState(WindowsHello.Availability.NONE) {
        value = withContext(Dispatchers.IO) { WindowsHello.availability() }
    }
    var helloBusy by remember { mutableStateOf(false) }
    var askWipe by remember { mutableStateOf(false) }
    var askDisable by remember { mutableStateOf(false) }
    var chooseRelock by remember { mutableStateOf(false) }
    var chooseRange by remember { mutableStateOf(false) }
    val yellow = SinusColors.warning
    val red = SinusColors.danger

    LaunchedEffect(config.hasPin) { if (!config.hasPin) onDisabled() }

    LaunchedEffect(helloBusy) {
        if (!helloBusy) return@LaunchedEffect
        val ok = withContext(Dispatchers.IO) { WindowsHello.verify(s.helloPrompt) }
        if (ok) lock.setBiometric(true)
        helloBusy = false
    }

    Spacer(Modifier.height(8.dp))
    Hero("ic_encrypted", SinusColors.online, s.privHeroProtected, s.lockHeroDesc) {
        Pill(s.pillPin, SinusColors.online)
        if (hello != WindowsHello.Availability.NONE) {
            Pill(if (config.biometric) s.pillHelloOn else s.pillHelloOff, if (config.biometric) SinusColors.online else SinusColors.textSecondary)
        }
        Pill(if (config.hasDuress) s.pillDuressOn else s.pillDuressOff, if (config.hasDuress) yellow else SinusColors.textSecondary)
    }

    SectionTitle(s.sectionPin)
    Card {
        NavRow("ic_changecode", SinusColors.primary, s.changePin, s.changePinDesc) {
            onPin(listOf(PinMode.SET)) { onToast(s.lockSet) }
        }
        RowDivider()
        SwitchRow("ic_visibility", SinusColors.primary, s.showDots, s.showDotsDesc, config.showDots) { lock.setShowDots(it) }
        RowDivider()
        SwitchRow("ic_check", SinusColors.primary, s.autoConfirm, s.autoConfirmDesc, config.autoConfirm) { lock.setAutoConfirm(it) }
        RowDivider()
        SwitchRow("ic_lock", SinusColors.primary, s.silent, s.silentDesc, config.silent, enabled = config.autoConfirm) { lock.setSilent(it) }
    }

    SectionTitle(s.sectionAuto)
    Card {
        val i = LockManager.RELOCK_OPTIONS.indexOf(config.relockMs).coerceAtLeast(0)
        NavRow(
            "ic_lock_clock", SinusColors.primary, s.autoTitle,
            if (config.relockMs == LockManager.RELOCK_NEVER) s.autoAfter[i] else s.autoDesc(s.autoAfter[i])
        ) { chooseRelock = true }
        RowDivider()
        SwitchRow("ic_visibility_off", SinusColors.primary, s.hideTitle, s.hideDesc, config.lockOnHide) { lock.setLockOnHide(it) }
        RowDivider()
        SwitchRow("ic_moon", SinusColors.primary, s.wakeTitle, s.wakeDesc, config.lockOnWake) { lock.setLockOnWake(it) }
        RowDivider()
        SwitchRow("ic_wifi", SinusColors.primary, s.disconnectTitle, s.disconnectDesc, config.lockOnDisconnect) { lock.setLockOnDisconnect(it) }
        RowDivider()
        val (proxText, proxWarn) = proximityStatus(config.proximity, link, proximity)
        SwitchRow(
            "ic_bluetooth_call", SinusColors.primary, s.proximityTitle, proxText, config.proximity,
            subtitleColor = if (proxWarn) yellow else SinusColors.textSecondary
        ) { on -> if (on) onProximitySetup() else lock.setProximity(false) }
        if (config.proximity) {
            RowDivider()
            NavRow("ic_new_phone", SinusColors.primary, s.rangeTitle, s.rangeOptions[config.proximityRange.coerceIn(0, 2)]) { chooseRange = true }
        }
    }

    if (hello != WindowsHello.Availability.NONE) {
        SectionTitle(s.sectionBio)
        Card {
            val usable = hello == WindowsHello.Availability.AVAILABLE || config.biometric
            SwitchRow(
                "ic_fingerprint", SinusColors.primary, s.helloTitle,
                if (usable) s.helloMore else s.helloSetupHint,
                config.biometric || helloBusy,
                enabled = usable && !helloBusy,
                subtitleColor = if (usable) SinusColors.textSecondary else yellow
            ) { on ->
                if (on) helloBusy = true else lock.setBiometric(false)
            }
        }
    }

    SectionTitle(s.sectionEmergency, yellow)
    Card {
        NavRow(
            if (config.hasDuress) "ic_duresspinchange" else "ic_duresspin", yellow,
            if (config.hasDuress) s.changeDuress else s.setDuress, s.duressDesc
        ) {
            val steps = if (config.hasDuress) listOf(PinMode.CONFIRM_DURESS, PinMode.SET_DURESS) else listOf(PinMode.SET_DURESS)
            onPin(steps) { onToast(s.duressSet) }
        }
        if (config.hasDuress) {
            RowDivider()
            NavRow("ic_duresspinoff", red, s.disableDuress, s.disableDuressDesc, titleColor = red) {
                onPin(listOf(PinMode.CONFIRM_DURESS)) {
                    lock.removeDuress()
                    onToast(s.duressDisabled)
                }
            }
        }
        RowDivider()
        SwitchRow("ic_danger", red, s.wipeTitle, s.wipeDesc, config.wipeOnFail) { on ->
            if (on) askWipe = true else lock.setWipeOnFail(false)
        }
    }

    SectionTitle(s.dangerZone, red)
    Card {
        NavRow("ic_codeoff", red, s.disableLock, s.disableLockDesc, titleColor = red) { askDisable = true }
    }

    if (askWipe) {
        ConfirmDialog("ic_danger", red, s.wipeConfirmTitle, s.wipeConfirmDesc, s.enable, onDismiss = { askWipe = false }) {
            askWipe = false
            lock.setWipeOnFail(true)
        }
    }
    if (askDisable) {
        ConfirmDialog("ic_codeoff", red, s.disableConfirmTitle, s.disableConfirmDesc, s.disableLock, onDismiss = { askDisable = false }) {
            askDisable = false
            lock.removePin()
            onToast(s.lockDisabled)
        }
    }
    if (chooseRange) {
        AlertDialog(
            onDismissRequest = { chooseRange = false },
            containerColor = SinusColors.card,
            title = { Text(s.rangeTitle, color = SinusColors.textPrimary) },
            text = {
                Column {
                    Text(s.rangeSheetDesc, color = SinusColors.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    for ((i, label) in s.rangeOptions.withIndex()) {
                        val selected = i == config.proximityRange
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(10.dp)))
                                .clickable {
                                    lock.setProximityRange(i)
                                    chooseRange = false
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label, color = if (selected) SinusColors.primary else SinusColors.textPrimary, fontSize = 15.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f)
                            )
                            if (selected) SinusIcon("ic_check", SinusColors.primary, Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chooseRange = false }) { Text(s.cancel, color = SinusColors.textSecondary) }
            }
        )
    }
    if (chooseRelock) {
        AlertDialog(
            onDismissRequest = { chooseRelock = false },
            containerColor = SinusColors.card,
            title = { Text(s.autoTitle, color = SinusColors.textPrimary) },
            text = {
                Column {
                    Text(s.autoSheetDesc, color = SinusColors.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    for ((i, ms) in LockManager.RELOCK_OPTIONS.withIndex()) {
                        val selected = ms == config.relockMs
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(10.dp)))
                                .clickable {
                                    lock.setRelockMs(ms)
                                    chooseRelock = false
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                s.autoOptions[i], color = if (selected) SinusColors.primary else SinusColors.textPrimary, fontSize = 15.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f)
                            )
                            if (selected) SinusIcon("ic_check", SinusColors.primary, Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chooseRelock = false }) { Text(s.cancel, color = SinusColors.textSecondary) }
            }
        )
    }
}

@Composable
private fun proximityStatus(
    enabled: Boolean,
    link: PhoneLink,
    guard: pl.sinusoide.sinuschat.desktop.security.ProximityGuard?
): Pair<String, Boolean> {
    val s = LocalSettingsStrings.current
    val idle = remember { kotlinx.coroutines.flow.MutableStateFlow(pl.sinusoide.sinuschat.desktop.security.ProximityReading(pl.sinusoide.sinuschat.desktop.security.ProximityStatus.OFF)) }
    val reading by (guard?.reading ?: idle).collectAsState()
    val phone by link.proximityState.collectAsState()
    val state by link.state.collectAsState()
    if (!enabled) return s.proximityDesc to false
    return when {
        !pl.sinusoide.sinuschat.desktop.system.Platform.isWindows -> s.proxOnlyWindows to true
        phone == pl.sinusoide.sinuschat.link.LinkFrame.PROXIMITY_PERMISSION -> s.proxPhonePermission to true
        phone == pl.sinusoide.sinuschat.link.LinkFrame.PROXIMITY_BLUETOOTH_OFF -> s.proxPhoneOff to true
        phone == pl.sinusoide.sinuschat.link.LinkFrame.PROXIMITY_UNSUPPORTED -> s.proxPhoneUnsupported to true
        reading.status == pl.sinusoide.sinuschat.desktop.security.ProximityStatus.BLUETOOTH_OFF -> s.proxPcOff to true
        reading.status == pl.sinusoide.sinuschat.desktop.security.ProximityStatus.UNSUPPORTED -> s.proxPcUnsupported to true
        reading.status == pl.sinusoide.sinuschat.desktop.security.ProximityStatus.NEAR -> s.proxNear(reading.rssi ?: 0) to false
        reading.status == pl.sinusoide.sinuschat.desktop.security.ProximityStatus.FAR -> s.proxFar(reading.rssi ?: 0) to true
        reading.status == pl.sinusoide.sinuschat.desktop.security.ProximityStatus.OFF || reading.status == pl.sinusoide.sinuschat.desktop.security.ProximityStatus.STARTING -> s.proxStarting to false
        state is LinkState.Connected && phone == null -> s.proxPhoneOld to true
        else -> s.proxSearching to false
    }
}

@Composable
private fun DevicesPage(link: PhoneLink, language: AppLanguage, onUnpair: () -> Unit) {
    val s = LocalSettingsStrings.current
    val strings = LocalStrings.current
    val chat = LocalChatStrings.current
    val state by link.state.collectAsState()
    var ask by remember { mutableStateOf(false) }
    val (name, connected) = when (val st = state) {
        is LinkState.Connected -> st.phoneName to true
        is LinkState.Searching -> st.phoneName to false
        is LinkState.Pairing -> "" to false
    }
    val since = link.pairedAt.takeIf { it > 0L }?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(language.locale))
    }
    Spacer(Modifier.height(8.dp))
    Hero(
        "ic_new_phone", teal,
        if (connected) strings.connected(name) else strings.searching(name),
        s.devicesHeroDesc
    ) {
        Pill(if (connected) s.statusConnected else s.statusSearching, if (connected) SinusColors.online else SinusColors.textSecondary)
    }
    SectionTitle(s.sectionDevices)
    Card {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile("ic_new_phone", teal)
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { s.devices }, color = SinusColors.textPrimary, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (connected) s.statusConnected else s.statusSearching,
                    color = if (connected) SinusColors.online else SinusColors.textSecondary, fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (since != null) {
                    Text(s.pairedSince(since), color = SinusColors.textSecondary, fontSize = 12.5.sp)
                }
            }
        }
    }
    SectionTitle(s.dangerZone, SinusColors.danger)
    Card {
        NavRow("ic_link", SinusColors.danger, strings.unpair, chat.unpairConfirm, titleColor = SinusColors.danger) { ask = true }
    }
    if (ask) {
        ConfirmDialog("ic_link", SinusColors.danger, strings.unpair, chat.unpairConfirm, strings.unpair, onDismiss = { ask = false }) {
            ask = false
            onUnpair()
        }
    }
}

@Composable
private fun ThemesPage(link: PhoneLink) {
    val s = LocalSettingsStrings.current
    val current by link.theme.collectAsState()
    val names = mapOf(
        Themes.FLOW to s.themeFlow, Themes.DARK to s.themeDark, Themes.LIGHT to s.themeLight,
        Themes.SYSTEM to s.themeSystem, Themes.RETRO to s.themeRetro
    )
    val descs = mapOf(
        Themes.FLOW to s.descFlow, Themes.DARK to s.descDark, Themes.LIGHT to s.descLight,
        Themes.SYSTEM to s.descSystem, Themes.RETRO to s.descRetro
    )
    SectionTitle(s.appTheme)
    for ((r, pair) in Themes.ORDER.chunked(2).withIndex()) {
        Row(Modifier.fillMaxWidth().padding(top = if (r > 0) 12.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (key in pair) {
                val enabled = key != Themes.SYSTEM || Themes.systemAvailable
                ThemeTile(
                    name = names[key].orEmpty(),
                    palette = if (key == Themes.SYSTEM && !Themes.systemAvailable) Themes.dark else Themes.of(key),
                    selected = key == current,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { if (key != current) link.setTheme(key) }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Column(
        Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(18.dp))).background(SinusColors.card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(s.themeSelected(names[current] ?: s.themeDark), color = SinusColors.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        Text(descs[current] ?: s.descDark, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (!Themes.systemAvailable) {
        Text(
            s.systemUnavailable, color = SinusColors.textSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 12.dp, end = 12.dp)
        )
    }
}

@Composable
private fun ThemeTile(name: String, palette: Palette, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(18.dp))
    Column(
        modifier.alpha(if (enabled) 1f else 0.45f).clip(shape).background(SinusColors.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) SinusColors.primary else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            MiniChat(palette)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(SinusColors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    SinusIcon("ic_check", Color.White, Modifier.size(14.dp))
                }
            }
        }
        Text(
            name, color = if (selected) SinusColors.primary else SinusColors.textPrimary, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
        )
        Box(
            Modifier.padding(top = 6.dp).size(12.dp).clip(CircleShape)
                .background(if (selected) SinusColors.primary else Color.Transparent)
                .then(if (selected) Modifier else Modifier.border(2.dp, SinusColors.textSecondary, CircleShape))
        )
    }
}

@Composable
private fun MiniChat(p: Palette) {
    val shape = RoundedCornerShape(if (p.square) 2.dp else 12.dp)
    Box(Modifier.fillMaxWidth().height(168.dp).clip(shape).background(p.backgroundSolid)) {
        if (p.backdrop) ThemeBackdrop(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(24.dp).background(p.surface).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(p.primary))
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(34.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(p.text.copy(alpha = 0.6f)))
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
                MiniBubble(p.bubbleOther, 0.62f, 18, end = false, square = p.square)
                MiniBubble(p.bubbleMine, 0.55f, 18, end = true, square = p.square)
                MiniBubble(p.bubbleOther, 0.42f, 14, end = false, square = p.square)
                MiniBubble(p.bubbleMine, 0.35f, 14, end = true, square = p.square)
            }
            Box(Modifier.fillMaxWidth().height(22.dp).background(p.surface).padding(horizontal = 6.dp, vertical = 5.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (p.square) 2.dp else 7.dp)).background(p.text.copy(alpha = 0.12f)))
            }
        }
    }
}

@Composable
private fun ColumnScope.MiniBubble(color: Color, fraction: Float, heightDp: Int, end: Boolean, square: Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
        if (end) Spacer(Modifier.weight(1f - fraction))
        Box(Modifier.weight(fraction).height(heightDp.dp).clip(RoundedCornerShape(if (square) 2.dp else 7.dp)).background(color))
        if (!end) Spacer(Modifier.weight(1f - fraction))
    }
}

@Composable
internal fun SectionTitle(text: String, color: Color = SinusColors.primary) {
    Text(
        text.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.72.sp,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(18.dp))).background(SinusColors.card),
        content = content
    )
}

@Composable
internal fun RowDivider(start: androidx.compose.ui.unit.Dp = 68.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(SinusColors.divider))
}

@Composable
internal fun IconTile(icon: String, color: Color) {
    Box(
        Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(color.copy(alpha = 0x24 / 255f)),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon(icon, color, Modifier.size(22.dp))
    }
}

@Composable
internal fun NavRow(
    icon: String,
    color: Color,
    title: String,
    subtitle: String,
    titleColor: Color = SinusColors.textPrimary,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 15.5.sp)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        SinusIcon("ic_chevron_right", SinusColors.textSecondary, Modifier.size(20.dp))
    }
}

@Composable
internal fun SwitchRow(
    icon: String,
    color: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    subtitleColor: Color = SinusColors.textSecondary,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled) { onChange(!checked) }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            Text(subtitle, color = subtitleColor, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SinusColors.primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF2A3A4A),
                uncheckedThumbColor = Color(0xFF8AA0B8),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
internal fun Hero(icon: String, color: Color, title: String, desc: String, pills: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(20.dp))).background(SinusColors.card)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(color.copy(alpha = 0x26 / 255f)), contentAlignment = Alignment.Center) {
            SinusIcon(icon, color, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = SinusColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            desc, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pills() }
    }
}

@Composable
internal fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(SinusColors.radius(14.dp))).background(color.copy(alpha = 0x24 / 255f))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
internal fun ConfirmDialog(
    icon: String,
    color: Color,
    title: String,
    desc: String,
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val s = LocalSettingsStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SinusColors.card,
        icon = {
            Box(Modifier.size(64.dp).clip(CircleShape).background(color.copy(alpha = 0x26 / 255f)), contentAlignment = Alignment.Center) {
                SinusIcon(icon, color, Modifier.size(32.dp))
            }
        },
        title = { Text(title, color = SinusColors.textPrimary, textAlign = TextAlign.Center) },
        text = { Text(desc, color = SinusColors.textSecondary, textAlign = TextAlign.Center) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(action, color = color, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel, color = SinusColors.textSecondary) }
        }
    )
}
