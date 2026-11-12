package pl.sinusoide.sinuschat.desktop.update

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.sinusoide.sinuschat.desktop.AppVersion
import pl.sinusoide.sinuschat.desktop.NotifyPrefs
import pl.sinusoide.sinuschat.desktop.SecureStore
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val LocalUpdater = staticCompositionLocalOf<DesktopUpdater?> { null }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Disabled : UpdateState
    data object Checking : UpdateState
    data object Unknown : UpdateState
    data class Latest(val version: String) : UpdateState
    data class Available(val manifest: UpdateManifest, val pkg: UpdatePackage?) : UpdateState
    data class Downloading(
        val manifest: UpdateManifest,
        val pkg: UpdatePackage,
        val percent: Int,
        val done: Long = 0L,
        val bytesPerSecond: Double = 0.0
    ) : UpdateState {
        val total: Long get() = pkg.size
        val progress: Float get() = if (pkg.size <= 0) 0f else (done.toFloat() / pkg.size).coerceIn(0f, 1f)
        val secondsLeft: Long? get() = if (bytesPerSecond < 1.0) null else ((pkg.size - done) / bytesPerSecond).toLong().coerceAtLeast(0L)
    }
    data class Verifying(val manifest: UpdateManifest, val pkg: UpdatePackage) : UpdateState
    data class Ready(val manifest: UpdateManifest, val pkg: UpdatePackage, val file: File) : UpdateState
    data class Installing(val manifest: UpdateManifest, val restarting: Boolean) : UpdateState
    data class Failed(val manifest: UpdateManifest, val pkg: UpdatePackage?, val reason: Reason) : UpdateState

    enum class Reason { NETWORK, REJECTED, INSTALL, DEV_BUILD }

    val busy: Boolean get() = this is Checking || this is Downloading || this is Verifying || this is Installing
}

class DesktopUpdater(
    private val prefs: () -> NotifyPrefs,
    private val updatePrefs: ((NotifyPrefs) -> NotifyPrefs) -> Unit,
    private val current: String = AppVersion.name,
    private val dir: File = updatesDir(),
    private val manifestUrl: String = MANIFEST_URL,
    private val publicKey: String = UpdateManifests.PUBLIC_KEY,
    private val platformKeys: List<String> = UpdateManifests.platformKeys()
) {

    companion object {
        const val MANIFEST_URL = "https://github.com/sinusoidetechnology/SinusChat-Desktop/releases/latest/download/desktop-latest.json"
        const val CHECK_EVERY_MS = 24 * 3_600_000L
        const val SNOOZE_MS = 3 * 24 * 3_600_000L
        private const val MAX_MANIFEST = 64 * 1024
        private const val TIMEOUT_MS = 15_000
        private const val MAX_START_TRIES = 2
        private val SAFE_NAME = Regex("[^A-Za-z0-9._-]")

        fun updatesDir(): File = File(SecureStore.dir, "updates")

        private fun isWindows() = System.getProperty("os.name", "").startsWith("Windows")

        private fun isMac() = System.getProperty("os.name", "").lowercase().startsWith("mac")

        private fun appPath(): String? = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }

        private fun quoted(s: String) = "'" + s.replace("'", "''") + "'"

        fun canInstall(): Boolean = !isWindows() || appPath() != null

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun pendingFile(p: NotifyPrefs, current: String = AppVersion.name, dir: File = updatesDir()): File? {
            val version = p.pendingVersion ?: return null
            val path = p.pendingFile ?: return null
            val sha = p.pendingSha ?: return null
            if (!UpdateManifests.isNewer(version, current)) return null
            val file = File(path).absoluteFile
            if (file.parentFile == null || !file.parentFile.absolutePath.equals(dir.absolutePath, ignoreCase = true)) return null
            if (!file.isFile) return null
            return file.takeIf { runCatching { sha256(it).equals(sha, ignoreCase = true) }.getOrDefault(false) }
        }

        fun shouldApplyAtStart(p: NotifyPrefs): Boolean = p.pendingTries < MAX_START_TRIES && canInstall() && pendingFile(p) != null

        private fun powershell(script: String) {
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }

        fun launchInstaller(file: File): Boolean? {
            val ext = file.extension.lowercase()
            return when {
                isWindows() && ext == "msi" -> {
                    val app = appPath() ?: return null
                    powershell(
                        listOf(
                            "\$msi = ${quoted(file.absolutePath)}",
                            "\$app = ${quoted(app)}",
                            "Start-Sleep -Seconds 2",
                            "\$p = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', ('\"' + \$msi + '\"'), '/passive', '/norestart') -Wait -PassThru",
                            "Start-Process -FilePath \$app"
                        ).joinToString("\n")
                    )
                    true
                }
                isWindows() && ext == "exe" -> {
                    appPath() ?: return null
                    powershell(
                        listOf(
                            "\$setup = ${quoted(file.absolutePath)}",
                            "Start-Sleep -Seconds 2",
                            "Start-Process -FilePath \$setup -ArgumentList '--update'"
                        ).joinToString("\n")
                    )
                    true
                }
                isMac() && (ext == "pkg" || ext == "dmg") -> {
                    val p = ProcessBuilder("open", file.absolutePath).start()
                    if (!p.waitFor(20, TimeUnit.SECONDS) || p.exitValue() != 0) return null
                    ext == "pkg"
                }
                !isWindows() && !isMac() && (ext == "deb" || ext == "rpm") -> {
                    val p = ProcessBuilder("xdg-open", file.absolutePath).start()
                    if (!p.waitFor(20, TimeUnit.SECONDS) || p.exitValue() != 0) return null
                    false
                }
                else -> null
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "desktop-updater").apply { isDaemon = true } }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val _dialog = MutableStateFlow(false)
    val dialog: StateFlow<Boolean> = _dialog

    private val _ask = MutableStateFlow(false)
    val askRestart: StateFlow<Boolean> = _ask

    val version: String get() = current

    internal fun preview(state: UpdateState, ask: Boolean = false) {
        _state.value = state
        _ask.value = ask
    }

    fun cleanDownloads() {
        worker.execute {
            val p = prefs()
            val keep = runCatching { pendingFile(p, current, dir) }.getOrNull()
            runCatching { dir.listFiles()?.filter { keep == null || !it.absolutePath.equals(keep.absolutePath, ignoreCase = true) }?.forEach { it.delete() } }
            if (keep == null) {
                if (p.pendingVersion != null) updatePrefs { it.copy(pendingVersion = null, pendingFile = null, pendingSha = null, pendingTries = 0) }
            } else {
                val manifest = UpdateManifest(UpdateManifests.APP, p.pendingVersion!!)
                _state.value = UpdateState.Ready(manifest, UpdatePackage("https://sinusoide.pl/", p.pendingSha!!, keep.length(), keep.name), keep)
            }
        }
    }

    fun check(showIfFound: Boolean = false, force: Boolean = false) {
        val now = _state.value
        if (now.busy || now is UpdateState.Ready) return
        if (!force && !prefs().updates) {
            _state.value = UpdateState.Disabled
            return
        }
        _state.value = UpdateState.Checking
        worker.execute {
            val result = runCatching { fetchManifest() }.getOrNull()
            val next = when {
                result == null -> UpdateState.Unknown
                UpdateManifests.isNewer(result.version, current) ->
                    UpdateState.Available(result, UpdateManifests.packageFor(result, platformKeys))
                else -> UpdateState.Latest(result.version)
            }
            _state.value = next
            if (showIfFound && next is UpdateState.Available) {
                if (next.pkg != null && canInstall()) download(next.manifest, next.pkg, then = null)
                else if (!snoozed(next.manifest.version)) _dialog.value = true
            }
        }
    }

    fun open() {
        _dialog.value = true
        val s = _state.value
        if (s !is UpdateState.Available && s !is UpdateState.Failed && s !is UpdateState.Ready && !s.busy) check(force = true)
    }

    fun close() {
        _dialog.value = false
    }

    fun requestRestart() {
        if (_state.value is UpdateState.Ready) _ask.value = true
    }

    fun dismissRestart() {
        _ask.value = false
    }

    fun later() {
        val s = _state.value
        val version = when (s) {
            is UpdateState.Available -> s.manifest.version
            is UpdateState.Failed -> s.manifest.version
            else -> null
        }
        if (version != null) {
            val until = System.currentTimeMillis() + SNOOZE_MS
            updatePrefs { it.copy(updateSnooze = version, updateSnoozeUntil = until) }
        }
        close()
    }

    private fun snoozed(version: String): Boolean {
        val p = prefs()
        return p.updateSnooze == version && System.currentTimeMillis() < p.updateSnoozeUntil
    }

    fun install(exit: () -> Unit) {
        when (val s = _state.value) {
            is UpdateState.Ready -> apply(exit)
            is UpdateState.Available -> s.pkg?.let { download(s.manifest, it, exit) }
            is UpdateState.Failed -> s.pkg?.let { download(s.manifest, it, exit) }
            else -> Unit
        }
    }

    fun apply(exit: () -> Unit) {
        val ready = _state.value as? UpdateState.Ready ?: return
        _ask.value = false
        worker.execute {
            val restart = runCatching { launchInstaller(ready.file) }.getOrNull()
            if (restart == null) {
                reveal(ready.file)
                _state.value = UpdateState.Failed(ready.manifest, ready.pkg, UpdateState.Reason.INSTALL)
                return@execute
            }
            updatePrefs { it.copy(pendingTries = it.pendingTries + 1) }
            _state.value = UpdateState.Installing(ready.manifest, restart)
            if (restart) {
                Thread.sleep(1_200)
                javax.swing.SwingUtilities.invokeLater { exit() }
            }
        }
    }

    private fun download(manifest: UpdateManifest, pkg: UpdatePackage, then: (() -> Unit)?) {
        if (!canInstall()) {
            _state.value = UpdateState.Failed(manifest, pkg, UpdateState.Reason.DEV_BUILD)
            return
        }
        _state.value = UpdateState.Downloading(manifest, pkg, 0)
        worker.execute {
            val file = runCatching { downloadPackage(manifest, pkg) }.getOrElse {
                _state.value = UpdateState.Failed(manifest, pkg, UpdateState.Reason.NETWORK)
                return@execute
            }
            _state.value = UpdateState.Verifying(manifest, pkg)
            if (file.length() != pkg.size || !sha256(file).equals(pkg.sha256, ignoreCase = true)) {
                file.delete()
                _state.value = UpdateState.Failed(manifest, pkg, UpdateState.Reason.REJECTED)
                return@execute
            }
            updatePrefs { it.copy(pendingVersion = manifest.version, pendingFile = file.absolutePath, pendingSha = pkg.sha256.lowercase(), pendingTries = 0) }
            _state.value = UpdateState.Ready(manifest, pkg, file)
            if (then != null) apply(then)
        }
    }

    private fun connect(url: String): HttpURLConnection {
        val uri = URI(url)
        require(uri.scheme.equals("https", ignoreCase = true))
        return (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "SinusChat-Desktop/$current")
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    private fun readSmall(url: String): ByteArray {
        val conn = connect(url)
        try {
            if (conn.responseCode != 200) error("http ${conn.responseCode}")
            if (!conn.url.protocol.equals("https", ignoreCase = true)) error("insecure redirect")
            return conn.inputStream.use { limited(it, MAX_MANIFEST) }
        } finally {
            conn.disconnect()
        }
    }

    private fun limited(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > max) error("too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun fetchManifest(): UpdateManifest? {
        val data = readSmall(manifestUrl)
        val sig = String(readSmall("$manifestUrl.sig"), Charsets.US_ASCII).trim()
        if (runCatching { Base64.getDecoder().decode(sig) }.isFailure) return null
        return UpdateManifests.parse(data, sig, publicKey)
    }

    private fun fileName(pkg: UpdatePackage): String {
        val fromUrl = runCatching { URI(pkg.url).path.substringAfterLast('/') }.getOrNull().orEmpty()
        val raw = pkg.name.ifBlank { fromUrl }.ifBlank { "SinusChat-Desktop-update" }
        return SAFE_NAME.replace(raw, "_").trimStart('.').ifBlank { "SinusChat-Desktop-update" }
    }

    private fun downloadPackage(manifest: UpdateManifest, pkg: UpdatePackage): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, fileName(pkg))
        val conn = connect(pkg.url)
        try {
            if (conn.responseCode != 200) error("http ${conn.responseCode}")
            if (!conn.url.protocol.equals("https", ignoreCase = true)) error("insecure redirect")
            var done = 0L
            var speed = 0.0
            var lastAt = System.nanoTime()
            var lastDone = 0L
            var shownAt = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > pkg.size) error("too large")
                        output.write(buf, 0, n)
                        val now = System.nanoTime()
                        val span = (now - lastAt) / 1e9
                        if (span >= 0.25) {
                            val inst = (done - lastDone) / span
                            speed = if (speed <= 0.0) inst else speed * 0.7 + inst * 0.3
                            lastAt = now
                            lastDone = done
                        }
                        if (now - shownAt >= 100_000_000L || done == pkg.size) {
                            shownAt = now
                            val percent = ((done * 100) / pkg.size).toInt().coerceIn(0, 100)
                            _state.value = UpdateState.Downloading(manifest, pkg, percent, done, speed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        return target
    }

    private fun reveal(file: File) {
        runCatching {
            if (isWindows()) ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            else java.awt.Desktop.getDesktop().open(file.parentFile)
        }
    }
}
