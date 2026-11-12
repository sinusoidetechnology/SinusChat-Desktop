package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.ProfileStrings
import pl.sinusoide.sinuschat.desktop.ui.chat.Greeting
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.LinkFrame
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatSyncTest {

    private fun msg(id: String, at: Long, pinned: Boolean = false) =
        ChatMessage(id, "c", "s", text = id, at = at, pinned = pinned)

    private fun store(): Pair<ChatStore, (LinkFrame) -> Unit> {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-sync").toString())
        val link = PhoneLink(object : Discovery {
            override fun start() {}
            override fun refresh() {}
            override fun candidates() = emptyList<Discovery.Candidate>()
            override fun stop() {}
        })
        val store = ChatStore(link)
        return store to link.frameListener!!
    }

    @Test
    fun pinnedListFromPhoneFixesLocalFlags() {
        val (store, deliver) = store()
        store.open("c")
        deliver(LinkFrame.Messages("c", listOf(msg("m1", 10), msg("m2", 20, pinned = true)), LinkFrame.MODE_LATEST, more = false))
        deliver(LinkFrame.PinnedList("c", listOf(msg("m1", 10, pinned = true))))
        assertEquals(listOf("m1"), store.pinned.value?.items?.map { it.id })
        assertEquals(listOf("m1"), store.messages.value.filter { it.pinned }.map { it.id })
    }

    @Test
    fun changesNeedAConnectedPhone() {
        val (store, deliver) = store()
        store.open("c")
        val m = msg("m1", 10)
        deliver(LinkFrame.Messages("c", listOf(m), LinkFrame.MODE_LATEST, more = false))
        assertFalse(store.setPinned(m, true))
        assertFalse(store.edit(m.copy(mine = true), "nowy"))
        assertFalse(store.messages.value.single().pinned)
        assertEquals("m1", store.messages.value.single().text)
    }

    @Test
    fun onlyDeliveredOwnTextCanBeEdited() {
        val (store, _) = store()
        val own = msg("m1", 10).copy(mine = true, status = ChatMessage.STATUS_DELIVERED)
        assertTrue(store.canEdit(own, isGroup = false))
        assertFalse(store.canEdit(own, isGroup = true))
        assertFalse(store.canEdit(own.copy(mine = false), isGroup = false))
        assertFalse(store.canEdit(own.copy(status = ChatMessage.STATUS_PENDING), isGroup = false))
        assertFalse(store.canEdit(own.copy(kind = ChatMessage.KIND_PHOTO), isGroup = false))
        assertFalse(store.canChange(own.copy(kind = ChatMessage.KIND_SYSTEM)))
    }

    @Test
    fun contactInfoIsKeptPerConversation() {
        val (store, deliver) = store()
        deliver(LinkFrame.ContactInfo("c", "peer", "Ania", nick = "Anka", muteUntil = LinkFrame.MUTE_FOREVER))
        assertEquals("Anka", store.contact.value?.nick)
        store.requestContact("other")
        assertEquals(null, store.contact.value)
    }

    @Test
    fun greetingUsesTheFirstNameAndEveryLanguageHasTemplates() {
        for (ps in listOf(ProfileStrings.PL, ProfileStrings.EN, ProfileStrings.CS, ProfileStrings.UK, ProfileStrings.DE, ProfileStrings.ZH)) {
            assertEquals(20, ps.greetings.size)
            assertTrue(ps.greetings.values.all { "%s" in it })
        }
        val text = Greeting.pick(ProfileStrings.PL.greetings, "Błażej", LocalDateTime.of(2026, 11, 9, 8, 0), Random(1))
        assertNotNull(text)
        assertTrue("Błażej" in text)
        assertFalse("%s" in text)
    }
}
