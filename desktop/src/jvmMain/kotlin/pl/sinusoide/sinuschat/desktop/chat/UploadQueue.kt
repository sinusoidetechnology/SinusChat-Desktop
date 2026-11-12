package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.link.LinkFrame
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.concurrent.thread

class UploadQueue(private val link: PhoneLink) {

    data class Staged(
        val id: String, val file: File, val name: String, val size: Long, val mime: String, val temp: Boolean,
        val viewOnce: Boolean = false, val original: Boolean = false
    ) {
        val isImage: Boolean get() = mime.startsWith("image/")
        val isVideo: Boolean get() = mime.startsWith("video/")
        val isMedia: Boolean get() = isImage || isVideo
    }

    data class Active(val batch: String, val conv: String, val count: Int, val sent: Long, val total: Long)

    sealed interface Outcome {
        data class Done(val batch: String, val ids: List<String> = emptyList()) : Outcome
        data class Failed(val batch: String, val error: String) : Outcome
    }

    private val _staged = MutableStateFlow<Map<String, List<Staged>>>(emptyMap())
    val staged: StateFlow<Map<String, List<Staged>>> = _staged

    private val _active = MutableStateFlow<List<Active>>(emptyList())
    val active: StateFlow<List<Active>> = _active

    private val _itemProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val itemProgress: StateFlow<Map<String, Float>> = _itemProgress

    @Volatile var onOutcome: ((Outcome) -> Unit)? = null
    @Volatile var onDelivered: ((conv: String, sent: List<Pair<String, Staged>>) -> Unit)? = null

    private val results = ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<LinkFrame.UploadResult>>()

    fun stage(conv: String, files: List<File>): List<File> {
        val tooBig = mutableListOf<File>()
        val add = files.filter { it.isFile }.mapNotNull { f ->
            if (f.length() > LinkFrame.MAX_UPLOAD_BYTES || f.length() == 0L) {
                tooBig += f
                null
            } else Staged(UUID.randomUUID().toString(), f, f.name, f.length(), mimeOf(f.name), temp = false)
        }
        if (add.isNotEmpty()) _staged.update { m -> m + (conv to (m[conv].orEmpty() + add).take(30)) }
        return tooBig
    }

    fun stageImage(conv: String, image: BufferedImage): Boolean {
        val dir = File(System.getProperty("java.io.tmpdir"), "sinuschat-paste").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "IMG_$stamp-${UUID.randomUUID().toString().take(6)}.png")
        val ok = runCatching { ImageIO.write(image, "png", file) }.getOrDefault(false)
        if (!ok || file.length() == 0L) return false
        val s = Staged(UUID.randomUUID().toString(), file, "IMG_$stamp.png", file.length(), "image/png", temp = true)
        _staged.update { m -> m + (conv to (m[conv].orEmpty() + s).take(30)) }
        return true
    }

    fun unstage(conv: String, id: String) {
        val list = _staged.value[conv].orEmpty()
        list.firstOrNull { it.id == id }?.let { if (it.temp) it.file.delete() }
        _staged.update { m -> m + (conv to m[conv].orEmpty().filterNot { it.id == id }) }
    }

    fun hasStaged(conv: String) = _staged.value[conv].orEmpty().isNotEmpty()

    fun allViewOnce(conv: String) = _staged.value[conv].orEmpty().let { it.isNotEmpty() && it.all { s -> s.viewOnce } }

    fun toggleViewOnce(conv: String, id: String): Boolean = toggle(conv, id) { it.copy(viewOnce = !it.viewOnce) }?.viewOnce == true

    fun toggleOriginal(conv: String, id: String): Boolean = toggle(conv, id) { it.copy(original = !it.original) }?.original == true

    private fun toggle(conv: String, id: String, change: (Staged) -> Staged): Staged? {
        var result: Staged? = null
        _staged.update { m ->
            m + (conv to m[conv].orEmpty().map { if (it.id == id) change(it).also { s -> result = s } else it })
        }
        return result
    }

    fun send(conv: String, caption: String, replyTo: String?): Pair<String, List<Staged>>? {
        val items = _staged.value[conv].orEmpty()
        if (items.isEmpty()) return null
        _staged.update { it - conv }
        val batch = UUID.randomUUID().toString()
        val total = items.sumOf { it.size }
        _active.update { it + Active(batch, conv, items.size, 0, total) }
        _itemProgress.update { m -> m + items.associate { it.id to 0f } }
        thread(isDaemon = true, name = "upload-$batch") { run(batch, conv, items, caption, replyTo, total) }
        return batch to items
    }

    private fun progress(batch: String, sent: Long, item: Staged, itemSent: Long) {
        _active.update { list -> list.map { if (it.batch == batch) it.copy(sent = sent) else it } }
        _itemProgress.update { it + (item.id to (itemSent.toFloat() / item.size.coerceAtLeast(1)).coerceIn(0f, 1f)) }
    }

    private fun run(batch: String, conv: String, items: List<Staged>, caption: String, replyTo: String?, total: Long) {
        val future = java.util.concurrent.CompletableFuture<LinkFrame.UploadResult>()
        results[batch] = future
        var sent = 0L
        var error: String? = null
        var ids = emptyList<String>()
        try {
            val reqs = mutableListOf<String>()
            for (item in items) {
                val req = UUID.randomUUID().toString()
                reqs += req
                if (!link.send(LinkFrame.UploadStart(req, batch, item.name, item.mime, item.size, item.viewOnce, item.original))) throw Offline()
                val buf = ByteArray(LinkFrame.CHUNK_BYTES)
                var seq = 0
                var itemSent = 0L
                item.file.inputStream().use { input ->
                    while (true) {
                        var n = 0
                        while (n < buf.size) {
                            val r = input.read(buf, n, buf.size - n)
                            if (r < 0) break
                            n += r
                        }
                        if (n <= 0) break
                        val data = Base64.getEncoder().encodeToString(if (n == buf.size) buf else buf.copyOf(n))
                        if (!link.send(LinkFrame.UploadChunk(req, seq++, data))) throw Offline()
                        sent += n
                        itemSent += n
                        progress(batch, sent, item, itemSent)
                        if (n < buf.size) break
                    }
                }
                if (!link.send(LinkFrame.UploadEnd(req))) throw Offline()
            }
            if (!link.send(LinkFrame.UploadCommit(batch, conv, reqs, caption.trim(), replyTo))) throw Offline()
            val result = future.get(5, TimeUnit.MINUTES)
            if (!result.ok) error = result.error ?: LinkFrame.ERROR_FAILED
            else {
                ids = result.ids
                if (ids.size == items.size) runCatching { onDelivered?.invoke(conv, ids.zip(items)) }
            }
        } catch (_: Offline) {
            error = ERROR_OFFLINE
        } catch (_: Exception) {
            link.send(LinkFrame.UploadCancel(batch))
            error = LinkFrame.ERROR_FAILED
        } finally {
            results.remove(batch)
            items.filter { it.temp }.forEach { it.file.delete() }
            _active.update { list -> list.filterNot { it.batch == batch } }
            _itemProgress.update { m -> m - items.map { it.id }.toSet() }
        }
        onOutcome?.invoke(if (error == null) Outcome.Done(batch, ids) else Outcome.Failed(batch, error))
    }

    fun onFrame(frame: LinkFrame): Boolean {
        if (frame !is LinkFrame.UploadResult) return false
        results[frame.batch]?.complete(frame)
        return true
    }

    fun onLinkDown() {
        results.values.forEach { it.complete(LinkFrame.UploadResult("", false, ERROR_OFFLINE)) }
    }

    private class Offline : Exception()

    companion object {
        const val ERROR_OFFLINE = "offline"

        fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic" -> "image/heic"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "txt" -> "text/plain"
                "mp3" -> "audio/mpeg"
                "ogg" -> "audio/ogg"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                else -> java.net.URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
            }
        }
    }
}
