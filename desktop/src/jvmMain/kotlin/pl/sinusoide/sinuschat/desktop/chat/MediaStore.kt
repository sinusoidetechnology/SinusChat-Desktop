package pl.sinusoide.sinuschat.desktop.chat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.LinkFrame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MediaStore(private val link: PhoneLink, private val blobs: BlobCache, val vault: MediaVault = MediaVault()) {

    sealed interface Result {
        data class Bytes(val data: ByteArray, val name: String, val mime: String) : Result
        data class Saved(val file: File) : Result
        data class Failed(val error: String) : Result
    }

    private class Pending(
        val req: String,
        val msg: String,
        val kind: String,
        val target: File?,
        val onDone: (Result) -> Unit
    ) {
        var out: OutputStream? = null
        var memory: ByteArrayOutputStream? = null
        var name = ""
        var mime = ""
        var expected = 0L
        var received = 0L
        var nextSeq = 0
        @Volatile var touched = System.currentTimeMillis()
    }

    private class Job(val conv: String, val msg: String, val kind: String, val fallback: Boolean = false)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val queue = LinkedBlockingDeque<Job>()
    private val wanted = ConcurrentHashMap.newKeySet<String>()
    private val parked = ConcurrentHashMap<String, Job>()
    private val tried = ConcurrentHashMap.newKeySet<String>()
    private val queueLock = Any()
    private var inFlight = 0
    private var avatarsInFlight = 0
    private val avatarMisses = AtomicInteger()
    private val worker = Executors.newFixedThreadPool(2) { r -> Thread(r, "media-load").apply { isDaemon = true } }
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "media-watchdog").apply { isDaemon = true } }

    private val _thumbs = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val thumbs: StateFlow<Map<String, ImageBitmap>> = _thumbs

    private val _avatars = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val avatars: StateFlow<Map<String, ImageBitmap>> = _avatars

    private val _gone = MutableStateFlow<Set<String>>(emptySet())
    val gone: StateFlow<Set<String>> = _gone

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation

    init {
        watchdog.scheduleWithFixedDelay({ expire() }, 2, 2, TimeUnit.SECONDS)
    }

    private fun thumbKey(msg: String) = "t:$msg"
    private fun fullKey(msg: String) = "f:$msg"
    private fun avatarKey(key: String) = "a:$key"

    fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    fun requestThumb(conv: String, msg: String, fallbackToFull: Boolean = false, remote: Boolean = true) {
        if (_thumbs.value.containsKey(msg)) return
        val key = thumbKey(msg)
        if (key in tried || !wanted.add(key)) return
        worker.execute {
            val cached = blobs.get(key)?.let { decode(it) }
                ?: if (fallbackToFull) vault.read(MediaVault.fileKey(msg))?.let { shrink(it) }?.let { (bytes, img) -> blobs.put(key, bytes); img } else null
            when {
                cached != null -> publishThumb(msg, cached)
                remote -> enqueue(Job(conv, msg, LinkFrame.MEDIA_THUMB, fallbackToFull))
                else -> wanted.remove(key)
            }
        }
    }

    fun downloadPhoto(conv: String, msg: String) {
        if (_thumbs.value.containsKey(msg) || _progress.value.containsKey(msg)) return
        tried.remove(thumbKey(msg))
        _gone.update { it - msg }
        wanted.add(thumbKey(msg))
        worker.execute {
            val local = vault.read(MediaVault.fileKey(msg))?.let { shrink(it) }
            if (local != null) {
                blobs.put(thumbKey(msg), local.first)
                publishThumb(msg, local.second)
            } else {
                enqueue(Job(conv, msg, JOB_FULL))
            }
        }
    }

    fun localPreview(file: File, video: Boolean): Pair<ByteArray, ImageBitmap>? = when {
        video -> VideoPoster.of(file, THUMB_PX)
        file.length() in 1..LOCAL_PREVIEW_MAX -> runCatching { shrink(file.readBytes()) }.getOrNull()
        else -> null
    }

    fun previewLocal(msg: String, file: File, video: Boolean) {
        worker.execute { localPreview(file, video)?.let { publishThumb(msg, it.second) } }
    }

    fun cacheBytes(): Long = blobs.bytes()

    fun clearCache() {
        blobs.wipe()
        tried.clear()
    }

    fun dropPreview(msg: String) {
        _thumbs.update { it - msg }
    }

    fun copyPreview(from: String, to: String) {
        _thumbs.value[from]?.let { publishThumb(to, it) }
    }

    fun adoptSent(msg: String, file: File, preview: Boolean, video: Boolean, keep: Boolean, conv: String = "") {
        if (preview) {
            localPreview(file, video)?.let { (bytes, img) ->
                blobs.put(thumbKey(msg), bytes)
                if (!_thumbs.value.containsKey(msg)) publishThumb(msg, img)
            }
        }
        val kind = when {
            video -> ChatMessage.KIND_VIDEO
            preview -> ChatMessage.KIND_PHOTO
            else -> ChatMessage.KIND_FILE
        }
        if (keep && file.isFile) vault.putFile(MediaVault.fileKey(msg), file, MediaVault.Meta(conv, kind))
    }

    fun requestAvatar(conv: String, key: String) {
        val k = avatarKey(key)
        if (k in tried || !wanted.add(k)) return
        worker.execute {
            if (!_avatars.value.containsKey(key)) blobs.get(k)?.let { decode(it) }?.let { img -> _avatars.update { it + (key to img) } }
            if (avatarMisses.get() >= AVATAR_GIVE_UP) {
                wanted.remove(k)
                return@execute
            }
            enqueue(Job(conv, key, KIND_AVATAR))
        }
    }

    fun invalidateAvatar(key: String) {
        if (key.isEmpty()) return
        val k = avatarKey(key)
        blobs.remove(k)
        tried.remove(k)
        _generation.update { it + 1 }
    }

    private fun publishThumb(msg: String, img: ImageBitmap) {
        wanted.remove(thumbKey(msg))
        _thumbs.update { m ->
            val next = LinkedHashMap(m)
            next.remove(msg)
            next[msg] = img
            while (next.size > MAX_THUMBS) next.remove(next.keys.first())
            next
        }
    }

    private fun enqueue(job: Job) {
        queue.offerLast(job)
        pump()
    }

    private fun pump() {
        while (true) {
            val job = synchronized(queueLock) {
                if (!link.isConnected) {
                    parkQueued()
                    return
                }
                nextJob()?.also { if (it.kind == KIND_AVATAR) avatarsInFlight++ else inFlight++ } ?: return
            }
            val kind = if (job.kind == JOB_FULL) LinkFrame.MEDIA_FILE else job.kind
            fetch(job.conv, job.msg, if (kind == KIND_AVATAR) LinkFrame.MEDIA_AVATAR else kind, null) { r ->
                worker.execute {
                    try {
                        onJobDone(job, r)
                    } finally {
                        synchronized(queueLock) {
                            if (job.kind == KIND_AVATAR) avatarsInFlight = (avatarsInFlight - 1).coerceAtLeast(0)
                            else inFlight = (inFlight - 1).coerceAtLeast(0)
                        }
                    }
                    pump()
                }
            }
        }
    }

    private fun nextJob(): Job? {
        val it = queue.descendingIterator()
        while (it.hasNext()) {
            val j = it.next()
            val avatar = j.kind == KIND_AVATAR
            if (avatar && avatarMisses.get() >= AVATAR_GIVE_UP) {
                it.remove()
                wanted.remove(keyOf(j))
                continue
            }
            if (if (avatar) avatarsInFlight < 1 else inFlight < MAX_PARALLEL) {
                it.remove()
                return j
            }
        }
        return null
    }

    private fun parkQueued() {
        while (true) {
            val job = queue.pollLast() ?: break
            park(job)
        }
    }

    private fun park(job: Job) {
        val key = keyOf(job)
        wanted.remove(key)
        parked[key] = job
    }

    private fun keyOf(job: Job) = if (job.kind == KIND_AVATAR) avatarKey(job.msg) else thumbKey(job.msg)

    private fun onJobDone(job: Job, r: Result) {
        val key = keyOf(job)
        val offline = r is Result.Failed && (r.error == ERROR_OFFLINE || r.error == ERROR_CANCELLED)
        if (r is Result.Failed && r.error == ERROR_OFFLINE) {
            park(job)
            return
        }
        when (job.kind) {
            KIND_AVATAR -> {
                val img = (r as? Result.Bytes)?.data?.takeIf { it.isNotEmpty() }?.let { bytes -> decode(bytes)?.let { bytes to it } }
                if (img != null) {
                    avatarMisses.set(AVATAR_SUPPORTED)
                    blobs.put(key, img.first)
                    _avatars.update { it + (job.msg to img.second) }
                } else if (!offline) {
                    if (r is Result.Failed && r.error == LinkFrame.ERROR_GONE) {
                        avatarMisses.set(AVATAR_SUPPORTED)
                        blobs.remove(key)
                        _avatars.update { it - job.msg }
                    } else if (avatarMisses.get() >= 0) {
                        avatarMisses.incrementAndGet()
                    }
                }
                wanted.remove(key)
                if (!offline) tried.add(key)
                return
            }
            LinkFrame.MEDIA_THUMB -> {
                val img = (r as? Result.Bytes)?.data?.let { bytes -> decode(bytes)?.let { bytes to it } }
                if (img != null) {
                    blobs.put(key, img.first)
                    publishThumb(job.msg, img.second)
                    return
                }
                if (!offline && job.fallback) {
                    queue.offerFirst(Job(job.conv, job.msg, JOB_FULL))
                    return
                }
            }
            JOB_FULL -> {
                val bytes = (r as? Result.Bytes)?.data
                val small = bytes?.let { shrink(it) }
                if (bytes != null && small != null) {
                    vault.put(MediaVault.fileKey(job.msg), bytes, MediaVault.Meta(job.conv, ChatMessage.KIND_PHOTO))
                    blobs.put(key, small.first)
                    publishThumb(job.msg, small.second)
                    return
                }
            }
        }
        wanted.remove(key)
        if (r is Result.Failed && r.error == LinkFrame.ERROR_GONE) _gone.update { it + job.msg }
        if (!offline) tried.add(key)
    }

    private fun shrink(bytes: ByteArray): Pair<ByteArray, ImageBitmap>? = runCatching {
        val img = Image.makeFromEncoded(bytes)
        val scale = minOf(1f, THUMB_PX.toFloat() / maxOf(img.width, img.height).coerceAtLeast(1))
        val w = (img.width * scale).toInt().coerceAtLeast(1)
        val h = (img.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(
            img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.CATMULL_ROM, null, true
        )
        val snap = surface.makeImageSnapshot()
        val data = snap.encodeToData(EncodedImageFormat.WEBP, 85) ?: snap.encodeToData(EncodedImageFormat.PNG)
        (data?.bytes ?: return null) to snap.toComposeImageBitmap()
    }.getOrNull()

    fun fetch(conv: String, msg: String, kind: String, target: File?, onDone: (Result) -> Unit): Boolean {
        val req = UUID.randomUUID().toString()
        val p = Pending(req, msg, kind, target, onDone)
        pending[req] = p
        if (kind == LinkFrame.MEDIA_FILE || kind == LinkFrame.MEDIA_VOICE) _progress.update { it + (msg to 0f) }
        if (!link.send(LinkFrame.MediaRequest(req, conv, msg, kind))) {
            finish(p, Result.Failed(ERROR_OFFLINE))
            return false
        }
        return true
    }

    fun fetchImage(conv: String, msg: String, onDone: (Result) -> Unit): Boolean {
        (vault.read(MediaVault.fileKey(msg)) ?: blobs.get(fullKey(msg)))?.let {
            onDone(Result.Bytes(it, "", ""))
            return true
        }
        return fetch(conv, msg, LinkFrame.MEDIA_FILE, null) { r ->
            if (r is Result.Bytes) {
                worker.execute { vault.put(MediaVault.fileKey(msg), r.data, MediaVault.Meta(conv, ChatMessage.KIND_PHOTO)) }
                if (!_thumbs.value.containsKey(msg)) worker.execute {
                    shrink(r.data)?.let { (bytes, img) ->
                        blobs.put(thumbKey(msg), bytes)
                        publishThumb(msg, img)
                    }
                }
            } else if (r is Result.Failed && r.error == LinkFrame.ERROR_GONE) {
                _gone.update { it + msg }
            }
            onDone(r)
        }
    }

    fun cancelFor(msg: String) {
        pending.values.filter { it.msg == msg && it.kind != LinkFrame.MEDIA_THUMB }.forEach {
            link.send(LinkFrame.MediaCancel(it.req))
            finish(it, Result.Failed(ERROR_CANCELLED))
        }
    }

    fun onLinkUp() {
        tried.clear()
        avatarMisses.set(0)
        val retry = parked.values.toList()
        parked.clear()
        for (job in retry) if (wanted.add(keyOf(job))) queue.offerLast(job)
        _generation.update { it + 1 }
        pump()
    }

    fun onLinkDown() {
        pending.values.toList().forEach { finish(it, Result.Failed(ERROR_OFFLINE)) }
        synchronized(queueLock) { parkQueued() }
    }

    fun wipe() {
        _thumbs.value = emptyMap()
        _avatars.value = emptyMap()
        _gone.value = emptySet()
        wanted.clear()
        tried.clear()
        parked.clear()
        blobs.wipe()
        vault.wipe()
    }

    private fun expire() {
        val now = System.currentTimeMillis()
        for (p in pending.values) {
            val limit = if (p.kind == LinkFrame.MEDIA_THUMB || p.kind == KIND_AVATAR) SMALL_TIMEOUT_MS else FILE_TIMEOUT_MS
            if (now - p.touched < limit) continue
            runCatching {
                link.send(LinkFrame.MediaCancel(p.req))
                finish(p, Result.Failed(ERROR_TIMEOUT))
            }
        }
    }

    private fun finish(p: Pending, result: Result) {
        if (pending.remove(p.req) == null) return
        runCatching { p.out?.close() }
        if (result !is Result.Saved && p.target != null) p.target.delete()
        _progress.update { it - p.msg }
        p.onDone(result)
    }

    fun onFrame(frame: LinkFrame): Boolean {
        when (frame) {
            is LinkFrame.MediaStart -> {
                val p = pending[frame.req] ?: return true
                p.touched = System.currentTimeMillis()
                p.name = frame.name
                p.mime = frame.mime
                p.expected = frame.size
                try {
                    if (p.target != null) {
                        p.target.parentFile?.mkdirs()
                        p.out = p.target.outputStream().buffered()
                    } else {
                        p.memory = ByteArrayOutputStream(frame.size.coerceIn(0, 64L * 1024 * 1024).toInt())
                        p.out = p.memory
                    }
                } catch (e: Exception) {
                    link.send(LinkFrame.MediaCancel(p.req))
                    finish(p, Result.Failed(ERROR_WRITE))
                }
            }
            is LinkFrame.MediaChunk -> {
                val p = pending[frame.req] ?: return true
                p.touched = System.currentTimeMillis()
                try {
                    if (frame.seq != p.nextSeq) throw IllegalStateException("seq")
                    val bytes = Base64.getDecoder().decode(frame.data)
                    p.out?.write(bytes) ?: throw IllegalStateException("no start")
                    p.nextSeq++
                    p.received += bytes.size
                    if (p.expected > 0 && p.kind != LinkFrame.MEDIA_THUMB && p.kind != KIND_AVATAR) {
                        _progress.update { it + (p.msg to (p.received.toFloat() / p.expected).coerceIn(0f, 1f)) }
                    }
                } catch (e: Exception) {
                    link.send(LinkFrame.MediaCancel(p.req))
                    finish(p, Result.Failed(ERROR_WRITE))
                }
            }
            is LinkFrame.MediaEnd -> {
                val p = pending[frame.req] ?: return true
                val ok = p.expected <= 0 || p.received == p.expected
                val result = when {
                    !ok -> Result.Failed(LinkFrame.ERROR_FAILED)
                    p.target != null -> Result.Saved(p.target)
                    else -> Result.Bytes(p.memory?.toByteArray() ?: ByteArray(0), p.name, p.mime)
                }
                finish(p, result)
            }
            is LinkFrame.MediaError -> {
                val p = pending[frame.req] ?: return true
                finish(p, Result.Failed(frame.error))
            }
            else -> return false
        }
        return true
    }

    companion object {
        const val ERROR_OFFLINE = "offline"
        const val ERROR_CANCELLED = "cancelled"
        const val ERROR_WRITE = "write"
        const val ERROR_TIMEOUT = "timeout"
        const val KIND_AVATAR = "avatar"
        private const val JOB_FULL = "full"
        private const val MAX_PARALLEL = 3
        private const val MAX_THUMBS = 400
        private const val THUMB_PX = 720
        private const val LOCAL_PREVIEW_MAX = 40L * 1024 * 1024
        private const val SMALL_TIMEOUT_MS = 20_000L
        private const val FILE_TIMEOUT_MS = 120_000L
        private const val AVATAR_GIVE_UP = 3
        private const val AVATAR_SUPPORTED = -1_000_000
    }
}
