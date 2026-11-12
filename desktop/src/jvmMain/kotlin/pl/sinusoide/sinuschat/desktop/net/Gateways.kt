package pl.sinusoide.sinuschat.desktop.net

import pl.sinusoide.sinuschat.desktop.system.Platform
import java.io.File
import java.net.InetAddress

object Gateways {

    private const val TTL_MS = 20_000L
    private val WINDOWS_ROUTE = Regex("^\\s*0\\.0\\.0\\.0\\s+0\\.0\\.0\\.0\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})\\s", RegexOption.MULTILINE)
    private val MAC_ROUTE = Regex("gateway:\\s*(\\d{1,3}(?:\\.\\d{1,3}){3})")

    @Volatile private var cached: Pair<Long, List<String>>? = null

    fun ipv4(now: Long = System.currentTimeMillis()): List<String> {
        cached?.let { (at, list) -> if (now - at < TTL_MS) return list }
        val list = runCatching { lookup() }.getOrDefault(emptyList()).filter(::usable).distinct()
        cached = now to list
        return list
    }

    private fun lookup(): List<String> = when {
        Platform.isWindows -> parseWindows(Platform.run("route", "print", "-4", "0.0.0.0")?.second.orEmpty())
        Platform.isMac -> MAC_ROUTE.findAll(Platform.run("route", "-n", "get", "default")?.second.orEmpty()).map { it.groupValues[1] }.toList()
        else -> parseLinux(runCatching { File("/proc/net/route").readText() }.getOrDefault(""))
    }

    fun parseWindows(text: String): List<String> = WINDOWS_ROUTE.findAll(text).map { it.groupValues[1] }.toList()

    fun parseLinux(text: String): List<String> = text.lines().drop(1).mapNotNull { line ->
        val f = line.trim().split(Regex("\\s+"))
        if (f.size < 3 || f[1] != "00000000") return@mapNotNull null
        val hex = f[2].toLongOrNull(16) ?: return@mapNotNull null
        if (hex == 0L) return@mapNotNull null
        listOf(hex and 0xFF, (hex shr 8) and 0xFF, (hex shr 16) and 0xFF, (hex shr 24) and 0xFF).joinToString(".")
    }

    private fun usable(ip: String): Boolean = runCatching {
        val a = InetAddress.getByName(ip)
        a.isSiteLocalAddress && !a.isLoopbackAddress && !a.isAnyLocalAddress
    }.getOrDefault(false)
}
