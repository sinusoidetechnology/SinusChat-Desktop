package pl.sinusoide.sinuschat.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.Structure
import com.sun.jna.WString
import pl.sinusoide.sinuschat.desktop.net.FirewallCheck
import pl.sinusoide.sinuschat.desktop.net.WindowsDnsSd
import pl.sinusoide.sinuschat.link.LinkProtocol
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionTest {

    @Test
    fun manualAddressParsing() {
        assertEquals(ManualAddress("192.168.1.23", LinkProtocol.DEFAULT_PORT), ManualAddress.parse("192.168.1.23"))
        assertEquals(ManualAddress("192.168.1.23", 5000), ManualAddress.parse(" 192.168.1.23:5000 "))
        assertEquals(ManualAddress("pixel.local", 47321), ManualAddress.parse("pixel.local:47321"))
        assertNull(ManualAddress.parse("192.168.1.300"))
        assertNull(ManualAddress.parse("192.168.1.2:99999"))
        assertNull(ManualAddress.parse("abc def"))
        assertNull(ManualAddress.parse(""))
        assertEquals("192.168.1.23", ManualAddress("192.168.1.23", LinkProtocol.DEFAULT_PORT).toString())
        assertEquals("192.168.1.23:5000", ManualAddress("192.168.1.23", 5000).toString())
    }

    @Test
    fun firewallOutputParsing() {
        val r = assertNotNull(FirewallCheck.parse("WARNING: something\r\nB=2;P=1;O=3\r\n"))
        assertTrue(r.blocked)
        assertTrue(r.publicNetwork)
        val off = assertNotNull(FirewallCheck.parse("B=1;P=0;O=0"))
        assertEquals(false, off.blocked)
        assertNull(FirewallCheck.parse("garbage"))
        assertTrue(FirewallCheck.fixScript("C:\\Program Files\\Sinus'Chat\\SinusChat.exe").contains("'C:\\Program Files\\Sinus''Chat\\SinusChat.exe'"))
    }

    @Test
    fun firewallCheckRunsReadOnly() {
        if (!FirewallCheck.isWindows) return
        assertNotNull(FirewallCheck.check())
    }

    interface RegisterApi : Library {
        fun DnsServiceConstructInstance(
            name: WString, host: WString, ip4: Pointer?, ip6: Pointer?, port: Short, priority: Short, weight: Short,
            count: Int, keys: StringArray, values: StringArray
        ): Pointer?
        fun DnsServiceRegister(request: RegisterRequest, cancel: WindowsDnsSd.CancelHandle?): Int
        fun DnsServiceDeRegister(request: RegisterRequest, cancel: WindowsDnsSd.CancelHandle?): Int
        fun DnsServiceFreeInstance(instance: Pointer)
    }

    fun interface RegisterCallback : Callback {
        fun invoke(status: Int, context: Pointer?, instance: Pointer?)
    }

    @Structure.FieldOrder("Version", "InterfaceIndex", "pServiceInstance", "pRegisterCompletionCallback",
        "pQueryContext", "hCredentials", "unicastEnabled")
    class RegisterRequest : Structure() {
        @JvmField var Version: Int = 1
        @JvmField var InterfaceIndex: Int = 0
        @JvmField var pServiceInstance: Pointer? = null
        @JvmField var pRegisterCompletionCallback: RegisterCallback? = null
        @JvmField var pQueryContext: Pointer? = null
        @JvmField var hCredentials: Pointer? = null
        @JvmField var unicastEnabled: Int = 0
    }

    @Test
    fun windowsDnsSdFindsRegisteredService() {
        if (!FirewallCheck.isWindows) return
        val api = runCatching { Native.load("dnsapi", RegisterApi::class.java) }.getOrNull() ?: return
        val type = "_sctest._tcp.local"
        val instanceName = "SinusChatTest${System.nanoTime() % 100000}.$type"
        val host = (System.getenv("COMPUTERNAME") ?: "localhost") + ".local"
        val ip4 = Memory(4).apply { write(0, InetAddress.getByName("127.0.0.1").address, 0, 4) }
        val instance = api.DnsServiceConstructInstance(
            WString(instanceName), WString(host), ip4, null, 47999.toShort(), 0, 0, 1,
            StringArray(arrayOf(LinkProtocol.TXT_ID), true), StringArray(arrayOf("abc123"), true)
        ) ?: return
        val done = java.util.concurrent.CountDownLatch(1)
        val callback = RegisterCallback { _, _, _ -> done.countDown() }
        val request = RegisterRequest().apply {
            pServiceInstance = instance
            pRegisterCompletionCallback = callback
        }
        val started = api.DnsServiceRegister(request, null)
        if (started != 9506) {
            api.DnsServiceFreeInstance(instance)
            return
        }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        val browser = WindowsDnsSd(type)
        try {
            browser.start()
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end && browser.candidates().none { it.beacon == "abc123" }) Thread.sleep(100)
            val found = browser.candidates().firstOrNull { it.beacon == "abc123" }
            assertNotNull(found, "systemowe mDNS nie znalazło usługi testowej")
            assertEquals(47999, found.port)
        } finally {
            browser.stop()
            api.DnsServiceDeRegister(request, null)
            Thread.sleep(300)
            api.DnsServiceFreeInstance(instance)
        }
    }

    @Test
    fun manualTargetConnectsWithoutDiscovery() {
        System.setProperty("sinuschat.dir", java.nio.file.Files.createTempDirectory("sinuschat-manual").toString())
        SecureStore.delete("device.bin")
        val phone = PhoneLinkFlowTest.FakePhone()
        val link = PhoneLink(object : Discovery {
            override fun start() {}
            override fun refresh() {}
            override fun candidates() = emptyList<Discovery.Candidate>()
            override fun stop() {}
        })
        try {
            link.start()
            val pairing = link.state.value as LinkState.Pairing
            phone.pending = assertNotNull(pl.sinusoide.sinuschat.link.PairingCode.decode(pairing.code))

            val closed = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            link.setManualAddress(ManualAddress("127.0.0.1", closed))
            waitFor { link.manualResult.value == ManualResult.REFUSED }

            link.setManualAddress(ManualAddress("127.0.0.1", phone.server.localPort))
            waitFor { link.state.value is LinkState.Connected }
            assertEquals(ManualResult.NONE, link.manualResult.value)

            phone.server.close()
            phone.connection!!.close(pl.sinusoide.sinuschat.link.LinkFrame.BYE_PAUSED)
            waitFor { link.notice.value == Notice.PHONE_PAUSED }
            waitFor { link.state.value is LinkState.Searching }
        } finally {
            link.stop()
            phone.server.close()
            SecureStore.delete("device.bin")
        }
    }

    private fun waitFor(timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(50)
        }
        throw AssertionError("timeout")
    }
}
