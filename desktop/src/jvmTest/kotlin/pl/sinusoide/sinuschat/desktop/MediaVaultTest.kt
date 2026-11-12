package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.chat.MediaVault
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaVaultTest {

    private val root = Files.createTempDirectory("sinus-vault").toFile()
    private val key = Random(7).nextBytes(32)
    private var forgotten = 0

    private var indexBytes: ByteArray? = null

    private fun vault(max: Long = Long.MAX_VALUE) =
        MediaVault(File(root, "media"), max, { key }, { forgotten++ }, { indexBytes }, { indexBytes = it })

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun keepsBytesAcrossInstancesAndEncryptsThem() {
        val data = Random(1).nextBytes(3 * 1024 * 1024 + 123)
        assertTrue(vault().put("f:photo", data))
        val again = vault()
        assertTrue(again.has("f:photo"))
        assertContentEquals(data, again.read("f:photo"))
        val stored = File(root, "media").listFiles()!!.single().readBytes()
        val head = data.copyOf(32)
        assertFalse((0..stored.size - head.size).any { i -> head.indices.all { stored[i + it] == head[it] } }, "plaintext visible on disk")
    }

    @Test
    fun exportsLargeFilesAndEmptyOnes() {
        val v = vault()
        val plain = File(root, "film.mp4").apply { writeBytes(Random(2).nextBytes(5 * 1024 * 1024 + 7)) }
        assertTrue(v.putFile("f:film", plain))
        val out = File(root, "out/film.mp4")
        assertTrue(v.export("f:film", out))
        assertContentEquals(plain.readBytes(), out.readBytes())
        assertTrue(v.put("f:empty", ByteArray(0)))
        assertContentEquals(ByteArray(0), v.read("f:empty"))
    }

    @Test
    fun rejectsTamperingAndTruncation() {
        val v = vault()
        v.put("f:a", Random(3).nextBytes(2 * 1024 * 1024 + 10))
        val file = File(root, "media").listFiles()!!.single()
        RandomAccessFile(file, "rw").use { it.seek(100); it.write(it.read() xor 1) }
        assertNull(v.read("f:a"))
        assertFalse(v.has("f:a"))

        v.put("f:b", Random(4).nextBytes(2 * 1024 * 1024 + 10))
        val f2 = File(root, "media").listFiles()!!.single()
        RandomAccessFile(f2, "rw").use { it.setLength(16 + 4 + 1024 * 1024 + 16) }
        assertNull(v.read("f:b"))
    }

    @Test
    fun announcesChanges() {
        val v = vault()
        val start = v.version.value
        v.put("f:a", byteArrayOf(1))
        assertTrue(v.version.value > start)
        val afterPut = v.version.value
        v.remove("f:a")
        assertTrue(v.version.value > afterPut)
        val plain = File(root, "x.bin").apply { writeBytes(byteArrayOf(5, 6)) }
        var done = false
        v.putFileAsync("f:b", plain) { done = it }
        val end = System.currentTimeMillis() + 3000
        while (!done && System.currentTimeMillis() < end) Thread.sleep(5)
        assertTrue(done && v.has("f:b"))
    }

    @Test
    fun remembersChatAndTypeOfEachFile() {
        val v = vault()
        v.put("f:p1", ByteArray(1000), MediaVault.Meta("c1", "photo"))
        v.put("f:v1", ByteArray(3000), MediaVault.Meta("c2", "video"))
        v.put("f:legacy", ByteArray(10))
        val again = vault().entries().sortedBy { it.bytes }
        assertEquals(listOf("file", "photo", "video"), again.map { it.kind })
        assertEquals(listOf("", "c1", "c2"), again.map { it.conv })
        assertTrue(again.all { it.bytes > 0 })
        vault().remove("f:p1")
        assertEquals(setOf("video", "file"), vault().entries().map { it.kind }.toSet())
        vault().wipe()
        assertTrue(vault().entries().isEmpty())
    }

    @Test
    fun wrongNameOrKeyCannotRead() {
        vault().put("f:x", byteArrayOf(1, 2, 3))
        val other = MediaVault(File(root, "media"), Long.MAX_VALUE, { Random(9).nextBytes(32) }, {}, { null }, {})
        assertNull(other.read("f:x"))
    }

    @Test
    fun evictsOldestWhenFullAndWipes() {
        val v = vault(max = 3L * 1024 * 1024)
        v.put("f:old", ByteArray(1024 * 1024))
        File(root, "media").listFiles()!!.single().setLastModified(System.currentTimeMillis() - 60_000)
        v.put("f:mid", ByteArray(1024 * 1024))
        v.put("f:new", ByteArray(1024 * 1024 + 500_000))
        assertFalse(v.has("f:old"))
        assertTrue(v.has("f:new"))
        v.wipe()
        assertFalse(v.has("f:new"))
        assertEquals(1, forgotten)
        assertEquals(0L, v.usage.value)
    }
}
