package pl.sinusoide.sinuschat.desktop.installer

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.util.Base64

data class InstalledApp(val dir: File, val version: String)

object InstallerCore {

    const val EXE = "SinusChat.exe"
    const val UNINSTALL_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\SinusChat-Desktop"
    private const val SHORTCUT = "SinusChat.lnk"
    private val OWNED = listOf("app", "runtime", EXE)

    fun defaultDir(): File {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: File(System.getProperty("user.home"), "AppData\\Local").path
        return File(File(base, "Programs"), "SinusChat")
    }

    fun dataDir(): File {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
        return File(base, "SinusChat")
    }

    fun sourceDir(): File? = System.getProperty("jpackage.app-path")?.let { File(it).absoluteFile.parentFile }?.takeIf { File(it, EXE).isFile }

    fun installed(): InstalledApp? = runCatching {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, UNINSTALL_KEY)) return null
        val dir = File(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_KEY, "InstallLocation"))
        val version = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_KEY, "DisplayVersion")
        if (File(dir, EXE).isFile) InstalledApp(dir, version) else null
    }.getOrNull()

    fun isInstalledBySetup(appDir: File): Boolean = installed()?.dir?.let { same(it, appDir) } == true

    fun same(a: File, b: File): Boolean = a.absoluteFile.normalize().path.equals(b.absoluteFile.normalize().path, ignoreCase = true)

    private fun inside(child: File, parent: File): Boolean {
        val c = child.absoluteFile.normalize().path.lowercase().trimEnd('\\', '/')
        val p = parent.absoluteFile.normalize().path.lowercase().trimEnd('\\', '/')
        return c == p || c.startsWith("$p\\") || c.startsWith("$p/")
    }

    fun resolveTarget(chosen: File): File {
        val dir = chosen.absoluteFile.normalize()
        if (File(dir, EXE).isFile) return dir
        val empty = !dir.exists() || dir.listFiles().isNullOrEmpty()
        val root = dir.parentFile == null
        return if (empty && !root) dir else File(dir, "SinusChat")
    }

    fun isSafeTarget(target: File, source: File?): Boolean {
        val t = target.absoluteFile.normalize()
        if (t.parentFile == null) return false
        if (source != null && (inside(t, source) || inside(source, t))) return false
        val home = File(System.getProperty("user.home")).absoluteFile.normalize()
        if (same(t, home)) return false
        if (t.exists() && !t.isDirectory) return false
        if (t.isDirectory && !File(t, EXE).isFile && !t.listFiles().isNullOrEmpty()) return false
        return true
    }

    fun sizeOf(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun freeSpace(target: File): Long {
        var probe: File? = target.absoluteFile
        while (probe != null && !probe.exists()) probe = probe.parentFile
        return probe?.usableSpace ?: -1L
    }

    fun install(source: File, target: File, progress: (Float) -> Unit) {
        require(isSafeTarget(target, source))
        val files = source.walkTopDown().filter { it.isFile }.toList()
        val total = files.sumOf { it.length() }.coerceAtLeast(1L)
        target.mkdirs()
        for (name in OWNED) File(target, name).let { if (it.isDirectory) it.deleteRecursively() else it.delete() }
        var done = 0L
        val buf = ByteArray(1 shl 20)
        for (f in files) {
            val rel = f.relativeTo(source)
            val dest = File(target, rel.path)
            dest.parentFile.mkdirs()
            f.inputStream().use { input ->
                dest.outputStream().use { output ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            dest.setLastModified(f.lastModified())
        }
        check(File(target, EXE).isFile)
    }

    fun register(target: File, version: String) {
        val hk = WinReg.HKEY_CURRENT_USER
        Advapi32Util.registryCreateKey(hk, UNINSTALL_KEY)
        val exe = File(target, EXE).absolutePath
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "DisplayName", "SinusChat")
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "DisplayVersion", version)
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "Publisher", "Sinusoide")
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "DisplayIcon", exe)
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "InstallLocation", target.absolutePath)
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "UninstallString", "\"$exe\" --uninstall")
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "URLInfoAbout", "https://sinusoide.pl")
        Advapi32Util.registrySetStringValue(hk, UNINSTALL_KEY, "HelpLink", "https://sinusoide.pl/pomoc.html")
        Advapi32Util.registrySetIntValue(hk, UNINSTALL_KEY, "NoModify", 1)
        Advapi32Util.registrySetIntValue(hk, UNINSTALL_KEY, "NoRepair", 1)
        Advapi32Util.registrySetIntValue(hk, UNINSTALL_KEY, "EstimatedSize", (sizeOf(target) / 1024).toInt())
    }

    fun unregister() {
        runCatching { Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, UNINSTALL_KEY) }
        runCatching { pl.sinusoide.sinuschat.desktop.system.Autostart.set(false) }
    }

    private fun quoted(s: String) = "'" + s.replace("'", "''") + "'"

    private fun powershell(script: String, wait: Boolean): Boolean {
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        return !wait || (p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0)
    }

    private const val FOLDERS = "\$start = [Environment]::GetFolderPath('Programs')\n\$desk = [Environment]::GetFolderPath('Desktop')\n"

    fun shortcuts(target: File, desktop: Boolean, start: Boolean): Boolean {
        val exe = File(target, EXE).absolutePath
        val script = buildString {
            append(FOLDERS)
            append("\$exe = ${quoted(exe)}\n")
            append("\$dir = ${quoted(target.absolutePath)}\n")
            append("\$shell = New-Object -ComObject WScript.Shell\n")
            append("function Make(\$path) { \$s = \$shell.CreateShortcut(\$path); \$s.TargetPath = \$exe; \$s.WorkingDirectory = \$dir; \$s.IconLocation = \"\$exe,0\"; \$s.Description = 'SinusChat'; \$s.Save() }\n")
            append("\$sp = Join-Path \$start '$SHORTCUT'\n")
            append("\$dp = Join-Path \$desk '$SHORTCUT'\n")
            append(if (start) "Make \$sp\n" else "Remove-Item -LiteralPath \$sp -ErrorAction SilentlyContinue\n")
            append(if (desktop) "Make \$dp\n" else "Remove-Item -LiteralPath \$dp -ErrorAction SilentlyContinue\n")
        }
        return runCatching { powershell(script, wait = true) }.getOrDefault(false)
    }

    fun hasDesktopShortcut(): Boolean = runCatching {
        val desk = javax.swing.filechooser.FileSystemView.getFileSystemView().homeDirectory
        File(desk, SHORTCUT).isFile
    }.getOrDefault(false)

    fun removeShortcuts() {
        val script = FOLDERS +
            "Remove-Item -LiteralPath (Join-Path \$start '$SHORTCUT') -ErrorAction SilentlyContinue\n" +
            "Remove-Item -LiteralPath (Join-Path \$desk '$SHORTCUT') -ErrorAction SilentlyContinue\n"
        runCatching { powershell(script, wait = true) }
    }

    private fun family(): Set<Long> {
        val ids = HashSet<Long>()
        var p: ProcessHandle? = ProcessHandle.current()
        while (p != null) {
            ids += p.pid()
            p = p.parent().orElse(null)
        }
        return ids
    }

    fun running(target: File): List<ProcessHandle> {
        val mine = family()
        val exe = File(target, EXE)
        return ProcessHandle.allProcesses().filter { p ->
            p.pid() !in mine && p.info().command().map { same(File(it), exe) }.orElse(false)
        }.toList()
    }

    fun closeRunning(target: File) {
        running(target).forEach { it.destroy() }
    }

    fun launch(target: File, language: String?) {
        val args = mutableListOf(File(target, EXE).absolutePath)
        if (language != null) args += "--language=$language"
        ProcessBuilder(args).directory(target).start()
    }

    fun scheduleRemoval(target: File, removeData: Boolean) {
        if (!File(target, EXE).isFile || target.parentFile == null) return
        val script = buildString {
            append("\$exe = ${quoted(File(target, EXE).absolutePath)}\n")
            append("\$deadline = (Get-Date).AddMinutes(2)\n")
            append("while ((Get-Date) -lt \$deadline -and (Get-Process -ErrorAction SilentlyContinue | Where-Object { \$_.Path -eq \$exe })) { Start-Sleep -Milliseconds 300 }\n")
            append("Start-Sleep -Milliseconds 500\n")
            for (name in OWNED) append("Remove-Item -LiteralPath ${quoted(File(target, name).absolutePath)} -Recurse -Force -ErrorAction SilentlyContinue\n")
            append("if (-not (Get-ChildItem -LiteralPath ${quoted(target.absolutePath)} -Force -ErrorAction SilentlyContinue)) { Remove-Item -LiteralPath ${quoted(target.absolutePath)} -Force -ErrorAction SilentlyContinue }\n")
            if (removeData) append("Remove-Item -LiteralPath ${quoted(dataDir().absolutePath)} -Recurse -Force -ErrorAction SilentlyContinue\n")
        }
        runCatching { powershell(script, wait = false) }
    }

    private const val REINSTALL_PREFIX = "SinusChat-reinstall-"

    private fun tempRoot(): File = File(System.getProperty("java.io.tmpdir")).absoluteFile

    fun startReinstall(source: File, language: String, progress: (Float) -> Unit) {
        val temp = File(tempRoot(), REINSTALL_PREFIX + System.currentTimeMillis())
        install(source, temp, progress)
        ProcessBuilder(File(temp, EXE).absolutePath, "--setup", "--reinstall", "--language=$language").directory(temp).start()
    }

    fun scheduleTempCleanup(dir: File) {
        val d = dir.absoluteFile
        if (!d.name.startsWith(REINSTALL_PREFIX) || d.parentFile == null || !same(d.parentFile, tempRoot())) return
        val script = buildString {
            append("\$exe = ${quoted(File(d, EXE).absolutePath)}\n")
            append("\$deadline = (Get-Date).AddMinutes(2)\n")
            append("while ((Get-Date) -lt \$deadline -and (Get-Process -ErrorAction SilentlyContinue | Where-Object { \$_.Path -eq \$exe })) { Start-Sleep -Milliseconds 300 }\n")
            append("Start-Sleep -Milliseconds 500\n")
            append("Remove-Item -LiteralPath ${quoted(d.absolutePath)} -Recurse -Force -ErrorAction SilentlyContinue\n")
        }
        runCatching { powershell(script, wait = false) }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "?"
        bytes >= 1L shl 30 -> "%.1f GB".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024.0 / 1024.0)
        else -> "%d MB".format(java.util.Locale.ROOT, (bytes + (1L shl 20) - 1) / (1L shl 20))
    }
}
