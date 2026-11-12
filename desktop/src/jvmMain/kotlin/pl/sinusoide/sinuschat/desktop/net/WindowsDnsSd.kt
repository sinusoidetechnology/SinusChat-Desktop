package pl.sinusoide.sinuschat.desktop.net

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import pl.sinusoide.sinuschat.desktop.Discovery
import pl.sinusoide.sinuschat.link.LinkProtocol
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class WindowsDnsSd(private val serviceType: String = LinkProtocol.SERVICE_TYPE + ".local") : Discovery {

    interface DnsApi : Library {
        fun DnsServiceBrowse(request: BrowseRequest, cancel: CancelHandle): Int
        fun DnsServiceBrowseCancel(cancel: CancelHandle): Int
        fun DnsServiceResolve(request: ResolveRequest, cancel: CancelHandle): Int
        fun DnsServiceResolveCancel(cancel: CancelHandle): Int
        fun DnsServiceFreeInstance(instance: Pointer)
        fun DnsRecordListFree(list: Pointer, freeType: Int)
    }

    fun interface BrowseCallback : Callback {
        fun invoke(status: Int, context: Pointer?, records: Pointer?)
    }

    fun interface ResolveCallback : Callback {
        fun invoke(status: Int, context: Pointer?, instance: Pointer?)
    }

    @Structure.FieldOrder("reserved")
    class CancelHandle : Structure() {
        @JvmField var reserved: Pointer? = null
    }

    @Structure.FieldOrder("Version", "InterfaceIndex", "QueryName", "pBrowseCallback", "pQueryContext")
    class BrowseRequest : Structure() {
        @JvmField var Version: Int = 1
        @JvmField var InterfaceIndex: Int = 0
        @JvmField var QueryName: WString? = null
        @JvmField var pBrowseCallback: BrowseCallback? = null
        @JvmField var pQueryContext: Pointer? = null
    }

    @Structure.FieldOrder("Version", "InterfaceIndex", "QueryName", "pResolveCompletionCallback", "pQueryContext")
    class ResolveRequest : Structure() {
        @JvmField var Version: Int = 1
        @JvmField var InterfaceIndex: Int = 0
        @JvmField var QueryName: WString? = null
        @JvmField var pResolveCompletionCallback: ResolveCallback? = null
        @JvmField var pQueryContext: Pointer? = null
    }

    @Structure.FieldOrder(
        "pszInstanceName", "pszHostName", "ip4Address", "ip6Address", "wPort", "wPriority", "wWeight",
        "dwPropertyCount", "keys", "values", "dwInterfaceIndex"
    )
    class ServiceInstance(p: Pointer) : Structure(p) {
        @JvmField var pszInstanceName: Pointer? = null
        @JvmField var pszHostName: Pointer? = null
        @JvmField var ip4Address: Pointer? = null
        @JvmField var ip6Address: Pointer? = null
        @JvmField var wPort: Short = 0
        @JvmField var wPriority: Short = 0
        @JvmField var wWeight: Short = 0
        @JvmField var dwPropertyCount: Int = 0
        @JvmField var keys: Pointer? = null
        @JvmField var values: Pointer? = null
        @JvmField var dwInterfaceIndex: Int = 0

        init { read() }
    }

    private class Pending(val request: Any, val callback: Any, val cancel: CancelHandle)

    private val api: DnsApi? by lazy {
        if (!System.getProperty("os.name", "").startsWith("Windows")) null
        else runCatching { Native.load("dnsapi", DnsApi::class.java) }.getOrNull()
    }

    private val found = ConcurrentHashMap<String, Discovery.Candidate>()
    private val resolving = ConcurrentHashMap<String, Pending>()
    @Volatile private var browse: Pending? = null
    @Volatile private var browseStarted = 0L

    val available: Boolean get() = api != null

    override fun start() {
        startBrowse()
    }

    override fun refresh() {
        if (System.currentTimeMillis() - browseStarted > RESTART_MS) {
            stopBrowse()
            startBrowse()
        }
    }

    override fun candidates(): List<Discovery.Candidate> = found.values.sortedByDescending { it.seenAt }

    override fun stop() {
        stopBrowse()
        for ((_, p) in resolving) runCatching { api?.DnsServiceResolveCancel(p.cancel) }
        resolving.clear()
    }

    @Synchronized
    private fun startBrowse() {
        val dns = api ?: return
        if (browse != null) return
        val cancel = CancelHandle()
        val callback = BrowseCallback { status, _, records -> onBrowse(status, records) }
        val request = BrowseRequest().apply {
            QueryName = WString(serviceType)
            pBrowseCallback = callback
        }
        val result = runCatching { dns.DnsServiceBrowse(request, cancel) }.getOrDefault(-1)
        if (result == DNS_REQUEST_PENDING) {
            browse = Pending(request, callback, cancel)
            browseStarted = System.currentTimeMillis()
        }
    }

    @Synchronized
    private fun stopBrowse() {
        val p = browse ?: return
        browse = null
        runCatching { api?.DnsServiceBrowseCancel(p.cancel) }
    }

    private fun onBrowse(status: Int, records: Pointer?) {
        val dns = api ?: return
        if (records == null) return
        try {
            if (status != 0) return
            var r: Pointer? = records
            var guard = 0
            while (r != null && guard++ < 64) {
                val type = r.getShort(TYPE_OFFSET).toInt() and 0xFFFF
                if (type == DNS_TYPE_PTR) {
                    r.getPointer(DATA_OFFSET)?.getWideString(0)?.let { resolve(it) }
                }
                r = r.getPointer(0)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { dns.DnsRecordListFree(records, DNS_FREE_RECORD_LIST) }
        }
    }

    private fun resolve(instanceName: String) {
        val dns = api ?: return
        if (resolving.containsKey(instanceName)) return
        val cancel = CancelHandle()
        val callback = ResolveCallback { status, _, instance -> onResolved(instanceName, status, instance) }
        val request = ResolveRequest().apply {
            QueryName = WString(instanceName)
            pResolveCompletionCallback = callback
        }
        resolving[instanceName] = Pending(request, callback, cancel)
        val result = runCatching { dns.DnsServiceResolve(request, cancel) }.getOrDefault(-1)
        if (result != DNS_REQUEST_PENDING) resolving.remove(instanceName)
    }

    private fun onResolved(instanceName: String, status: Int, instance: Pointer?) {
        val dns = api ?: return
        try {
            if (status != 0 || instance == null) return
            val info = ServiceInstance(instance)
            val port = info.wPort.toInt() and 0xFFFF
            val ip = info.ip4Address?.getByteArray(0, 4)?.let { InetAddress.getByAddress(it).hostAddress }
            var id: String? = null
            for (i in 0 until info.dwPropertyCount.coerceIn(0, 32)) {
                val k = info.keys?.getPointer(i.toLong() * Native.POINTER_SIZE)?.getWideString(0)
                val v = info.values?.getPointer(i.toLong() * Native.POINTER_SIZE)?.getWideString(0)
                if (k == LinkProtocol.TXT_ID) id = v
            }
            if (ip != null && port > 0 && id != null) {
                found[instanceName] = Discovery.Candidate(id, ip, port, System.currentTimeMillis())
            }
        } catch (_: Throwable) {
        } finally {
            resolving.remove(instanceName)
            if (instance != null) runCatching { dns.DnsServiceFreeInstance(instance) }
        }
    }

    companion object {
        private const val DNS_REQUEST_PENDING = 9506
        private const val DNS_TYPE_PTR = 12
        private const val DNS_FREE_RECORD_LIST = 1
        private const val RESTART_MS = 30_000L
        private val TYPE_OFFSET = 2L * Native.POINTER_SIZE
        private val DATA_OFFSET = if (Native.POINTER_SIZE == 8) 32L else 24L
    }
}
