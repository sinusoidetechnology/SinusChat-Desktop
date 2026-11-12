package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.ChatMenuStrings
import pl.sinusoide.sinuschat.desktop.i18n.ContactStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkFrame

internal enum class ChatListSheet { MUTE, NICK, BLOCK, DELETE }

private class AtPoint(val point: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = point.x.coerceAtMost(windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)
        val y = if (point.y + popupContentSize.height + 8 > windowSize.height) point.y - popupContentSize.height else point.y
        return IntOffset(x, y.coerceAtLeast(8))
    }
}

private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

@Composable
internal fun ChatRowMenu(store: ChatStore, chat: ChatSummary, at: IntOffset, onDismiss: () -> Unit, onSheet: (ChatListSheet) -> Unit) {
    val s = LocalChatStrings.current
    val ms = remember(s) { ChatMenuStrings.of(s) }
    val pinned = chat.pinnedAt > 0L
    val items = buildList {
        add(MenuItem(if (pinned) "ic_pinoff" else "ic_pin", if (pinned) ms.unpin else ms.pin) {
            if (store.chatAction(chat.id, if (pinned) LinkFrame.CHAT_UNPIN else LinkFrame.CHAT_PIN)) {
                store.emit(ChatEvent.Info(if (pinned) ms.unpinned else ms.pinned))
            }
        })
        add(MenuItem("ic_notifications_off", ms.mute) { onSheet(ChatListSheet.MUTE) })
        if (chat.unread > 0) add(MenuItem("ic_check_double", ms.markRead) { store.markRead(chat.id) })
        if (!chat.isGroup && chat.peer.isNotEmpty()) {
            add(MenuItem("ic_edit", ms.localName) { onSheet(ChatListSheet.NICK) })
            if (chat.blocked) add(MenuItem("ic_block", ms.unblock) {
                if (store.chatAction(chat.id, LinkFrame.CHAT_UNBLOCK)) store.emit(ChatEvent.Info(ms.unblocked))
            })
            else add(MenuItem("ic_block", ms.block, danger = true) { onSheet(ChatListSheet.BLOCK) })
        }
        add(
            if (chat.isGroup) MenuItem("ic_leave", ms.leaveGroup, danger = true) { onSheet(ChatListSheet.DELETE) }
            else MenuItem("ic_delete", ms.deleteChat, danger = true) { onSheet(ChatListSheet.DELETE) }
        )
    }
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(200, easing = overshoot(1.1f))) }
    val shape = RoundedCornerShape(SinusColors.radius(20.dp))
    Popup(popupPositionProvider = AtPoint(at), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.padding(4.dp).widthIn(min = 220.dp).width(IntrinsicSize.Max)
                .graphicsLayer {
                    alpha = k.value.coerceIn(0f, 1f)
                    val sc = 0.85f + 0.15f * k.value
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .shadow(if (SinusColors.palette.backdrop) 0.dp else 14.dp, shape).clip(shape)
                .background(sheetFill).border(1.dp, sheetStroke, shape).padding(6.dp)
        ) {
            items.forEachIndexed { i, item -> MenuRow(item, i, onDismiss) }
        }
    }
}

@Composable
internal fun ChatListDialog(store: ChatStore, chat: ChatSummary, sheet: ChatListSheet, onDismiss: () -> Unit) {
    val s = LocalChatStrings.current
    val ms = remember(s) { ChatMenuStrings.of(s) }
    val cs = remember(s) { ContactStrings.of(s) }
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val name = chat.title.ifBlank { if (chat.isGroup) s.group else s.contact }
    val contact by store.contact.collectAsState()
    LaunchedEffect(chat.id, sheet) { if (sheet == ChatListSheet.NICK) store.requestContact(chat.id) }
    Popup(popupPositionProvider = WholeWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.requiredSize(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })) {
            when (sheet) {
                ChatListSheet.MUTE -> ChoiceDialog(
                    cs.mute, cs.muteDesc,
                    listOf(0L to cs.disabled, HOUR to cs.mute1h, 8 * HOUR to cs.mute8h, DAY to cs.oneDay, 7 * DAY to cs.oneWeek, LinkFrame.MUTE_FOREVER to cs.muteForever),
                    current = if (chat.muted) -2L else 0L, cs = cs, onDismiss = onDismiss
                ) { picked ->
                    onDismiss()
                    val until = when (picked) {
                        0L -> 0L
                        LinkFrame.MUTE_FOREVER -> LinkFrame.MUTE_FOREVER
                        else -> System.currentTimeMillis() + picked
                    }
                    store.chatAction(chat.id, LinkFrame.CHAT_MUTE, until)
                }
                ChatListSheet.NICK -> {
                    val c = contact?.takeIf { it.conv == chat.id }
                    androidx.compose.runtime.key(c?.nick) {
                        NickDialog(c?.nick.orEmpty(), c?.realName?.ifBlank { null } ?: name, cs, onDismiss) { nick ->
                            onDismiss()
                            if (store.updateContact(LinkFrame.ContactUpdate(chat.id, nick = nick))) {
                                store.emit(ChatEvent.Info(if (nick.isBlank()) cs.localNameRemoved else cs.localNameSaved))
                            }
                        }
                    }
                }
                ChatListSheet.BLOCK -> ConfirmBox(
                    ms.blockQ(name), "", listOf(ms.factProfile, ms.factServer, ms.factUndo), ms.block, SinusColors.danger, cs, onDismiss,
                    secondary = ms.blockAndDelete to {
                        onDismiss()
                        if (store.chatAction(chat.id, LinkFrame.CHAT_BLOCK_DELETE)) store.emit(ChatEvent.Info(ms.blocked(name)))
                    }
                ) {
                    onDismiss()
                    if (store.chatAction(chat.id, LinkFrame.CHAT_BLOCK)) store.emit(ChatEvent.Info(ms.blocked(name)))
                }
                ChatListSheet.DELETE -> ConfirmBox(
                    if (chat.isGroup) ms.deleteQGroup else ms.deleteQ,
                    if (chat.isGroup) ms.deleteDescGroup else ms.deleteDesc(name),
                    emptyList(), if (chat.isGroup) ms.deleteLeave else ms.deleteChat, SinusColors.danger, cs, onDismiss
                ) {
                    onDismiss()
                    if (store.chatAction(chat.id, LinkFrame.CHAT_DELETE)) store.emit(ChatEvent.Info(ms.deleted))
                }
            }
        }
    }
}
