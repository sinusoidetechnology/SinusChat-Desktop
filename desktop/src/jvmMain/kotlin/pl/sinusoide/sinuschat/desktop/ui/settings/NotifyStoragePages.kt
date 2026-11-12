package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.NotifyPrefs
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.SecureStore
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.StorageUsage
import pl.sinusoide.sinuschat.desktop.i18n.FileStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.NotifyStrings
import pl.sinusoide.sinuschat.desktop.i18n.SafeStrings
import pl.sinusoide.sinuschat.desktop.i18n.StorageStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.chat.Avatar
import pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions
import pl.sinusoide.sinuschat.desktop.ui.chat.avatarKey
import pl.sinusoide.sinuschat.desktop.ui.chat.rememberAvatar
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import java.time.LocalTime

internal val notifyAmber = Color(0xFFE5A50A)
internal val storageGreen = Color(0xFF3FB97A)
private val purple = Color(0xFF9B6BE0)
private val pinkTone = Color(0xFFE0578B)
private val tealTone = Color(0xFF26A5B8)
private val orange = Color(0xFFE67E22)
private val green = Color(0xFF3FB97A)
private val red = Color(0xFFE5484D)

internal fun minutesNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

@Composable
internal fun notifySummary(prefs: NotifyPrefs): String {
    val ns = NotifyStrings.of(LocalChatStrings.current)
    return when {
        !prefs.messages && !prefs.groups -> ns.stateOff
        prefs.quietAt(minutesNow()) -> ns.heroQuiet
        else -> ns.stateOn
    }
}

@Composable
internal fun NotificationsPage(link: PhoneLink, onTest: ((String, String) -> Unit)?, localNote: String, onToast: (String) -> Unit) {
    val ns = NotifyStrings.of(LocalChatStrings.current)
    val safe = SafeStrings.of(LocalChatStrings.current)
    val newMessage = LocalChatStrings.current.newMessage
    val prefs by link.notifyPrefs.collectAsState()
    val safety by link.safetyPrompts.collectAsState()
    val now by produceState(minutesNow()) {
        while (true) {
            delay(30_000)
            value = minutesNow()
        }
    }
    var previewOpen by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf<Boolean?>(null) }
    val accent = SinusColors.primary

    Spacer(Modifier.height(8.dp))
    when {
        !prefs.messages && !prefs.groups -> Hero("ic_notifications_off", red, ns.heroOff, ns.heroOffDesc) { Pill(ns.stateOff, red) }
        prefs.quietAt(now) -> Hero("ic_moon", purple, ns.heroQuiet, ns.heroQuietDesc(NotifyPrefs.format(prefs.quietTo))) { Pill(ns.stateOn, green) }
        else -> Hero("ic_notifications", green, ns.heroOk, ns.heroOkDesc) { Pill(ns.stateOn, green) }
    }
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(14.dp)))
            .background(SinusColors.primary.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon("ic_pc", SinusColors.primary, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(localNote, color = SinusColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
    }

    SectionTitle(ns.sectionMessages)
    Card {
        SwitchRow("ic_chat", accent, ns.privateChats, ns.privateDesc, prefs.messages) { on -> link.updateNotify { it.copy(messages = on) } }
        RowDivider()
        SwitchRow("ic_group", purple, ns.groups, ns.groupsDesc, prefs.groups) { on -> link.updateNotify { it.copy(groups = on) } }
        RowDivider()
        ValueRow("ic_visibility", pinkTone, ns.preview, null, previewLabel(prefs.preview, ns)) { previewOpen = true }
        RowDivider()
        SwitchRow("ic_settings", tealTone, ns.inApp, ns.inAppDesc, prefs.inApp) { on -> link.updateNotify { it.copy(inApp = on) } }
    }

    SectionTitle(ns.sectionQuiet)
    Card {
        SwitchRow("ic_moon", purple, ns.quiet, ns.quietDesc, prefs.quiet) { on -> link.updateNotify { it.copy(quiet = on) } }
        RowDivider()
        ValueRow("ic_timer", purple, ns.quietFrom, null, NotifyPrefs.format(prefs.quietFrom), enabled = prefs.quiet) { pickFrom = true }
        RowDivider()
        ValueRow("ic_timer", purple, ns.quietTo, null, NotifyPrefs.format(prefs.quietTo), enabled = prefs.quiet) { pickFrom = false }
        RowDivider()
        SwitchRow("ic_call_answer", green, ns.quietCalls, ns.quietCallsDesc, prefs.quietCalls, enabled = prefs.quiet) { on ->
            link.updateNotify { it.copy(quietCalls = on) }
        }
    }

    SectionTitle(safe.section)
    Card {
        SwitchRow("ic_privacy_warning", accent, safe.toggle, safe.toggleDesc, safety) { link.setSafetyPrompts(it) }
    }

    val updater = pl.sinusoide.sinuschat.desktop.update.LocalUpdater.current
    if (updater != null) {
        val updateChat = LocalChatStrings.current
        val us = remember(updateChat) { pl.sinusoide.sinuschat.desktop.i18n.UpdateStrings.of(updateChat) }
        SectionTitle(us.section)
        Card {
            SwitchRow("ic_update", accent, us.notice, us.noticeDesc, prefs.updates) { on -> link.updateNotify { it.copy(updates = on) } }
        }
    }

    SectionTitle(ns.sectionSystem)
    Card {
        val canOpen = pl.sinusoide.sinuschat.desktop.system.Platform.canOpenSettings(pl.sinusoide.sinuschat.desktop.system.Platform.Settings.NOTIFICATIONS)
        if (canOpen) {
            ValueRow("ic_settings", tealTone, ns.sysChannel, ns.sysChannelDesc, "") {
                pl.sinusoide.sinuschat.desktop.system.Platform.openSettings(pl.sinusoide.sinuschat.desktop.system.Platform.Settings.NOTIFICATIONS)
            }
        }
        if (onTest != null) {
            if (canOpen) RowDivider()
            ValueRow("ic_send", accent, ns.sysTest, ns.sysTestDesc, "") {
                val hidden = prefs.preview == NotifyPrefs.PREVIEW_HIDDEN
                onTest(if (hidden) "SinusChat" else ns.testTitle, if (hidden) newMessage else ns.testText)
                onToast(ns.testSent)
            }
        }
    }

    Text(
        ns.footer, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp)
    )

    if (previewOpen) {
        PreviewDialog(prefs.preview, ns, onDismiss = { previewOpen = false }) { mode ->
            link.updateNotify { it.copy(preview = mode) }
            previewOpen = false
        }
    }
    pickFrom?.let { from ->
        TimeDialog(
            title = if (from) ns.quietFrom else ns.quietTo,
            minutes = if (from) prefs.quietFrom else prefs.quietTo,
            onDismiss = { pickFrom = null }
        ) { m ->
            link.updateNotify { if (from) it.copy(quietFrom = m) else it.copy(quietTo = m) }
            pickFrom = null
        }
    }
}

private fun previewLabel(mode: String, ns: NotifyStrings) = when (mode) {
    NotifyPrefs.PREVIEW_NAME -> ns.previewName
    NotifyPrefs.PREVIEW_HIDDEN -> ns.previewHiddenShort
    else -> ns.previewFull
}

@Composable
internal fun ValueRow(
    icon: String,
    color: Color,
    title: String,
    subtitle: String?,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value.isNotEmpty()) Text(value, color = SinusColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        SinusIcon("ic_chevron_right", SinusColors.textSecondary, Modifier.padding(start = 4.dp).size(20.dp))
    }
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(24.dp)))
            .background(if (SinusColors.palette.backdrop) Color(0xFF16202C) else SinusColors.surface)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp).size(36.dp, 4.dp)
                .clip(RoundedCornerShape(3.dp)).background(SinusColors.textSecondary.copy(alpha = 0x55 / 255f))
        )
        content()
    }
}

@Composable
private fun PreviewDialog(current: String, ns: NotifyStrings, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        SheetCard {
            Text(
                ns.preview, color = SinusColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )
            Card {
                val options = listOf(
                    Triple(NotifyPrefs.PREVIEW_FULL, ns.previewFull, ns.previewFullDesc),
                    Triple(NotifyPrefs.PREVIEW_NAME, ns.previewName, ns.previewNameDesc),
                    Triple(NotifyPrefs.PREVIEW_HIDDEN, ns.previewHidden, ns.previewHiddenDesc)
                )
                options.forEachIndexed { i, (mode, title, desc) ->
                    if (i > 0) RowDivider()
                    val (icon, color) = when (mode) {
                        NotifyPrefs.PREVIEW_FULL -> "ic_chat" to SinusColors.primary
                        NotifyPrefs.PREVIEW_NAME -> "ic_visibility" to pinkTone
                        else -> "ic_lock" to purple
                    }
                    OptionRow(icon, color, title, desc, selected = mode == current) { onPick(mode) }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(icon: String, color: Color, title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            Text(desc, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .then(
                    if (selected) Modifier.background(SinusColors.primary)
                    else Modifier.border(2.dp, SinusColors.textSecondary.copy(alpha = 0.5f), CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) SinusIcon("ic_check", Color.White, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var hour by remember { mutableIntStateOf(minutes / 60) }
    var minute by remember { mutableIntStateOf(minutes % 60) }
    val cancel = LocalChatStrings.current.cancel
    Dialog(onDismissRequest = onDismiss) {
        SheetCard {
            Text(
                title, color = SinusColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Stepper("%02d".format(hour), onUp = { hour = (hour + 1) % 24 }, onDown = { hour = (hour + 23) % 24 })
                Text(":", color = SinusColors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                Stepper("%02d".format(minute), onUp = { minute = (minute + 5) % 60 }, onDown = { minute = (minute + 55) % 60 })
            }
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(26.dp))).background(SinusColors.primary)
                    .clickable { onPick(hour * 60 + minute) }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("OK", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.fillMaxWidth().clickable(onClick = onDismiss).pointerHoverIcon(PointerIcon.Hand).padding(top = 14.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(cancel, color = SinusColors.textSecondary, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepButton("ic_chevron_down", 180f, onUp)
        Box(
            Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(SinusColors.radius(16.dp))).background(SinusColors.card)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(value, color = SinusColors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        StepButton("ic_chevron_down", 0f, onDown)
    }
}

@Composable
private fun StepButton(icon: String, rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon(icon, SinusColors.textSecondary, Modifier.size(26.dp).rotate(rotation))
    }
}

@Composable
internal fun StoragePage(store: ChatStore?, onOpen: (StorageFilter) -> Unit = {}) {
    val ss = StorageStrings.of(LocalChatStrings.current)
    val fs = FileStrings.of(LocalChatStrings.current)
    if (store == null) return
    val vault = store.media.vault
    val version by vault.version.collectAsState()
    val chats by store.chats.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val snap by produceState<StorageUsage.Snapshot?>(null, version, refresh) {
        value = withContext(Dispatchers.IO) {
            val temp = listOf(MediaActions.openDir, MediaActions.sandboxFolder, MediaActions.pasteFolder)
            val tempBytes = temp.sumOf { StorageUsage.folderBytes(it) }
            StorageUsage.snapshot(
                vault.entries(),
                cacheBytes = store.media.cacheBytes() + tempBytes,
                appBytes = StorageUsage.folderBytes(SecureStore.dir) + tempBytes,
                disk = SecureStore.dir
            )
        }
    }
    val scope = rememberCoroutineScope()
    var askCache by remember { mutableStateOf(false) }
    var askMedia by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3_000)
            toast = null
        }
    }
    val s = snap ?: return
    val accent = SinusColors.primary
    val track = SinusColors.textSecondary.copy(alpha = 0x26 / 255f)
    val other = SinusColors.textSecondary.copy(alpha = 0x99 / 255f)

    Spacer(Modifier.height(8.dp))
    StorageHero(s, ss, accent, track, other)

    SectionTitle(ss.sectionTypes)
    Card {
        val max = s.kinds.maxOf { it.bytes }.coerceAtLeast(1L)
        s.kinds.forEachIndexed { i, k ->
            if (i > 0) RowDivider()
            val (icon, color, label) = when (k.kind) {
                StorageUsage.Kind.IMAGE -> Triple("ic_image", accent, ss.kindImages)
                StorageUsage.Kind.VIDEO -> Triple("ic_video", purple, ss.kindVideos)
                StorageUsage.Kind.VOICE -> Triple("ic_mic", green, ss.kindVoice)
                StorageUsage.Kind.DOCUMENT -> Triple("ic_document", orange, ss.kindDocs)
            }
            UsageRow({ IconTile(icon, color) }, label, ss.files(k.count), MediaActions.formatSize(k.bytes).ifEmpty { "0 B" },
                k.bytes.toFloat() / max, color, track) { onOpen(StorageFilter(k.kind, null, label)) }
        }
    }

    SectionTitle(ss.sectionManage)
    Card {
        ActionRow("ic_delete", red, ss.cacheTitle, ss.cacheDesc, MediaActions.formatSize(s.cacheBytes).ifEmpty { "0 B" }) {
            if (s.cacheBytes <= 0L) toast = ss.cacheEmpty else askCache = true
        }
        RowDivider()
        ActionRow("ic_download", accent, fs.storageClear, if (s.mediaBytes > 0) fs.storageDesc(MediaActions.formatSize(s.mediaBytes)) else fs.storageEmpty,
            MediaActions.formatSize(s.mediaBytes).ifEmpty { "0 B" }) {
            if (s.mediaBytes > 0) askMedia = true else toast = fs.storageEmpty
        }
    }

    SectionTitle(ss.chatsCaps)
    if (s.conversations.isEmpty()) {
        EmptyStorageCard(ss, track)
    } else {
        Card {
            val max = s.conversations.first().bytes.coerceAtLeast(1L)
            s.conversations.forEachIndexed { i, c ->
                if (i > 0) RowDivider()
                val chat = chats.firstOrNull { it.id == c.conv }
                val name = chat?.title?.takeIf { it.isNotBlank() } ?: ss.unknownChat
                UsageRow(
                    {
                        Box(Modifier.padding(end = 14.dp)) {
                            Avatar(name, chat?.color, 40.dp, if (chat != null) rememberAvatar(store, chat.id, chat.avatarKey) else null)
                        }
                    },
                    name, ss.files(c.count), MediaActions.formatSize(c.bytes).ifEmpty { "0 B" }, c.bytes.toFloat() / max, accent, track
                ) { onOpen(StorageFilter(null, c.conv, name)) }
            }
        }
    }

    Text(
        ss.storageFooter, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp)
    )
    toast?.let {
        Text(
            it, color = SinusColors.text, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(SinusColors.radius(14.dp)))
                .background(SinusColors.card).padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }

    if (askCache) {
        ConfirmDialog("ic_delete", red, ss.cacheClearQ, ss.cacheClearDesc, ss.clear + " · " + MediaActions.formatSize(s.cacheBytes), onDismiss = { askCache = false }) {
            askCache = false
            val before = s.cacheBytes
            scope.launch(Dispatchers.IO) {
                store.media.clearCache()
                MediaActions.cleanTemp()
                toast = if (before > 0) ss.freed(MediaActions.formatSize(before)) else ss.cacheEmpty
                refresh++
            }
        }
    }
    if (askMedia) {
        ConfirmDialog("ic_delete", red, fs.storageClearTitle, fs.storageClearDesc, fs.storageClear, onDismiss = { askMedia = false }) {
            askMedia = false
            val before = s.mediaBytes
            scope.launch(Dispatchers.IO) {
                vault.wipe()
                toast = ss.freed(MediaActions.formatSize(before))
                refresh++
            }
        }
    }
}

@Composable
private fun StorageHero(s: StorageUsage.Snapshot, ss: StorageStrings, accent: Color, track: Color, other: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(20.dp))).background(SinusColors.card)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(end = 14.dp).size(52.dp).clip(CircleShape).background(accent.copy(alpha = 0x26 / 255f)),
                contentAlignment = Alignment.Center
            ) {
                SinusIcon("ic_storage", accent, Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                val parts = MediaActions.formatSize(s.appBytes).ifEmpty { "0 B" }.split(" ")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(parts.getOrElse(0) { "0" }, color = SinusColors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                    Text(
                        parts.getOrElse(1) { "B" }, color = SinusColors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 5.dp, bottom = 3.dp)
                    )
                }
                Text(ss.heroLabel, color = SinusColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        val total = s.totalBytes.coerceAtLeast(1L).toFloat()
        val app = maxOf(s.appBytes / total, 0.02f).coerceAtMost(1f)
        val others = ((s.totalBytes - s.freeBytes - s.appBytes).coerceAtLeast(0L) / total).coerceAtMost(1f - app)
        Row(
            Modifier.padding(top = 18.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(track)
        ) {
            Box(Modifier.weight(app).fillMaxHeight().background(accent))
            Box(Modifier.width(2.dp).fillMaxHeight().background(SinusColors.card))
            if (others > 0f) Box(Modifier.weight(others).fillMaxHeight().background(other))
            val rest = 1f - app - others
            if (rest > 0.0001f) Spacer(Modifier.weight(rest))
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("SinusChat", accent)
            Spacer(Modifier.width(14.dp))
            Legend(ss.legendOther, other)
            Spacer(Modifier.width(14.dp))
            Legend(ss.free, track)
        }
        Box(Modifier.padding(top = 14.dp, bottom = 12.dp).fillMaxWidth().height(1.dp).background(SinusColors.divider))
        Text(
            ss.summary(MediaActions.formatSize(s.freeBytes), MediaActions.formatSize(s.totalBytes)),
            color = SinusColors.textSecondary, fontSize = 13.sp
        )
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 6.dp).size(9.dp).clip(CircleShape).background(color))
        Text(label, color = SinusColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun UsageRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    size: String,
    fraction: Float,
    barColor: Color,
    track: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, color = SinusColors.textPrimary, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(size, color = SinusColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            Text(subtitle, color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp, bottom = 7.dp))
            Bar(fraction, barColor, track, 5.dp)
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, track: Color, height: Dp) {
    val f = fraction.coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(3.dp)).background(track)) {
        if (f > 0f) {
            val shown = maxOf(f, 0.02f)
            Box(Modifier.weight(shown).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
            if (shown < 1f) Spacer(Modifier.weight(1f - shown))
        }
    }
}

@Composable
private fun ActionRow(icon: String, color: Color, title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            Text(subtitle, color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(value, color = SinusColors.textSecondary, fontSize = 13.5.sp)
        SinusIcon("ic_chevron_right", SinusColors.textSecondary, Modifier.padding(start = 6.dp).size(20.dp))
    }
}

@Composable
private fun EmptyStorageCard(ss: StorageStrings, track: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(18.dp))).background(SinusColors.card)
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.padding(bottom = 12.dp).size(52.dp).clip(CircleShape).background(track), contentAlignment = Alignment.Center) {
            SinusIcon("ic_image", SinusColors.textSecondary, Modifier.size(26.dp))
        }
        Text(ss.noSavedMedia, color = SinusColors.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(ss.emptyDesc, color = SinusColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
