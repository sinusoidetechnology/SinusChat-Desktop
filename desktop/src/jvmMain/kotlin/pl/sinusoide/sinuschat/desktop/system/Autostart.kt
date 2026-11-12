package pl.sinusoide.sinuschat.desktop.system

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

object Autostart {

    const val ARG = "--background"
    private const val KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "SinusChat"
    private const val MAC_LABEL = "pl.sinusoide.sinuschat.desktop"

    fun appPath(): String? = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }

    val available: Boolean get() = Platform.os != Platform.Os.OTHER && appPath() != null && !AppMode.portable

    private fun command(path: String) = "\"$path\" $ARG"

    private val home: String get() = System.getProperty("user.home")

    private fun macFile() = File(home, "Library/LaunchAgents/$MAC_LABEL.plist")

    private fun linuxFile(): File {
        val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config"
        return File(config, "autostart/sinuschat-desktop.desktop")
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun macPlist(path: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
          <key>Label</key>
          <string>$MAC_LABEL</string>
          <key>ProgramArguments</key>
          <array>
            <string>${xml(path)}</string>
            <string>$ARG</string>
          </array>
          <key>RunAtLoad</key>
          <true/>
        </dict>
        </plist>
    """.trimIndent() + "\n"

    private fun linuxEntry(path: String) = listOf(
        "[Desktop Entry]",
        "Type=Application",
        "Name=SinusChat",
        "Exec=\"${path.replace("\"", "\\\"")}\" $ARG",
        "Terminal=false",
        "X-GNOME-Autostart-enabled=true"
    ).joinToString("\n") + "\n"

    fun isEnabled(): Boolean = when (Platform.os) {
        Platform.Os.WINDOWS -> runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, VALUE) }.getOrDefault(false)
        Platform.Os.MAC -> macFile().isFile
        Platform.Os.LINUX -> linuxFile().isFile
        Platform.Os.OTHER -> false
    }

    fun set(on: Boolean): Boolean = runCatching {
        when (Platform.os) {
            Platform.Os.WINDOWS -> {
                if (on) {
                    val path = appPath() ?: return false
                    Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, VALUE, command(path))
                } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, VALUE)) {
                    Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, VALUE)
                }
            }
            Platform.Os.MAC -> {
                val f = macFile()
                if (on) {
                    val path = appPath() ?: return false
                    f.parentFile.mkdirs()
                    f.writeText(macPlist(path))
                } else f.delete()
            }
            Platform.Os.LINUX -> {
                val f = linuxFile()
                if (on) {
                    val path = appPath() ?: return false
                    f.parentFile.mkdirs()
                    f.writeText(linuxEntry(path))
                } else f.delete()
            }
            Platform.Os.OTHER -> return false
        }
        true
    }.getOrDefault(false)

    fun refresh() {
        if (!isEnabled()) return
        val path = appPath() ?: return
        val current = when (Platform.os) {
            Platform.Os.WINDOWS -> runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, KEY, VALUE) }.getOrNull()
            Platform.Os.MAC -> runCatching { macFile().readText() }.getOrNull()?.takeIf { it == macPlist(path) }?.let { command(path) }
            Platform.Os.LINUX -> runCatching { linuxFile().readText() }.getOrNull()?.takeIf { it == linuxEntry(path) }?.let { command(path) }
            Platform.Os.OTHER -> null
        }
        if (current != command(path)) set(true)
    }
}
