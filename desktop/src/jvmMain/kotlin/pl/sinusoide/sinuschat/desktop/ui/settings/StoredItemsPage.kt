package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.StorageUsage
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.StoredStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.chat.ChatFormat
import pl.sinusoide.sinuschat.desktop.ui.chat.MediaActions
import pl.sinusoide.sinuschat.desktop.ui.chat.thumbOf
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage

internal class StorageFilter(val kind: StorageUsage.Kind?, val conv: String?, val title: String)

private fun kindOf(m: ChatMessage): StorageUsage.Kind = StorageUsage.kindOf(m.kind)

@Composable
internal fun StoredItemsPage(store: ChatStore, filter: StorageFilter, onToast: (String) -> Unit) {
    val chat = LocalChatStrings.current
    val ss = remember(chat) { StoredStrings.of(chat) }
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<Pair<List<ChatStore.StoredItem>, Int>?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(refresh, filter.conv, filter.kind) {
        data = withContext(Dispatchers.IO) {
            val (items, older) = store.storedMedia(filter.conv)
            items.filter { filter.kind == null || kindOf(it.message) == filter.kind } to (if (filter.kind == null) older else 0)
        }
    }
    val current = data
    Spacer(Modifier.height(8.dp))
    if (current == null) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
        }
        return
    }
    val (items, older) = current
    val total = items.sumOf { it.bytes }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(14.dp))).background(SinusColors.primary.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon("ic_pc", SinusColors.primary, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(ss.localNote, color = SinusColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
    }
    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SinusIcon("ic_no_media", SinusColors.textMuted, Modifier.size(44.dp))
            Text(ss.empty, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            if (older > 0) Text(ss.older(older), color = SinusColors.textMuted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    SectionTitle("${filter.title} · ${MediaActions.formatSize(total)}")
    val visual = items.filter { it.message.kind == ChatMessage.KIND_PHOTO || it.message.kind == ChatMessage.KIND_VIDEO }
    val rest = items - visual.toSet()
    if (visual.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visual.chunked(4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { item ->
                        Box(Modifier.weight(1f)) { Tile(store, item, ss) { store.deleteStored(listOf(item)); onToast(ss.deleted); refresh++ } }
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (rest.isNotEmpty()) {
        if (visual.isNotEmpty()) Spacer(Modifier.height(12.dp))
        Card {
            rest.forEachIndexed { i, item ->
                if (i > 0) RowDivider()
                val m = item.message
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (m.kind == ChatMessage.KIND_VOICE) "ic_mic" else "ic_document", SinusColors.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.fileName?.ifBlank { null } ?: ChatFormat.kindLabel(m.kind, null, chat).orEmpty(),
                            color = SinusColors.textPrimary, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            MediaActions.formatSize(item.bytes) + " · " + ChatFormat.listTime(m.at, chat, java.util.Locale.getDefault()),
                            color = SinusColors.textMuted, fontSize = 12.sp
                        )
                    }
                    pl.sinusoide.sinuschat.desktop.ui.IconAction("ic_delete", ss.deleteOne, SinusColors.danger) {
                        store.deleteStored(listOf(item)); onToast(ss.deleted); refresh++
                    }
                }
            }
        }
    }
    if (older > 0) Text(ss.older(older), color = SinusColors.textMuted, fontSize = 12.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(16.dp))).background(SinusColors.danger.copy(alpha = 0.14f))
            .clickable { confirmAll = true }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(ss.deleteAll + " · " + MediaActions.formatSize(total), color = SinusColors.danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    if (confirmAll) {
        ConfirmDialog("ic_delete", SinusColors.danger, ss.deleteAllQ, ss.localNote, ss.deleteAll, onDismiss = { confirmAll = false }) {
            confirmAll = false
            store.deleteStored(items)
            onToast(ss.deleted)
            refresh++
        }
    }
}

@Composable
private fun Tile(store: ChatStore, item: ChatStore.StoredItem, ss: StoredStrings, onDelete: () -> Unit) {
    val m = item.message
    val thumb = thumbOf(store, m)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(SinusColors.palette.mediaPlaceholder),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else SinusIcon(if (m.kind == ChatMessage.KIND_VIDEO) "ic_video" else "ic_image", SinusColors.textMuted, Modifier.size(28.dp))
        Text(
            MediaActions.formatSize(item.bytes), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).clip(CircleShape).background(Color(0x99000000))
                .clickable(onClick = onDelete).pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon("ic_delete", Color.White, Modifier.size(16.dp), ss.deleteOne)
        }
    }
}
