package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.system.KeyVault
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyVaultTest {

    @Test
    fun encryptsWithStoredKey() {
        val dir = Files.createTempDirectory("sc-vault").toFile()
        KeyVault.dir = dir
        val data = "wiadomość testowa".toByteArray()
        val a = KeyVault.encrypt(data)
        val b = KeyVault.encrypt(data)
        assertFalse(a.contentEquals(b))
        assertContentEquals(data, KeyVault.decrypt(a))
        assertContentEquals(data, KeyVault.decrypt(b))
        if (!System.getProperty("os.name").startsWith("Windows")) return
        assertEquals(KeyVault.Backend.FILE, KeyVault.backend)
        assertTrue(File(dir, "storage.key").isFile)
        val tampered = a.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFailsWith<Exception> { KeyVault.decrypt(tampered) }
        assertFailsWith<Exception> { KeyVault.decrypt(data) }
    }
}
