package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.installer.InstallerCore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallerCoreTest {

    private fun tmp(): File = Files.createTempDirectory("sc-installer").toFile()

    private fun fakeApp(root: File): File {
        val app = File(root, "image").apply { mkdirs() }
        File(app, "SinusChat.exe").writeText("exe")
        File(app, "app").mkdirs()
        File(app, "app/a.jar").writeBytes(ByteArray(3000) { it.toByte() })
        File(app, "runtime/bin").mkdirs()
        File(app, "runtime/bin/java.dll").writeText("dll")
        return app
    }

    @Test
    fun putsAppIntoSubfolderOfBusyDirectory() {
        val root = tmp()
        val busy = File(root, "Documents").apply { mkdirs() }
        File(busy, "notes.txt").writeText("x")
        assertEquals(File(busy, "SinusChat").absolutePath, InstallerCore.resolveTarget(busy).absolutePath)
        val empty = File(root, "Empty").apply { mkdirs() }
        assertEquals(empty.absolutePath, InstallerCore.resolveTarget(empty).absolutePath)
        root.deleteRecursively()
    }

    @Test
    fun refusesDangerousTargets() {
        val root = tmp()
        val app = fakeApp(root)
        assertFalse(InstallerCore.isSafeTarget(app, app))
        assertFalse(InstallerCore.isSafeTarget(File(app, "inner"), app))
        assertFalse(InstallerCore.isSafeTarget(File(System.getProperty("user.home")), app))
        val busy = File(root, "busy").apply { mkdirs() }
        File(busy, "x.txt").writeText("x")
        assertFalse(InstallerCore.isSafeTarget(busy, app))
        assertTrue(InstallerCore.isSafeTarget(File(root, "target"), app))
        root.deleteRecursively()
    }

    @Test
    fun copiesAndReplacesOldVersion() {
        val root = tmp()
        val app = fakeApp(root)
        val target = File(root, "target")
        File(target, "app").mkdirs()
        File(target, "SinusChat.exe").writeText("old")
        File(target, "app/old.jar").writeText("old")
        File(target, "keep.txt").writeText("keep")
        var last = 0f
        InstallerCore.install(app, target) { last = it }
        assertEquals(1f, last)
        assertEquals("exe", File(target, "SinusChat.exe").readText())
        assertFalse(File(target, "app/old.jar").exists())
        assertTrue(File(target, "app/a.jar").length() == 3000L)
        assertTrue(File(target, "runtime/bin/java.dll").isFile)
        assertTrue(File(target, "keep.txt").isFile)
        root.deleteRecursively()
    }
}
