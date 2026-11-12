package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.update.DesktopUpdater
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingUpdateTest {

    private fun setup(): Pair<File, File> {
        val dir = Files.createTempDirectory("sc-updates").toFile()
        val file = File(dir, "SinusChat-Setup-9.9.exe").apply { writeBytes(ByteArray(5000) { (it % 251).toByte() }) }
        return dir to file
    }

    @Test
    fun keepsVerifiedNewerDownload() {
        val (dir, file) = setup()
        val prefs = NotifyPrefs(pendingVersion = "9.9", pendingFile = file.absolutePath, pendingSha = DesktopUpdater.sha256(file))
        assertEquals(file.absolutePath, DesktopUpdater.pendingFile(prefs, "1.0", dir)?.absolutePath)
        dir.deleteRecursively()
    }

    @Test
    fun dropsAppliedTamperedOrForeignDownloads() {
        val (dir, file) = setup()
        val sha = DesktopUpdater.sha256(file)
        assertNull(DesktopUpdater.pendingFile(NotifyPrefs(pendingVersion = "1.0", pendingFile = file.absolutePath, pendingSha = sha), "1.0", dir))
        assertNull(DesktopUpdater.pendingFile(NotifyPrefs(pendingVersion = "9.9", pendingFile = file.absolutePath, pendingSha = "0".repeat(64)), "1.0", dir))
        val other = Files.createTempDirectory("sc-other").toFile()
        val moved = file.copyTo(File(other, file.name))
        assertNull(DesktopUpdater.pendingFile(NotifyPrefs(pendingVersion = "9.9", pendingFile = moved.absolutePath, pendingSha = sha), "1.0", dir))
        dir.deleteRecursively()
        other.deleteRecursively()
    }
}
