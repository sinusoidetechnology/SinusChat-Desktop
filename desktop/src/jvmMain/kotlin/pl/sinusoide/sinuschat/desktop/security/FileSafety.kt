package pl.sinusoide.sinuschat.desktop.security

import java.io.File
import java.io.FileOutputStream

object FileSafety {

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val risky = setOf(
        "exe", "com", "scr", "pif", "cpl", "msi", "msp", "mst", "msix", "msixbundle", "appx", "appxbundle", "appinstaller",
        "bat", "cmd", "ps1", "ps1xml", "ps2", "psc1", "psm1", "psd1", "vb", "vbe", "vbs", "js", "jse", "ws", "wsc", "wsf", "wsh",
        "hta", "jar", "jnlp", "lnk", "url", "scf", "inf", "reg", "dll", "sys", "ocx", "drv", "chm", "hlp", "gadget",
        "application", "appref-ms", "settingcontent-ms", "library-ms", "search-ms", "diagcab", "msc", "xll", "xlam", "ppam",
        "docm", "dotm", "xlsm", "xltm", "xlsb", "pptm", "potm", "ppsm", "sldm", "iso", "img", "vhd", "vhdx", "py", "pyw",
        "sh", "rdp", "one", "website", "mof",
        "app", "pkg", "mpkg", "dmg", "command", "tool", "workflow", "scpt", "applescript", "terminal", "kext",
        "deb", "rpm", "appimage", "run", "bin", "desktop", "snap", "flatpakref", "ko"
    )

    fun extension(name: String): String = name.trim().trimEnd('.', ' ').substringAfterLast('.', "").lowercase()

    fun isRisky(name: String): Boolean = extension(name) in risky

    fun markFromInternet(file: File) {
        if (!windows || !file.isFile) return
        runCatching {
            FileOutputStream(file.absolutePath + ":Zone.Identifier").use {
                it.write("[ZoneTransfer]\r\nZoneId=3\r\n".toByteArray(Charsets.US_ASCII))
            }
        }
    }

    val sandboxExe: File?
        get() {
            if (!windows) return null
            val root = System.getenv("SystemRoot")?.takeIf { it.isNotBlank() } ?: "C:\\Windows"
            return File(root, "System32\\WindowsSandbox.exe").takeIf { it.isFile }
        }

    val sandboxAvailable: Boolean get() = sandboxExe != null

    private const val GUEST_FOLDER = "C:\\Users\\WDAGUtilityAccount\\Desktop\\SinusChat"

    fun sandboxConfig(hostFolder: File): String = """
        <Configuration>
          <Networking>Disable</Networking>
          <ClipboardRedirection>Disable</ClipboardRedirection>
          <PrinterRedirection>Disable</PrinterRedirection>
          <AudioInput>Disable</AudioInput>
          <VideoInput>Disable</VideoInput>
          <MappedFolders>
            <MappedFolder>
              <HostFolder>${xml(hostFolder.absolutePath)}</HostFolder>
              <SandboxFolder>$GUEST_FOLDER</SandboxFolder>
              <ReadOnly>true</ReadOnly>
            </MappedFolder>
          </MappedFolders>
          <LogonCommand>
            <Command>explorer.exe $GUEST_FOLDER</Command>
          </LogonCommand>
        </Configuration>
    """.trimIndent()

    fun launchSandbox(hostFolder: File): Boolean {
        val exe = sandboxExe ?: return false
        return runCatching {
            val config = File(hostFolder.parentFile, hostFolder.name + ".wsb")
            config.writeText(sandboxConfig(hostFolder), Charsets.UTF_8)
            ProcessBuilder(exe.absolutePath, config.absolutePath).start()
            true
        }.getOrDefault(false)
    }

    private fun xml(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
