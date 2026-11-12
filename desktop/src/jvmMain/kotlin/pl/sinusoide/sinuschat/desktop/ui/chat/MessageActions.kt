package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import pl.sinusoide.sinuschat.desktop.ui.anim.Reveal
import pl.sinusoide.sinuschat.desktop.ui.anim.dialogIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import kotlin.math.roundToInt

internal val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "👏", "🔥", "🥳", "😍", "💯", "😎", "🤔", "😭", "🙌")

internal const val UNDO_MS = 5_000L

internal val selectedRowColor = Color(0x335288C1)

internal fun overshoot(tension: Float): Easing = Easing { t ->
    val x = t - 1f
    x * x * ((tension + 1f) * x + tension) + 1f
}

internal val sheetFill: Color get() = if (SinusColors.palette.backdrop) Color(0xF2141C2B) else SinusColors.card
internal val sheetStroke: Color get() = if (SinusColors.palette.backdrop) Color(0x3DFFFFFF) else SinusColors.border
private val reactionFill: Color
    get() = when {
        SinusColors.palette.backdrop -> Color(0xE6141C2B)
        SinusColors.palette.dark -> Color(0xFF242F3D)
        else -> SinusColors.card
    }
private val reactionStroke: Color get() = if (SinusColors.palette.dark) Color(0x33FFFFFF) else Color(0x22000000)

internal class MessageCapture(val layer: GraphicsLayer) {
    var coords: LayoutCoordinates? = null
}

internal class Shot(val image: ImageBitmap, val topLeft: Offset)

@Stable
internal class MessagePicker {
    var picked by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    var reactFor by mutableStateOf<String?>(null)
    var reactAnchor by mutableStateOf(Rect.Zero)
        private set
    var ghosts by mutableStateOf<Set<String>>(emptySet())
    val captures = HashMap<String, MessageCapture>()
    var origin = Offset.Zero

    val active: Boolean get() = picked.isNotEmpty()

    fun isPicked(id: String) = id in picked

    fun start(id: String, members: List<String>) {
        picked = mapOf(id to members)
        val anchor = bounds(id)
        reactAnchor = anchor ?: Rect.Zero
        reactFor = if (anchor != null) id else null
    }

    fun toggle(id: String, members: List<String>) {
        picked = if (id in picked) picked - id else picked + (id to members)
        reactFor = null
    }

    fun clear() {
        picked = emptyMap()
        reactFor = null
    }

    fun retain(present: Set<String>) {
        if (picked.keys.any { it !in present }) picked = picked.filterKeys { it in present }
        if (reactFor != null && reactFor !in picked) reactFor = null
    }

    private fun bounds(id: String): Rect? {
        val c = captures[id]?.coords?.takeIf { it.isAttached } ?: return null
        return Rect(c.positionInRoot() - origin, c.size.toSize())
    }

    suspend fun shot(id: String): Shot? {
        val cap = captures[id] ?: return null
        val c = cap.coords?.takeIf { it.isAttached } ?: return null
        if (c.size.width <= 0 || c.size.height <= 0) return null
        val image = runCatching { cap.layer.toImageBitmap() }.getOrNull() ?: return null
        return Shot(image, c.positionInRoot() - origin)
    }
}

internal class PendingUndo(val keys: List<String>, val ids: List<String>) {
    val startedAt = System.currentTimeMillis()
}

@Composable
internal fun SelectionBar(
    count: Int,
    canReply: Boolean,
    canMore: Boolean,
    onClose: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onMore: () -> Unit,
    canPin: Boolean = false,
    pinned: Boolean = false,
    pinLabel: String = "",
    onPin: () -> Unit = {}
) {
    val s = LocalChatStrings.current
    val tint = SinusColors.textPrimary
    val shown = remember { mutableIntStateOf(1) }
    if (count > 0) shown.intValue = count
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).background(SinusColors.surface).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction("ic_back", s.cancel, tint, Modifier.size(44.dp), onClick = onClose)
            AnimatedContent(
                targetState = shown.intValue,
                transitionSpec = {
                    val up = targetState > initialState
                    (fadeIn(tween(160)) + slideInVertically(tween(200, easing = Motion.enter)) { if (up) it / 2 else -it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(160)) { if (up) -it / 2 else it / 2 })
                },
                modifier = Modifier.padding(start = 12.dp).weight(1f)
            ) { n ->
                Text(
                    n.toString(), color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            SlideAction(canReply, "ic_reply", s.reply, onReply)
            SlideAction(canPin, if (pinned) "ic_pinoff" else "ic_pin", pinLabel, onPin)
            IconAction("ic_delete", s.delete, tint, onClick = onDelete)
            IconAction("ic_forward", s.forward, tint, onClick = onForward)
            SlideAction(canMore, "ic_more", s.more, onMore)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SinusColors.border))
    }
}

@Composable
private fun SlideAction(visible: Boolean, icon: String, label: String, onClick: () -> Unit) {
    Reveal(
        visible = visible,
        enter = fadeIn(tween(160)) + expandHorizontally(tween(200, easing = Motion.enter)),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160))
    ) {
        IconAction(icon, label, SinusColors.textPrimary, onClick = onClick)
    }
}

@Composable
internal fun ReactionBar(
    mine: Boolean,
    anchor: Rect,
    area: IntSize,
    onPick: (String) -> Unit,
    onMore: () -> Unit
) {
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val barH = with(density) { 56.dp.toPx() }
    val barW = with(density) { 352.dp.toPx() }.coerceAtMost(area.width - margin * 2).coerceAtLeast(0f)
    val above = anchor.top - gap - barH
    val y = when {
        above >= margin -> above
        anchor.bottom + gap + barH <= area.height - margin -> anchor.bottom + gap
        else -> margin
    }
    val wantX = if (mine) anchor.right - barW else anchor.left
    val x = wantX.coerceIn(margin, (area.width - barW - margin).coerceAtLeast(margin))
    val fromBelow = y < anchor.top
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(180, easing = Motion.enter)) }
    val shape = RoundedCornerShape(SinusColors.radius(26.dp))
    Row(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(with(density) { barW.toDp() })
            .height(56.dp)
            .graphicsLayer {
                alpha = k.value
                val sc = 0.9f + 0.1f * k.value
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(if (mine) 0.85f else 0.15f, if (fromBelow) 1f else 0f)
            }
            .shadow(if (SinusColors.palette.backdrop) 0.dp else 10.dp, shape)
            .clip(shape)
            .background(reactionFill)
            .border(1.dp, reactionStroke, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            QUICK_REACTIONS.forEachIndexed { i, emoji -> EmojiKey(emoji, i) { onPick(emoji) } }
        }
        Spacer(Modifier.width(6.dp))
        PlusKey(QUICK_REACTIONS.size, onMore)
    }
}

@Composable
private fun Modifier.emojiPop(index: Int): Modifier {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 35L)
        k.animateTo(1f, tween(160, easing = overshoot(2f)))
    }
    return graphicsLayer {
        scaleX = k.value
        scaleY = k.value
    }
}

@Composable
private fun EmojiKey(emoji: String, index: Int, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier.emojiPop(index)
            .clip(RoundedCornerShape(SinusColors.radius(16.dp)))
            .background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 24.sp, lineHeight = 30.sp)
    }
}

@Composable
private fun PlusKey(index: Int, onClick: () -> Unit) {
    val flow = SinusColors.palette.backdrop
    Box(
        Modifier.emojiPop(index).size(40.dp).clip(CircleShape)
            .background(if (flow) Color(0x2BFFFFFF) else SinusColors.surfaceVariant)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon("ic_add", if (flow) Color.White else SinusColors.textPrimary, Modifier.size(24.dp))
    }
}

internal class MenuItem(val icon: String, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun MoreMenu(items: List<MenuItem>, onDismiss: () -> Unit) {
    val flow = SinusColors.palette.backdrop
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(if (flow) 420 else 260, easing = overshoot(if (flow) 1.25f else 1.1f))) }
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 12.dp)
                .widthIn(min = 212.dp)
                .width(IntrinsicSize.Max)
                .graphicsLayer {
                    alpha = k.value.coerceIn(0f, 1f)
                    scaleX = (if (flow) 0.3f else 0.7f) + (1f - (if (flow) 0.3f else 0.7f)) * k.value
                    scaleY = (if (flow) 0.15f else 0.7f) + (1f - (if (flow) 0.15f else 0.7f)) * k.value
                    transformOrigin = TransformOrigin(1f - 28.dp.toPx() / size.width.coerceAtLeast(1f), 0f)
                }
                .shadow(if (flow) 0.dp else 14.dp, shape)
                .clip(shape)
                .background(sheetFill)
                .border(1.dp, sheetStroke, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(6.dp)
        ) {
            items.forEachIndexed { i, item -> MenuRow(item, i, onDismiss) }
        }
    }
}

@Composable
internal fun MenuRow(item: MenuItem, index: Int, onDismiss: () -> Unit) {
    val flow = SinusColors.palette.backdrop
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L + index * 45L)
        k.animateTo(1f, tween(220, easing = Motion.enter))
    }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val fg = if (item.danger) SinusColors.danger else if (flow) Color.White else SinusColors.textPrimary
    val iconTint = if (item.danger) SinusColors.danger else if (flow) Color.White else SinusColors.textSecondary
    Row(
        Modifier
            .graphicsLayer {
                alpha = k.value
                translationY = -6.dp.toPx() * (1f - k.value)
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(SinusColors.radius(16.dp)))
            .background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
            .clickable(interactionSource = hover, indication = null) {
                onDismiss()
                item.onClick()
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 14.dp, end = 22.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon(item.icon, iconTint, Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(item.label, color = fg, fontSize = 15.5.sp)
    }
}

@Composable
internal fun DeleteSheet(
    items: List<ChatMessage>,
    isGroup: Boolean,
    senderOf: (ChatMessage) -> String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalChatStrings.current
    val red = SinusColors.danger
    val many = items.size > 1
    val first = items.firstOrNull() ?: return
    val shape = RoundedCornerShape(topStart = SinusColors.radius(28.dp), topEnd = SinusColors.radius(28.dp))
    Column(
        modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .shadow(if (SinusColors.palette.backdrop) 0.dp else 16.dp, shape)
            .clip(shape)
            .background(sheetFill)
            .border(1.dp, if (SinusColors.palette.backdrop) sheetStroke else Color.Transparent, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(3.dp))
                .background(SinusColors.textSecondary.copy(alpha = 0x55 / 255f))
        )
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(64.dp).clip(CircleShape).background(red.copy(alpha = 0x26 / 255f)), contentAlignment = Alignment.Center) {
            SinusIcon("ic_delete", red, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (many) s.deleteManyQ(items.size) else s.deleteOneQ,
            color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Text(
            when {
                many && isGroup -> s.deleteManyDescGroup
                many -> s.deleteManyDesc
                isGroup -> s.deleteDescGroup
                else -> s.deleteDesc
            },
            color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
        )
        if (!many) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 22.dp)
                    .clip(RoundedCornerShape(SinusColors.radius(16.dp)))
                    .background(if (SinusColors.palette.backdrop) Color(0x17FFFFFF) else SinusColors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(senderOf(first), color = SinusColors.primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    ChatFormat.preview(first.kind, first.text, first.fileName, s),
                    color = SinusColors.textPrimary, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(26.dp))).background(red)
                .clickable(onClick = onConfirm).pointerHoverIcon(PointerIcon.Hand)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(s.delete, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(14.dp)))
                .clickable(onClick = onDismiss).pointerHoverIcon(PointerIcon.Hand)
                .padding(top = 14.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(s.cancel, color = SinusColors.textSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
internal fun UndoBar(undo: PendingUndo, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalChatStrings.current
    val count = undo.keys.size
    val ring = remember(undo) { Animatable(1f) }
    LaunchedEffect(undo) {
        val left = (UNDO_MS - (System.currentTimeMillis() - undo.startedAt)).coerceAtLeast(0L)
        ring.snapTo(left.toFloat() / UNDO_MS)
        ring.animateTo(0f, tween(left.toInt(), easing = LinearEasing))
    }
    val shape = RoundedCornerShape(SinusColors.radius(16.dp))
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(if (SinusColors.palette.backdrop) sheetFill else SinusColors.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            val arc = Color(0xFF2196F3)
            Canvas(Modifier.size(32.dp)) {
                val w = 2.dp.toPx()
                val inset = w
                val box = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Color(0x22888888), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
                drawArc(arc, -90f, 360f * ring.value, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
            }
            SinusIcon("ic_delete", SinusColors.textPrimary, Modifier.size(16.dp))
        }
        Text(
            if (count > 1) s.deletedCount(count) else s.deleted,
            color = SinusColors.textPrimary, fontSize = 15.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
        Text(
            s.undo, color = SinusColors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable(onClick = onUndo)
                .pointerHoverIcon(PointerIcon.Hand).padding(8.dp)
        )
    }
}

@Composable
internal fun ForwardDialog(store: ChatStore, onDismiss: () -> Unit, onPick: (ChatSummary, String) -> Unit) {
    val s = LocalChatStrings.current
    val chats by store.chats.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val titled = remember(chats) { chats.map { it to it.title.ifBlank { if (it.isGroup) s.group else s.contact } } }
    val shown = remember(titled, query) {
        val q = query.trim()
        if (q.isEmpty()) titled else titled.filter { it.second.contains(q, ignoreCase = true) }
    }
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .dialogIn()
                .shadow(if (SinusColors.palette.backdrop) 0.dp else 16.dp, shape)
                .clip(shape)
                .background(sheetFill)
                .border(1.dp, sheetStroke, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.forwardTo, color = SinusColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconAction("ic_close", s.cancel, SinusColors.textSecondary, onClick = onDismiss)
            }
            val fieldShape = RoundedCornerShape(SinusColors.radius(22.dp))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(44.dp).clip(fieldShape)
                    .background(SinusColors.surfaceVariant).border(1.dp, SinusColors.border, fieldShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SinusIcon("ic_search", SinusColors.textSecondary, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(s.forwardSearch, color = SinusColors.textMuted, fontSize = 15.sp, maxLines = 1)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(SinusColors.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
            }
            Text(
                s.forwardChats.uppercase(), color = SinusColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
            )
            if (shown.isEmpty()) {
                Text(
                    s.forwardNoResults, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 8.dp)) {
                    items(shown, key = { it.first.id }) { (chat, title) ->
                        ForwardRow(store, chat, title) { onPick(chat, title) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardRow(store: ChatStore, chat: ChatSummary, title: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(SinusColors.radius(14.dp)))
            .background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(title, chat.color, 42.dp, rememberAvatar(store, chat.id, chat.avatarKey))
        Spacer(Modifier.width(12.dp))
        Text(
            title, color = SinusColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (chat.isGroup) SinusIcon("ic_group", SinusColors.textMuted, Modifier.size(18.dp))
    }
}
