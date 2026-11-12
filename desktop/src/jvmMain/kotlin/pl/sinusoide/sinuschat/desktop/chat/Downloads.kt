package pl.sinusoide.sinuschat.desktop.chat

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Downloads {

    const val FOLDER = "SinusChat"

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    var rootOverride: File? = null

    fun root(): File {
        rootOverride?.let { return it }
        if (windows) {
            runCatching { File(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads)) }.getOrNull()
                ?.takeIf { it.isDirectory }?.let { return it }
        }
        return File(System.getProperty("user.home"), "Downloads")
    }

    fun folder(): File = File(root(), FOLDER).apply { mkdirs() }

    fun cleanName(name: String?): String =
        name.orEmpty().replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimEnd('.', ' ')

    fun fallbackName(kind: String): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "SinusChat_${kind}_$stamp"
    }

    @Synchronized
    fun reserve(name: String): File {
        val dir = folder()
        val clean = cleanName(name).ifEmpty { fallbackName("plik") }
        val dot = clean.lastIndexOf('.').takeIf { it > 0 } ?: -1
        val base = if (dot > 0) clean.substring(0, dot) else clean
        val ext = if (dot > 0) clean.substring(dot) else ""
        var n = 0
        while (true) {
            val candidate = File(dir, if (n == 0) clean else "$base ($n)$ext")
            if (!candidate.exists() && !File(dir, candidate.name + ".part").exists() && runCatching { candidate.createNewFile() }.getOrDefault(false)) {
                return candidate
            }
            n++
        }
    }

    fun withSniffedExtension(file: File): File {
        if (file.name.substringAfterLast('.', "").isNotEmpty()) return file
        val head = runCatching { file.inputStream().use { it.readNBytes(32) } }.getOrDefault(ByteArray(0))
        val ext = extensionOf(head) ?: return file
        synchronized(this) {
            var n = 0
            while (true) {
                val target = File(file.parentFile, if (n == 0) "${file.name}.$ext" else "${file.name} ($n).$ext")
                if (!target.exists()) return if (file.renameTo(target)) target else file
                n++
            }
        }
    }

    fun extensionOf(head: ByteArray): String? {
        fun at(offset: Int, text: String) =
            head.size >= offset + text.length && text.indices.all { head[offset + it] == text[it].code.toByte() }
        fun bytes(vararg b: Int) = head.size >= b.size && b.indices.all { head[it] == b[it].toByte() }
        return when {
            bytes(0xFF, 0xD8, 0xFF) -> "jpg"
            bytes(0x89, 0x50, 0x4E, 0x47) -> "png"
            at(0, "GIF8") -> "gif"
            at(0, "RIFF") && at(8, "WEBP") -> "webp"
            at(0, "RIFF") && at(8, "WAVE") -> "wav"
            at(0, "RIFF") && at(8, "AVI ") -> "avi"
            at(4, "ftypheic") || at(4, "ftypheix") || at(4, "ftypmif1") -> "heic"
            at(4, "ftypqt") -> "mov"
            at(4, "ftyp3g") -> "3gp"
            at(4, "ftypM4A") -> "m4a"
            at(4, "ftyp") -> "mp4"
            bytes(0x1A, 0x45, 0xDF, 0xA3) -> "webm"
            at(0, "OggS") -> "ogg"
            at(0, "ID3") -> "mp3"
            at(0, "%PDF") -> "pdf"
            at(0, "PK") -> "zip"
            else -> null
        }
    }

    fun reveal(file: File) {
        runCatching {
            if (windows) {
                ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            } else if (pl.sinusoide.sinuschat.desktop.system.Platform.isMac) {
                ProcessBuilder("open", "-R", file.absolutePath).start()
            } else {
                java.awt.Desktop.getDesktop().open(file.parentFile)
            }
        }
    }
}
