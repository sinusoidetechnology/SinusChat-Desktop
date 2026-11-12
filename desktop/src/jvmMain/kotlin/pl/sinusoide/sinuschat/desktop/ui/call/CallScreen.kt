package pl.sinusoide.sinuschat.desktop.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import pl.sinusoide.sinuschat.desktop.call.ShareTarget
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.sinusoide.sinuschat.desktop.call.CallManager
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.CallStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.chat.Avatar
import pl.sinusoide.sinuschat.desktop.ui.chat.avatarKey
import pl.sinusoide.sinuschat.desktop.ui.chat.rememberAvatar
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.LinkFrame
import kotlin.math.min

private val Red = Color(0xFFE5484D)
private val Green = Color(0xFF2FBF71)
private val Dark = Color(0xFF0E1218)

fun endText(reason: String?, s: CallStrings): String = when (reason) {
    LinkFrame.CALL_PEER_HUNG_UP -> s.peerHungUp
    LinkFrame.CALL_PEER_REJECTED -> s.peerRejected
    LinkFrame.CALL_NO_ANSWER -> s.noAnswer
    LinkFrame.CALL_BUSY -> s.busy
    LinkFrame.CALL_FAILED -> s.failed
    LinkFrame.CALL_ON_PHONE -> s.onPhone
    LinkFrame.CALL_GROUP -> s.groupOnPhone
    CallManager.ERROR_OFFLINE -> s.offline
    else -> s.finished
}

@Composable
fun CallOverlay(store: ChatStore) {
    val calls = store.calls
    val call by calls.state.collectAsState()
    val s = CallStrings.of(LocalChatStrings.current)
    val notice by calls.notice.collectAsState()
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2_500)
            calls.clearNotice()
        }
    }
    AnimatedVisibility(visible = call != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
        val c = call ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize()
                .background(if (c.phase == CallManager.Phase.INCOMING) Color(0x99000000) else Dark)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
        ) {
            if (c.phase == CallManager.Phase.INCOMING) IncomingCard(store, c, s)
            else ActiveCall(store, c, s)
            val noticeText = when (notice) {
                CallManager.ERROR_NO_CAMERA -> s.noCamera
                CallManager.ERROR_SHARE -> s.screenFailed
                CallManager.ERROR_SHARE_WAIT -> s.screenWait
                else -> null
            }
            if (noticeText != null) {
                Text(
                    noticeText, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun peerImage(store: ChatStore, conv: String): ImageBitmap? {
    val chats by store.chats.collectAsState()
    val chat = chats.firstOrNull { it.id == conv }
    return rememberAvatar(store, conv, chat?.avatarKey ?: conv)
}

@Composable
private fun peerColor(store: ChatStore, conv: String): String? {
    val chats by store.chats.collectAsState()
    return chats.firstOrNull { it.id == conv }?.color
}

@Composable
private fun IncomingCard(store: ChatStore, c: CallManager.CallUi, s: CallStrings) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(340.dp).clip(RoundedCornerShape(28.dp)).background(Dark).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(c.name, peerColor(store, c.conv), 112.dp, peerImage(store, c.conv))
            Spacer(Modifier.height(18.dp))
            Text(c.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(if (c.video) s.incomingVideo else s.incomingVoice, color = Color(0xCCFFFFFF), fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                RoundAction("ic_call_end", s.decline, Red, 64.dp) { store.calls.decline() }
                RoundAction("ic_call_answer", s.answer, Green, 64.dp) { store.calls.accept() }
            }
        }
    }
}

@Composable
private fun ActiveCall(store: ChatStore, c: CallManager.CallUi, s: CallStrings) {
    val calls = store.calls
    var picking by remember { mutableStateOf(false) }
    val remote by calls.remote.collectAsState()
    val local by calls.local.collectAsState()
    val now by produceState(System.currentTimeMillis(), c.connectedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val status = when (c.phase) {
        CallManager.Phase.CALLING -> s.calling
        CallManager.Phase.CONNECTING -> s.connecting
        CallManager.Phase.RECONNECTING -> s.reconnecting
        CallManager.Phase.ENDED -> endText(c.endReason, s)
        else -> {
            val secs = ((now - c.connectedAt) / 1000L).coerceAtLeast(0L)
            String.format("%02d:%02d", secs / 60, secs % 60)
        }
    }
    val frame = remote
    Box(Modifier.fillMaxSize()) {
        if (c.remoteVideo && frame != null) {
            VideoView(frame.image, frame.rotation, mirror = false, Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopCenter).padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (c.remoteSharing) s.screenRemote(c.name) else status, color = Color(0xE6FFFFFF), fontSize = 13.sp)
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(c.name, peerColor(store, c.conv), 150.dp, peerImage(store, c.conv), ring = Dark)
                Spacer(Modifier.height(22.dp))
                Text(
                    c.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(status, color = Color(0xE6FFFFFF), fontSize = 15.sp)
            }
        }
        if (c.sharing && c.phase != CallManager.Phase.ENDED) {
            Row(
                Modifier.align(Alignment.TopStart).padding(20.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC2FBF71)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SinusIcon("ic_screen_share", Color.White, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.sharingOn, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        val preview = local
        if (c.cameraOn && !c.sharing && preview != null && c.phase != CallManager.Phase.ENDED) {
            VideoView(
                preview, 0, mirror = true,
                Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp).size(220.dp, 140.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color.Black)
            )
        }
        if (c.phase != CallManager.Phase.ENDED) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
                    .clip(RoundedCornerShape(40.dp)).background(Color(0x33FFFFFF))
                    .padding(horizontal = 26.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleAction(if (c.micOn) "ic_mic" else "ic_mic_off", s.mic, !c.micOn) { calls.toggleMic() }
                ToggleAction(if (c.cameraOn) "ic_call_video_on" else "ic_call_video_off", s.camera, c.video && !c.cameraOn && !c.sharing) { calls.toggleCamera() }
                ToggleAction("ic_screen_share", s.screen, c.sharing) {
                    if (c.sharing) calls.stopShare() else picking = true
                }
                RoundAction("ic_call_end", s.end, Red, 58.dp) { calls.hangup() }
            }
        }
        if (picking && c.phase == CallManager.Phase.CONNECTED) SharePicker(store, s) { picking = false }
    }
}

@Composable
private fun SharePicker(store: ChatStore, s: CallStrings, onDone: () -> Unit) {
    val targets by produceState<List<ShareTarget>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.calls.shareTargets() }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A2029))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp)
        ) {
            Text(s.pickTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            val list = targets
            if (list == null) {
                Text(s.connecting, color = Color(0xCCFFFFFF), fontSize = 14.sp)
            } else {
                val screens = list.filter { !it.window }
                val windows = list.filter { it.window }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    if (screens.isNotEmpty()) item { SectionLabel(s.screens) }
                    items(screens.size) { i ->
                        ShareRow("ic_screen_share", s.screenName(i + 1)) {
                            store.calls.startShare(screens[i])
                            onDone()
                        }
                    }
                    if (windows.isNotEmpty()) item { SectionLabel(s.windows) }
                    items(windows) { w ->
                        ShareRow("ic_document", w.title) {
                            store.calls.startShare(w)
                            onDone()
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                s.cancel, color = Color(0xFF8FB8FF), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).clickable(onClick = onDone)
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0x99FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun ShareRow(icon: String, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon(icon, Color.White, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VideoView(image: ImageBitmap, rotation: Int, mirror: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val turned = rotation % 180 != 0
        val iw = if (turned) image.height else image.width
        val ih = if (turned) image.width else image.height
        val scale = min(size.width / iw, size.height / ih)
        val dw = image.width * scale
        val dh = image.height * scale
        val cx = size.width / 2f
        val cy = size.height / 2f
        rotate(rotation.toFloat(), Offset(cx, cy)) {
            scale(if (mirror) -1f else 1f, 1f, Offset(cx, cy)) {
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset((cx - dw / 2f).toInt(), (cy - dh / 2f).toInt()),
                    dstSize = IntSize(dw.toInt(), dh.toInt())
                )
            }
        }
    }
}

@Composable
private fun RoundAction(icon: String, label: String, color: Color, size: Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon(icon, Color.White, Modifier.size(size * 0.45f), label)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xCCFFFFFF), fontSize = 12.sp)
    }
}

@Composable
private fun ToggleAction(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (active) Color(0xF2FFFFFF) else Color(0x29FFFFFF))
                .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon(icon, if (active) Color(0xFF101820) else Color.White, Modifier.size(24.dp), label)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xCCFFFFFF), fontSize = 12.sp)
    }
}
