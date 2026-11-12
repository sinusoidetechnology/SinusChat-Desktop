package pl.sinusoide.sinuschat.desktop

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import pl.sinusoide.sinuschat.desktop.chat.ChatEvent
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.mergeLatest
import pl.sinusoide.sinuschat.desktop.chat.mergeOlder
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.LinkFrame
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatStoreTest {

    private fun msg(id: String, at: Long) = ChatMessage(id, "c", "s", text = id, at = at)

    @Test
    fun latestWindowReplacesItsRangeAndKeepsOlderCache() {
        val cached = listOf(msg("a", 1), msg("b", 2), msg("c", 3), msg("deleted", 4))
        val window = listOf(msg("c", 3), msg("d", 5))
        assertEquals(listOf("a", "b", "c", "d"), mergeLatest(cached, window, more = true).map { it.id })
    }

    @Test
    fun fullHistoryReplacesEverything() {
        val cached = listOf(msg("old", 1), msg("x", 2))
        assertEquals(listOf("x"), mergeLatest(cached, listOf(msg("x", 2)), more = false).map { it.id })
        assertEquals(emptyList(), mergeLatest(cached, emptyList(), more = false))
    }

    @Test
    fun olderPagePrependsWithoutDuplicates() {
        val cached = listOf(msg("c", 3), msg("d", 4))
        val older = listOf(msg("a", 1), msg("b", 2), msg("c", 3))
        assertEquals(listOf("a", "b", "c", "d"), mergeOlder(cached, older).map { it.id })
    }

    @Test
    fun framesDriveStateAndEvents() = runBlocking {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-store").toString())
        val link = PhoneLink(object : Discovery {
            override fun start() {}
            override fun refresh() {}
            override fun candidates() = emptyList<Discovery.Candidate>()
            override fun stop() {}
        })
        val store = ChatStore(link)
        val deliver = link.frameListener!!

        store.open("c")
        deliver(LinkFrame.Messages("c", listOf(msg("m1", 10), msg("m2", 20)), LinkFrame.MODE_LATEST, more = true))
        assertEquals(listOf("m1", "m2"), store.messages.value.map { it.id })
        assertTrue(store.hasMore.value)

        deliver(LinkFrame.Messages("other", listOf(msg("x", 1)), LinkFrame.MODE_LATEST, more = false))
        assertEquals(listOf("m1", "m2"), store.messages.value.map { it.id })

        deliver(LinkFrame.PhoneState(true))
        assertTrue(store.phoneLocked.value)

        deliver(LinkFrame.TypingIn("c", "Ania", true))
        assertEquals("Ania", store.typing.value["c"])
        deliver(LinkFrame.Incoming("c", "m3", "Ania", "Ania", "hej", at = 30))
        assertFalse("c" in store.typing.value)

        assertTrue(store.send("hej", null))
        val queued = store.messages.value.last()
        assertEquals("hej", queued.text)
        assertTrue(queued.mine)
        assertEquals(ChatMessage.STATUS_PENDING, queued.status)
        deliver(LinkFrame.Messages("c", listOf(msg("m1", 10), msg("m2", 20)), LinkFrame.MODE_LATEST, more = false))
        assertEquals(queued.id, store.messages.value.last().id)
        val confirmed = queued.copy(status = ChatMessage.STATUS_SENT)
        deliver(LinkFrame.Messages("c", listOf(msg("m1", 10), msg("m2", 20), confirmed), LinkFrame.MODE_LATEST, more = false))
        assertEquals(1, store.messages.value.count { it.id == queued.id })
        assertEquals(ChatMessage.STATUS_SENT, store.messages.value.last().status)
    }
}
