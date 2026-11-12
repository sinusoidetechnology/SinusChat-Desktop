package pl.sinusoide.sinuschat.desktop.chat

import pl.sinusoide.sinuschat.desktop.SecureStore
import pl.sinusoide.sinuschat.link.LinkCrypto
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BlobCache(private val dir: File = File(SecureStore.dir, "blobs"), private val maxBytes: Long = 200L * 1024 * 1024) {

    @Volatile private var keySpec: SecretKeySpec? = null

    private val key: SecretKeySpec
        @Synchronized get() = keySpec ?: run {
            val raw = SecureStore.read(KEY_FILE)?.takeIf { it.size == 32 }
                ?: LinkCrypto.randomBytes(32).also { SecureStore.write(KEY_FILE, it) }
            SecretKeySpec(raw, "AES").also { keySpec = it }
        }

    private fun fileFor(name: String) = File(dir, LinkCrypto.fingerprint(name.toByteArray()) + ".bin")

    fun get(name: String): ByteArray? {
        val f = fileFor(name)
        if (!f.isFile) return null
        val data = runCatching { f.readBytes() }.getOrNull() ?: return null
        if (data.size < 13) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
            c.updateAAD(name.toByteArray())
            c.doFinal(data, 12, data.size - 12).also { f.setLastModified(System.currentTimeMillis()) }
        } catch (_: Exception) {
            f.delete()
            null
        }
    }

    @Synchronized
    fun put(name: String, bytes: ByteArray) {
        try {
            dir.mkdirs()
            val nonce = LinkCrypto.randomBytes(12)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            c.updateAAD(name.toByteArray())
            val target = fileFor(name)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeBytes(nonce + c.doFinal(bytes))
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            trim()
        } catch (_: Exception) {
        }
    }

    fun remove(name: String) {
        fileFor(name).delete()
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes * 8 / 10) break
            total -= f.length()
            f.delete()
        }
    }

    fun bytes(): Long = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    @Synchronized
    fun wipe() {
        dir.listFiles()?.forEach { it.delete() }
        SecureStore.delete(KEY_FILE)
        keySpec = null
    }

    companion object {
        private const val KEY_FILE = "blob.key"
    }
}
