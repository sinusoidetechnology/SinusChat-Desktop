@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.MediaStore
import pl.sinusoide.sinuschat.desktop.chat.VideoPlayback
import pl.sinusoide.sinuschat.desktop.i18n.FileStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalMediaStrings
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.Reveal
import pl.sinusoide.sinuschat.desktop.ui.anim.SineRing
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import java.io.File
import kotlin.math.min

private const val CHROME_HIDE_MS = 3_000L
private const val SEEK_STEP_MS = 5_000L

@Composable
fun VideoViewer(store: ChatStore, m: ChatMessage, onClose: () -> Unit) {
    val ms = LocalMediaStrings.current
    val fs = FileStrings.of(LocalChatStrings.current)
    val progress by store.media.progress.collectAsState()
    val thumb = thumbOf(store, m)
    var file by remember(m.id) { mutableStateOf<File?>(null) }
    var error by remember(m.id) { mutableStateOf<String?>(null) }

    DisposableEffect(m.id) {
        store.voice.stop()
        var alive = true
        MediaActions.fetchLocal(store, m) { f, e ->
            if (!alive) return@fetchLocal
            when {
                f != null -> file = f
                e != null && e != MediaStore.ERROR_CANCELLED -> error = MediaActions.errorText(e, ms.gone, ms.failed)
            }
        }
        onDispose {
            alive = false
            if (file == null) store.media.cancelFor(m.id)
        }
    }

    val playback = remember(file) { file?.let { VideoPlayback(it) } }
    DisposableEffect(playback) {
        val shown = file
        playback?.onClosed = { if (shown != null && MediaActions.isKept(store, m)) shown.delete() }
        playback?.start(play = true)
        onDispose { playback?.close() }
    }
    VideoStage(
        playback = playback,
        poster = thumb,
        loading = if (file == null && error == null) (progress[m.id] ?: 0f) else null,
        loadError = error,
        onSave = { MediaActions.requestSave(store, m, fs) },
        onOpenElsewhere = { MediaActions.openExternally(store, m) },
        onClose = onClose
    )
}

@Composable
internal fun ViewOnceVideo(data: ByteArray, onClose: () -> Unit) {
    val playback = remember(data) { VideoPlayback(VideoPlayback.Source.Memory(data)) }
    DisposableEffect(playback) {
        playback.onClosed = { java.util.Arrays.fill(data, 0) }
        playback.start(play = true)
        onDispose { playback.close() }
    }
    VideoStage(playback, poster = null, loading = null, loadError = null, onSave = null, onOpenElsewhere = null, onClose = onClose)
}

@Composable
internal fun VideoStage(
    playback: VideoPlayback?,
    poster: ImageBitmap?,
    loading: Float?,
    loadError: String?,
    onSave: (() -> Unit)?,
    onOpenElsewhere: (() -> Unit)?,
    onClose: () -> Unit
) {
    val ms = LocalMediaStrings.current
    val fs = FileStrings.of(LocalChatStrings.current)
    val focus = remember { FocusRequester() }
    val idle = remember { MutableStateFlow(VideoPlayback.State()) }
    val state by (playback?.state ?: idle).collectAsState()
    val frames = remember(playback) { VideoFrames() }
    DisposableEffect(frames) { onDispose { frames.close() } }
    var frame by remember(playback) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(playback) {
        playback?.picture?.collect { pic ->
            if (pic != null) frames.fill(pic)?.let { frame = it.asComposeImageBitmap() }
        }
    }

    var scrubbing by remember(playback) { mutableStateOf(false) }
    var scrubAt by remember(playback) { mutableFloatStateOf(0f) }
    var wasPlaying by remember(playback) { mutableStateOf(false) }
    var activity by remember { mutableLongStateOf(System.nanoTime()) }
    var chrome by remember { mutableStateOf(true) }
    fun bump() {
        activity = System.nanoTime()
    }
    LaunchedEffect(activity, state.playing, scrubbing) {
        chrome = true
        if (state.playing && !scrubbing) {
            delay(CHROME_HIDE_MS)
            chrome = false
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val failure = loadError ?: state.failure?.let {
        when (it) {
            VideoPlayback.Failure.LIBRARY -> fs.videoNoLibrary
            VideoPlayback.Failure.NOT_VIDEO -> fs.videoNotVideo
            VideoPlayback.Failure.FORMAT -> fs.videoUnsupported
            VideoPlayback.Failure.OTHER -> fs.videoFailed
        }
    }
    val details = if (loadError == null) state.error else null
    var copied by remember(details) { mutableStateOf(false) }
    LaunchedEffect(details) { details?.let { MediaActions.logVideoError(it) } }
    var smoothMs by remember(playback) { mutableLongStateOf(0L) }
    LaunchedEffect(playback, state.playing) {
        val p = playback ?: return@LaunchedEffect
        if (!state.playing) return@LaunchedEffect
        smoothMs = p.positionNow()
        while (true) {
            withFrameNanos {
                val now = p.positionNow()
                smoothMs = if (now < smoothMs && smoothMs - now < 250) smoothMs else now
            }
        }
    }
    val duration = state.durationMs
    val shownMs = when {
        scrubbing -> (scrubAt * duration).toLong()
        state.playing -> smoothMs
        else -> state.positionMs
    }
    val fraction = if (duration > 0) (shownMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun toggle() {
        playback?.toggle()
        bump()
    }

    fun seekBy(delta: Long) {
        val p = playback ?: return
        p.seek((state.positionMs + delta).coerceIn(0L, duration))
        bump()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Escape -> onClose()
                    Key.Spacebar, Key.K -> toggle()
                    Key.DirectionRight, Key.L -> seekBy(SEEK_STEP_MS)
                    Key.DirectionLeft, Key.J -> seekBy(-SEEK_STEP_MS)
                    else -> return@onKeyEvent false
                }
                true
            }
            .onPointerEvent(PointerEventType.Move) { bump() }
            .clickable(remember { MutableInteractionSource() }, null) { toggle() },
        contentAlignment = Alignment.Center
    ) {
        val picture = frame
        if (picture != null) {
            VideoPicture(picture, state.rotation)
        } else if (poster != null) {
            Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        when {
            failure != null -> Column(
                Modifier.padding(horizontal = 32.dp).widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(failure, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                if (details != null) {
                    Text(
                        details, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center,
                        lineHeight = 15.sp, maxLines = 5, modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        if (copied) fs.detailsCopied else fs.copyDetails, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF))
                            .clickable {
                                runCatching {
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(java.awt.datatransfer.StringSelection(details), null)
                                }
                                copied = true
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
                if (loadError == null && onOpenElsewhere != null) {
                    Text(
                        fs.openElsewhere, color = SinusColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenElsewhere() }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            loading != null -> SineRing(loading, Modifier.size(64.dp), color = Color.White, disc = Color.Transparent)
            !state.ready || picture == null -> SineRing(null, Modifier.size(64.dp), color = Color.White, disc = Color.Transparent)
        }

        Reveal(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(450))
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xA6000000), Color.Transparent)))
                    .clickable(remember { MutableInteractionSource() }, null) { bump() }
                    .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconAction("ic_back", fs.back, Color.White, Modifier.size(46.dp), onClick = onClose)
                Spacer(Modifier.weight(1f))
                if (onSave != null) IconAction("ic_download", ms.save, Color.White, Modifier.size(46.dp), onClick = onSave)
            }
        }

        Reveal(
            visible = chrome && failure == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(450))
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA6000000))))
                    .clickable(remember { MutableInteractionSource() }, null) { bump() }
                    .padding(start = 12.dp, end = 14.dp, top = 40.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                        .clickable { toggle() }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    SinusIcon(
                        if (state.playing) "ic_pause" else "ic_play_voice", Color.White, Modifier.size(24.dp),
                        if (state.playing) fs.pause else fs.play
                    )
                }
                Spacer(Modifier.width(12.dp))
                TimeText(MediaActions.formatDuration(shownMs))
                Box(
                    Modifier.weight(1f).padding(horizontal = 10.dp).height(40.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(playback, duration) {
                            val p = playback ?: return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                wasPlaying = p.state.value.playing
                                if (wasPlaying) p.pause()
                                scrubbing = true
                                scrubAt = (down.position.x / width).coerceIn(0f, 1f)
                                down.consume()
                                drag(down.id) { change ->
                                    scrubAt = (change.position.x / width).coerceIn(0f, 1f)
                                    change.consume()
                                    bump()
                                }
                                p.seek((scrubAt * duration).toLong())
                                if (wasPlaying) p.play()
                                scrubbing = false
                                bump()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    VoiceWave("video", fraction, state.playing, SinusColors.primary, Color(0x73FFFFFF), Modifier.fillMaxSize())
                }
                TimeText("-" + MediaActions.formatDuration((duration - shownMs).coerceAtLeast(0L)))
            }
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun VideoPicture(picture: ImageBitmap, rotation: Int) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val turned = rotation % 180 != 0
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val shownW = (if (turned) picture.height else picture.width).toFloat().coerceAtLeast(1f)
        val shownH = (if (turned) picture.width else picture.height).toFloat().coerceAtLeast(1f)
        val scale = min(boxW / shownW, boxH / shownH)
        val w = with(density) { (picture.width * scale).toDp() }
        val h = with(density) { (picture.height * scale).toDp() }
        Image(
            picture, null,
            Modifier.requiredSize(w, h).graphicsLayer { rotationZ = rotation.toFloat() },
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low
        )
    }
}
