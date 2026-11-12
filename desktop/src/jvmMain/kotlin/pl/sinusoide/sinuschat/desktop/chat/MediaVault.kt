package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import pl.sinusoide.sinuschat.desktop.SecureStore
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaVault(
    private val dir: File = File(SecureStore.dir, "media"),
    private val maxBytes: Long = 20L * 1024 * 1024 * 1024,
    private val keySource: () -> ByteArray = ::storedKey,
    private val forgetKey: () -> Unit = { SecureStore.delete(KEY_FILE) },
    private val indexRead: () -> ByteArray? = { SecureStore.read(INDEX_FILE) },
    private val indexWrite: (ByteArray) -> Unit = { SecureStore.write(INDEX_FILE, it) }
) {

    data class Meta(val conv: String, val kind: String)

    class Entry(val conv: String, val kind: String, val bytes: Long)

    private var loadedIndex: HashMap<String, Meta>? = null

    private fun index(): HashMap<String, Meta> = loadedIndex ?: HashMap<String, Meta>().also { map ->
        runCatching {
            indexRead()?.toString(Charsets.UTF_8)?.lineSequence()?.forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 3) map[parts[0]] = Meta(parts[2], parts[1])
            }
        }
        loadedIndex = map
    }

    private fun saveIndex() {
        val map = loadedIndex ?: return
        val text = map.entries.joinToString("\n") { (file, meta) -> "$file\t${clean(meta.kind)}\t${clean(meta.conv)}" }
        runCatching { indexWrite(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    private fun forget(file: File) {
        file.delete()
        if (index().remove(file.name) != null) saveIndex()
    }

    @Synchronized
    fun entries(): List<Entry> {
        val map = index()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") }.orEmpty().map { f ->
            val meta = map[f.name]
            Entry(meta?.conv.orEmpty(), meta?.kind ?: "file", f.length())
        }
    }

    private val random = SecureRandom()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "media-vault").apply { isDaemon = true } }
    @Volatile private var keySpec: SecretKeySpec? = null

    private val _usage = MutableStateFlow(-1L)
    val usage: StateFlow<Long> = _usage

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    private fun changed() {
        _version.update { it + 1 }
    }

    private val key: SecretKeySpec
        @Synchronized get() = keySpec ?: SecretKeySpec(keySource(), "AES").also { keySpec = it }

    private fun fileFor(name: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
        return File(dir, hash.joinToString("") { "%02x".format(it) }.take(40) + ".bin")
    }

    fun has(name: String): Boolean = fileFor(name).isFile

    fun size(name: String): Long = fileFor(name).let { if (it.isFile) it.length() else -1L }

    fun put(name: String, data: ByteArray, meta: Meta? = null): Boolean = write(name, meta) { out -> out.write(data) }

    fun putFile(name: String, plain: File, meta: Meta? = null): Boolean =
        write(name, meta) { out -> plain.inputStream().use { it.copyTo(out, CHUNK) } }

    fun read(name: String): ByteArray? {
        val f = fileFor(name)
        if (!f.isFile) return null
        return runCatching {
            val out = java.io.ByteArrayOutputStream(f.length().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
            f.inputStream().buffered().use { decrypt(name, it, out) }
            f.setLastModified(System.currentTimeMillis())
            out.toByteArray()
        }.getOrElse {
            forget(f)
            changed()
            null
        }
    }

    fun export(name: String, dest: File): Boolean {
        val f = fileFor(name)
        if (!f.isFile) return false
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        return runCatching {
            f.inputStream().buffered().use { input -> part.outputStream().buffered().use { decrypt(name, input, it) } }
            dest.delete()
            check(part.renameTo(dest))
            f.setLastModified(System.currentTimeMillis())
            true
        }.getOrElse {
            part.delete()
            val corrupt = it !is java.io.IOException || it is EOFException
            if (corrupt) {
                forget(f)
                changed()
            }
            false
        }
    }

    fun putAsync(name: String, data: ByteArray, meta: Meta? = null, then: (Boolean) -> Unit = {}) {
        worker.execute { then(put(name, data, meta)) }
    }

    fun putFileAsync(name: String, plain: File, meta: Meta? = null, then: (Boolean) -> Unit = {}) {
        worker.execute { then(putFile(name, plain, meta)) }
    }

    fun exportAsync(name: String, dest: File, then: (Boolean) -> Unit) {
        worker.execute { then(export(name, dest)) }
    }

    fun remove(name: String) {
        forget(fileFor(name))
        changed()
        refreshUsage()
    }

    fun removeConversation(conv: String) {
        if (conv.isEmpty()) return
        synchronized(this) {
            val map = index()
            val gone = map.filterValues { it.conv == conv }.keys.toList()
            if (gone.isEmpty()) return
            gone.forEach { File(dir, it).delete(); map.remove(it) }
            saveIndex()
        }
        changed()
        refreshUsage()
    }

    @Synchronized
    fun wipe() {
        dir.listFiles()?.forEach { it.delete() }
        synchronized(this) { keySpec = null }
        runCatching { forgetKey() }
        loadedIndex = HashMap()
        saveIndex()
        _usage.value = 0L
        changed()
    }

    fun refreshUsage() {
        _usage.value = dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") }?.sumOf { it.length() } ?: 0L
    }

    @Synchronized
    private fun write(name: String, meta: Meta?, body: (OutputStream) -> Unit): Boolean {
        val target = fileFor(name)
        val tmp = File(dir, target.name + ".tmp")
        return runCatching {
            dir.mkdirs()
            tmp.outputStream().buffered().use { raw -> EncryptingStream(name, raw).use(body) }
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target))
            }
            if (meta != null) {
                index()[target.name] = meta
                saveIndex()
            }
            trim()
            changed()
            true
        }.getOrElse {
            tmp.delete()
            false
        }
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") } ?: return
        var total = files.sumOf { it.length() }
        if (total > maxBytes) {
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= maxBytes * 9 / 10) break
                total -= f.length()
                f.delete()
                index().remove(f.name)
            }
            saveIndex()
        }
        _usage.value = total
    }

    private fun nonce(base: ByteArray, index: Long): ByteArray {
        val n = base.copyOf()
        for (i in 0 until 8) n[4 + i] = (n[4 + i].toInt() xor (index ushr (56 - 8 * i)).toInt()).toByte()
        return n
    }

    private fun aad(name: String, index: Long, last: Boolean): ByteArray {
        val id = name.toByteArray(Charsets.UTF_8)
        val out = ByteArray(id.size + 9)
        id.copyInto(out)
        for (i in 0 until 8) out[id.size + i] = (index ushr (56 - 8 * i)).toByte()
        out[id.size + 8] = if (last) 1 else 0
        return out
    }

    private inner class EncryptingStream(private val name: String, raw: OutputStream) : OutputStream() {
        private val out = DataOutputStream(raw)
        private val base = ByteArray(12).also { random.nextBytes(it) }
        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        private var index = 0L
        private var closed = false

        init {
            out.write(MAGIC)
            out.write(base)
        }

        override fun write(b: Int) {
            if (filled == CHUNK) flushChunk(false)
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var left = len
            while (left > 0) {
                if (filled == CHUNK) flushChunk(false)
                val n = minOf(left, CHUNK - filled)
                System.arraycopy(b, o, buffer, filled, n)
                filled += n
                o += n
                left -= n
            }
        }

        private fun flushChunk(last: Boolean) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(base, index)))
            c.updateAAD(aad(name, index, last))
            val sealed = c.doFinal(buffer, 0, filled)
            out.writeInt(filled)
            out.write(sealed)
            index++
            filled = 0
        }

        override fun close() {
            if (closed) return
            closed = true
            flushChunk(true)
            out.flush()
        }
    }

    private fun decrypt(name: String, raw: InputStream, out: OutputStream) {
        val input = DataInputStream(raw)
        val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
        check(magic.contentEquals(MAGIC)) { "bad vault file" }
        val base = ByteArray(12).also { input.readFully(it) }
        var index = 0L
        var length = input.readInt()
        while (true) {
            check(length in 0..CHUNK) { "bad chunk" }
            val sealed = ByteArray(length + 16).also { input.readFully(it) }
            val next = runCatching { input.readInt() }.getOrNull()
            val last = next == null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(base, index)))
            c.updateAAD(aad(name, index, last))
            out.write(c.doFinal(sealed))
            if (last) return
            index++
            length = next
        }
    }

    companion object {
        private const val CHUNK = 1024 * 1024
        private const val KEY_FILE = "media.key"
        private const val INDEX_FILE = "media.index"
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())

        private fun storedKey(): ByteArray =
            SecureStore.read(KEY_FILE)?.takeIf { it.size == 32 }
                ?: ByteArray(32).also { SecureRandom().nextBytes(it); SecureStore.write(KEY_FILE, it) }

        fun fileKey(msg: String) = "f:$msg"
        fun voiceKey(msg: String) = "v:$msg"
    }
}
