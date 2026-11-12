@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.Anim
import pl.sinusoide.sinuschat.desktop.ui.anim.rememberLast
import pl.sinusoide.sinuschat.desktop.ui.anim.riseIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatSummary
import java.util.Locale

@Composable
fun ChatList(
    store: ChatStore,
    chats: List<ChatSummary>,
    hidePreviews: Boolean,
    openConv: String?,
    locale: Locale,
    onOpen: (String) -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalChatStrings.current
    val ps = remember(s) { pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings.of(s) }
    val profile by store.profile.collectAsState()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Pair<ChatSummary, androidx.compose.ui.unit.IntOffset>?>(null) }
    var sheet by remember { mutableStateOf<Pair<ChatSummary, ChatListSheet>?>(null) }
    val shown = remember(chats, query) {
        val q = fold(query.trim(), locale)
        if (q.isEmpty()) chats else chats.filter { fold(it.title, locale).contains(q) }
    }
    fun closeSearch() {
        searching = false
        query = ""
    }
    ChatListOverlays(store, chats, menu, sheet, { menu = it }, { sheet = it })
    Column(modifier.background(SinusColors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GreetingTitle(s.chatsTitle, profile?.name, ps.greetings, Modifier.weight(1f))
            pl.sinusoide.sinuschat.desktop.ui.update.UpdateIndicator()
            IconAction(
                if (searching) "ic_close" else "ic_search", s.searchHint,
                if (searching) SinusColors.primary else SinusColors.textSecondary
            ) { if (searching) closeSearch() else searching = true }
            actions()
        }
        AnimatedVisibility(
            visible = searching,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
        ) {
            SearchField(query, { query = it }, s.searchHint, ::closeSearch)
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (chats.isEmpty()) s.chatsEmpty else s.noResults,
                    color = SinusColors.textSecondary, fontSize = 14.sp
                )
            }
        } else {
            val introUntil = remember { System.currentTimeMillis() + 700 }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                itemsIndexed(shown, key = { _, chat -> chat.id }) { index, chat ->
                    val intro = remember { System.currentTimeMillis() < introUntil }
                    ChatRow(
                        store, chat, chat.id == openConv, hidePreviews, locale,
                        modifier = Modifier.animateItem().then(
                            if (intro) Modifier.riseIn(delayMs = minOf(index, 14) * 16, durationMs = 260, distanceDp = 14f) else Modifier
                        ),
                        onMenu = { at -> menu = chat to at }
                    ) { onOpen(chat.id) }
                }
            }
        }
    }
}

@Composable
private fun ChatListOverlays(
    store: ChatStore,
    chats: List<ChatSummary>,
    menu: Pair<ChatSummary, androidx.compose.ui.unit.IntOffset>?,
    sheet: Pair<ChatSummary, ChatListSheet>?,
    onMenu: (Pair<ChatSummary, androidx.compose.ui.unit.IntOffset>?) -> Unit,
    onSheet: (Pair<ChatSummary, ChatListSheet>?) -> Unit
) {
    menu?.let { (chat, at) ->
        val live = chats.firstOrNull { it.id == chat.id } ?: chat
        ChatRowMenu(store, live, at, onDismiss = { onMenu(null) }) { which -> onMenu(null); onSheet(live to which) }
    }
    sheet?.let { (chat, which) -> ChatListDialog(store, chat, which) { onSheet(null) } }
}

private fun fold(text: String, locale: Locale): String =
    java.text.Normalizer.normalize(text.lowercase(locale), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(SinusColors.radius(20.dp)))
            .background(SinusColors.surfaceVariant)
            .border(1.dp, SinusColors.border, RoundedCornerShape(SinusColors.radius(20.dp)))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon("ic_search", SinusColors.textSecondary, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = SinusColors.textMuted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(SinusColors.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                        onClose()
                        true
                    } else false
                }
            )
        }
        if (value.isNotEmpty()) {
            SinusIcon(
                "ic_close", SinusColors.textSecondary,
                Modifier.size(18.dp).clickable { onChange("") }.pointerHoverIcon(PointerIcon.Hand)
            )
        }
    }
}

@Composable
private fun ChatRow(
    store: ChatStore,
    chat: ChatSummary,
    selected: Boolean,
    hidePreviews: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
    onMenu: (androidx.compose.ui.unit.IntOffset) -> Unit = {},
    onClick: () -> Unit
) {
    val s = LocalChatStrings.current
    val ps = remember(s) { pl.sinusoide.sinuschat.desktop.i18n.ChatMenuStrings.of(s) }
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val title = chat.title.ifBlank { if (chat.isGroup) s.group else s.contact }
    val bg by animateColorAsState(
        when {
            selected -> SinusColors.bubbleMine
            hovered -> SinusColors.surfaceHigh
            else -> SinusColors.surface
        },
        tween(160)
    )
    val unread = chat.unread > 0
    val strong = unread && !chat.muted
    val preview = if (hidePreviews) s.previewLocked else ChatFormat.listPreview(chat.lastKind, chat.lastText, s)
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords = it }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) { e ->
                if (e.buttons.isSecondaryPressed) {
                    val local = e.changes.firstOrNull()?.position ?: androidx.compose.ui.geometry.Offset.Zero
                    val origin = coords?.positionInWindow() ?: androidx.compose.ui.geometry.Offset.Zero
                    onMenu(androidx.compose.ui.unit.IntOffset((origin.x + local.x).toInt(), (origin.y + local.y).toInt()))
                    e.changes.forEach { it.consume() }
                }
            }
            .clip(RoundedCornerShape(SinusColors.radius(12.dp)))
            .background(bg)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 8.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Avatar(
                title, chat.color, 54.dp, rememberAvatar(store, chat.id, chat.avatarKey),
                online = chat.online && !chat.isGroup, ring = bg
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, color = if (selected) SinusColors.onBubbleMine else SinusColors.textPrimary, fontSize = 16.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (chat.pinnedAt > 0L) {
                    Spacer(Modifier.width(6.dp))
                    SinusIcon("ic_pin", if (selected) SinusColors.onBubbleMine.copy(alpha = 0.75f) else SinusColors.textMuted, Modifier.size(14.dp), ps.pinned)
                }
                if (chat.muted) {
                    Spacer(Modifier.width(6.dp))
                    SinusIcon("ic_notifications_off", if (selected) SinusColors.onBubbleMine.copy(alpha = 0.75f) else SinusColors.textMuted, Modifier.size(14.dp))
                }
                if (!hidePreviews && preview.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        ChatFormat.listTime(chat.lastAt, s, locale),
                        color = when {
                            selected -> SinusColors.onBubbleMine.copy(alpha = 0.75f)
                            strong -> SinusColors.primary
                            else -> SinusColors.textMuted
                        },
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hidePreviews) {
                    SinusIcon("ic_lock", if (selected) SinusColors.onBubbleMine.copy(alpha = 0.75f) else SinusColors.textMuted, Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    preview,
                    color = when {
                        selected -> SinusColors.onBubbleMine.copy(alpha = 0.8f)
                        strong -> SinusColors.textPrimary
                        else -> SinusColors.textSecondary
                    },
                    fontSize = 14.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val count = rememberLast(chat.unread.takeIf { it > 0 }) ?: 0
                AnimatedVisibility(visible = unread, enter = Anim.popIn, exit = Anim.popOut) {
                    Box(
                        Modifier.padding(start = 8.dp)
                            .height(20.dp)
                            .widthIn(min = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    selected -> SinusColors.onBubbleMine
                                    chat.muted -> SinusColors.textMuted
                                    else -> SinusColors.primary
                                }
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (count > 99) "99+" else count.toString(),
                            color = if (selected) SinusColors.bubbleMine else SinusColors.onAccent,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
