package pl.sinusoide.sinuschat.desktop.system

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object KeyVault {

    enum class Backend { KEYCHAIN, SECRET_SERVICE, FILE }

    private const val SERVICE = "pl.sinusoide.sinuschat.desktop"
    private const val ACCOUNT = "storage-key"
    private const val ERR_NOT_FOUND = -25300
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte(), 1)
    private const val IV = 12

    @Volatile var backend: Backend = Backend.FILE
        private set

    var dir: File = Platform.dataDir()

    private interface Security : Library {
        fun SecKeychainFindGenericPassword(
            keychain: Pointer?, serviceLength: Int, service: String, accountLength: Int, account: String,
            passwordLength: IntByReference, passwordData: PointerByReference, item: Pointer?
        ): Int

        fun SecKeychainAddGenericPassword(
            keychain: Pointer?, serviceLength: Int, service: String, accountLength: Int, account: String,
            passwordLength: Int, passwordData: ByteArray, item: Pointer?
        ): Int

        fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer?): Int
    }

    private val security: Security? by lazy {
        if (!Platform.isMac) null else runCatching { Native.load("Security", Security::class.java) }.getOrNull()
    }

    private val key: ByteArray by lazy { loadOrCreate() }

    private fun fresh(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun keychainRead(): ByteArray? {
        val lib = security ?: return null
        val length = IntByReference()
        val data = PointerByReference()
        val status = lib.SecKeychainFindGenericPassword(null, SERVICE.length, SERVICE, ACCOUNT.length, ACCOUNT, length, data, null)
        if (status != 0) return null
        val bytes = data.value.getByteArray(0, length.value)
        lib.SecKeychainItemFreeContent(null, data.value)
        return runCatching { Base64.getDecoder().decode(String(bytes, Charsets.US_ASCII)) }.getOrNull()?.takeIf { it.size == 32 }
    }

    private fun keychainWrite(k: ByteArray): Boolean {
        val lib = security ?: return false
        val encoded = Base64.getEncoder().encode(k)
        return lib.SecKeychainAddGenericPassword(null, SERVICE.length, SERVICE, ACCOUNT.length, ACCOUNT, encoded.size, encoded, null) == 0
    }

    private val secretTool: String? by lazy {
        if (!Platform.isLinux) null else listOf("/usr/bin/secret-tool", "/usr/local/bin/secret-tool", "/bin/secret-tool").firstOrNull { File(it).canExecute() }
    }

    private fun secretRead(): ByteArray? {
        val tool = secretTool ?: return null
        return runCatching {
            val p = ProcessBuilder(tool, "lookup", "service", SERVICE, "account", ACCOUNT).start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader().readText().trim()
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) null
            else Base64.getDecoder().decode(out).takeIf { it.size == 32 }
        }.getOrNull()
    }

    private fun secretWrite(k: ByteArray): Boolean {
        val tool = secretTool ?: return false
        return runCatching {
            val p = ProcessBuilder(tool, "store", "--label=SinusChat-Desktop", "service", SERVICE, "account", ACCOUNT).start()
            p.outputStream.use { it.write(Base64.getEncoder().encode(k)) }
            p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false) && secretRead()?.contentEquals(k) == true
    }

    private fun keyFile(): File = File(dir, "storage.key")

    private fun fileRead(): ByteArray? = runCatching {
        Base64.getDecoder().decode(keyFile().readText().trim()).takeIf { it.size == 32 }
    }.getOrNull()

    private fun fileWrite(k: ByteArray) {
        dir.mkdirs()
        val f = keyFile()
        f.writeText(Base64.getEncoder().encodeToString(k))
        runCatching { Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------")) }
    }

    private fun loadOrCreate(): ByteArray {
        when {
            Platform.isMac && security != null -> {
                keychainRead()?.let { backend = Backend.KEYCHAIN; return it }
                fileRead()?.let { k ->
                    if (keychainWrite(k)) { keyFile().delete(); backend = Backend.KEYCHAIN } else backend = Backend.FILE
                    return k
                }
                val k = fresh()
                if (keychainWrite(k)) { backend = Backend.KEYCHAIN; return k }
                fileWrite(k); backend = Backend.FILE; return k
            }
            Platform.isLinux && secretTool != null -> {
                secretRead()?.let { backend = Backend.SECRET_SERVICE; return it }
                fileRead()?.let { k ->
                    if (secretWrite(k)) { keyFile().delete(); backend = Backend.SECRET_SERVICE } else backend = Backend.FILE
                    return k
                }
                val k = fresh()
                if (secretWrite(k)) { backend = Backend.SECRET_SERVICE; return k }
                fileWrite(k); backend = Backend.FILE; return k
            }
            else -> {
                fileRead()?.let { backend = Backend.FILE; return it }
                val k = fresh()
                fileWrite(k); backend = Backend.FILE; return k
            }
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(IV).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + iv + cipher.doFinal(data)
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > MAGIC.size + IV && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
        val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(data, MAGIC.size + IV, data.size - MAGIC.size - IV)
    }
}
