package pl.sinusoide.sinuschat.desktop.legal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.sinusoide.sinuschat.desktop.system.Platform
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

object TermsGate {

    const val BUILT = "2026-09-23"
    const val URL = "https://sinusoide.pl/warunki.json"
    const val DOCS = "https://sinusoide.pl/dokumentacja.html"
    const val CHECK_EVERY_MS = 6 * 3_600_000L
    private const val MAX = 4096
    private val VERSION = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val FIELD = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")

    private val lock = Any()
    private var fileOverride: File? = null

    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    private fun file(): File = fileOverride ?: File(Platform.dataDir(), "terms.properties")

    internal fun useFile(f: File?) {
        synchronized(lock) { fileOverride = f }
        update()
    }

    private fun load(): Properties = Properties().apply {
        runCatching { file().takeIf { it.isFile }?.inputStream()?.use { load(it) } }
    }

    private fun save(p: Properties) {
        runCatching {
            val f = file()
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { p.store(it, null) }
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    fun accepted(): String? = synchronized(lock) { load().getProperty("accepted") }

    fun newest(): String = synchronized(lock) {
        val remote = load().getProperty("remote")?.takeIf { VERSION.matches(it) }
        if (remote != null && remote > BUILT) remote else BUILT
    }

    fun isRequired(): Boolean {
        val accepted = accepted() ?: return true
        return accepted < newest()
    }

    fun update() {
        _required.value = isRequired()
    }

    fun accept() {
        synchronized(lock) {
            val p = load()
            val remote = p.getProperty("remote")?.takeIf { VERSION.matches(it) }
            p.setProperty("accepted", if (remote != null && remote > BUILT) remote else BUILT)
            p.setProperty("acceptedAt", System.currentTimeMillis().toString())
            save(p)
        }
        update()
    }

    fun remember(remote: String) {
        if (!VERSION.matches(remote)) return
        synchronized(lock) {
            val p = load()
            p.setProperty("remote", remote)
            p.setProperty("checked", System.currentTimeMillis().toString())
            save(p)
        }
        update()
    }

    fun due(now: Long = System.currentTimeMillis()): Boolean {
        val last = synchronized(lock) { load().getProperty("checked")?.toLongOrNull() ?: 0L }
        return now - last >= CHECK_EVERY_MS
    }

    fun parse(json: String): String? = FIELD.find(json)?.groupValues?.get(1)?.trim()?.takeIf { VERSION.matches(it) }

    fun refresh(): Boolean {
        val remote = runCatching { parse(fetch()) }.getOrNull() ?: return false
        remember(remote)
        return true
    }

    private fun fetch(): String {
        val conn = (URI(URL).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "SinusChat-Desktop")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode != 200) error("http ${conn.responseCode}")
            if (!conn.url.protocol.equals("https", ignoreCase = true)) error("insecure redirect")
            val bytes = conn.inputStream.use { it.readNBytes(MAX + 1) }
            if (bytes.size > MAX) error("too large")
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
