package pl.sinusoide.sinuschat.desktop.system

import java.io.File
import java.util.concurrent.TimeUnit

object Platform {

    enum class Os { WINDOWS, MAC, LINUX, OTHER }

    enum class Settings { MICROPHONE, NOTIFICATIONS, NETWORK, BLUETOOTH }

    val os: Os by lazy {
        val name = System.getProperty("os.name", "").lowercase()
        when {
            name.startsWith("windows") -> Os.WINDOWS
            name.startsWith("mac") || name.contains("darwin") -> Os.MAC
            name.contains("linux") -> Os.LINUX
            else -> Os.OTHER
        }
    }

    val isWindows: Boolean get() = os == Os.WINDOWS
    val isMac: Boolean get() = os == Os.MAC
    val isLinux: Boolean get() = os == Os.LINUX

    val arm: Boolean by lazy { System.getProperty("os.arch", "").lowercase().let { it == "aarch64" || it == "arm64" } }

    fun dataDir(): File {
        System.getProperty("sinuschat.dir")?.let { return File(it) }
        val home = System.getProperty("user.home")
        return when (os) {
            Os.WINDOWS -> File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: home, "SinusChat")
            Os.MAC -> File(home, "Library/Application Support/SinusChat")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "SinusChat")
        }
    }

    fun run(vararg command: String, timeoutSec: Long = 5, input: ByteArray? = null): Pair<Int, String>? = runCatching {
        val p = ProcessBuilder(*command).redirectErrorStream(input == null).start()
        if (input != null) p.outputStream.use { it.write(input) } else p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else p.exitValue() to out
    }.getOrNull()

    fun canOpenSettings(kind: Settings): Boolean = when (os) {
        Os.WINDOWS, Os.MAC -> true
        Os.LINUX -> File("/usr/bin/gnome-control-center").exists()
        Os.OTHER -> false
    }

    fun openSettings(kind: Settings): Boolean = runCatching {
        when (os) {
            Os.WINDOWS -> {
                val uri = when (kind) {
                    Settings.MICROPHONE -> "ms-settings:privacy-microphone"
                    Settings.NOTIFICATIONS -> "ms-settings:notifications"
                    Settings.NETWORK -> "ms-settings:network-wifi"
                    Settings.BLUETOOTH -> "ms-settings:bluetooth"
                }
                ProcessBuilder("cmd.exe", "/c", "start", "", uri).start()
            }
            Os.MAC -> {
                val uri = when (kind) {
                    Settings.MICROPHONE -> "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone"
                    Settings.NOTIFICATIONS -> "x-apple.systempreferences:com.apple.preference.notifications"
                    Settings.NETWORK -> "x-apple.systempreferences:com.apple.preference.network"
                    Settings.BLUETOOTH -> "x-apple.systempreferences:com.apple.BluetoothSettings"
                }
                ProcessBuilder("open", uri).start()
            }
            Os.LINUX -> {
                val panel = when (kind) {
                    Settings.MICROPHONE -> "sound"
                    Settings.NOTIFICATIONS -> "notifications"
                    Settings.NETWORK -> "wifi"
                    Settings.BLUETOOTH -> "bluetooth"
                }
                ProcessBuilder("gnome-control-center", panel).start()
            }
            Os.OTHER -> error("unsupported")
        }
        true
    }.getOrDefault(false)
}
