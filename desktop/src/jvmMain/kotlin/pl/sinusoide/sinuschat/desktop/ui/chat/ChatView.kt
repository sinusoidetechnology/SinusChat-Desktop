@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableLongStateOf
import pl.sinusoide.sinuschat.desktop.ui.anim.Anim
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import pl.sinusoide.sinuschat.desktop.ui.anim.Reveal
import pl.sinusoide.sinuschat.desktop.ui.anim.rememberLast
import pl.sinusoide.sinuschat.desktop.ui.anim.riseIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.lastSeenAgo
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkFrame
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private val flashColor = Color(0x334A90D9)

internal val LocalShowOriginal = androidx.compose.runtime.staticCompositionLocalOf { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

private const val MAX_JUMP_PAGES = 25

private sealed interface ListRow {
    val key: String
    data class Day(val date: LocalDate) : ListRow { override val key get() = "day-$date" }
    data class Note(val m: ChatMessage, val top: Dp) : ListRow { override val key get() = m.id }
    data class Msg(
        val m: ChatMessage,
        val album: List<ChatMessage>?,
        val top: Dp,
        val showName: Boolean,
        val avatarSlot: Boolean,
        val showAvatar: Boolean
    ) : ListRow {
        override val key get() = album?.let { "album-" + it.first().id } ?: m.id
        fun contains(id: String) = m.id == id || album?.any { it.id == id } == true
    }
}

private fun albumKey(m: ChatMessage): String? =
    m.albumId?.takeIf { m.kind == ChatMessage.KIND_PHOTO && !m.viewOnce }?.let { "$it|${m.sender}" }

internal fun groupAlbums(messages: List<ChatMessage>): List<List<ChatMessage>> {
    val out = ArrayList<List<ChatMessage>>(messages.size)
    var i = 0
    while (i < messages.size) {
        val key = albumKey(messages[i])
        var j = i + 1
        if (key != null) while (j < messages.size && albumKey(messages[j]) == key) j++
        out += messages.subList(i, j).toList()
        i = j
    }
    return out
}

private fun rowsOf(messages: List<ChatMessage>, isGroup: Boolean): List<ListRow> {
    val groups = groupAlbums(messages)
    val out = ArrayList<ListRow>(groups.size + 8)
    var lastDay: LocalDate? = null
    for ((i, group) in groups.withIndex()) {
        val m = if (group.size > 1) group.firstOrNull { it.text.isNotBlank() || it.replyTo != null } ?: group.last() else group[0]
        val d = ChatFormat.date(group[0].at)
        if (d != lastDay) {
            out += ListRow.Day(d)
            lastDay = d
        }
        if (m.kind == ChatMessage.KIND_SYSTEM) {
            out += ListRow.Note(m, if (i > 0) 12.dp else 8.dp)
            continue
        }
        val prev = groups.getOrNull(i - 1)?.last()
        val next = groups.getOrNull(i + 1)?.first()
        val prevSame = prev != null && prev.kind != ChatMessage.KIND_SYSTEM && prev.sender == m.sender
        val nextSame = next != null && next.kind != ChatMessage.KIND_SYSTEM && next.sender == m.sender
        val other = isGroup && !m.mine
        out += ListRow.Msg(
            m = m,
            album = group.takeIf { it.size > 1 },
            top = if (prev != null && prev.sender != m.sender) 16.dp else 6.dp,
            showName = other && !prevSame,
            avatarSlot = other,
            showAvatar = other && !nextSame
        )
    }
    return out
}

@Composable
fun ChatView(store: ChatStore, chat: ChatSummary?, convId: String, focused: Boolean, locale: Locale) {
    val s = LocalChatStrings.current
    val ms = pl.sinusoide.sinuschat.desktop.i18n.LocalMediaStrings.current
    val messages by store.messages.collectAsState()
    val hidden by store.hidden.collectAsState()
    val hasMore by store.hasMore.collectAsState()
    val loadingOlder by store.loadingOlder.collectAsState()
    val typing by store.typing.collectAsState()
    val chats by store.chats.collectAsState()
    val isGroup = chat?.isGroup ?: false
    val title = chat?.title?.ifBlank { null } ?: if (isGroup) s.group else s.contact
    var replyTo by remember(convId) { mutableStateOf<ChatMessage?>(null) }
    val peerColors = remember(chats) { chats.filter { !it.isGroup && it.peer.isNotEmpty() }.associate { it.peer to it.color } }
    val shownMessages = remember(messages, hidden) { if (hidden.isEmpty()) messages else messages.filter { it.id !in hidden } }
    val byId = remember(shownMessages) { shownMessages.associateBy { it.id } }

    val newestIncoming = messages.lastOrNull { !it.mine && it.kind != ChatMessage.KIND_SYSTEM }?.at ?: 0L
    LaunchedEffect(convId, newestIncoming, focused, chat?.unread) {
        if (focused && (newestIncoming > 0L || (chat?.unread ?: 0) > 0)) store.markRead(convId)
    }

    val picker = remember(convId) { MessagePicker() }
    val dust = remember { DustState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var area by remember { mutableStateOf(IntSize.Zero) }
    var moreOpen by remember(convId) { mutableStateOf(false) }
    var deleting by remember(convId) { mutableStateOf<List<ChatMessage>?>(null) }
    var forwarding by remember(convId) { mutableStateOf<List<String>?>(null) }
    var undo by remember(convId) { mutableStateOf<PendingUndo?>(null) }

    LaunchedEffect(picker, shownMessages) { picker.retain(byId.keys) }
    val selecting = picker.active
    LaunchedEffect(selecting) { if (!selecting) moreOpen = false }
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        delay((UNDO_MS - (System.currentTimeMillis() - u.startedAt)).coerceAtLeast(0L))
        if (undo === u) undo = null
    }

    var viewing by remember(convId) { mutableStateOf<ChatMessage?>(null) }
    var viewOnce by remember(convId) { mutableStateOf<ChatMessage?>(null) }
    val fs = pl.sinusoide.sinuschat.desktop.i18n.FileStrings.of(s)
    val acts = remember(s) { pl.sinusoide.sinuschat.desktop.i18n.ActionStrings.of(s) }
    var editing by remember(convId) { mutableStateOf<ChatMessage?>(null) }
    var emojiFor by remember(convId) { mutableStateOf<ChatMessage?>(null) }
    var pinsOpen by remember(convId) { mutableStateOf(false) }
    var jumpTarget by remember(convId) { mutableStateOf<String?>(null) }
    var profileOpen by remember(convId) { mutableStateOf(false) }
    var contactSheet by remember(convId) { mutableStateOf<ContactSheet?>(null) }
    var chatSearch by remember(convId) { mutableStateOf<String?>(null) }
    val cs = remember(s) { pl.sinusoide.sinuschat.desktop.i18n.ContactStrings.of(s) }
    val found = remember(shownMessages, chatSearch) {
        chatSearch?.trim()?.takeIf { it.isNotEmpty() }?.let { q -> shownMessages.filter { it.text.contains(q, ignoreCase = true) } }
    }
    val pinnedState by store.pinned.collectAsState()
    val loadedPins = remember(shownMessages) { shownMessages.filter { it.pinned }.map { it.id } }
    LaunchedEffect(pinsOpen, loadedPins) { if (pinsOpen) store.requestPinned(convId) }
    val pinnedItems = remember(pinnedState, shownMessages, hidden, convId) {
        val fromPhone = pinnedState?.takeIf { it.conv == convId }?.items
        (fromPhone ?: shownMessages.filter { it.pinned }.sortedByDescending { it.at }).filter { it.id !in hidden }
    }

    val picked = picker.picked.keys.mapNotNull { byId[it] }.sortedBy { it.at }
    val single = picked.singleOrNull()
    val singleIsAlbum = single != null && (picker.picked[single.id]?.size ?: 1) > 1
    val moreItems = single?.let { m ->
        buildList {
            if (!singleIsAlbum && store.canEdit(m, isGroup)) add(MenuItem("ic_edit", acts.edit) { editing = m; picker.clear() })
            if (m.text.isNotEmpty() && !m.viewOnce) add(MenuItem("ic_copy", s.copy) { copyText(m.text); picker.clear() })
            val savable = !m.viewOnce && m.kind in setOf(ChatMessage.KIND_PHOTO, ChatMessage.KIND_VIDEO, ChatMessage.KIND_FILE, ChatMessage.KIND_VOICE)
            if (savable && !singleIsAlbum) add(MenuItem("ic_download", ms.save) { MediaActions.requestSave(store, m, fs); picker.clear() })
            if (m.kind == ChatMessage.KIND_VIDEO && !m.viewOnce) add(MenuItem("ic_video", ms.open) { viewing = m; picker.clear() })
            if (m.kind == ChatMessage.KIND_FILE && pl.sinusoide.sinuschat.desktop.security.FileSafety.sandboxAvailable) {
                add(MenuItem("ic_shield", fs.openSandbox) {
                    MediaActions.openInSandbox(store, m, fs.sandboxStarting, fs.sandboxFailed)
                    picker.clear()
                })
            }
        }
    }.orEmpty()

    fun confirmDelete() {
        val chosen = picker.picked
        deleting = null
        if (chosen.isEmpty()) return
        val keys = chosen.keys.toList()
        val ids = chosen.values.flatten().distinct()
        picker.clear()
        scope.launch {
            val shots = keys.associateWith { picker.shot(it) }
            picker.ghosts = picker.ghosts + shots.filterValues { it != null }.keys
            store.deleteMessages(convId, ids, UNDO_MS)
            for ((key, shot) in shots) {
                if (shot == null) continue
                dust.add(shot.image, shot.topLeft, density, tag = key) { picker.ghosts = picker.ghosts - key }
            }
            undo = PendingUndo(keys, ids)
        }
    }

    fun undoDelete() {
        val u = undo ?: return
        undo = null
        u.keys.forEach { dust.cancel(it) }
        picker.ghosts = picker.ghosts + u.keys
        store.undoDelete(u.ids)
        scope.launch {
            repeat(3) { withFrameNanos { } }
            for (key in u.keys) {
                val shot = picker.shot(key)
                if (shot == null) {
                    picker.ghosts = picker.ghosts - key
                    continue
                }
                dust.add(shot.image, shot.topLeft, density, reverse = true, tag = key) { picker.ghosts = picker.ghosts - key }
            }
        }
    }

    var dragging by remember { mutableStateOf(false) }
    val dropTarget = remember(convId) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragging = true }
            override fun onExited(event: DragAndDropEvent) { dragging = false }
            override fun onEnded(event: DragAndDropEvent) { dragging = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val files = droppedFiles(event)
                if (files.isEmpty()) return false
                if (store.uploads.stage(convId, files).isNotEmpty()) store.emit(ChatEvent.SendFailed(LinkFrame.ERROR_TOO_BIG))
                return true
            }
        }
    }

    Box(
        Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { e -> droppedFlavors(e) },
            target = dropTarget
        ).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key != Key.Escape) return@onPreviewKeyEvent false
            when {
                store.recorder.recording.value -> store.recorder.cancel()
                editing != null -> editing = null
                emojiFor != null -> emojiFor = null
                pinsOpen -> pinsOpen = false
                contactSheet != null -> contactSheet = null
                profileOpen -> profileOpen = false
                deleting != null -> deleting = null
                forwarding != null -> forwarding = null
                moreOpen -> moreOpen = false
                picker.active -> picker.clear()
                chatSearch != null -> chatSearch = null
                else -> return@onPreviewKeyEvent false
            }
            true
        }
    ) {
        Column(Modifier.fillMaxSize().background(SinusColors.background)) {
            val headerMode = when {
                selecting -> 1
                chatSearch != null -> 2
                else -> 0
            }
            Crossfade(targetState = headerMode, animationSpec = tween(160)) { mode ->
                if (mode == 2) {
                    ChatSearchBar(chatSearch.orEmpty(), cs.searchHint, s.cancel, { chatSearch = it }) { chatSearch = null }
                } else if (mode == 1) {
                    SelectionBar(
                        count = picker.picked.size,
                        canReply = single != null,
                        canMore = moreItems.isNotEmpty(),
                        onClose = { picker.clear() },
                        onReply = {
                            single?.let { replyTo = it }
                            picker.clear()
                        },
                        onDelete = {
                            picker.reactFor = null
                            moreOpen = false
                            deleting = picked.takeIf { it.isNotEmpty() }
                        },
                        onForward = {
                            val texts = picked.filter { it.kind == ChatMessage.KIND_TEXT && !it.viewOnce && it.text.isNotBlank() }.map { it.text }
                            picker.clear()
                            if (texts.isEmpty()) store.emit(ChatEvent.Info(s.forwardNothing)) else forwarding = texts
                        },
                        onMore = {
                            picker.reactFor = null
                            moreOpen = !moreOpen
                        },
                        canPin = single != null && store.canChange(single),
                        pinned = single?.pinned == true,
                        pinLabel = if (single?.pinned == true) acts.unpin else acts.pin,
                        onPin = {
                            val m = single
                            picker.clear()
                            if (m != null && store.setPinned(m, !m.pinned)) {
                                store.emit(ChatEvent.Info(if (m.pinned) acts.unpinnedDone else acts.pinnedDone))
                            }
                        }
                    )
                } else {
                    ChatHeader(store, title, chat, convId, isGroup, acts.pinnedTitle, { if (!isGroup && chat != null) profileOpen = true }) { pinsOpen = true }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                    picker.origin = it.positionInRoot()
                    area = it.size
                }
            ) {
                val typingName = typing[convId]
                MessageList(
                    store, found ?: shownMessages, isGroup, peerColors, hasMore, loadingOlder, locale, typingName != null, picker,
                    store::loadOlder, { replyTo = it }, { if (it.viewOnce) viewOnce = it else viewing = it },
                    jumpTarget
                ) { found ->
                    jumpTarget = null
                    if (!found) store.emit(ChatEvent.Info(acts.pinnedMissing))
                }
                if (found != null && found.isEmpty()) {
                    Text(
                        cs.searchNone, color = SinusColors.textSecondary, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(SinusColors.surface)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                val trOffers by store.trOffers.collectAsState()
                val offer = trOffers[convId]
                val lastOffer = rememberLast(offer)
                Reveal(
                    visible = offer != null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    enter = Anim.toastIn,
                    exit = Anim.toastOut
                ) {
                    lastOffer?.let { TranslatePill(it) { yes -> store.answerTranslation(it.conv, it.lang, yes) } }
                }
                val lastTyping = rememberLast(typingName)
                val typingLift by animateDpAsState(if (undo != null) 76.dp else 10.dp, tween(220))
                Reveal(
                    visible = typingName != null,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = typingLift),
                    enter = Anim.popIn,
                    exit = Anim.popOut
                ) {
                    TypingBubble(lastTyping?.takeIf { isGroup && it.isNotBlank() }, Modifier)
                }
                val lastUndo = rememberLast(undo)
                Reveal(
                    visible = undo != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = Anim.toastIn,
                    exit = Anim.toastOut
                ) {
                    lastUndo?.let { UndoBar(it, ::undoDelete) }
                }
                DustOverlay(dust, Modifier.matchParentSize().clipToBounds())
                val reacting = picker.reactFor?.let { byId[it] }?.takeIf { !isGroup && store.canChange(it) }
                val lastReacting = rememberLast(reacting)
                Reveal(
                    visible = reacting != null,
                    modifier = Modifier.matchParentSize(),
                    enter = fadeIn(tween(1)),
                    exit = fadeOut(tween(120))
                ) {
                    lastReacting?.let { m ->
                        Box(Modifier.fillMaxSize()) {
                            ReactionBar(
                                mine = m.mine,
                                anchor = picker.reactAnchor,
                                area = area,
                                onPick = { emoji ->
                                    picker.clear()
                                    store.react(m, emoji)
                                },
                                onMore = {
                                    picker.clear()
                                    emojiFor = m
                                }
                            )
                        }
                    }
                }
                val lastMore = rememberLast(moreItems.takeIf { it.isNotEmpty() })
                Reveal(
                    visible = moreOpen && moreItems.isNotEmpty(),
                    modifier = Modifier.matchParentSize(),
                    enter = fadeIn(tween(1)),
                    exit = fadeOut(tween(150))
                ) {
                    lastMore?.let { MoreMenu(it) { moreOpen = false } }
                }
            }
            Composer(store, convId, isGroup, replyTo) { replyTo = null }
        }
        AnimatedVisibility(visible = dragging, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) { DropHint() }

        val trSend by store.trSend.collectAsState()
        val sendOffer = trSend?.takeIf { it.conv == convId }
        val lastSendOffer = rememberLast(sendOffer)
        Reveal(visible = sendOffer != null, modifier = Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            lastSendOffer?.let { o -> TranslateSendDialog(o, onTranslated = { store.chooseSend(o, true) }, onOriginal = { store.chooseSend(o, false) }) { store.cancelSend(o) } }
        }

        val panelChat = chat?.takeIf { !isGroup }
        AnimatedVisibility(
            visible = profileOpen && panelChat != null,
            enter = fadeIn(tween(180)) + androidx.compose.animation.slideInHorizontally(tween(260, easing = Motion.enter)) { it / 6 },
            exit = fadeOut(tween(160)) + androidx.compose.animation.slideOutHorizontally(tween(200)) { it / 8 }
        ) {
            val shownChat = rememberLast(panelChat)
            if (shownChat != null) {
                ContactPanel(
                    store, shownChat, convId, contactSheet, { contactSheet = it },
                    onClose = {
                        contactSheet = null
                        profileOpen = false
                    },
                    onSearch = {
                        profileOpen = false
                        chatSearch = ""
                    },
                    onJump = { jumpTarget = it },
                    onOpenMedia = { viewing = it }
                )
            }
        }

        val sheetShown = deleting != null
        val lastDeleting = rememberLast(deleting)
        Reveal(visible = sheetShown, modifier = Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Scrim { deleting = null }
        }
        Reveal(
            visible = sheetShown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280, easing = Motion.enter)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220, easing = Motion.ease)) { it } + fadeOut(tween(200))
        ) {
            lastDeleting?.let { items ->
                DeleteSheet(
                    items,
                    isGroup = isGroup,
                    senderOf = { m -> if (m.mine) s.you else m.senderName.ifBlank { title } },
                    onDismiss = { deleting = null },
                    onConfirm = ::confirmDelete
                )
            }
        }

        val lastForwarding = rememberLast(forwarding)
        Reveal(visible = forwarding != null, modifier = Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize()) {
                Scrim { forwarding = null }
                if (lastForwarding != null) {
                    ForwardDialog(store, onDismiss = { forwarding = null }) { target, name ->
                        val texts = forwarding ?: lastForwarding
                        forwarding = null
                        if (store.forward(target.id, texts)) store.emit(ChatEvent.Info(s.forwardSentTo(name)))
                    }
                }
            }
        }

        Reveal(visible = pinsOpen, modifier = Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize()) {
                Scrim { pinsOpen = false }
                PinnedDialog(
                    pinnedItems, isGroup, title, acts,
                    onJump = { m ->
                        pinsOpen = false
                        jumpTarget = m.id
                    },
                    onUnpin = { m -> if (store.setPinned(m, false)) store.emit(ChatEvent.Info(acts.unpinnedDone)) },
                    onDismiss = { pinsOpen = false }
                )
            }
        }

        val lastEditing = rememberLast(editing)
        Reveal(visible = editing != null, modifier = Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize()) {
                Scrim { editing = null }
                lastEditing?.let { m ->
                    EditDialog(m, acts, onDismiss = { editing = null }) { text ->
                        editing = null
                        store.edit(m, text)
                    }
                }
            }
        }

        val lastEmojiFor = rememberLast(emojiFor)
        Reveal(visible = emojiFor != null, modifier = Modifier.matchParentSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize()) {
                Scrim { emojiFor = null }
                lastEmojiFor?.let { m ->
                    EmojiDialog(store.myReaction(byId[m.id] ?: m), acts, onPick = { emoji ->
                        emojiFor = null
                        store.react(byId[m.id] ?: m, emoji)
                    }, onDismiss = { emojiFor = null })
                }
            }
        }

        val shownImage = rememberLast(viewing)
        AnimatedVisibility(visible = viewing != null, enter = Anim.dialogIn, exit = Anim.dialogOut) {
            shownImage?.let {
                if (it.kind == ChatMessage.KIND_VIDEO) VideoViewer(store, it) { viewing = null } else ImageViewer(store, it) { viewing = null }
            }
        }
        viewOnce?.let { ViewOnceViewer(store, it) { viewOnce = null } }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    )
}

private fun droppedFlavors(event: DragAndDropEvent): Boolean =
    runCatching { event.awtTransferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor) }.getOrDefault(false)

private fun droppedFiles(event: DragAndDropEvent): List<java.io.File> = runCatching {
    val t = event.awtTransferable
    @Suppress("UNCHECKED_CAST")
    (t.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File>).filter { it.isFile }
}.getOrDefault(emptyList())

@Composable
private fun HeaderCallButton(icon: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon(icon, SinusColors.textSecondary, Modifier.size(22.dp), label)
    }
}

@Composable
private fun ChatHeader(
    store: ChatStore,
    title: String,
    chat: ChatSummary?,
    convId: String,
    isGroup: Boolean,
    pinsLabel: String,
    onProfile: () -> Unit,
    onPins: () -> Unit
) {
    val s = LocalChatStrings.current
    val image = rememberAvatar(store, convId, chat?.avatarKey ?: convId)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).background(SinusColors.surface).padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .then(if (!isGroup) Modifier.clickable(onClick = onProfile).pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Avatar(title, chat?.color, 40.dp, image)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = SinusColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val lastSeen = chat?.lastSeen ?: 0L
                val now by produceState(System.currentTimeMillis(), lastSeen) {
                    while (true) {
                        value = System.currentTimeMillis()
                        kotlinx.coroutines.delay(30_000L)
                    }
                }
                val (sub, color) = when {
                    isGroup -> s.group to SinusColors.textSecondary
                    chat?.online == true -> s.online to SinusColors.active
                    lastSeen > 0L -> s.lastSeenAgo(now - lastSeen) to SinusColors.textSecondary
                    else -> "" to SinusColors.textSecondary
                }
                if (sub.isNotEmpty()) {
                    Text(
                        sub, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            }
            if (!isGroup) {
                val cs = pl.sinusoide.sinuschat.desktop.i18n.CallStrings.of(s)
                HeaderCallButton("ic_normal_call", cs.voiceCall) { store.calls.start(convId, title, false) }
                Spacer(Modifier.width(4.dp))
                HeaderCallButton("ic_call_video_on", cs.videoCall) { store.calls.start(convId, title, true) }
                Spacer(Modifier.width(4.dp))
            }
            HeaderCallButton("ic_pin", pinsLabel, onPins)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SinusColors.border))
    }
}

@Composable
private fun ChatSearchBar(query: String, hint: String, cancel: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).background(SinusColors.surface).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction("ic_back", cancel, SinusColors.textPrimary, Modifier.size(44.dp), onClick = onClose)
            Spacer(Modifier.width(6.dp))
            val shape = RoundedCornerShape(SinusColors.radius(20.dp))
            Row(
                Modifier.weight(1f).height(40.dp).clip(shape).background(SinusColors.surfaceVariant).border(1.dp, SinusColors.border, shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SinusIcon("ic_search", SinusColors.textSecondary, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(hint, color = SinusColors.textMuted, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = onChange,
                        singleLine = true,
                        textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(SinusColors.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) {
                    SinusIcon(
                        "ic_close", SinusColors.textSecondary,
                        Modifier.size(18.dp).clickable { onChange("") }.pointerHoverIcon(PointerIcon.Hand)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SinusColors.border))
    }
}

@Composable
private fun MessageList(
    store: ChatStore,
    messages: List<ChatMessage>,
    isGroup: Boolean,
    peerColors: Map<String, String>,
    hasMore: Boolean,
    loadingOlder: Boolean,
    locale: Locale,
    typingShown: Boolean,
    picker: MessagePicker,
    onLoadOlder: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onOpenImage: (ChatMessage) -> Unit,
    jumpRequest: String?,
    onJumpDone: (Boolean) -> Unit
) {
    val rows = remember(messages, isGroup) { rowsOf(messages, isGroup).asReversed() }
    val byId = remember(messages) { messages.associateBy { it.id } }
    val openedAt = remember(messages.firstOrNull()?.conv) { System.currentTimeMillis() }
    val bottomPad by animateDpAsState(if (typingShown) 60.dp else 4.dp, tween(220))
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var flashId by remember { mutableStateOf<String?>(null) }
    val nearTop by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearTop, hasMore, rows.size) {
        if (nearTop && hasMore && !loadingOlder) onLoadOlder()
    }
    val newestId = messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (state.firstVisibleItemIndex <= 2) state.scrollToItem(0)
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) picker.reactFor = null
    }
    LaunchedEffect(flashId) {
        if (flashId != null) {
            delay(1_800)
            flashId = null
        }
    }
    val jumpTo: (String) -> Unit = { target ->
        val id = byId[target]?.id ?: messages.firstOrNull { it.id.startsWith(target + "_") }?.id
        val index = if (id == null) -1 else rows.indexOfFirst { it is ListRow.Msg && it.contains(id) }
        if (index >= 0) {
            flashId = (rows[index] as ListRow.Msg).m.id
            scope.launch { state.animateScrollToItem(index) }
        }
    }
    var jumpPages by remember { mutableStateOf(0) }
    LaunchedEffect(jumpRequest, rows, hasMore, loadingOlder) {
        val target = jumpRequest ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is ListRow.Msg && it.contains(target) }
        when {
            index >= 0 -> {
                jumpPages = 0
                flashId = (rows[index] as ListRow.Msg).m.id
                scope.launch { state.animateScrollToItem(index) }
                onJumpDone(true)
            }
            loadingOlder -> Unit
            hasMore && jumpPages < MAX_JUMP_PAGES -> {
                jumpPages++
                onLoadOlder()
            }
            else -> {
                jumpPages = 0
                onJumpDone(false)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bubbleMax = minOf((maxWidth - 64.dp) * 0.78f, 560.dp)
        LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomPad)
        ) {
            items(rows, key = { it.key }) { row ->
                val fresh = row is ListRow.Msg && row.m.at > openedAt
                Box(
                    Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(160))
                        .then(if (fresh) Modifier.riseIn(durationMs = 260, distanceDp = 14f) else Modifier)
                ) {
                    when (row) {
                        is ListRow.Day -> DayDivider(ChatFormat.dayLabel(row.date, locale))
                        is ListRow.Note -> SystemLine(row.m.text, row.top, row.m.sysType)
                        is ListRow.Msg -> MessageRow(
                            store, row, byId, peerColors, bubbleMax, row.m.id == flashId, picker, onReply, onOpenImage, jumpTo
                        )
                    }
                }
            }
            if (loadingOlder) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
        val away by remember { derivedStateOf { state.firstVisibleItemIndex > 2 } }
        var awaySince by remember { mutableLongStateOf(0L) }
        LaunchedEffect(away) { awaySince = if (away) System.currentTimeMillis() else 0L }
        val unseen = if (away && awaySince > 0L) messages.count { !it.mine && it.at > awaySince } else 0
        AnimatedVisibility(
            visible = away,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomPad + 12.dp),
            enter = Anim.popIn,
            exit = Anim.popOut
        ) {
            Box {
                Box(
                    Modifier.padding(top = 6.dp).size(44.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(SinusColors.card)
                        .clickable { scope.launch { state.animateScrollToItem(0) } }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    SinusIcon("ic_chevron_down", SinusColors.textPrimary, Modifier.size(24.dp))
                }
                if (unseen > 0) {
                    Text(
                        if (unseen > 99) "99+" else unseen.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(10.dp)).background(SinusColors.primary)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    val measurer = rememberTextMeasurer()
    val color = SinusColors.textSecondary
    val style = TextStyle(color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        val layout = measurer.measure(label, style)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = layout.size.width / 2f
        val gap = 10.dp.toPx()
        val edge = 18.dp.toPx()
        dayWave(edge, cx - half - gap, cy, cx, fadeAtStart = true, color = color)
        dayWave(cx + half + gap, size.width - edge, cy, cx, fadeAtStart = false, color = color)
        drawText(layout, topLeft = Offset(cx - half, cy - layout.size.height / 2f))
    }
}

private fun DrawScope.dayWave(x0: Float, x1: Float, cy: Float, cx: Float, fadeAtStart: Boolean, color: Color) {
    if (x1 - x0 < 12.dp.toPx()) return
    val amp = 2.6.dp.toPx()
    val length = 22.dp.toPx()
    val step = 1.5.dp.toPx()
    fun y(x: Float) = cy - amp * sin(2 * PI * abs(x - cx) / length).toFloat()
    val path = Path()
    path.moveTo(x0, y(x0))
    var x = x0
    while (x < x1) {
        x = min(x + step, x1)
        path.lineTo(x, y(x))
    }
    val solid = color.copy(alpha = color.alpha * 0.55f)
    val clear = color.copy(alpha = 0f)
    drawPath(
        path,
        Brush.horizontalGradient(if (fadeAtStart) listOf(clear, solid) else listOf(solid, clear), x0, x1),
        style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun systemIcon(type: String): String = when (type) {
    "call_done" -> "ic_call_made"
    "call_missed" -> "ic_call_missed"
    "call_declined" -> "ic_call_end"
    "key_change", "sys_key_change" -> "ic_key"
    "sys_disappearing_change" -> "ic_clock"
    else -> "ic_info"
}

@Composable
private fun SystemLine(text: String, top: Dp, type: String) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = top), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(shape).background(Color(0x1F5288C1)).border(1.dp, Color(0x4D5288C1), shape)
                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SinusIcon(systemIcon(type), SinusColors.primary, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = SinusColors.primary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun MessageRow(
    store: ChatStore,
    row: ListRow.Msg,
    byId: Map<String, ChatMessage>,
    peerColors: Map<String, String>,
    maxWidth: Dp,
    flash: Boolean,
    picker: MessagePicker,
    onReply: (ChatMessage) -> Unit,
    onOpenImage: (ChatMessage) -> Unit,
    onJump: (String) -> Unit
) {
    val m = row.m
    val s = LocalChatStrings.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val picked by remember(m.id, picker) { derivedStateOf { picker.isPicked(m.id) } }
    val selecting by remember(picker) { derivedStateOf { picker.active } }
    val highlight by animateColorAsState(if (flash) flashColor else Color.Transparent, tween(if (flash) 150 else 900))
    val pickedBg by animateColorAsState(if (picked) selectedRowColor else Color.Transparent, tween(150))
    val senderHex = remember(m.sender, m.senderColor, peerColors) {
        m.senderColor.ifBlank { null } ?: peerColors[m.sender] ?: AvatarColors.forHash(m.sender)
    }
    val senderName = m.senderName.ifBlank { s.member }
    val members = remember(row) { row.album?.map { it.id } ?: listOf(m.id) }
    val layer = rememberGraphicsLayer()
    val capture = remember(layer) { MessageCapture(layer) }
    DisposableEffect(m.id, capture) {
        picker.captures[m.id] = capture
        onDispose { if (picker.captures[m.id] === capture) picker.captures.remove(m.id) }
    }
    Row(
        Modifier.fillMaxWidth().background(highlight).background(pickedBg).hoverable(hover)
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { e ->
                val buttons = e.buttons
                if (picker.active) {
                    if (buttons.isPrimaryPressed || buttons.isSecondaryPressed) {
                        picker.toggle(m.id, members)
                        e.changes.forEach { it.consume() }
                    }
                } else if (buttons.isSecondaryPressed) {
                    picker.start(m.id, members)
                    e.changes.forEach { it.consume() }
                }
            }
            .padding(start = 6.dp, end = 6.dp, top = row.top),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (row.avatarSlot) {
            Box(Modifier.width(40.dp).padding(start = 2.dp, bottom = 2.dp)) {
                if (row.showAvatar) Avatar(senderName, senderHex, 32.dp, rememberAvatar(store, m.conv, m.sender))
            }
        }
        if (m.mine) ReplyHint(hovered && !selecting, Modifier.align(Alignment.CenterVertically)) { onReply(m) }
        Column(
            Modifier
                .onGloballyPositioned { capture.coords = it }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    if (m.id !in picker.ghosts) drawLayer(layer)
                },
            horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start
        ) {
            if (m.pinned) PinnedLabel(s.pinned)
            val replyId = m.replyTo
            if (replyId != null) {
                ReplyQuote(quoteText(m, byId, s), maxWidth) { onJump(replyId) }
            }
            Bubble(store, m, row, senderName, senderHex, maxWidth, onOpenImage)
            if (m.reactions.isNotEmpty()) Reactions(m.reactions.values.toList())
        }
        if (!m.mine) ReplyHint(hovered && !selecting, Modifier.align(Alignment.CenterVertically)) { onReply(m) }
    }
}

private fun quoteText(m: ChatMessage, byId: Map<String, ChatMessage>, s: pl.sinusoide.sinuschat.desktop.i18n.ChatStrings): String {
    m.replyText?.takeIf { it.isNotBlank() }?.let { return it }
    val target = m.replyTo ?: return s.quoteFallback
    val original = byId[target] ?: byId.values.firstOrNull { it.id.startsWith(target + "_") }
    return original?.let { ChatFormat.preview(it.kind, it.text, it.fileName, s) }?.takeIf { it.isNotBlank() } ?: s.quoteFallback
}

@Composable
private fun Bubble(
    store: ChatStore,
    m: ChatMessage,
    row: ListRow.Msg,
    senderName: String,
    senderHex: String,
    maxWidth: Dp,
    onOpenImage: (ChatMessage) -> Unit
) {
    val big = SinusColors.radius(16.dp)
    val small = SinusColors.radius(4.dp)
    val shape = if (m.mine) RoundedCornerShape(big, big, small, big) else RoundedCornerShape(small, big, big, big)
    val album = row.album
    val media = album != null || m.kind != ChatMessage.KIND_TEXT
    Column(
        Modifier
            .widthIn(max = maxWidth)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(if (m.mine) SinusColors.bubbleMine else SinusColors.bubbleOther)
            .padding(top = 6.dp, bottom = 6.dp)
    ) {
        if (row.showName) {
            Text(
                senderName, color = ChatFormat.color(senderHex), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 3.dp)
            )
        }
        if (album != null) {
            Box(Modifier.padding(start = 3.dp, end = 3.dp, bottom = 3.dp)) { AlbumGrid(store, album, onOpenImage) }
        } else if (m.kind != ChatMessage.KIND_TEXT) {
            Box(Modifier.padding(start = 3.dp, end = 3.dp, bottom = 3.dp)) { MediaContent(store, m, onOpenImage) }
        }
        val originals = LocalShowOriginal.current
        val trShown = m.trShown?.takeIf { m.kind == ChatMessage.KIND_TEXT && !m.viewOnce && it.isNotBlank() }
        val showingOriginal = trShown != null && originals[m.id] == true
        val body = when {
            trShown == null -> m.text
            showingOriginal -> m.trOriginal ?: m.text
            else -> trShown
        }
        val caption = body.takeIf { it.isNotEmpty() && m.kind != ChatMessage.KIND_LOCATION && !m.viewOnce }
        if (caption != null) {
            MessageText(
                caption, m.mine,
                Modifier.padding(horizontal = 12.dp).then(if (media) Modifier.widthIn(max = 246.dp) else Modifier)
            )
        }
        if (trShown != null) {
            val ts = pl.sinusoide.sinuschat.desktop.i18n.TranslateStrings.of(LocalChatStrings.current)
            Text(
                if (showingOriginal) ts.showTranslation else ts.showOriginal,
                color = if (m.mine) Color(0xFFD6E9FA) else SinusColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp).clip(RoundedCornerShape(6.dp))
                    .clickable { if (showingOriginal) originals.remove(m.id) else originals[m.id] = true }
                    .pointerHoverIcon(PointerIcon.Hand).padding(vertical = 1.dp)
            )
        }
        TimeRow(m, Modifier.align(Alignment.End).padding(top = 3.dp, start = 12.dp, end = 12.dp))
    }
}

private val urlPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

private fun linkify(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    for (match in urlPattern.findAll(text)) {
        val raw = match.value.trimEnd('.', ',', '!', '?', ')', ';', ':', '\'')
        if (raw.isEmpty()) continue
        val start = match.range.first
        val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        addLink(LinkAnnotation.Url(url, style), start, start + raw.length)
    }
}

@Composable
private fun MessageText(text: String, mine: Boolean, modifier: Modifier) {
    val linkColor = if (mine) SinusColors.onBubbleMine else SinusColors.primary
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    Text(
        annotated, color = if (mine) SinusColors.onBubbleMine else SinusColors.textPrimary, fontSize = 15.sp, lineHeight = 20.sp,
        modifier = modifier
    )
}

@Composable
private fun TimeRow(m: ChatMessage, modifier: Modifier) {
    val s = LocalChatStrings.current
    val color = if (m.mine) SinusColors.palette.timeMine else SinusColors.palette.timeOther
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if ((m.expiresAt ?: 0L) > 0L) {
            SinusIcon("ic_timer", color, Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (m.trShown != null && m.kind == ChatMessage.KIND_TEXT) {
            SinusIcon("ic_translate", color, Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        if (m.edited) {
            SinusIcon("ic_edit", color, Modifier.size(12.dp), s.edited)
            Spacer(Modifier.width(3.dp))
        }
        Text(ChatFormat.time(m.at), color = color, fontSize = 11.sp)
        if (m.mine) {
            Spacer(Modifier.width(4.dp))
            StatusIcon(m.status, 16.dp)
        }
    }
}

@Composable
fun StatusIcon(status: String, size: Dp) {
    val (icon, tint) = when (status) {
        "pending", "uploading" -> "ic_clock" to Color(0x80FFFFFF)
        "failed" -> "ic_close" to Color(0xFFE65100)
        "undelivered" -> "ic_close" to Color(0xFFE5484D)
        ChatMessage.STATUS_DELIVERED -> "ic_check_double" to Color(0xFFAAAAAA)
        ChatMessage.STATUS_READ -> "ic_check_double" to SinusColors.primary
        else -> "ic_check" to Color(0xFFAAAAAA)
    }
    SinusIcon(icon, tint, Modifier.size(size))
}

@Composable
private fun PinnedLabel(text: String) {
    Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        SinusIcon("ic_pin", SinusColors.textMuted, Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = SinusColors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ReplyHint(visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            SinusIcon(
                "ic_reply", SinusColors.textSecondary,
                Modifier.size(20.dp).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            )
        }
    }
}

@Composable
private fun ReplyQuote(text: String, maxWidth: Dp, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 2.dp).widthIn(max = maxWidth).height(IntrinsicSize.Min)
            .background(SinusColors.surfaceVariant)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(end = 10.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(SinusColors.primary))
        Spacer(Modifier.width(8.dp))
        Text(
            text, color = SinusColors.textSecondary, fontSize = 12.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Reactions(values: List<String>) {
    val grouped = values.groupingBy { it }.eachCount()
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((emoji, count) in grouped) {
            Text(
                if (count > 1) "$emoji $count" else emoji,
                color = SinusColors.textPrimary, fontSize = 14.sp,
                modifier = Modifier.clip(shape).background(SinusColors.palette.overlay).border(1.dp, SinusColors.palette.overlayStroke, shape)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun TypingBubble(name: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing))
    )
    Row(
        modifier.shadow(6.dp, shape).clip(shape).background(if (SinusColors.palette.dark) Color(0xFF2C2C2E) else Color(0xFFE0E0E0))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (name != null) {
            LetterAvatar(name, AvatarColors.forHash(name), 22.dp)
            Spacer(Modifier.width(8.dp))
        }
        val waveColor = if (SinusColors.palette.dark) Color(0xFF8BBFED) else Color(0xFF5288C1)
        Canvas(Modifier.width(72.dp).height(26.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val amplitude = size.height * 0.38f
            val path = Path()
            var px = 0f
            while (px <= w) {
                val y = cy + amplitude * sin(px / w * 2.2f * 2f * PI.toFloat() - phase)
                if (px == 0f) path.moveTo(px, y) else path.lineTo(px, y)
                px += 1f
            }
            drawPath(path, waveColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Composer(store: ChatStore, convId: String, isGroup: Boolean, replyTo: ChatMessage?, onCancelReply: () -> Unit) {
    val s = LocalChatStrings.current
    val ms = pl.sinusoide.sinuschat.desktop.i18n.LocalMediaStrings.current
    var value by remember(convId) { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(convId) {
        store.restoreDraft.collect { (c, text) -> if (c == convId) value = TextFieldValue(text, TextRange(text.length)) }
    }
    val focus = remember { FocusRequester() }
    val staged by store.uploads.staged.collectAsState()
    val hasFiles = staged[convId].orEmpty().isNotEmpty()
    LaunchedEffect(convId, replyTo) { runCatching { focus.requestFocus() } }
    val vs = remember(s) { pl.sinusoide.sinuschat.desktop.i18n.VoiceStrings.of(s) }
    val recording by store.recorder.recording.collectAsState()
    val voiceSending by store.voiceSending.collectAsState()
    var locked by remember(convId) { mutableStateOf(false) }
    val voiceMode = !isGroup && value.text.isBlank() && !hasFiles
    val scope = rememberCoroutineScope()

    fun finishVoice(send: Boolean) {
        locked = false
        val take = store.recorder.stop() ?: return
        if (!send) return
        when {
            take.durationMs < 1_000L -> store.emit(ChatEvent.Info(vs.tooShort))
            take.silent -> store.emit(ChatEvent.Info(vs.micBlocked))
            else -> store.sendVoice(convId, take)
        }
    }

    DisposableEffect(convId) {
        store.recorder.onLimit = {
            scope.launch {
                finishVoice(true)
                store.emit(ChatEvent.Info(vs.limit))
            }
        }
        onDispose {
            store.recorder.onLimit = null
            if (store.recorder.recording.value) store.recorder.cancel()
        }
    }

    fun submit() {
        if (value.text.isBlank() && !hasFiles) return
        if (store.send(value.text, replyTo?.id)) {
            value = TextFieldValue("")
            onCancelReply()
        }
    }

    fun stageFiles(files: List<java.io.File>) {
        if (files.isEmpty()) return
        if (store.uploads.stage(convId, files).isNotEmpty()) store.emit(ChatEvent.SendFailed(LinkFrame.ERROR_TOO_BIG))
        runCatching { focus.requestFocus() }
    }

    Column(Modifier.fillMaxWidth().background(SinusColors.surface)) {
        val shownReply = rememberLast(replyTo)
        AnimatedVisibility(visible = replyTo != null && shownReply != null, enter = Anim.barIn, exit = Anim.barOut) {
            val reply = shownReply ?: return@AnimatedVisibility
            Row(
                Modifier.fillMaxWidth().background(SinusColors.surfaceVariant).height(IntrinsicSize.Min)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(SinusColors.primary))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.replying, color = SinusColors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        ChatFormat.preview(reply.kind, reply.text, reply.fileName, s),
                        color = SinusColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconAction("ic_close", s.cancel, SinusColors.textSecondary, Modifier.size(36.dp), onClick = onCancelReply)
            }
        }
        StagedBar(store, convId)
        AnimatedVisibility(visible = convId in voiceSending, enter = Anim.barIn, exit = Anim.barOut) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(10.dp))
                Text(vs.sending, color = SinusColors.textSecondary, fontSize = 12.5.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (recording) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { finishVoice(false) }.pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    SinusIcon("ic_delete", SinusColors.danger, Modifier.size(22.dp), s.cancel)
                }
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .clickable { stageFiles(MediaActions.chooseOpen(ms.attach)) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    SinusIcon("ic_attach", SinusColors.textSecondary, Modifier.size(22.dp), ms.attach)
                }
            }
            Spacer(Modifier.width(6.dp))
            val inputShape = RoundedCornerShape(22.dp)
            if (recording) RecordingStrip(store, locked, vs, Modifier.weight(1f)) else Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(inputShape).background(SinusColors.surfaceVariant)
                    .border(1.dp, SinusColors.border, inputShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        if (hasFiles) ms.captionHint else if (isGroup) s.inputGroupHint else s.inputHint,
                        color = SinusColors.textMuted, fontSize = 16.sp, maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        store.onComposerChanged(it.text)
                    },
                    textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(SinusColors.primary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.V && (e.isCtrlPressed || e.isMetaPressed)) {
                            pasteAttachments(store, convId, ::stageFiles)
                        } else if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                            if (e.isShiftPressed) {
                                val t = value.text
                                val sel = value.selection
                                val next = t.substring(0, sel.min) + "\n" + t.substring(sel.max)
                                value = TextFieldValue(next, TextRange(sel.min + 1))
                            } else {
                                submit()
                            }
                            true
                        } else false
                    }
                )
            }
            Spacer(Modifier.width(6.dp))
            val enabled = value.text.isNotBlank() || hasFiles || voiceMode || recording
            val showMic = voiceMode && !recording
            val currentRecording by androidx.compose.runtime.rememberUpdatedState(recording)
            val currentVoiceMode by androidx.compose.runtime.rememberUpdatedState(voiceMode)
            val currentSubmit by androidx.compose.runtime.rememberUpdatedState({ submit() })
            val currentFinish by androidx.compose.runtime.rememberUpdatedState({ send: Boolean -> finishVoice(send) })
            Box(
                Modifier.size(44.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                    .background(if (recording) SinusColors.danger else SinusColors.primary)
                    .pointerInput(convId) {
                        detectTapGestures(onPress = {
                            when {
                                currentRecording -> if (tryAwaitRelease()) currentFinish(true)
                                currentVoiceMode -> {
                                    if (!store.startRecording()) {
                                        store.emit(ChatEvent.Info(vs.cantRecord))
                                        return@detectTapGestures
                                    }
                                    locked = false
                                    val pressedAt = System.currentTimeMillis()
                                    val released = tryAwaitRelease()
                                    when {
                                        !released -> currentFinish(false)
                                        System.currentTimeMillis() - pressedAt >= HOLD_TO_SEND_MS -> currentFinish(true)
                                        else -> locked = true
                                    }
                                }
                                else -> if (tryAwaitRelease()) currentSubmit()
                            }
                        })
                    }
                    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = showMic, animationSpec = tween(140)) { mic ->
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        if (mic) SinusIcon("ic_mic", Color.White, Modifier.size(22.dp), vs.hint)
                        else SinusIcon("ic_send", Color.White, Modifier.size(22.dp), s.send)
                    }
                }
            }
        }
    }
}

private const val HOLD_TO_SEND_MS = 350L

@Composable
private fun RecordingStrip(store: ChatStore, locked: Boolean, vs: pl.sinusoide.sinuschat.desktop.i18n.VoiceStrings, modifier: Modifier) {
    val bars = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            bars.add(store.recorder.level.value)
            if (bars.size > 72) bars.removeAt(0)
            delay(70)
        }
    }
    val elapsed by produceState(0L) {
        while (true) {
            value = (System.currentTimeMillis() - store.recorder.startedAt).coerceAtLeast(0L)
            delay(200)
        }
    }
    val pulse = rememberInfiniteTransition()
    val dot by pulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse))
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(SinusColors.surfaceVariant).border(1.dp, SinusColors.border, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(SinusColors.danger.copy(alpha = dot)))
        Spacer(Modifier.width(10.dp))
        val sec = elapsed / 1000L
        Text("%d:%02d".format(sec / 60, sec % 60), color = SinusColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        val color = SinusColors.primary
        Canvas(Modifier.weight(1f).height(26.dp)) {
            val step = 4.dp.toPx()
            val w = 2.4.dp.toPx()
            val count = (size.width / step).toInt().coerceAtLeast(1)
            val shown = bars.takeLast(count)
            val startX = size.width - shown.size * step
            shown.forEachIndexed { i, v ->
                val h = (size.height * (0.12f + 0.88f * kotlin.math.sqrt(v))).coerceAtMost(size.height)
                val x = startX + i * step + w / 2f
                drawLine(color, Offset(x, (size.height - h) / 2f), Offset(x, (size.height + h) / 2f), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
        if (locked) {
            Spacer(Modifier.width(10.dp))
            SinusIcon("ic_lock", SinusColors.textMuted, Modifier.size(15.dp), vs.locked)
        }
    }
}

private fun pasteAttachments(store: ChatStore, convId: String, stageFiles: (List<java.io.File>) -> Unit): Boolean = runCatching {
    val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard
    val flavors = clip.availableDataFlavors.toSet()
    when {
        java.awt.datatransfer.DataFlavor.javaFileListFlavor in flavors -> {
            @Suppress("UNCHECKED_CAST")
            val files = (clip.getData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<java.io.File>).filter { it.isFile }
            stageFiles(files)
            files.isNotEmpty()
        }
        java.awt.datatransfer.DataFlavor.imageFlavor in flavors && java.awt.datatransfer.DataFlavor.stringFlavor !in flavors -> {
            val image = clip.getData(java.awt.datatransfer.DataFlavor.imageFlavor) as? java.awt.Image ?: return@runCatching false
            store.uploads.stageImage(convId, toBuffered(image))
        }
        else -> false
    }
}.getOrDefault(false)

private fun toBuffered(image: java.awt.Image): java.awt.image.BufferedImage {
    if (image is java.awt.image.BufferedImage) return image
    val w = image.getWidth(null).coerceAtLeast(1)
    val h = image.getHeight(null).coerceAtLeast(1)
    val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()
    return out
}

private fun copyText(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}
