package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.MediaStore
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.ViewOnceStrings
import pl.sinusoide.sinuschat.desktop.security.CaptureShield
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.LinkFrame

@Composable
fun ViewOnceViewer(store: ChatStore, m: ChatMessage, onClose: () -> Unit) {
    val vs = ViewOnceStrings.of(LocalChatStrings.current)
    var image by remember(m.id) { mutableStateOf<ImageBitmap?>(null) }
    var video by remember(m.id) { mutableStateOf<ByteArray?>(null) }
    var error by remember(m.id) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    DisposableEffect(m.id) {
        CaptureShield.acquire()
        var alive = true
        store.media.fetch(m.conv, m.id, LinkFrame.MEDIA_VIEW_ONCE, null) { r ->
            if (!alive) {
                if (r is MediaStore.Result.Bytes) java.util.Arrays.fill(r.data, 0)
                return@fetch
            }
            when (r) {
                is MediaStore.Result.Bytes -> {
                    val img = store.media.decode(r.data)
                    when {
                        img != null -> {
                            java.util.Arrays.fill(r.data, 0)
                            image = img
                        }
                        pl.sinusoide.sinuschat.desktop.chat.looksLikeMedia(r.data.copyOf(minOf(64, r.data.size))) -> video = r.data
                        else -> {
                            java.util.Arrays.fill(r.data, 0)
                            error = vs.unavailable
                        }
                    }
                }
                is MediaStore.Result.Failed ->
                    error = if (r.error == LinkFrame.ERROR_OPENED) vs.alreadyOpened else vs.unavailable
                else -> error = vs.unavailable
            }
        }
        onDispose {
            alive = false
            if (image == null && video == null) store.media.cancelFor(m.id)
            image = null
            CaptureShield.release()
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    video?.let {
        ViewOnceVideo(it, onClose)
        return
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false
            },
        contentAlignment = Alignment.Center
    ) {
        val img = image
        val err = error
        when {
            img != null -> Image(img, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit)
            err != null -> Text(err, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            else -> pl.sinusoide.sinuschat.desktop.ui.anim.SineRing(null, Modifier.size(76.dp), color = Color.White, disc = Color.Transparent)
        }
        if (img != null) {
            Text(
                vs.closeHint, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0x99000000))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(20.dp).size(44.dp).clip(CircleShape)
                .background(Color(0x33FFFFFF)).clickable(onClick = onClose).pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon("ic_close", Color.White, Modifier.size(22.dp))
        }
    }
}
