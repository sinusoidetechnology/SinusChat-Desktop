package pl.sinusoide.sinuschat.desktop.system

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

object AppMode {

    enum class Kind { INSTALLED, PORTABLE, SESSION }

    const val MARKER = "SinusChat.portable"
    const val DATA_FOLDER = "data"
    const val SESSION_VALUE = "session"
    private const val SESSION_PREFIX = "sinuschat-session-"

    @Volatile var kind: Kind = Kind.INSTALLED
        private set
    @Volatile private var sessionDir: File? = null

    fun wipeSession() {
        val dir = sessionDir ?: return
        repeat(5) {
            if (dir.deleteRecursively() || !dir.exists()) return
            System.gc()
            Thread.sleep(150)
        }
    }

    val portable: Boolean get() = kind != Kind.INSTALLED
    val session: Boolean get() = kind == Kind.SESSION

    fun appDir(): File? = System.getProperty("jpackage.app-path")?.let { File(it).absoluteFile.parentFile }

    fun detect(dir: File?): Kind {
        val marker = dir?.let { File(it, MARKER) }?.takeIf { it.isFile } ?: return Kind.INSTALLED
        val text = runCatching { marker.readText().trim().lowercase() }.getOrDefault("")
        return if (text.startsWith(SESSION_VALUE)) Kind.SESSION else Kind.PORTABLE
    }

    fun configure() {
        if (System.getProperty("sinuschat.dir") != null) return
        val dir = appDir()
        kind = detect(dir)
        when (kind) {
            Kind.INSTALLED -> Unit
            Kind.PORTABLE -> {
                val data = File(dir, DATA_FOLDER).apply { mkdirs() }
                System.setProperty("sinuschat.dir", data.absolutePath)
            }
            Kind.SESSION -> {
                cleanStaleSessions()
                val data = Files.createTempDirectory(SESSION_PREFIX).toFile()
                sessionDir = data
                System.setProperty("sinuschat.dir", data.absolutePath)
                Runtime.getRuntime().addShutdownHook(Thread { wipeSession() })
            }
        }
    }

    private fun cleanStaleSessions() {
        val tmp = File(System.getProperty("java.io.tmpdir") ?: return)
        tmp.listFiles { f -> f.isDirectory && f.name.startsWith(SESSION_PREFIX) }?.forEach { dir ->
            val lock = File(dir, "instance.lock")
            val free = !lock.exists() || runCatching {
                RandomAccessFile(lock, "rw").channel.use { ch -> ch.tryLock()?.also { it.release() } != null }
            }.getOrDefault(false)
            if (free) dir.deleteRecursively()
        }
    }
}
