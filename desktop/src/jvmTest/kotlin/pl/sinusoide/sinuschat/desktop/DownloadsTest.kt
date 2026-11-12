package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.chat.Downloads
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsTest {

    private val root = Files.createTempDirectory("sinus-downloads").toFile()

    @BeforeTest
    fun setUp() {
        Downloads.rootOverride = root
    }

    @AfterTest
    fun cleanup() {
        Downloads.rootOverride = null
        root.deleteRecursively()
    }

    @Test
    fun createsSinusChatFolderAndKeepsUsingIt() {
        val folder = File(root, "SinusChat")
        assertFalse(folder.exists())
        val first = Downloads.reserve("zdjecie.jpg")
        assertEquals(folder, first.parentFile)
        assertTrue(folder.isDirectory)
        File(folder, "stary.txt").writeText("x")
        val second = Downloads.reserve("film.mp4")
        assertEquals(folder, second.parentFile)
        assertTrue(File(folder, "stary.txt").exists())
    }

    @Test
    fun numbersRepeatedNamesLikeThePhone() {
        val a = Downloads.reserve("raport.pdf")
        val b = Downloads.reserve("raport.pdf")
        val c = Downloads.reserve("raport.pdf")
        assertEquals(listOf("raport.pdf", "raport (1).pdf", "raport (2).pdf"), listOf(a.name, b.name, c.name))
        val n1 = Downloads.reserve("notatki")
        val n2 = Downloads.reserve("notatki")
        assertEquals(listOf("notatki", "notatki (1)"), listOf(n1.name, n2.name))
    }

    @Test
    fun cleansDangerousNames() {
        assertEquals("a_b_c_.txt", Downloads.cleanName("a/b\\c:.txt").replace("..", "."))
        assertEquals("plik", Downloads.cleanName("  plik.  "))
        assertTrue(Downloads.reserve("..\\..\\Windows\\evil.exe").parentFile == File(root, "SinusChat"))
        assertTrue(Downloads.reserve("").name.startsWith("SinusChat_plik_"))
    }

    @Test
    fun recognisesTypesWhenThePhoneSendsNoName() {
        assertEquals("jpg", Downloads.extensionOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("png", Downloads.extensionOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        assertEquals("mp4", Downloads.extensionOf("\u0000\u0000\u0000\u0018ftypmp42xxxx".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("heic", Downloads.extensionOf("\u0000\u0000\u0000\u0018ftypheicxxxx".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("webp", Downloads.extensionOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertNull(Downloads.extensionOf(byteArrayOf(1, 2, 3, 4, 5)))

        val f = Downloads.reserve("SinusChat_zdjecie_1")
        f.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        val renamed = Downloads.withSniffedExtension(f)
        assertEquals("SinusChat_zdjecie_1.jpg", renamed.name)
        assertTrue(renamed.isFile && !f.exists())
        val named = Downloads.reserve("ma.pdf")
        assertEquals(named, Downloads.withSniffedExtension(named))
    }
}
