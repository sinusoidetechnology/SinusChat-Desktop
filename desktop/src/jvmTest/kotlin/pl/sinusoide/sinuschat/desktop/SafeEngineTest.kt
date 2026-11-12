package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.security.SafeEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeEngineTest {

    @AfterTest
    fun reset() {
        SafeEngine.pending.value?.let { SafeEngine.cancel(it) }
        SafeEngine.enabled = true
    }

    @Test
    fun sinusoidePagesOpenWithoutAsking() {
        for (url in listOf("https://sinusoide.pl", "https://www.sinusoide.pl/docs?lang=pl", "https://help.sinusoide.pl/a", "https://ko-fi.com/sinusoide", "https://patreon.com/cw/sinusoidetechnology/posts")) {
            assertTrue(SafeEngine.isExempt(url), url)
        }
        for (url in listOf(
            "https://sinusoide.pl.evil.com", "https://evilsinusoide.pl", "https://user@sinusoide.pl", "https://sinusoide.pl:8443",
            "ftp://sinusoide.pl", "https://ko-fi.com/someoneelse", "https://example.com", "javascript:alert(1)"
        )) {
            assertFalse(SafeEngine.isExempt(url), url)
        }
    }

    @Test
    fun linkAsksAndOpensOnlyAfterConsent() {
        val opened = mutableListOf<String>()
        SafeEngine.openLink("https://example.com/a") { opened += it }
        val ask = assertNotNull(SafeEngine.pending.value)
        assertEquals(SafeEngine.Risk.OPEN_LINK, ask.risk)
        assertEquals("example.com", ask.subject)
        assertTrue(opened.isEmpty())
        SafeEngine.proceed(ask)
        SafeEngine.proceed(ask)
        assertEquals(listOf("https://example.com/a"), opened)
        assertNull(SafeEngine.pending.value)

        SafeEngine.openLink("https://sinusoide.pl") { opened += it }
        assertNull(SafeEngine.pending.value)
        assertEquals("https://sinusoide.pl", opened.last())
    }

    @Test
    fun cancelAndReplaceCallOnCancel() {
        var cancelled = 0
        var saved = 0
        SafeEngine.guard(SafeEngine.Risk.SAVE_FILE, "a.pdf", onCancel = { cancelled++ }) { saved++ }
        SafeEngine.guard(SafeEngine.Risk.OPEN_FILE, "b.mp4", onCancel = { cancelled++ }) { saved++ }
        assertEquals(1, cancelled)
        val ask = assertNotNull(SafeEngine.pending.value)
        assertEquals("b.mp4", ask.subject)
        SafeEngine.cancel(ask)
        assertEquals(2, cancelled)
        assertEquals(0, saved)
    }

    @Test
    fun disabledPromptsSkipButDangerousFilesStillWarn() {
        SafeEngine.enabled = false
        var saved = 0
        SafeEngine.guard(SafeEngine.Risk.SAVE_FILE, "a.pdf") { saved++ }
        assertEquals(1, saved)
        assertNull(SafeEngine.pending.value)

        var sandboxed = 0
        SafeEngine.warnDangerous("setup.exe", onSandbox = { sandboxed++ }) { saved++ }
        val ask = assertNotNull(SafeEngine.pending.value)
        assertEquals(SafeEngine.Risk.DANGEROUS_FILE, ask.risk)
        SafeEngine.sandbox(ask)
        assertEquals(1, sandboxed)
        assertEquals(1, saved)
        assertNull(SafeEngine.pending.value)
    }
}
