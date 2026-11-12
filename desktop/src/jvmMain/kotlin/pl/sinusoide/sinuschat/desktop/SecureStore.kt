package pl.sinusoide.sinuschat.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File

object SecureStore {

    private val entropy = "pl.sinusoide.sinuschat.desktop".toByteArray()

    val dir: File by lazy {
        pl.sinusoide.sinuschat.desktop.system.Platform.dataDir().apply { mkdirs() }.also {
            pl.sinusoide.sinuschat.desktop.system.KeyVault.dir = it
        }
    }

    private val windows: Boolean get() = pl.sinusoide.sinuschat.desktop.system.Platform.isWindows

    fun protect(data: ByteArray): ByteArray =
        if (windows) Crypt32Util.cryptProtectData(data, entropy, 0, "SinusChat", null)
        else pl.sinusoide.sinuschat.desktop.system.KeyVault.also { dir }.encrypt(data)

    fun unprotect(data: ByteArray): ByteArray =
        if (windows) Crypt32Util.cryptUnprotectData(data, entropy, 0, null)
        else pl.sinusoide.sinuschat.desktop.system.KeyVault.also { dir }.decrypt(data)

    fun write(name: String, data: ByteArray) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(protect(data))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun read(name: String): ByteArray? {
        val f = File(dir, name)
        if (!f.isFile) return null
        return try {
            unprotect(f.readBytes())
        } catch (_: Exception) {
            null
        }
    }

    fun delete(name: String) {
        File(dir, name).delete()
    }
}
