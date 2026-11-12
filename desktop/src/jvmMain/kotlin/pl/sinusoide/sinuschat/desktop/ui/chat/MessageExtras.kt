package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.i18n.ActionStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.dialogIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object ReactionEmojis {
    val FACES = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
        "😋", "😛", "😜", "🤪", "😝", "🤗", "🤭", "🤫", "🤔", "🫡", "🤨", "😐", "😶", "😏", "😒", "🙄",
        "😬", "😌", "😔", "😪", "😴", "🤒", "🤢", "🤮", "🥵", "🥶", "🥴", "🤯", "🤠", "🥳", "😎", "🤓",
        "🧐", "😕", "😟", "🙁", "😮", "😲", "😳", "🥺", "😦", "😨", "😰", "😥", "😢", "😭", "😱", "😖",
        "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻", "👽", "🤖"
    )
    val GESTURES = listOf(
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋",
        "🤚", "🖖", "👋", "🤝", "🙏", "👏", "🙌", "🫶", "👐", "🤲", "✍️", "💪"
    )
    val HEARTS = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗",
        "💖", "💘", "💝", "💯"
    )
    val MORE = listOf(
        "🔥", "✨", "⭐", "🌟", "💫", "🎉", "🎊", "🎁", "🏆", "⚡", "💥", "💦", "💤", "👀", "🧠", "🙈",
        "🙉", "🙊", "🐶", "🐱", "🦄", "🍕", "🍺", "☕", "🎂", "⚽", "🎮", "🎵", "💡", "✅", "❌", "❓"
    )
}

private val pinDate = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@Composable
private fun DialogCard(maxWidth: Int, maxHeight: Int, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = maxWidth.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .dialogIn()
                .shadow(if (SinusColors.palette.backdrop) 0.dp else 16.dp, shape)
                .clip(shape)
                .background(sheetFill)
                .border(1.dp, sheetStroke, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            content()
        }
    }
}

@Composable
private fun DialogTitle(text: String, closeLabel: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, color = SinusColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconAction("ic_close", closeLabel, SinusColors.textSecondary, onClick = onDismiss)
    }
}

@Composable
internal fun PinnedDialog(
    items: List<ChatMessage>,
    isGroup: Boolean,
    title: String,
    acts: ActionStrings,
    onJump: (ChatMessage) -> Unit,
    onUnpin: (ChatMessage) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalChatStrings.current
    DialogCard(440, 560) {
        DialogTitle(acts.pinnedTitle, s.cancel, onDismiss)
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SinusIcon("ic_pin", SinusColors.textMuted, Modifier.size(34.dp))
                Text(
                    acts.noPinned, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 4.dp, bottom = 8.dp)) {
                items(items, key = { it.id }) { m ->
                    val who = when {
                        m.mine -> s.you
                        isGroup -> m.senderName.ifBlank { s.member }
                        else -> title
                    }
                    PinnedRow(m, who, acts, { onJump(m) }, { onUnpin(m) })
                }
            }
        }
    }
}

@Composable
private fun PinnedRow(m: ChatMessage, who: String, acts: ActionStrings, onClick: () -> Unit, onUnpin: () -> Unit) {
    val s = LocalChatStrings.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val preview = if (m.viewOnce) s.viewOnce else ChatFormat.preview(m.kind, m.text, m.fileName, s).ifBlank { s.quoteFallback }
    val icon = if (m.viewOnce) null else ChatFormat.kindIcon(m.kind)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(SinusColors.radius(14.dp)))
            .background(if (hovered) SinusColors.palette.overlay else Color.Transparent)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).heightIn(min = 36.dp).clip(RoundedCornerShape(2.dp)).background(SinusColors.primary))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    who, color = SinusColors.primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    Instant.ofEpochMilli(m.at).atZone(ZoneId.systemDefault()).format(pinDate),
                    color = SinusColors.textMuted, fontSize = 11.5.sp, maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    SinusIcon(icon, SinusColors.textSecondary, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    preview, color = SinusColors.textPrimary, fontSize = 14.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconAction("ic_pinoff", acts.unpin, SinusColors.textSecondary, Modifier.size(40.dp), onClick = onUnpin)
    }
}

@Composable
internal fun EditDialog(m: ChatMessage, acts: ActionStrings, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val s = LocalChatStrings.current
    var value by remember(m.id) { mutableStateOf(TextFieldValue(m.text, TextRange(m.text.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(m.id) { runCatching { focus.requestFocus() } }
    val changed = value.text.isNotBlank() && value.text.trim() != m.text
    fun save() {
        if (changed) onSave(value.text.trim()) else onDismiss()
    }
    DialogCard(460, 520) {
        DialogTitle(acts.edit, s.cancel, onDismiss)
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(14.dp)))
                    .background(if (SinusColors.palette.backdrop) Color(0x17FFFFFF) else SinusColors.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SinusIcon("ic_edit", SinusColors.primary, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(m.text, color = SinusColors.textSecondary, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(12.dp))
            val fieldShape = RoundedCornerShape(SinusColors.radius(18.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(fieldShape)
                    .background(SinusColors.surfaceVariant).border(1.dp, SinusColors.primary.copy(alpha = 0.6f), fieldShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.text.isEmpty()) Text(s.inputHint, color = SinusColors.textMuted, fontSize = 15.sp, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = TextStyle(color = SinusColors.textPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(SinusColors.primary),
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                if (e.isShiftPressed) {
                                    val t = value.text
                                    val sel = value.selection
                                    value = TextFieldValue(t.substring(0, sel.min) + "\n" + t.substring(sel.max), TextRange(sel.min + 1))
                                } else save()
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
                )
            }
            Spacer(Modifier.size(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.cancel, color = SinusColors.textSecondary, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable(onClick = onDismiss)
                        .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(SinusColors.radius(22.dp)))
                        .background(if (changed) SinusColors.primary else SinusColors.primary.copy(alpha = 0.4f))
                        .clickable(enabled = changed, onClick = ::save)
                        .pointerHoverIcon(if (changed) PointerIcon.Hand else PointerIcon.Default)
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(acts.save, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmojiDialog(current: String?, acts: ActionStrings, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val s = LocalChatStrings.current
    val sections = listOf(
        acts.faces to ReactionEmojis.FACES,
        acts.gestures to ReactionEmojis.GESTURES,
        acts.hearts to ReactionEmojis.HEARTS,
        acts.others to ReactionEmojis.MORE
    )
    DialogCard(460, 600) {
        DialogTitle(acts.chooseReaction, s.cancel, onDismiss)
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            for ((label, emojis) in sections) {
                Text(
                    label.uppercase(), color = SinusColors.textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp)
                )
                FlowRow(Modifier.fillMaxWidth()) {
                    emojis.forEach { emoji -> EmojiCell(emoji, emoji == current) { onPick(emoji) } }
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(SinusColors.radius(14.dp))
    Box(
        Modifier.size(50.dp).padding(2.dp).clip(shape)
            .background(
                when {
                    selected -> SinusColors.primary.copy(alpha = 0.24f)
                    hovered -> SinusColors.palette.overlay
                    else -> Color.Transparent
                }
            )
            .then(if (selected) Modifier.border(1.5.dp, SinusColors.primary, shape) else Modifier)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 26.sp, lineHeight = 32.sp)
    }
}

internal fun langName(ts: pl.sinusoide.sinuschat.desktop.i18n.TranslateStrings, code: String): String = when (code) {
    "pl" -> ts.polish
    "en" -> ts.english
    "cs" -> ts.czech
    "uk" -> ts.ukrainian
    "zh" -> ts.chinese
    else -> ts.german
}

@Composable
internal fun TranslatePill(offer: pl.sinusoide.sinuschat.link.LinkFrame.TranslateOffer, onAnswer: (Boolean) -> Unit) {
    val ts = pl.sinusoide.sinuschat.desktop.i18n.TranslateStrings.of(LocalChatStrings.current)
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.padding(horizontal = 12.dp).shadow(6.dp, shape).clip(shape).background(SinusColors.surfaceVariant)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon("ic_translate", SinusColors.primary, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.padding(end = 12.dp)) {
            Text(ts.pill, color = SinusColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(langName(ts, offer.lang) + " > " + langName(ts, offer.to), color = SinusColors.textSecondary, fontSize = 12.sp)
        }
        Text(
            ts.yes, color = SinusColors.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(SinusColors.primary).clickable { onAnswer(true) }
                .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp, vertical = 7.dp)
        )
        Text(
            ts.no, color = SinusColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(18.dp)).clickable { onAnswer(false) }
                .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
internal fun TranslateSendDialog(
    offer: pl.sinusoide.sinuschat.link.LinkFrame.TranslateSendOffer,
    onTranslated: () -> Unit,
    onOriginal: () -> Unit,
    onCancel: () -> Unit
) {
    val ts = pl.sinusoide.sinuschat.desktop.i18n.TranslateStrings.of(LocalChatStrings.current)
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
    ) {
        DialogCard(460, 640) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(SinusColors.radius(14.dp))).background(SinusColors.primary.copy(alpha = 0x24 / 255f)),
                        contentAlignment = Alignment.Center
                    ) { SinusIcon("ic_translate", SinusColors.primary, Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(ts.sendTitle, color = SinusColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(langName(ts, offer.from) + " > " + langName(ts, offer.to), color = SinusColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Column(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(18.dp))).background(SinusColors.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(ts.sendYours.uppercase(), color = SinusColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.66.sp)
                    Text(offer.original, color = SinusColors.textSecondary, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(SinusColors.divider))
                    Text(ts.sendPreview.uppercase(), color = SinusColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.66.sp, modifier = Modifier.padding(top = 12.dp))
                    Text(offer.translated, color = SinusColors.textPrimary, fontSize = 15.5.sp, lineHeight = 21.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Text(ts.sendHint, color = SinusColors.textSecondary, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(26.dp))).background(SinusColors.primary)
                        .clickable(onClick = onTranslated).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    SinusIcon("ic_translate", SinusColors.onAccent, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ts.sendTranslated, color = SinusColors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(26.dp)))
                        .border(1.5.dp, SinusColors.primary.copy(alpha = 0.4f), RoundedCornerShape(SinusColors.radius(26.dp)))
                        .clickable(onClick = onOriginal).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    SinusIcon("ic_send", SinusColors.primary, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ts.sendOriginal, color = SinusColors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    ts.cancel, color = SinusColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable(onClick = onCancel)
                        .pointerHoverIcon(PointerIcon.Hand).padding(top = 14.dp, bottom = 4.dp)
                )
            }
        }
    }
}
