package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.ContactStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.lastSeenAgo
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.dialogIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkFrame
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val orange = Color(0xFFF39C12)
private val shortDate = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val linkPattern = Regex("""(?:https?://|www\.|[a-zA-Z0-9-]+\.)[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/\S*)?""", RegexOption.IGNORE_CASE)

internal const val HOUR = 3_600_000L
internal const val DAY = 24 * HOUR
private val TTL_PRESETS = listOf(0L, 5_000L, 30_000L, 5 * 60_000L, 30 * 60_000L, HOUR, 8 * HOUR, DAY, 7 * DAY, 28 * DAY)

internal enum class ContactSheet { NICK, MEDIA, TTL, MUTE, SAFETY, CLEAR, BLOCK }

internal fun ttlLabel(ms: Long, cs: ContactStrings): String {
    if (ms <= 0L) return cs.timeOff
    val sec = ms / 1000L
    return when {
        sec < 60 -> cs.timeSec(sec.toInt())
        sec < 3600 -> cs.timeMin((sec / 60).toInt())
        sec < 86400 -> cs.timeHour((sec / 3600).toInt())
        else -> cs.timeDay((sec / 86400).toInt())
    }
}

private fun muteLabel(until: Long, cs: ContactStrings): String {
    val now = System.currentTimeMillis()
    return when {
        until == LinkFrame.MUTE_FOREVER -> cs.muteAlways
        until <= now -> cs.muteOff
        else -> cs.muteUntil(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).format(shortDate))
    }
}

private fun stamp(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(shortDate)

@Composable
internal fun ContactPanel(
    store: ChatStore,
    chat: ChatSummary,
    convId: String,
    sheet: ContactSheet?,
    onSheet: (ContactSheet?) -> Unit,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onJump: (String) -> Unit,
    onOpenMedia: (ChatMessage) -> Unit
) {
    val s = LocalChatStrings.current
    val cs = remember(s) { ContactStrings.of(s) }
    val callStrings = remember(s) { pl.sinusoide.sinuschat.desktop.i18n.CallStrings.of(s) }
    val info by store.contact.collectAsState()
    val contact = info?.takeIf { it.conv == convId }
    LaunchedEffect(convId) { store.requestContact(convId) }
    val name = contact?.name?.ifBlank { null } ?: chat.title.ifBlank { s.contact }
    val image = rememberAvatar(store, convId, chat.avatarKey)
    fun update(u: LinkFrame.ContactUpdate, toast: String? = null) {
        if (store.updateContact(u) && toast != null) store.emit(ChatEvent.Info(toast))
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x55000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth()
                .shadow(18.dp).background(SinusColors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction("ic_close", s.cancel, SinusColors.textPrimary, onClick = onClose)
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name, chat.color, 110.dp, image)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        name, color = SinusColors.textPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    val real = contact?.realName.orEmpty()
                    if (contact != null && contact.nick.isNotBlank() && real.isNotBlank() && real != contact.nick) {
                        Text(cs.localNameReal(real), color = SinusColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    val now by produceState(System.currentTimeMillis()) {
                        while (true) {
                            value = System.currentTimeMillis()
                            kotlinx.coroutines.delay(30_000L)
                        }
                    }
                    val status = when {
                        contact?.blocked == true -> ""
                        chat.online -> cs.activeNow
                        chat.lastSeen > 0L -> s.lastSeenAgo(now - chat.lastSeen)
                        else -> ""
                    }
                    if (status.isNotEmpty()) {
                        Text(
                            status, color = if (chat.online) SinusColors.active else SinusColors.textSecondary, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        RoundAction("ic_normal_call", cs.callAudio) { onClose(); store.calls.start(convId, name, false) }
                        RoundAction("ic_call_video_on", cs.video) { onClose(); store.calls.start(convId, name, true) }
                        RoundAction("ic_search", cs.search, onSearch)
                        RoundAction("ic_download", pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings.of(pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings.current).downloadPhoto) {
                            AvatarExport.save(store, name, chat.color, image)
                        }
                    }
                }
                Separator()
                PanelRow("ic_edit", SinusColors.primary, cs.localName) { onSheet(ContactSheet.NICK) }
                PanelRow("ic_image", SinusColors.primary, cs.media) { onSheet(ContactSheet.MEDIA) }
                PanelRow(
                    "ic_clock", SinusColors.primary, cs.disappearing,
                    when {
                        contact == null -> ""
                        contact.awaitingTtl >= 0L -> cs.awaiting(name)
                        else -> ttlLabel(contact.ttlMs, cs)
                    }
                ) { if (contact != null) onSheet(ContactSheet.TTL) }
                PanelRow("ic_notifications_off", SinusColors.primary, cs.mute, contact?.let { muteLabel(it.muteUntil, cs) }.orEmpty()) {
                    if (contact != null) onSheet(ContactSheet.MUTE)
                }
                if (contact?.translate == true) {
                    SwitchPanelRow("ic_translate", cs.translateBlock, cs.translateBlockDesc, contact.translateBlocked) { on ->
                        update(LinkFrame.ContactUpdate(convId, translateBlocked = on), if (on) cs.translateBlocked else cs.translateUnblocked)
                    }
                }
                PanelRow("ic_shield", SinusColors.primary, cs.verify) { onSheet(ContactSheet.SAFETY) }
                Separator()
                PanelRow("ic_delete", orange, cs.clearChat) { onSheet(ContactSheet.CLEAR) }
                if (contact?.blocked == true) {
                    PanelRow("ic_block", SinusColors.textPrimary, cs.unblock) {
                        update(LinkFrame.ContactUpdate(convId, blocked = false), cs.unblocked)
                    }
                } else {
                    PanelRow("ic_block", SinusColors.danger, cs.blockUser, danger = true) { if (contact != null) onSheet(ContactSheet.BLOCK) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        when (sheet) {
            ContactSheet.NICK -> NickDialog(contact?.nick.orEmpty(), contact?.realName?.ifBlank { null } ?: name, cs, { onSheet(null) }) { nick ->
                onSheet(null)
                update(LinkFrame.ContactUpdate(convId, nick = nick), if (nick.isBlank()) cs.localNameRemoved else cs.localNameSaved)
            }
            ContactSheet.MUTE -> contact?.let { c ->
                ChoiceDialog(
                    cs.mute, cs.muteDesc,
                    listOf(0L to cs.disabled, HOUR to cs.mute1h, 8 * HOUR to cs.mute8h, DAY to cs.oneDay, 7 * DAY to cs.oneWeek, LinkFrame.MUTE_FOREVER to cs.muteForever),
                    current = when {
                        c.muteUntil == LinkFrame.MUTE_FOREVER -> LinkFrame.MUTE_FOREVER
                        c.muteUntil <= System.currentTimeMillis() -> 0L
                        else -> -2L
                    },
                    cs = cs, onDismiss = { onSheet(null) }
                ) { picked ->
                    onSheet(null)
                    val until = when (picked) {
                        0L -> 0L
                        LinkFrame.MUTE_FOREVER -> LinkFrame.MUTE_FOREVER
                        else -> System.currentTimeMillis() + picked
                    }
                    update(LinkFrame.ContactUpdate(convId, muteUntil = until))
                }
            }
            ContactSheet.TTL -> contact?.let { c ->
                ChoiceDialog(
                    cs.disappearingTitle, cs.disappearingDesc,
                    TTL_PRESETS.map { it to if (it == 0L) cs.disabled else ttlLabel(it, cs) },
                    current = if (c.awaitingTtl >= 0L) c.awaitingTtl else c.ttlMs,
                    cs = cs, onDismiss = { onSheet(null) }
                ) { picked ->
                    onSheet(null)
                    update(LinkFrame.ContactUpdate(convId, ttlMs = picked, trigger = c.trigger.ifBlank { LinkFrame.TRIGGER_READ }))
                }
            }
            ContactSheet.MEDIA -> MediaDialog(store, convId, name, cs, { onSheet(null) }, { id -> onSheet(null); onClose(); onJump(id) }) { m ->
                onSheet(null)
                onClose()
                onOpenMedia(m)
            }
            ContactSheet.SAFETY -> SafetyDialog(store, convId, name, cs) { onSheet(null) }
            ContactSheet.CLEAR -> ConfirmBox(cs.clearChat, cs.clearChatDesc, emptyList(), cs.clearWord, orange, cs, { onSheet(null) }) {
                onSheet(null)
                update(LinkFrame.ContactUpdate(convId, clearChat = true), cs.chatCleared)
            }
            ContactSheet.BLOCK -> ConfirmBox(
                cs.blockQ(name), "", listOf(cs.blockFactProfile, cs.blockFactServer, cs.blockFactUndo), cs.block, SinusColors.danger, cs,
                { onSheet(null) }
            ) {
                onSheet(null)
                update(LinkFrame.ContactUpdate(convId, blocked = true), cs.blockedToast(name))
            }
            null -> Unit
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(SinusColors.border))
}

@Composable
private fun RoundAction(icon: String, label: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(
        Modifier.clip(RoundedCornerShape(SinusColors.radius(14.dp)))
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (hovered) SinusColors.surfaceHigh else SinusColors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon(icon, SinusColors.textPrimary, Modifier.size(24.dp), label)
        }
        Text(label, color = SinusColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PanelRow(icon: String, tint: Color, title: String, subtitle: String = "", danger: Boolean = false, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon(icon, tint, Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) SinusColors.danger else SinusColors.textPrimary, fontSize = 15.5.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = SinusColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun SwitchPanelRow(icon: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon(icon, SinusColors.primary, Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp)
            Text(subtitle, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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
private fun PanelDialog(title: String, cs: ContactStrings, maxWidth: Int, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Box(
        Modifier.fillMaxSize().background(Color(0x55000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = maxWidth.dp).fillMaxWidth().heightIn(max = 620.dp).dialogIn()
                .shadow(if (SinusColors.palette.backdrop) 0.dp else 16.dp, shape).clip(shape).background(sheetFill)
                .border(1.dp, sheetStroke, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = SinusColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconAction("ic_close", cs.cancel, SinusColors.textSecondary, onClick = onDismiss)
            }
            content()
        }
    }
}

@Composable
private fun DialogButtons(cs: ContactStrings, confirm: String, color: Color, enabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            cs.cancel, color = SinusColors.textSecondary, fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable(onClick = onDismiss)
                .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 10.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(SinusColors.radius(22.dp))).background(if (enabled) color else color.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onConfirm).pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Text(confirm, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun NickDialog(current: String, real: String, cs: ContactStrings, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val changed = value.text.trim() != current.trim()
    PanelDialog(cs.localNameTitle, cs, 440, onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text(cs.localNameDesc, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 14.dp))
            val fieldShape = RoundedCornerShape(SinusColors.radius(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(fieldShape).background(SinusColors.surfaceVariant)
                    .border(1.5.dp, SinusColors.primary.copy(alpha = 0.7f), fieldShape).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(cs.localNameLabel, color = SinusColors.primary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().heightIn(min = 26.dp).padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) Text(real, color = SinusColors.textMuted, fontSize = 16.sp, maxLines = 1)
                    BasicTextField(
                        value = value,
                        onValueChange = { if (it.text.length <= 50) value = it.copy(text = it.text.replace("\n", " ")) },
                        singleLine = true,
                        textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 16.sp),
                        cursorBrush = SolidColor(SinusColors.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                                if (changed) onSave(value.text.trim())
                                true
                            } else false
                        }
                    )
                }
            }
            if (current.isNotBlank()) {
                Text(
                    cs.localNameReset, color = SinusColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(SinusColors.radius(10.dp)))
                        .clickable { onSave("") }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
        DialogButtons(cs, cs.save, SinusColors.primary, changed, onDismiss) { onSave(value.text.trim()) }
    }
}

@Composable
internal fun ChoiceDialog(
    title: String,
    desc: String,
    options: List<Pair<Long, String>>,
    current: Long,
    cs: ContactStrings,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    var selected by remember { mutableLongStateOf(current) }
    PanelDialog(title, cs, 420, onDismiss) {
        Text(desc, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            for ((value, label) in options) {
                val on = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { selected = value }.pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) SinusColors.primary else SinusColors.textMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(SinusColors.primary))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(label, color = SinusColors.textPrimary, fontSize = 15.sp)
                }
            }
        }
        DialogButtons(cs, cs.save, SinusColors.primary, selected != current && selected >= 0L, onDismiss) { onPick(selected) }
    }
}

@Composable
internal fun ConfirmBox(
    title: String,
    desc: String,
    facts: List<String>,
    confirm: String,
    color: Color,
    cs: ContactStrings,
    onDismiss: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null,
    onConfirm: () -> Unit
) {
    PanelDialog(title, cs, 420, onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            if (desc.isNotEmpty()) Text(desc, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            for (fact in facts) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    SinusIcon("ic_check", color, Modifier.padding(top = 1.dp).size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(fact, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        if (secondary != null) {
            Box(
                Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(22.dp)))
                    .border(1.5.dp, color, RoundedCornerShape(SinusColors.radius(22.dp)))
                    .clickable(onClick = secondary.second).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(secondary.first, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        DialogButtons(cs, confirm, color, true, onDismiss, onConfirm)
    }
}

@Composable
private fun SafetyDialog(store: ChatStore, convId: String, name: String, cs: ContactStrings, onDismiss: () -> Unit) {
    val safety by store.safety.collectAsState()
    LaunchedEffect(convId) { store.requestSafety(convId) }
    val shown = safety?.takeIf { it.conv == convId }
    PanelDialog(cs.safetyTitle, cs, 460, onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                shown == null -> CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.5.dp, modifier = Modifier.padding(28.dp).size(30.dp))
                !shown.ok -> Text(cs.safetyFailed, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 20.dp))
                else -> {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(SinusColors.radius(16.dp)))
                            .background(SinusColors.surfaceVariant).padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (row in shown.rows) {
                            Row {
                                for (group in row.trim().split(Regex("\\s+"))) {
                                    Text(
                                        group, color = SinusColors.textPrimary, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        cs.safetyDesc(name), color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    val verified = shown.verified
                    Box(
                        Modifier.padding(top = 16.dp).clip(RoundedCornerShape(SinusColors.radius(22.dp)))
                            .background(if (verified) SinusColors.surfaceVariant else SinusColors.primary)
                            .clickable {
                                if (store.updateContact(LinkFrame.ContactUpdate(convId, verified = !verified))) {
                                    store.emit(ChatEvent.Info(if (verified) cs.unverifiedDone else cs.verifiedDone))
                                }
                            }
                            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 22.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (verified) {
                                SinusIcon("ic_check", SinusColors.active, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (verified) cs.markedVerified else cs.markVerified,
                                color = if (verified) SinusColors.textPrimary else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaDialog(
    store: ChatStore,
    convId: String,
    name: String,
    cs: ContactStrings,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit,
    onOpen: (ChatMessage) -> Unit
) {
    val list by store.mediaList.collectAsState()
    LaunchedEffect(convId) { store.requestMediaList(convId) }
    val items = list?.takeIf { it.conv == convId }?.items
    var tab by remember { mutableStateOf(0) }
    val media = remember(items) { items.orEmpty().filter { it.kind == ChatMessage.KIND_PHOTO || it.kind == ChatMessage.KIND_VIDEO } }
    val docs = remember(items) { items.orEmpty().filter { it.kind == ChatMessage.KIND_FILE || it.kind == ChatMessage.KIND_VOICE } }
    val links = remember(items) {
        items.orEmpty().filter { it.kind == ChatMessage.KIND_TEXT }.mapNotNull { m -> linkPattern.find(m.text)?.value?.let { m to it } }
    }
    val uri = LocalUriHandler.current
    val fs = pl.sinusoide.sinuschat.desktop.i18n.FileStrings.of(LocalChatStrings.current)
    PanelDialog(cs.media, cs, 640, onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            listOf(cs.tabMedia, cs.tabDocs, cs.tabLinks).forEachIndexed { i, label ->
                val on = i == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable { tab = i }
                        .pointerHoverIcon(PointerIcon.Hand).padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, color = if (on) SinusColors.primary else SinusColors.textSecondary, fontSize = 14.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.6f).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(if (on) SinusColors.primary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 480.dp).padding(bottom = 12.dp)) {
            when {
                items == null -> CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.5.dp, modifier = Modifier.align(Alignment.Center).size(30.dp))
                tab == 0 && media.isEmpty() || tab == 1 && docs.isEmpty() || tab == 2 && links.isEmpty() ->
                    Text(cs.emptyMedia, color = SinusColors.textSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
                tab == 0 -> LazyVerticalGrid(GridCells.Adaptive(116.dp), Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(media, key = { it.id }) { m -> MediaTile(store, m) { onOpen(m) } }
                }
                tab == 1 -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(docs, key = { it.id }) { m ->
                        ListEntry(
                            if (m.kind == ChatMessage.KIND_VOICE) "ic_mic" else "ic_document",
                            m.fileName?.ifBlank { null } ?: LocalChatStrings.current.attachment,
                            listOfNotNull(MediaActions.formatSize(m.size).ifEmpty { null }, stamp(m.at)).joinToString(" · "),
                            cs.showInChat, { onJump(m.id) }
                        ) { MediaActions.requestSave(store, m, fs) }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(links, key = { it.first.id }) { (m, link) ->
                        ListEntry("ic_link", link, stamp(m.at), cs.showInChat, { onJump(m.id) }) {
                            val url = if (link.startsWith("http", ignoreCase = true)) link else "https://$link"
                            runCatching { uri.openUri(url) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(store: ChatStore, m: ChatMessage, onClick: () -> Unit) {
    val thumb = thumbOf(store, m)
    Box(
        Modifier.padding(3.dp).aspectRatio(1f).clip(RoundedCornerShape(SinusColors.radius(10.dp)))
            .background(SinusColors.palette.mediaPlaceholder).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else SinusIcon(if (m.kind == ChatMessage.KIND_VIDEO) "ic_video" else "ic_image", SinusColors.textMuted, Modifier.size(28.dp))
        if (m.kind == ChatMessage.KIND_VIDEO) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                SinusIcon("ic_video", Color.White, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ListEntry(icon: String, title: String, sub: String, jumpLabel: String, onJump: () -> Unit, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp)))
            .background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
            .clickable(interactionSource = hover, indication = null, onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(SinusColors.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            SinusIcon(icon, SinusColors.primary, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = SinusColors.textMuted, fontSize = 12.sp, maxLines = 1)
        }
        IconAction("ic_chevron_right", jumpLabel, SinusColors.textSecondary, Modifier.size(38.dp).alpha(0.85f), onClick = onJump)
    }
}
