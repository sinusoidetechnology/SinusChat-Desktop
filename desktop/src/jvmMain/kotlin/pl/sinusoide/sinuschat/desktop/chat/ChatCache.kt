package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pl.sinusoide.sinuschat.desktop.SecureStore
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkCrypto
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChatCache(private val dir: File = File(SecureStore.dir, "cache")) {

    @Serializable
    private data class ChatsFile(val items: List<ChatSummary>, val hidePreviews: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true }
    private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "chat-cache").apply { isDaemon = true } }
    private val pending = ConcurrentHashMap<String, () -> ByteArray>()

    @Volatile private var keySpec: SecretKeySpec? = null

    private val key: SecretKeySpec
        @Synchronized get() = keySpec ?: run {
            val raw = SecureStore.read(KEY_FILE)?.takeIf { it.size == 32 }
                ?: LinkCrypto.randomBytes(32).also { SecureStore.write(KEY_FILE, it) }
            SecretKeySpec(raw, "AES").also { keySpec = it }
        }

    private fun seal(plain: ByteArray): ByteArray {
        val nonce = LinkCrypto.randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return nonce + c.doFinal(plain)
    }

    private fun open(data: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        c.doFinal(data, 12, data.size - 12)
    } catch (_: Exception) {
        null
    }

    private fun fileFor(name: String) = File(dir, name)

    private fun convFile(conv: String) = "m_" + LinkCrypto.fingerprint(conv.toByteArray()) + ".bin"

    private fun read(name: String): String? {
        val f = fileFor(name)
        if (!f.isFile) return null
        return open(f.readBytes())?.toString(Charsets.UTF_8)
    }

    private fun schedule(name: String, body: () -> ByteArray) {
        if (pending.put(name, body) != null) return
        writer.schedule({
            val b = pending.remove(name) ?: return@schedule
            try {
                dir.mkdirs()
                val tmp = fileFor("$name.tmp")
                tmp.writeBytes(seal(b()))
                val target = fileFor(name)
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            } catch (_: Exception) {
            }
        }, 800, TimeUnit.MILLISECONDS)
    }

    fun loadChats(): Pair<List<ChatSummary>, Boolean> {
        val text = read(CHATS_FILE) ?: return emptyList<ChatSummary>() to false
        return runCatching { json.decodeFromString(ChatsFile.serializer(), text) }
            .map { it.items to it.hidePreviews }
            .getOrDefault(emptyList<ChatSummary>() to false)
    }

    fun saveChats(items: List<ChatSummary>, hidePreviews: Boolean) {
        schedule(CHATS_FILE) {
            json.encodeToString(ChatsFile.serializer(), ChatsFile(items, hidePreviews)).toByteArray(Charsets.UTF_8)
        }
    }

    fun loadMessages(conv: String): List<ChatMessage> {
        val text = read(convFile(conv)) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(ChatMessage.serializer()), text) }.getOrDefault(emptyList())
    }

    fun saveMessages(conv: String, items: List<ChatMessage>) {
        val keep = items.takeLast(MAX_CACHED)
        schedule(convFile(conv)) {
            json.encodeToString(ListSerializer(ChatMessage.serializer()), keep).toByteArray(Charsets.UTF_8)
        }
    }

    fun loadHidden(): Set<String> {
        val text = read(HIDDEN_FILE) ?: return emptySet()
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), text).toSet() }.getOrDefault(emptySet())
    }

    fun saveHidden(ids: Set<String>) {
        val keep = ids.toList().takeLast(MAX_HIDDEN)
        schedule(HIDDEN_FILE) {
            json.encodeToString(ListSerializer(String.serializer()), keep).toByteArray(Charsets.UTF_8)
        }
    }

    fun flush() {
        writer.shutdown()
        writer.awaitTermination(3, TimeUnit.SECONDS)
    }

    @Synchronized
    fun wipe() {
        pending.clear()
        dir.listFiles()?.forEach { it.delete() }
        SecureStore.delete(KEY_FILE)
        keySpec = null
    }

    companion object {
        private const val KEY_FILE = "cache.key"
        private const val CHATS_FILE = "chats.bin"
        private const val HIDDEN_FILE = "hidden.bin"
        private const val MAX_HIDDEN = 5_000
        const val MAX_CACHED = 500
    }
}
