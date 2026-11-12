package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.legal.TermsGate
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TermsGateTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("terms").toFile()
        TermsGate.useFile(File(dir, "terms.properties"))
    }

    @AfterTest
    fun tearDown() {
        TermsGate.useFile(null)
        dir.deleteRecursively()
    }

    @Test
    fun firstRunRequiresAcceptance() {
        assertNull(TermsGate.accepted())
        assertTrue(TermsGate.isRequired())
        assertTrue(TermsGate.required.value)
        TermsGate.accept()
        assertEquals(TermsGate.BUILT, TermsGate.accepted())
        assertFalse(TermsGate.required.value)
    }

    @Test
    fun newerRemoteVersionAsksAgain() {
        TermsGate.accept()
        TermsGate.remember("2099-01-01")
        assertTrue(TermsGate.required.value)
        assertEquals("2099-01-01", TermsGate.newest())
        TermsGate.accept()
        assertEquals("2099-01-01", TermsGate.accepted())
        assertFalse(TermsGate.required.value)
    }

    @Test
    fun olderOrInvalidRemoteIsIgnored() {
        TermsGate.accept()
        TermsGate.remember("2020-01-01")
        assertFalse(TermsGate.required.value)
        TermsGate.remember("jutro")
        assertEquals(TermsGate.BUILT, TermsGate.newest())
    }

    @Test
    fun parsesWebsiteFile() {
        assertEquals("2026-09-23", TermsGate.parse("{\n  \"version\": \"2026-09-23\",\n  \"url\": \"https://sinusoide.pl/dokumentacja.html\"\n}"))
        assertNull(TermsGate.parse("{\"version\": \"<script>\"}"))
        assertNull(TermsGate.parse("nie json"))
    }

    @Test
    fun checksAreThrottled() {
        assertTrue(TermsGate.due())
        TermsGate.remember("2026-09-23")
        assertFalse(TermsGate.due())
        assertTrue(TermsGate.due(System.currentTimeMillis() + TermsGate.CHECK_EVERY_MS + 1))
    }
}
