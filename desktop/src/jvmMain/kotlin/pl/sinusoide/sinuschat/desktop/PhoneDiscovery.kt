package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.link.LinkProtocol
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

interface Discovery {
    data class Candidate(val beacon: String, val host: String, val port: Int, val seenAt: Long)

    fun start()
    fun refresh()
    fun candidates(): List<Candidate>
    fun stop()
}

class PhoneDiscovery : Discovery {

    private val serviceType = LinkProtocol.SERVICE_TYPE + ".local."
    private val found = ConcurrentHashMap<String, Discovery.Candidate>()
    @Volatile private var jmm: JmmDNS? = null

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmm?.requestServiceInfo(event.type, event.name, 3_000)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            found.remove(event.name)
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info ?: return
            val id = info.getPropertyString(LinkProtocol.TXT_ID) ?: return
            val addr = info.inet4Addresses.firstOrNull { !it.isLoopbackAddress }
                ?: info.inetAddresses.firstOrNull { it is Inet4Address || !it.isLinkLocalAddress }
                ?: return
            if (info.port <= 0) return
            found[event.name] = Discovery.Candidate(id, addr.hostAddress, info.port, System.currentTimeMillis())
        }
    }

    override fun start() {
        if (jmm != null) return
        Thread({
            runCatching {
                val instance = JmmDNS.Factory.getInstance()
                jmm = instance
                instance.addServiceListener(serviceType, listener)
            }
        }, "mdns-start").apply { isDaemon = true }.start()
    }

    override fun refresh() {
        val instance = jmm ?: return
        runCatching {
            instance.list(serviceType, 1_500).forEach { info ->
                val id = info.getPropertyString(LinkProtocol.TXT_ID) ?: return@forEach
                val addr = info.inet4Addresses.firstOrNull() ?: return@forEach
                if (info.port > 0) found[info.name] = Discovery.Candidate(id, addr.hostAddress, info.port, System.currentTimeMillis())
            }
        }
    }

    override fun candidates(): List<Discovery.Candidate> = found.values.sortedByDescending { it.seenAt }

    override fun stop() {
        val instance = jmm ?: return
        jmm = null
        Thread({ runCatching { instance.close() } }, "mdns-stop").apply { isDaemon = true }.start()
    }
}
