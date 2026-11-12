package pl.sinusoide.sinuschat.desktop.diag

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.SecureStore
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.net.FirewallCheck
import pl.sinusoide.sinuschat.desktop.security.LockManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

object DesktopDiagnostics {

    enum class State { OK, PROBLEM, INFO }

    enum class Id { PHONE, PHONE_LOCKED, FIREWALL, LOCAL_NETWORK, NOTIFICATIONS, MICROPHONE, SPEAKERS, LOCK, ENCRYPTION, DISK }

    data class Check(val id: Id, val state: State, val value: String = "")

    const val CODENAME = "Dolphin"

    val version: String get() = PhoneLink.APP_NAME.substringAfterLast(' ')

    private const val LOW_DISK = 1024L * 1024 * 1024

    @Volatile private var model: String? = null

    private fun join(maker: String, name: String): String = when {
        name.isEmpty() -> maker
        maker.isEmpty() || name.startsWith(maker, ignoreCase = true) -> name
        else -> "$maker $name"
    }

    private fun macModel(): String? =
        pl.sinusoide.sinuschat.desktop.system.Platform.run("sysctl", "-n", "hw.model")?.takeIf { it.first == 0 }?.second?.trim()?.let { "Apple $it" }

    private fun linuxModel(): String? = runCatching {
        val maker = java.io.File("/sys/class/dmi/id/sys_vendor").readText().trim()
        val name = java.io.File("/sys/class/dmi/id/product_name").readText().trim()
        join(maker, name)
    }.getOrNull()

    fun deviceName(): String {
        model?.let { return it }
        if (!pl.sinusoide.sinuschat.desktop.system.Platform.isWindows) {
            val other = when (pl.sinusoide.sinuschat.desktop.system.Platform.os) {
                pl.sinusoide.sinuschat.desktop.system.Platform.Os.MAC -> macModel()
                pl.sinusoide.sinuschat.desktop.system.Platform.Os.LINUX -> linuxModel()
                else -> null
            }?.takeIf { it.isNotBlank() && "System Product Name" !in it && "To Be Filled" !in it && it.length < 120 }
            return (other ?: if (pl.sinusoide.sinuschat.desktop.system.Platform.isMac) "Mac" else "PC").also { model = it }
        }
        val found = runCatching {
            val p = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "\$c = Get-CimInstance Win32_ComputerSystem; \$c.Manufacturer + '|' + \$c.Model"
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor(8, TimeUnit.SECONDS)
            val parts = out.lines().lastOrNull().orEmpty().split('|')
            val maker = parts.getOrNull(0).orEmpty().trim()
            val name = parts.getOrNull(1).orEmpty().trim()
            when {
                name.isEmpty() -> maker
                maker.isEmpty() || name.startsWith(maker, ignoreCase = true) -> name
                else -> "$maker $name"
            }
        }.getOrNull()?.takeIf { it.isNotBlank() && "System Product Name" !in it && it.length < 120 }
        val result = found ?: "PC"
        model = result
        return result
    }

    fun osName(): String {
        val base = System.getProperty("os.name", "Windows")
        if (pl.sinusoide.sinuschat.desktop.system.Platform.isMac) {
            val v = pl.sinusoide.sinuschat.desktop.system.Platform.run("sw_vers", "-productVersion")?.takeIf { it.first == 0 }?.second?.trim()
            return "macOS ${v ?: System.getProperty("os.version", "")}".trim()
        }
        if (pl.sinusoide.sinuschat.desktop.system.Platform.isLinux) {
            val pretty = runCatching {
                java.io.File("/etc/os-release").readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter('=')?.trim('"')
            }.getOrNull()
            return pretty?.takeIf { it.isNotBlank() } ?: "Linux ${System.getProperty("os.version", "")}".trim()
        }
        if (!FirewallCheck.isWindows) return "$base ${System.getProperty("os.version", "")}".trim()
        val key = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"
        val display = runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, key, "DisplayVersion") }.getOrNull()
        val build = runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, key, "CurrentBuild") }.getOrNull()
        val buildNo = build?.toIntOrNull() ?: 0
        val name = if (base.startsWith("Windows 10") && buildNo >= 22000) "Windows 11" else base
        return listOfNotNull(name, display, build?.let { "build $it" }).joinToString(" ")
    }

    private fun lanAddress(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().any { nic ->
            nic.isUp && !nic.isLoopback && !nic.isVirtual &&
                nic.inetAddresses.toList().any { it is Inet4Address && !it.isLinkLocalAddress && !it.isLoopbackAddress }
        }
    }.getOrDefault(false)

    private fun microphone(): Boolean = runCatching {
        val format = AudioFormat(16_000f, 16, 1, true, false)
        val line = AudioSystem.getTargetDataLine(format)
        line.open(format)
        line.close()
        true
    }.getOrDefault(false)

    private fun speakers(): Boolean = runCatching {
        val format = AudioFormat(48_000f, 16, 2, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.close()
        true
    }.getOrDefault(false)

    private fun encryption(): Boolean = runCatching {
        val probe = "sinuschat".toByteArray()
        SecureStore.unprotect(SecureStore.protect(probe)).contentEquals(probe)
    }.getOrDefault(false)

    fun run(link: PhoneLink, store: ChatStore?, lock: LockManager?): List<Check> {
        val out = ArrayList<Check>()
        val state = link.state.value
        out += when (state) {
            is LinkState.Connected -> Check(Id.PHONE, State.OK, state.phoneName)
            is LinkState.Searching -> Check(Id.PHONE, State.PROBLEM, state.phoneName)
            is LinkState.Pairing -> Check(Id.PHONE, State.INFO)
        }
        if (state is LinkState.Connected && store != null) {
            out += Check(Id.PHONE_LOCKED, if (store.phoneLocked.value) State.PROBLEM else State.OK)
        }
        out += Check(Id.LOCAL_NETWORK, if (lanAddress()) State.OK else State.PROBLEM)
        if (FirewallCheck.isWindows) {
            val fw = runCatching { FirewallCheck.check() }.getOrNull()
            out += when {
                fw == null -> Check(Id.FIREWALL, State.INFO)
                fw.blocked -> Check(Id.FIREWALL, State.PROBLEM, "blocked")
                fw.publicNetwork -> Check(Id.FIREWALL, State.PROBLEM, "public")
                else -> Check(Id.FIREWALL, State.OK)
            }
        }
        val prefs = link.notifyPrefs.value
        out += Check(
            Id.NOTIFICATIONS,
            when {
                !java.awt.SystemTray.isSupported() -> State.PROBLEM
                !prefs.messages && !prefs.groups -> State.INFO
                else -> State.OK
            },
            if (!java.awt.SystemTray.isSupported()) "tray" else if (!prefs.messages && !prefs.groups) "off" else ""
        )
        out += Check(Id.MICROPHONE, if (microphone()) State.OK else State.PROBLEM)
        out += Check(Id.SPEAKERS, if (speakers()) State.OK else State.PROBLEM)
        out += Check(Id.ENCRYPTION, if (encryption()) State.OK else State.PROBLEM)
        if (lock != null) out += Check(Id.LOCK, if (lock.config.value.hasPin) State.OK else State.INFO)
        val free = runCatching { SecureStore.dir.usableSpace }.getOrDefault(-1L)
        out += Check(Id.DISK, if (free in 0 until LOW_DISK) State.PROBLEM else State.OK, if (free >= 0) gb(free) else "")
        return out
    }

    private fun gb(bytes: Long): String = "%.1f GB".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024.0 / 1024.0)

    fun summary(link: PhoneLink, store: ChatStore?, lock: LockManager?): String {
        val checks = run(link, store, lock)
        val rt = Runtime.getRuntime()
        val screen = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.joinToString(", ") {
                val m = it.displayMode
                "${m.width}x${m.height}"
            }
        }.getOrDefault("?")
        val lines = mutableListOf(
            "${PhoneLink.APP_NAME} ($CODENAME)",
            "Urządzenie: ${deviceName()}",
            "System: ${osName()} (${System.getProperty("os.arch", "?")})",
            "Java: ${System.getProperty("java.runtime.version", System.getProperty("java.version", "?"))}",
            "Pamięć JVM: ${rt.maxMemory() / 1024 / 1024} MB, procesory: ${rt.availableProcessors()}",
            "Ekrany: $screen",
            "Tryb: ${pl.sinusoide.sinuschat.desktop.system.AppMode.kind.name.lowercase()}",
            "Klucz danych: ${if (pl.sinusoide.sinuschat.desktop.system.Platform.isWindows) "DPAPI" else pl.sinusoide.sinuschat.desktop.system.KeyVault.backend.name}",
            "Język systemu: ${java.util.Locale.getDefault().toLanguageTag()}",
            "Motyw: ${link.theme.value}",
            "Telefon połączony: ${if (link.state.value is LinkState.Connected) "tak" else "nie"}",
            "Telefony w sieci: ${runCatching { link.phonesNearby() }.getOrDefault(-1)}",
            "Ręczny adres: ${if (link.manual.value != null) "tak" else "nie"}",
            "Powiadomienia: wiadomości=${link.notifyPrefs.value.messages}, grupy=${link.notifyPrefs.value.groups}, cisza=${link.notifyPrefs.value.quiet}",
            "Ostrzeżenia SafeEngine: ${link.safetyPrompts.value}"
        )
        store?.let { s ->
            lines += "Czaty w pamięci: ${s.chats.value.size}"
            lines += "Multimedia na dysku: ${s.media.vault.usage.value.coerceAtLeast(0L) / 1024 / 1024} MB"
        }
        lines += ""
        lines += "Sprawdzenia:"
        for (c in checks) {
            val detail = when (c.id) {
                Id.PHONE -> ""
                else -> c.value
            }
            lines += "- ${c.id.name.lowercase()}: ${c.state.name.lowercase()}${if (detail.isNotEmpty()) " ($detail)" else ""}"
        }
        return lines.joinToString("\n").take(15_000)
    }
}
