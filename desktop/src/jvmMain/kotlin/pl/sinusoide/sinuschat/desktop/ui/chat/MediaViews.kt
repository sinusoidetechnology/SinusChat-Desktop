package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.Downloads
import pl.sinusoide.sinuschat.desktop.chat.MediaStore
import pl.sinusoide.sinuschat.desktop.chat.MediaVault
import pl.sinusoide.sinuschat.desktop.chat.UploadQueue
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalMediaStrings
import pl.sinusoide.sinuschat.desktop.i18n.FileStrings
import pl.sinusoide.sinuschat.desktop.security.FileSafety
import pl.sinusoide.sinuschat.desktop.security.SafeEngine
import pl.sinusoide.sinuschat.desktop.ui.IconAction
import pl.sinusoide.sinuschat.desktop.ui.anim.SineRing
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.LinkFrame
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

private val disc = Color(0x73000000)
private val chip = Color(0x8C000000)
private const val AUTO_FULL_MAX = 15L * 1024 * 1024

object MediaActions {

    val openDir: File get() = File(System.getProperty("java.io.tmpdir"), "sinuschat-open")
    private val sandboxDir: File get() = File(System.getProperty("java.io.tmpdir"), "sinuschat-sandbox")
    val sandboxFolder: File get() = sandboxDir
    val pasteFolder: File get() = File(System.getProperty("java.io.tmpdir"), "sinuschat-paste")

    private val archives = setOf(
        "zip", "zipx", "rar", "7z", "tar", "gz", "gzip", "tgz", "bz2", "bzip2", "tbz", "tbz2", "xz", "txz",
        "lz", "lzma", "tlz", "lz4", "zst", "tzst", "z", "taz", "br", "cab", "arj", "lzh", "lha", "ace",
        "cpio", "sit", "sitx", "wim", "zpaq", "pea", "uue", "xar"
    )
    private val code = setOf(
        "kt", "kts", "java", "c", "h", "cpp", "hpp", "cc", "cs", "py", "rb", "go", "rs", "swift", "m", "dart",
        "lua", "pl", "r", "scala", "groovy", "gradle", "yaml", "yml", "toml", "ini", "md", "vue", "svelte"
    )

    fun cleanTemp() {
        runCatching { openDir.deleteRecursively() }
        runCatching { sandboxDir.deleteRecursively() }
        runCatching { File(System.getProperty("java.io.tmpdir"), "sinuschat-paste").deleteRecursively() }
    }

    fun errorText(error: String, gone: String, failed: String) =
        if (error == LinkFrame.ERROR_GONE) gone else failed

    fun fileIcon(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk", "xapk", "apks", "apkm" -> "ic_apk"
            in archives -> "ic_zip"
            "html", "htm", "xhtml" -> "ic_html"
            "js", "mjs", "cjs", "jsx", "ts", "tsx" -> "ic_js"
            "css", "scss", "sass", "less" -> "ic_css"
            "php" -> "ic_php"
            "json" -> "ic_json"
            "sql" -> "ic_sql"
            "xml", "xsd", "xsl", "plist" -> "ic_xml"
            "sh", "bash", "zsh", "ps1", "bat", "cmd" -> "ic_terminal"
            in code -> "ic_code"
            else -> "ic_document"
        }
    }

    fun chooseOpen(title: String): List<File> {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.isVisible = true
        return dialog.files?.toList().orEmpty()
    }

    fun saveToDownloads(store: ChatStore, m: ChatMessage) {
        val voice = m.kind == ChatMessage.KIND_VOICE
        val given = Downloads.cleanName(m.fileName)
        val name = when {
            given.isEmpty() && voice -> Downloads.fallbackName("glos") + ".wav"
            given.isEmpty() -> Downloads.fallbackName(if (m.kind == ChatMessage.KIND_VIDEO) "film" else if (m.kind == ChatMessage.KIND_PHOTO) "zdjecie" else "plik")
            voice && !given.endsWith(".wav", true) -> "$given.wav"
            else -> given
        }
        val dest = runCatching { Downloads.reserve(name) }.getOrElse {
            store.emit(ChatEvent.MediaFailed(MediaStore.ERROR_WRITE))
            return
        }
        obtain(store, m, dest, if (voice) LinkFrame.MEDIA_VOICE else LinkFrame.MEDIA_FILE, sniff = true) { file, error ->
            when {
                file != null -> store.emit(ChatEvent.Saved(file))
                else -> {
                    if (dest.length() == 0L) dest.delete()
                    if (error != null && error != MediaStore.ERROR_CANCELLED) store.emit(ChatEvent.MediaFailed(error))
                }
            }
        }
    }

    private fun vaultKey(m: ChatMessage, kind: String) =
        if (kind == LinkFrame.MEDIA_VOICE) MediaVault.voiceKey(m.id) else MediaVault.fileKey(m.id)

    fun isKept(store: ChatStore, m: ChatMessage): Boolean = store.media.vault.has(MediaVault.fileKey(m.id))

    fun logVideoError(details: String) {
        runCatching {
            val log = File(pl.sinusoide.sinuschat.desktop.SecureStore.dir, "video-errors.log")
            if (log.length() > 64 * 1024) log.writeText(log.readText().takeLast(32 * 1024))
            log.appendText("${java.time.LocalDateTime.now()}  $details\n")
        }
    }

    private fun obtain(store: ChatStore, m: ChatMessage, target: File, kind: String, sniff: Boolean = false, onDone: (File?, String?) -> Unit) {
        val vault = store.media.vault
        val key = vaultKey(m, kind)
        if (vault.has(key)) {
            vault.exportAsync(key, target) { ok ->
                if (ok) {
                    val out = if (sniff) Downloads.withSniffedExtension(target) else target
                    FileSafety.markFromInternet(out)
                    onDone(out, null)
                } else {
                    download(store, m, target, kind, sniff, onDone)
                }
            }
            return
        }
        download(store, m, target, kind, sniff, onDone)
    }

    private fun download(store: ChatStore, m: ChatMessage, target: File, kind: String, sniff: Boolean, onDone: (File?, String?) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        store.media.fetch(m.conv, m.id, kind, part) { r ->
            when (r) {
                is MediaStore.Result.Saved -> {
                    target.delete()
                    if (part.renameTo(target)) {
                        val out = if (sniff) Downloads.withSniffedExtension(target) else target
                        FileSafety.markFromInternet(out)
                        store.media.vault.putFileAsync(vaultKey(m, kind), out, MediaVault.Meta(m.conv, if (kind == LinkFrame.MEDIA_VOICE) ChatMessage.KIND_VOICE else m.kind))
                        onDone(out, null)
                    } else {
                        part.delete()
                        onDone(null, MediaStore.ERROR_WRITE)
                    }
                }
                is MediaStore.Result.Failed -> {
                    part.delete()
                    onDone(null, r.error)
                }
                else -> {
                    part.delete()
                    onDone(null, LinkFrame.ERROR_FAILED)
                }
            }
        }
    }

    private fun safeName(m: ChatMessage) =
        (m.fileName?.takeIf { it.isNotBlank() } ?: "plik").replace(Regex("[\\\\/:*?\"<>|]"), "_").trimEnd('.', ' ').ifEmpty { "plik" }

    private fun safeFolder(m: ChatMessage) = m.id.take(12).replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun openTarget(m: ChatMessage): File = File(File(openDir, safeFolder(m)), safeName(m))

    fun localCopy(m: ChatMessage): File? = openTarget(m).takeIf { it.isFile && it.length() > 0 }

    fun fetchLocal(store: ChatStore, m: ChatMessage, target: File = openTarget(m), onDone: (File?, String?) -> Unit) {
        if (target.isFile && target.length() > 0) {
            onDone(target, null)
            return
        }
        obtain(store, m, target, LinkFrame.MEDIA_FILE, onDone = onDone)
    }

    fun openExternally(store: ChatStore, m: ChatMessage) {
        SafeEngine.guard(SafeEngine.Risk.OPEN_FILE, m.fileName) {
            fetchLocal(store, m) { file, error ->
                when {
                    file != null -> runCatching { Desktop.getDesktop().open(file) }
                    error != null && error != MediaStore.ERROR_CANCELLED -> store.emit(ChatEvent.MediaFailed(error))
                }
            }
        }
    }

    fun requestSave(store: ChatStore, m: ChatMessage, fs: FileStrings) {
        val name = m.fileName?.takeIf { it.isNotBlank() }
        if (FileSafety.isRisky(name.orEmpty())) {
            val sandbox = if (FileSafety.sandboxAvailable) ({ openInSandbox(store, m, fs.sandboxStarting, fs.sandboxFailed) }) else null
            SafeEngine.warnDangerous(name, sandbox) { saveToDownloads(store, m) }
        } else {
            SafeEngine.guard(SafeEngine.Risk.SAVE_FILE, name) { saveToDownloads(store, m) }
        }
    }

    fun openInSandbox(store: ChatStore, m: ChatMessage, starting: String, failed: String) {
        if (!FileSafety.sandboxAvailable) {
            store.emit(ChatEvent.Info(failed))
            return
        }
        val dir = File(sandboxDir, safeFolder(m))
        fetchLocal(store, m, File(dir, safeName(m))) { file, error ->
            when {
                file != null -> store.emit(ChatEvent.Info(if (FileSafety.launchSandbox(dir)) starting else failed))
                error != null && error != MediaStore.ERROR_CANCELLED -> store.emit(ChatEvent.MediaFailed(error))
            }
        }
    }

    fun playVoice(store: ChatStore, m: ChatMessage) {
        val voice = store.voice
        if (voice.toggle(m.id)) return
        voice.cached(m.id)?.let { wav ->
            if (!voice.play(m.id, wav)) store.emit(ChatEvent.MediaFailed(MediaStore.ERROR_WRITE))
            return
        }
        store.media.vault.read(MediaVault.voiceKey(m.id))?.let { wav ->
            if (voice.play(m.id, wav)) return
        }
        store.media.fetch(m.conv, m.id, LinkFrame.MEDIA_VOICE, null) { r ->
            when (r) {
                is MediaStore.Result.Bytes -> {
                    if (voice.play(m.id, r.data)) store.media.vault.putAsync(MediaVault.voiceKey(m.id), r.data, MediaVault.Meta(m.conv, ChatMessage.KIND_VOICE))
                    else store.emit(ChatEvent.MediaFailed(MediaStore.ERROR_WRITE))
                }
                is MediaStore.Result.Failed -> if (r.error != MediaStore.ERROR_CANCELLED) store.emit(ChatEvent.MediaFailed(r.error))
                else -> Unit
            }
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}

@Composable
fun thumbOf(store: ChatStore, m: ChatMessage): ImageBitmap? {
    val thumbs by store.media.thumbs.collectAsState()
    val generation by store.media.generation.collectAsState()
    val waiting = store.isLocalOnly(m.id) || (m.mine && m.status == ChatMessage.STATUS_UPLOADING)
    LaunchedEffect(m.id, m.hasThumb, generation, waiting) {
        if (waiting) return@LaunchedEffect
        store.media.requestThumb(
            m.conv, m.id,
            fallbackToFull = m.kind == ChatMessage.KIND_PHOTO && m.size in 0L..AUTO_FULL_MAX,
            remote = m.hasThumb
        )
    }
    return thumbs[m.id]
}

@Composable
fun MediaContent(store: ChatStore, m: ChatMessage, onOpenImage: (ChatMessage) -> Unit) {
    when {
        m.viewOnce -> ViewOnceRow(store, m) { onOpenImage(m) }
        m.kind == ChatMessage.KIND_PHOTO -> PhotoThumb(store, m) { onOpenImage(m) }
        m.kind == ChatMessage.KIND_VIDEO -> VideoThumb(store, m) { onOpenImage(m) }
        m.kind == ChatMessage.KIND_VOICE -> VoiceRow(store, m)
        m.kind == ChatMessage.KIND_FILE -> DocCard(store, m)
        m.kind == ChatMessage.KIND_LOCATION -> LocationBox()
    }
}

@Composable
private fun sendingOf(store: ChatStore, m: ChatMessage): Float? {
    if (!m.mine || m.status != ChatMessage.STATUS_UPLOADING) return null
    val phone by store.sending.collectAsState()
    val local by store.uploads.itemProgress.collectAsState()
    return store.sendProgress(m, phone, local)
}

@Composable
private fun SendDisc(size: Dp, progress: Float, color: Color = Color.White, background: Color = disc) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        SineRing(progress.takeIf { it >= 0f }, Modifier.fillMaxSize(), color = color, disc = Color.Transparent)
    }
}

@Composable
private fun ViewOnceRow(store: ChatStore, m: ChatMessage, onOpen: () -> Unit) {
    val s = LocalChatStrings.current
    val sending = sendingOf(store, m)
    val vs = pl.sinusoide.sinuschat.desktop.i18n.ViewOnceStrings.of(s)
    val fg = if (m.mine) SinusColors.onBubbleMine else SinusColors.primary
    val title = when {
        m.viewed -> vs.opened
        m.kind == ChatMessage.KIND_VIDEO -> s.viewOnceVideo
        else -> s.viewOncePhoto
    }
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .clickable {
                when {
                    sending != null -> Unit
                    m.mine -> store.emit(ChatEvent.Info(vs.senderCantOpen))
                    m.viewed -> store.emit(ChatEvent.Info(vs.alreadyOpened))
                    m.kind != ChatMessage.KIND_PHOTO -> store.emit(ChatEvent.Info(s.openOnPhone))
                    else -> onOpen()
                }
            }
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
            .alpha(if (m.viewed) 0.6f else 1f)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sending != null) SendDisc(26.dp, sending, fg, Color.Transparent)
        else SinusIcon(if (m.viewed) "ic_lock_open" else "ic_lock", fg, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = if (m.mine) SinusColors.onBubbleMine else SinusColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LocationBox() {
    val s = LocalChatStrings.current
    Column(
        Modifier.width(240.dp).clip(RoundedCornerShape(6.dp)).background(SinusColors.primary).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SinusIcon("ic_location", Color.White, Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(s.location, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(s.openOnPhone, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

private fun photoHeight(img: ImageBitmap?, width: Dp): Dp {
    if (img == null || img.width <= 0 || img.height <= 0) return width * 0.75f
    return (width.value * img.height / img.width).coerceIn(48f, 300f).dp
}

@Composable
private fun Disc(icon: String, size: Dp, progress: Float?, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
        if (progress != null) {
            SineRing(progress, Modifier.fillMaxSize(), color = Color.White, disc = Color.Transparent)
        } else {
            SinusIcon(icon, Color.White, Modifier.size(size / 2))
        }
    }
}

@Composable
private fun BoxScope.Chip(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.align(Alignment.BottomStart).clip(RoundedCornerShape(10.dp)).background(chip)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun PhotoThumb(store: ChatStore, m: ChatMessage, onOpen: () -> Unit) {
    val ms = LocalMediaStrings.current
    val img = thumbOf(store, m)
    val progress by store.media.progress.collectAsState()
    val gone by store.media.gone.collectAsState()
    val p = progress[m.id]
    val sending = sendingOf(store, m)
    val missing = img == null && m.id in gone && sending == null
    val width = 240.dp
    Box(
        Modifier.size(width, photoHeight(img, width)).clip(RoundedCornerShape(6.dp)).background(SinusColors.mediaPlaceholder)
            .clickable {
                when {
                    sending != null -> Unit
                    img != null -> onOpen()
                    p != null -> store.media.cancelFor(m.id)
                    !missing -> store.media.downloadPhoto(m.conv, m.id)
                }
            }
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        when {
            sending != null -> SendDisc(56.dp, sending)
            p != null -> Disc("ic_download", 56.dp, p)
            missing -> Disc("ic_no_media", 56.dp, null)
            img == null -> Disc("ic_download", 56.dp, null)
        }
        if (missing) Chip(ms.gone, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 11.dp))
    }
}

@Composable
private fun VideoThumb(store: ChatStore, m: ChatMessage, onOpen: () -> Unit) {
    val ms = LocalMediaStrings.current
    val img = thumbOf(store, m)
    val progress by store.media.progress.collectAsState()
    val gone by store.media.gone.collectAsState()
    val p = progress[m.id]
    val sending = sendingOf(store, m)
    val missing = m.id in gone && sending == null
    val kept by store.media.vault.version.collectAsState()
    val local = remember(m.id, p, kept) { MediaActions.isKept(store, m) || MediaActions.localCopy(m) != null }
    val width = 240.dp
    Box(
        Modifier.size(width, photoHeight(img, width)).clip(RoundedCornerShape(6.dp)).background(SinusColors.mediaPlaceholder)
            .clickable {
                when {
                    sending != null -> Unit
                    p != null -> store.media.cancelFor(m.id)
                    !missing -> onOpen()
                }
            }
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        when {
            sending != null -> SendDisc(56.dp, sending)
            p != null -> Disc("ic_download", 56.dp, p)
            missing -> Disc("ic_no_media", 56.dp, null)
            else -> Disc(if (local) "ic_play_voice" else "ic_download", 56.dp, null)
        }
        if (missing) {
            Chip(ms.gone, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 11.dp))
        } else {
            val badge = listOfNotNull(
                MediaActions.formatDuration(m.durationMs).takeIf { m.durationMs > 0 },
                MediaActions.formatSize(m.size).takeIf { it.isNotEmpty() && !local }
            ).joinToString("  ·  ")
            if (badge.isNotEmpty()) Chip(badge, Modifier.padding(10.dp))
        }
    }
}

@Composable
fun AlbumGrid(store: ChatStore, items: List<ChatMessage>, onOpenImage: (ChatMessage) -> Unit) {
    val width = 246.dp
    val gap = 2.dp
    val half = (width - gap) / 2
    val shown = items.take(4)
    val more = items.size - shown.size
    @Composable
    fun tile(index: Int, w: Dp, h: Dp) {
        AlbumTile(store, shown[index], w, h, if (index == shown.lastIndex) more else 0, onOpenImage)
    }
    Column(Modifier.clip(RoundedCornerShape(14.dp)), verticalArrangement = Arrangement.spacedBy(gap)) {
        when (shown.size) {
            1 -> tile(0, width, width * 0.6f)
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                tile(0, half, 170.dp)
                tile(1, half, 170.dp)
            }
            3 -> {
                tile(0, width, 150.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    tile(1, half, half)
                    tile(2, half, half)
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    tile(0, half, half)
                    tile(1, half, half)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    tile(2, half, half)
                    tile(3, half, half)
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(store: ChatStore, m: ChatMessage, w: Dp, h: Dp, more: Int, onOpenImage: (ChatMessage) -> Unit) {
    val img = thumbOf(store, m)
    val progress by store.media.progress.collectAsState()
    val gone by store.media.gone.collectAsState()
    val p = progress[m.id]
    val sending = sendingOf(store, m)
    val missing = img == null && m.id in gone && sending == null
    Box(
        Modifier.size(w, h).background(SinusColors.mediaPlaceholder)
            .clickable {
                when {
                    sending != null -> Unit
                    img != null -> onOpenImage(m)
                    p != null -> store.media.cancelFor(m.id)
                    !missing -> store.media.downloadPhoto(m.conv, m.id)
                }
            }
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        when {
            sending != null -> SendDisc(44.dp, sending)
            p != null -> Disc("ic_download", 44.dp, p)
            missing -> Disc("ic_no_media", 44.dp, null)
            img == null -> Disc("ic_download", 44.dp, null)
        }
        if (more > 0) {
            Box(Modifier.fillMaxSize().background(chip), contentAlignment = Alignment.Center) {
                Text("+$more", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DocCard(store: ChatStore, m: ChatMessage) {
    val ms = LocalMediaStrings.current
    val c = LocalChatStrings.current
    val progress by store.media.progress.collectAsState()
    val gone by store.media.gone.collectAsState()
    val sending = sendingOf(store, m)
    val p = progress[m.id] ?: sending?.coerceAtLeast(0f)
    val missing = m.id in gone && sending == null
    val name = m.fileName?.takeIf { it.isNotBlank() } ?: c.attachment
    val ext = name.substringAfterLast('.', "").uppercase()
    val meta = listOf(
        ext,
        when {
            missing -> ms.gone
            p != null -> "${(p * 100).toInt()}%"
            else -> MediaActions.formatSize(m.size)
        }
    ).filter { it.isNotEmpty() }.joinToString("  ·  ")
    val onDark = m.mine || SinusColors.palette.dark
    val ink = if (m.mine) SinusColors.onBubbleMine else SinusColors.textPrimary
    val iconInk = if (onDark && SinusColors.onBubbleMine == Color.White) Color.White else SinusColors.primary
    Row(
        Modifier.width(260.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(if (onDark) Color(0x30FFFFFF) else Color(0x12000000))
            .clickable {
                when {
                    sending != null -> Unit
                    p == null -> MediaActions.requestSave(store, m, FileStrings.of(c))
                    else -> store.media.cancelFor(m.id)
                }
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (onDark) Color(0x50FFFFFF) else SinusColors.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (p != null) {
                SineRing(if (sending != null && sending < 0f) null else p, Modifier.fillMaxSize(), color = iconInk, disc = Color.Transparent)
            } else {
                SinusIcon(MediaActions.fileIcon(name), iconInk, Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                meta.ifEmpty { ms.save }, color = if (m.mine) ink.copy(alpha = 0.7f) else SinusColors.textSecondary,
                fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (p == null) {
            Spacer(Modifier.width(8.dp))
            SinusIcon(if (missing) "ic_no_media" else "ic_download", iconInk, Modifier.size(28.dp).padding(4.dp), ms.save)
        }
    }
}

@Composable
private fun VoiceRow(store: ChatStore, m: ChatMessage) {
    val state by store.voice.state.collectAsState()
    val progress by store.media.progress.collectAsState()
    val playing = state?.takeIf { it.msg == m.id }
    val loading = progress[m.id] != null
    val total = playing?.durationMs?.takeIf { it > 0 } ?: m.durationMs
    val running = playing?.playing == true
    var smoothMs by remember(m.id) { mutableLongStateOf(0L) }
    LaunchedEffect(m.id, running) {
        if (!running) return@LaunchedEffect
        while (true) withFrameNanos { store.voice.positionNow(m.id)?.let { smoothMs = it } }
    }
    val positionMs = when {
        playing == null -> 0L
        running && smoothMs > 0 -> smoothMs
        else -> playing.positionMs
    }
    val fraction = if (playing != null && total > 0) (positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    val accent = if (m.mine) SinusColors.onBubbleMine else SinusColors.primary
    val unplayed = if (m.mine) SinusColors.onBubbleMine.copy(alpha = 0.4f) else SinusColors.textSecondary.copy(alpha = 0x55 / 255f)
    Row(
        Modifier.width(240.dp).padding(start = 9.dp, end = 11.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable { MediaActions.playVoice(store, m) }.pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> SineRing(null, Modifier.size(30.dp), color = accent, disc = Color.Transparent)
                playing?.playing == true -> SinusIcon("ic_pause", accent, Modifier.size(24.dp))
                else -> SinusIcon("ic_play_voice", accent, Modifier.size(24.dp))
            }
        }
        VoiceWave(m.id, fraction, playing?.playing == true, accent, unplayed, Modifier.weight(1f).padding(horizontal = 8.dp).height(28.dp))
        Text(
            MediaActions.formatDuration(if (playing != null && positionMs > 0) positionMs else total),
            color = if (m.mine) SinusColors.onBubbleMine.copy(alpha = 0.8f) else SinusColors.textSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
internal fun VoiceWave(id: String, progress: Float, animate: Boolean, played: Color, unplayed: Color, modifier: Modifier) {
    val phase by produceState(0f, animate) {
        if (!animate) return@produceState
        val start = System.nanoTime()
        while (true) {
            kotlinx.coroutines.delay(16)
            value = (((System.nanoTime() - start) / 1_000_000L) % 1200L) / 1200f * 2f * PI.toFloat()
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 10f || h <= 10f) return@Canvas
        val cy = h / 2f
        val amplitude = h * 0.32f
        val path = Path()
        path.moveTo(0f, cy)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, cy + amplitude * sin(x / w * 6f * 2f * PI.toFloat() - phase))
            x += 2f
        }
        val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
        if (progress <= 0f) {
            drawPath(path, unplayed, style = stroke)
        } else {
            val split = w * progress.coerceIn(0f, 1f)
            clipRect(right = split) { drawPath(path, played, style = stroke) }
            clipRect(left = split) { drawLine(unplayed, Offset(split, cy), Offset(w, cy), strokeWidth = stroke.width, cap = StrokeCap.Round) }
            drawLine(played, Offset(split, cy - amplitude * 1.15f), Offset(split, cy + amplitude * 1.15f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun ImageViewer(store: ChatStore, m: ChatMessage, onClose: () -> Unit) {
    val ms = LocalMediaStrings.current
    val chat = LocalChatStrings.current
    val progress by store.media.progress.collectAsState()
    val thumb = thumbOf(store, m)
    var error by remember(m.id) { mutableStateOf<String?>(null) }
    val full by produceState<ImageBitmap?>(null, m.id) {
        value = withContext(Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { store.media.cancelFor(m.id) }
                store.media.fetchImage(m.conv, m.id) { r ->
                    val img = (r as? MediaStore.Result.Bytes)?.let { store.media.decode(it.data) }
                    if (r is MediaStore.Result.Failed) error = MediaActions.errorText(r.error, ms.gone, ms.failed)
                    if (cont.isActive) cont.resumeWith(Result.success(img))
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        val shown = full ?: thumb
        if (shown != null) {
            Image(shown, null, Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 64.dp), contentScale = ContentScale.Fit)
        }
        val p = progress[m.id]
        if (full == null && error == null) {
            SineRing(p, Modifier.size(64.dp), color = Color.White, disc = Color.Transparent)
        }
        error?.let { Text(it, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center) }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(m.fileName.orEmpty(), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconAction("ic_download", ms.save, Color.White) { MediaActions.requestSave(store, m, FileStrings.of(chat)) }
            IconAction("ic_close", ms.close, Color.White, onClick = onClose)
        }
        if (m.text.isNotBlank()) {
            Text(
                m.text, color = Color.White, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 720.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun StagedBar(store: ChatStore, conv: String) {
    val staged by store.uploads.staged.collectAsState()
    val items = staged[conv].orEmpty()
    if (items.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { item -> StagedChip(store, conv, item) }
    }
}

internal fun previewOf(file: File, max: Int): ImageBitmap? = runCatching {
    val img = org.jetbrains.skia.Image.makeFromEncoded(file.readBytes())
    val scale = minOf(1f, max.toFloat() / maxOf(img.width, img.height).coerceAtLeast(1))
    val w = (img.width * scale).toInt().coerceAtLeast(1)
    val h = (img.height * scale).toInt().coerceAtLeast(1)
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
    surface.makeImageSnapshot().toComposeImageBitmap()
}.getOrNull()

@Composable
private fun StagedChip(store: ChatStore, conv: String, item: UploadQueue.Staged) {
    val ms = LocalMediaStrings.current
    val preview by produceState<ImageBitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            when {
                item.isVideo -> store.media.localPreview(item.file, video = true)?.second
                item.isImage && item.size < 30L * 1024 * 1024 -> previewOf(item.file, 240)
                else -> null
            }
        }
    }
    val toggleOnce: () -> Unit = {
        when {
            conv.startsWith("g_") -> store.emit(ChatEvent.Info(ms.viewOnceGroups))
            !item.isMedia -> store.emit(ChatEvent.Info(ms.viewOnceMediaOnly))
            else -> store.uploads.toggleViewOnce(conv, item.id)
        }
    }
    Box(Modifier.pointerInput(item.id, conv) { detectTapGestures(onDoubleTap = { toggleOnce() }) }) {
        if (item.isMedia) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(SinusColors.card),
                contentAlignment = Alignment.Center
            ) {
                val p = preview
                if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else SinusIcon(if (item.isVideo) "ic_call_video_on" else "ic_image", SinusColors.textSecondary, Modifier.size(26.dp))
                AnimatedVisibility(item.viewOnce, enter = fadeIn(tween(300)), exit = fadeOut(tween(160))) {
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        SinusIcon("ic_lock", Color.White, Modifier.size(28.dp))
                    }
                }
                if (item.isVideo && !item.viewOnce && p != null) SinusIcon("ic_call_video_on", Color.White, Modifier.size(22.dp))
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(2.dp).size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.original) SinusColors.primary else Color(0x99000000))
                    .alpha(if (item.original) 1f else 0.7f)
                    .clickable {
                        val on = store.uploads.toggleOriginal(conv, item.id)
                        store.emit(ChatEvent.Info(if (on) ms.originalOn else ms.originalOff))
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                SinusIcon("ic_hd", if (item.original) SinusColors.onAccent else Color.White, Modifier.fillMaxSize(), ms.original)
            }
        } else {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SinusColors.surfaceVariant).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(SinusColors.card), contentAlignment = Alignment.Center) {
                    SinusIcon(MediaActions.fileIcon(item.name), SinusColors.textSecondary, Modifier.size(24.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.widthIn(max = 190.dp)) {
                    Text(item.name, color = SinusColors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(MediaActions.formatSize(item.size), color = SinusColors.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape).background(Color(0x99000000))
                .clickable { store.uploads.unstage(conv, item.id) }.pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon("ic_close", Color.White, Modifier.size(14.dp), ms.remove)
        }
    }
}

@Composable
fun DropHint() {
    val ms = LocalMediaStrings.current
    Box(Modifier.fillMaxSize().background(SinusColors.backgroundSolid.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(SinusColors.primary.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                SinusIcon("ic_attach", SinusColors.primary, Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(ms.dropHere, color = SinusColors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
