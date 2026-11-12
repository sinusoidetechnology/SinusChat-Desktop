package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.chat.MediaVault
import pl.sinusoide.sinuschat.desktop.chat.StorageUsage
import java.awt.Rectangle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRulesTest {

    @Test
    fun quietHoursWorkAcrossMidnightLikeThePhone() {
        val p = NotifyPrefs(quiet = true, quietFrom = 22 * 60, quietTo = 7 * 60)
        assertTrue(p.quietAt(23 * 60))
        assertTrue(p.quietAt(3 * 60))
        assertFalse(p.quietAt(7 * 60))
        assertFalse(p.quietAt(12 * 60))
        assertFalse(p.copy(quiet = false).quietAt(23 * 60))
        val day = NotifyPrefs(quiet = true, quietFrom = 9 * 60, quietTo = 17 * 60)
        assertTrue(day.quietAt(10 * 60))
        assertFalse(day.quietAt(18 * 60))
        assertFalse(NotifyPrefs(quiet = true, quietFrom = 600, quietTo = 600).quietAt(600))
    }

    @Test
    fun messageRulesMatchThePhone() {
        val noon = 12 * 60
        val all = NotifyPrefs()
        assertTrue(all.allowsMessage("abc", muted = false, appInUse = false, minutes = noon))
        assertFalse(all.allowsMessage("abc", muted = true, appInUse = false, minutes = noon))
        assertFalse(all.copy(messages = false).allowsMessage("abc", false, false, noon))
        assertTrue(all.copy(messages = false).allowsMessage("g_team", false, false, noon))
        assertFalse(all.copy(groups = false).allowsMessage("g_team", false, false, noon))
        assertTrue(all.allowsMessage("abc", false, appInUse = true, minutes = noon))
        assertFalse(all.copy(inApp = false).allowsMessage("abc", false, appInUse = true, minutes = noon))
        val quiet = all.copy(quiet = true, quietFrom = 11 * 60, quietTo = 13 * 60)
        assertFalse(quiet.allowsMessage("abc", false, false, noon))
        assertTrue(quiet.allowsCallAlert(noon))
        assertFalse(quiet.copy(quietCalls = false).allowsCallAlert(noon))
    }

    @Test
    fun previewTakesTheStricterOfPhoneAndComputer() {
        assertEquals("full", NotifyPrefs().previewFor("full"))
        assertEquals("name", NotifyPrefs(preview = "name").previewFor("full"))
        assertEquals("hidden", NotifyPrefs(preview = "name").previewFor("hidden"))
        assertEquals("hidden", NotifyPrefs(preview = "hidden").previewFor("full"))
        assertEquals("name", NotifyPrefs().previewFor("name"))
        assertEquals("21:05", NotifyPrefs.format(21 * 60 + 5))
    }

    @Test
    fun windowIsRestoredOnlyWhereItCanBeGrabbed() {
        val screens = listOf(Rectangle(0, 0, 1920, 1080), Rectangle(1920, 0, 1280, 1024))
        assertTrue(WindowBounds(100f, 100f, 1180f, 760f).visibleOn(screens))
        assertTrue(WindowBounds(2000f, 50f, 1000f, 700f).visibleOn(screens))
        assertFalse(WindowBounds(4000f, 100f, 1180f, 760f).visibleOn(screens))
        assertFalse(WindowBounds(100f, -900f, 1180f, 760f).visibleOn(screens))
        assertFalse(WindowBounds(100f, 100f, 50f, 40f).visibleOn(screens))
    }

    @Test
    fun storageSnapshotGroupsByTypeAndChat() {
        val entries = listOf(
            MediaVault.Entry("c1", "photo", 100), MediaVault.Entry("c1", "photo", 50), MediaVault.Entry("c2", "video", 900),
            MediaVault.Entry("c2", "voice", 20), MediaVault.Entry("", "file", 5)
        )
        val s = StorageUsage.snapshot(entries, cacheBytes = 30, appBytes = 10, disk = File(System.getProperty("java.io.tmpdir")))
        assertEquals(listOf(StorageUsage.Kind.IMAGE, StorageUsage.Kind.VIDEO, StorageUsage.Kind.VOICE, StorageUsage.Kind.DOCUMENT), s.kinds.map { it.kind })
        assertEquals(listOf(2, 1, 1, 1), s.kinds.map { it.count })
        assertEquals(listOf(150L, 900L, 20L, 5L), s.kinds.map { it.bytes })
        assertEquals(listOf("c2", "c1", ""), s.conversations.map { it.conv })
        assertEquals(1075L, s.mediaBytes)
        assertEquals(1105L, s.appBytes)
        assertTrue(s.totalBytes > 0 && s.freeBytes in 0..s.totalBytes)
    }
}
