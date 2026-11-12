package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.ui.SetupStep
import pl.sinusoide.sinuschat.link.LinkAuthorization
import pl.sinusoide.sinuschat.link.LinkBeacon
import pl.sinusoide.sinuschat.link.LinkConnection
import pl.sinusoide.sinuschat.link.LinkCrypto
import pl.sinusoide.sinuschat.link.LinkFrame
import pl.sinusoide.sinuschat.link.LinkHandshake
import pl.sinusoide.sinuschat.link.LinkIdentity
import pl.sinusoide.sinuschat.link.PairingCode
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneLinkFlowTest {

    class FakePhone {
        val identity = LinkIdentity.generate()
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        @Volatile var pending: PairingCode? = null
        val known = CopyOnWriteArrayList<ByteArray>()
        val received = CopyOnWriteArrayList<LinkFrame>()
        @Volatile var connection: LinkConnection? = null
        @Volatile var handler: ((LinkConnection, LinkFrame) -> Unit)? = null

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching {
                        val ch = LinkHandshake.accept(s, identity, { listOfNotNull(pending?.token) }) { mode, key ->
                            val p = pending
                            when {
                                mode == LinkHandshake.MODE_PAIR && p != null && LinkCrypto.equal(p.pcKey, key) ->
                                    LinkAuthorization.Accept(p.token)
                                mode == LinkHandshake.MODE_RESUME && known.any { LinkCrypto.equal(it, key) } ->
                                    LinkAuthorization.Accept(ByteArray(0))
                                else -> LinkAuthorization.Reject(LinkHandshake.REJECT_UNKNOWN_DEVICE)
                            }
                        }
                        if (ch.mode == LinkHandshake.MODE_PAIR) { known += ch.peerKey; pending = null }
                        val c = LinkConnection(s, ch, object : LinkConnection.Listener {
                            override fun onFrame(connection: LinkConnection, frame: LinkFrame) { received += frame; handler?.invoke(connection, frame) }
                            override fun onClosed(connection: LinkConnection, error: Throwable?) {}
                        })
                        connection = c
                        c.start()
                        c.send(LinkFrame.Hello("Testowy telefon", "test"))
                    }.onFailure { runCatching { s.close() } }
                }
            }
        }
    }

    private class FakeDiscovery(private val phone: FakePhone) : Discovery {
        override fun start() {}
        override fun refresh() {}
        override fun stop() {}
        override fun candidates() =
            listOf(Discovery.Candidate(LinkBeacon.create(phone.identity, phone.known.toList()), "127.0.0.1", phone.server.localPort, System.currentTimeMillis()))
    }

    private fun waitFor(timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(50)
        }
        throw AssertionError("timeout")
    }

    @Test
    fun pairResumeAndUnpair() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-test").toString())
        SecureStore.delete("device.bin")
        val phone = FakePhone()

        val first = PhoneLink(FakeDiscovery(phone))
        first.start()
        assertEquals(SetupStep.WELCOME, setupStep(first.setup.value, first.state.value))
        first.markWelcomed()
        assertEquals(SetupStep.LANGUAGE, setupStep(first.setup.value, first.state.value))
        first.setLanguage("de")
        first.markLanguageChosen()
        assertEquals(SetupStep.PAIR, setupStep(first.setup.value, first.state.value))
        val pairing = first.state.value as LinkState.Pairing
        phone.pending = assertNotNull(PairingCode.decode(pairing.code))

        waitFor { (first.state.value as? LinkState.Connected)?.phoneName == "Testowy telefon" }
        waitFor { phone.received.any { it is LinkFrame.Hello } }
        val hello = phone.received.first { it is LinkFrame.Hello } as LinkFrame.Hello
        assertEquals(PhoneLink.APP_NAME, hello.app)
        assertEquals(LinkCrypto.fingerprint(phone.identity.publicBytes), DeviceState.load().phone?.id)
        first.stop()

        val second = PhoneLink(FakeDiscovery(phone))
        second.setActive(true)
        second.start()
        assertTrue(second.state.value is LinkState.Searching)
        waitFor { second.state.value is LinkState.Connected }
        waitFor { phone.received.any { it == LinkFrame.Activity(true) } }
        waitFor(15_000) { phone.received.count { it == LinkFrame.Activity(true) } >= 2 }
        second.setActive(false)
        waitFor { phone.received.lastOrNull { it is LinkFrame.Activity } == LinkFrame.Activity(false) }
        assertEquals(SetupStep.DONE, setupStep(second.setup.value, second.state.value))
        second.finishSetup()
        assertTrue(DeviceState.load().setupDone)

        phone.connection!!.close(LinkFrame.BYE_UNPAIRED)
        waitFor { second.state.value is LinkState.Pairing }
        assertNotNull(second.notice.value)
        assertEquals(SetupProgress(welcomed = true, languageChosen = true, done = false, language = "de"), second.setup.value)
        assertEquals(Notice.PHONE_UNPAIRED, second.notice.value)
        assertEquals(SetupStep.PAIR, setupStep(second.setup.value, second.state.value))
        assertNull(DeviceState.load().phone)
        second.stop()
        phone.server.close()
    }
}
