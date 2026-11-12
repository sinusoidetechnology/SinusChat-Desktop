package pl.sinusoide.sinuschat.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.sinusoide.sinuschat.link.LinkBeacon
import pl.sinusoide.sinuschat.link.LinkChannel
import pl.sinusoide.sinuschat.link.LinkConnection
import pl.sinusoide.sinuschat.link.LinkFrame
import pl.sinusoide.sinuschat.link.LinkHandshake
import pl.sinusoide.sinuschat.link.LinkIdentity
import pl.sinusoide.sinuschat.link.LinkMode
import pl.sinusoide.sinuschat.link.LinkRejected
import pl.sinusoide.sinuschat.link.PairingCode
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

sealed interface LinkState {
    data class Pairing(val code: String, val phonesNearby: Int) : LinkState
    data class Searching(val phoneName: String, val rejected: Boolean) : LinkState
    data class Connected(val phoneName: String) : LinkState
}

data class SetupProgress(
    val welcomed: Boolean,
    val languageChosen: Boolean,
    val done: Boolean,
    val language: String?
)

enum class Notice { PHONE_UNPAIRED, PHONE_PAUSED }

enum class ManualResult { NONE, TRYING, TIMEOUT, REFUSED, UNREACHABLE, REJECTED }

data class ManualAddress(val host: String, val port: Int) {
    override fun toString(): String = if (port == pl.sinusoide.sinuschat.link.LinkProtocol.DEFAULT_PORT) host else "$host:$port"

    companion object {
        fun parse(text: String): ManualAddress? {
            val t = text.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            if (t.isEmpty() || t.any { it.isWhitespace() }) return null
            val colon = t.lastIndexOf(':')
            val host = if (colon > 0) t.substring(0, colon) else t
            val port = if (colon > 0) t.substring(colon + 1).toIntOrNull() ?: return null
            else pl.sinusoide.sinuschat.link.LinkProtocol.DEFAULT_PORT
            if (port !in 1..65535) return null
            val parts = host.split('.')
            val ipv4 = parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true && p.isNotEmpty() }
            val name = host.all { it.isLetterOrDigit() || it == '-' || it == '.' } && host.any { it.isLetter() }
            if (!ipv4 && !name) return null
            return ManualAddress(host, port)
        }
    }
}

class PhoneLink(
    private val discovery: Discovery = pl.sinusoide.sinuschat.desktop.net.CompositeDiscovery(
        listOf(PhoneDiscovery(), pl.sinusoide.sinuschat.desktop.net.WindowsDnsSd())
    )
) {

    companion object {
        val APP_NAME: String = "SinusChat Desktop " + AppVersion.name
        const val DEFAULT_THEME = "default"
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val LOOP_MS = 2_000L
        @Volatile private var active: PhoneLink? = null
    }

    private val _state = MutableStateFlow<LinkState>(LinkState.Searching("", false))
    val state: StateFlow<LinkState> = _state

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice

    @Volatile private var device: DeviceState = DeviceState.load()
    private val deviceLock = Any()
    private val identity: LinkIdentity = device.identity()
    private val wake = LinkedBlockingQueue<Unit>()

    private fun progressOf(d: DeviceState) = SetupProgress(d.welcomed, d.languageChosen, d.setupDone, d.language)

    private val _setup = MutableStateFlow(progressOf(device))
    val setup: StateFlow<SetupProgress> = _setup

    private fun updateDevice(change: (DeviceState) -> DeviceState) {
        synchronized(deviceLock) {
            val next = change(device)
            if (next == device) return
            device = next
            next.save()
            _setup.value = progressOf(next)
        }
    }

    fun markWelcomed() = updateDevice { it.copy(welcomed = true) }

    fun backToWelcome() = updateDevice { it.copy(welcomed = false) }

    fun setLanguage(code: String?) = updateDevice { it.copy(language = code) }

    private val _theme = MutableStateFlow(device.theme ?: DEFAULT_THEME)
    val theme: StateFlow<String> = _theme

    fun setTheme(key: String) {
        updateDevice { it.copy(theme = key) }
        _theme.value = key
    }

    val savedWindow: WindowBounds? get() = device.window

    fun saveWindow(bounds: WindowBounds) = updateDevice { it.copy(window = bounds) }

    private val _notify = MutableStateFlow(device.notify)
    val notifyPrefs: StateFlow<NotifyPrefs> = _notify

    fun updateNotify(change: (NotifyPrefs) -> NotifyPrefs) {
        val next = change(_notify.value)
        updateDevice { it.copy(notify = next) }
        _notify.value = next
    }

    private val _proximitySetup = kotlinx.coroutines.flow.MutableSharedFlow<LinkFrame.ProximitySetup>(extraBufferCapacity = 16)
    val proximitySetup: kotlinx.coroutines.flow.SharedFlow<LinkFrame.ProximitySetup> = _proximitySetup

    @Volatile private var proximityWanted = false
    private val _proximityState = MutableStateFlow<String?>(null)
    val proximityState: StateFlow<String?> = _proximityState

    fun proximityKey(): ByteArray? = device.phone?.let {
        runCatching { pl.sinusoide.sinuschat.link.LinkProximity.key(identity, it.keyBytes) }.getOrNull()
    }

    fun setProximity(on: Boolean) {
        if (proximityWanted == on && connection != null) return
        proximityWanted = on
        if (!on) _proximityState.value = null
        send(LinkFrame.Proximity(on))
    }

    private val _safetyPrompts = MutableStateFlow(device.safetyPrompts)
    val safetyPrompts: StateFlow<Boolean> = _safetyPrompts

    fun setSafetyPrompts(on: Boolean) {
        updateDevice { it.copy(safetyPrompts = on) }
        _safetyPrompts.value = on
    }

    val pairedAt: Long get() = device.phone?.pairedAt ?: 0L

    data class Profile(val name: String, val color: String, val avatar: String)

    private fun profileOf(d: DeviceState): Profile? =
        d.phone?.takeIf { it.userName.isNotBlank() }?.let { Profile(it.userName, it.userColor, it.userAvatar) }

    private val _profile = MutableStateFlow(profileOf(device))
    val profile: StateFlow<Profile?> = _profile

    @Volatile private var adoptTheme = false

    private fun onProfile(f: LinkFrame.Profile) {
        val name = f.name.filter { !it.isISOControl() }.trim().take(64)
        val color = f.color.trim().takeIf { Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$").matches(it) }.orEmpty()
        val avatar = f.avatar.filter { it.isLetterOrDigit() || it == '-' }.take(32)
        updateDevice { d -> d.phone?.let { d.copy(phone = it.copy(userName = name, userColor = color, userAvatar = avatar)) } ?: d }
        _profile.value = profileOf(device)
        if (adoptTheme) {
            adoptTheme = false
            if (f.theme in pl.sinusoide.sinuschat.desktop.ui.Themes.ORDER && f.theme != _theme.value) setTheme(f.theme)
        }
    }

    fun markLanguageChosen() = updateDevice { it.copy(languageChosen = true) }

    fun backToLanguage() = updateDevice { it.copy(languageChosen = false) }

    fun finishSetup() = updateDevice { it.copy(welcomed = true, languageChosen = true, setupDone = true) }

    private val _manual = MutableStateFlow(device.manual())
    val manual: StateFlow<ManualAddress?> = _manual

    private val _manualResult = MutableStateFlow(ManualResult.NONE)
    val manualResult: StateFlow<ManualResult> = _manualResult

    private val _searchingSince = MutableStateFlow(System.currentTimeMillis())
    val searchingSince: StateFlow<Long> = _searchingSince

    fun setManualAddress(address: ManualAddress?) {
        updateDevice { it.copy(manualHost = address?.host, manualPort = address?.port ?: 0) }
        _manual.value = address
        _manualResult.value = if (address == null) ManualResult.NONE else ManualResult.TRYING
        wake.offer(Unit)
    }

    fun phonesNearby(): Int = discovery.candidates().size

    @Volatile private var pairing: PairingCode? = null
    @Volatile private var connection: LinkConnection? = null
    @Volatile private var running = false
    @Volatile private var rejected = false
    @Volatile private var userActive = false
    @Volatile private var activitySentAt = 0L

    fun setActive(active: Boolean) {
        if (userActive == active) return
        userActive = active
        sendActivity()
    }

    private fun sendActivity() {
        val c = connection ?: return
        if (c.send(LinkFrame.Activity(userActive))) activitySentAt = System.currentTimeMillis()
    }

    val computerId: String get() = identity.id

    fun start() {
        if (running) return
        synchronized(PhoneLink::class.java) {
            active?.takeIf { it !== this }?.stop()
            active = this
        }
        running = true
        discovery.start()
        val phone = device.phone
        if (phone == null) beginPairing() else _state.value = LinkState.Searching(phone.name, false)
        thread(isDaemon = true, name = "link-connect") { connectLoop() }
    }

    fun stop() {
        running = false
        synchronized(PhoneLink::class.java) { if (active === this) active = null }
        connection?.close(LinkFrame.BYE_CLOSING)
        discovery.stop()
        wake.offer(Unit)
    }

    fun unpair() {
        val c = connection
        forgetPhone(null)
        c?.close(LinkFrame.BYE_UNPAIRED)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    @Volatile var frameListener: ((LinkFrame) -> Unit)? = null
    @Volatile var onLinkUp: (() -> Unit)? = null
    @Volatile var onForgotten: (() -> Unit)? = null
    @Volatile var onUnpaired: (() -> Unit)? = null
    @Volatile var onLinkDown: (() -> Unit)? = null

    fun send(frame: LinkFrame): Boolean = connection?.send(frame) ?: false

    val isConnected: Boolean get() = connection?.isOpen == true

    private fun computerName(): String =
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() } ?: "Windows"

    private fun beginPairing() {
        val code = PairingCode.create(identity.publicBytes)
        pairing = code
        rejected = false
        _searchingSince.value = System.currentTimeMillis()
        _state.value = LinkState.Pairing(code.encode(), discovery.candidates().size)
    }

    private fun forgetPhone(message: Notice?) {
        updateDevice { it.copy(phone = null, setupDone = false) }
        _profile.value = null
        connection = null
        onForgotten?.invoke()
        onUnpaired?.invoke()
        if (message != null) _notice.value = message
        beginPairing()
        wake.offer(Unit)
    }

    private fun connectLoop() {
        var round = 0
        while (running) {
            if (connection?.isOpen != true) {
                if (round % 5 == 0) discovery.refresh()
                tryConnect()
                val s = _state.value
                if (s is LinkState.Pairing) _state.value = s.copy(phonesNearby = discovery.candidates().size)
            } else if (userActive && System.currentTimeMillis() - activitySentAt >= pl.sinusoide.sinuschat.link.LinkProtocol.ACTIVITY_REPEAT_MS) {
                sendActivity()
            }
            round++
            wake.poll(LOOP_MS, TimeUnit.MILLISECONDS)
            wake.clear()
        }
    }

    private data class Target(val host: String, val port: Int, val manual: Boolean)

    private fun targets(): List<Target> {
        val phone = device.phone
        val found = discovery.candidates()
        val out = mutableListOf<Target>()
        _manual.value?.let { out += Target(it.host, it.port, true) }
        if (phone == null) {
            found.forEach { out += Target(it.host, it.port, false) }
        } else {
            found.filter { LinkBeacon.matches(identity, phone.keyBytes, it.beacon) }.forEach { out += Target(it.host, it.port, false) }
            if (phone.host != null && phone.port > 0) out += Target(phone.host, phone.port, false)
        }
        pl.sinusoide.sinuschat.desktop.net.Gateways.ipv4().forEach { out += Target(it, pl.sinusoide.sinuschat.link.LinkProtocol.DEFAULT_PORT, false) }
        return out.distinctBy { it.host to it.port }
    }

    private fun tryConnect() {
        for (target in targets()) {
            if (!running || connection?.isOpen == true) return
            val phone = device.phone
            val code = pairing
            val mode = when {
                phone != null -> LinkMode.Resume(phone.keyBytes)
                code != null -> LinkMode.Pair(code.token)
                else -> return
            }
            val socket = Socket()
            val channel = try {
                socket.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
                socket.tcpNoDelay = true
                LinkHandshake.connect(socket, identity, mode)
            } catch (e: LinkRejected) {
                runCatching { socket.close() }
                if (target.manual) _manualResult.value = ManualResult.REJECTED
                if (mode is LinkMode.Resume && e.code == LinkHandshake.REJECT_UNKNOWN_DEVICE && !rejected) {
                    rejected = true
                    _state.value = LinkState.Searching(phone?.name ?: "", true)
                }
                null
            } catch (e: Exception) {
                runCatching { socket.close() }
                if (target.manual) _manualResult.value = classify(e)
                null
            } ?: continue
            try {
                onLinked(socket, channel, target.host, target.port)
            } catch (e: Exception) {
                System.err.println("SinusChat link: linked: $e")
                runCatching { socket.close() }
                continue
            }
            return
        }
    }

    private fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            System.err.println("SinusChat link: $what: $e")
        }
    }

    private fun classify(e: Exception): ManualResult = when (e) {
        is java.net.SocketTimeoutException -> ManualResult.TIMEOUT
        is java.net.ConnectException ->
            if (e.message.orEmpty().contains("timed out", true)) ManualResult.TIMEOUT else ManualResult.REFUSED
        is java.net.NoRouteToHostException, is java.net.UnknownHostException -> ManualResult.UNREACHABLE
        else -> ManualResult.UNREACHABLE
    }

    private fun onLinked(socket: Socket, channel: LinkChannel, host: String, port: Int) {
        val current = device.phone
        val phone = if (channel.mode == LinkHandshake.MODE_PAIR || current == null) {
            PairedPhone(
                key = Base64.getEncoder().encodeToString(channel.peerKey),
                name = "Telefon",
                host = host,
                port = port,
                pairedAt = System.currentTimeMillis()
            )
        } else {
            current.copy(host = host, port = port)
        }
        pairing = null
        rejected = false
        adoptTheme = channel.mode == LinkHandshake.MODE_PAIR
        updateDevice { it.copy(phone = phone) }
        _manualResult.value = ManualResult.NONE
        val conn = LinkConnection(socket, channel, listener)
        connection = conn
        _notice.value = null
        _state.value = LinkState.Connected(phone.name)
        conn.start()
        safely("hello") { conn.send(LinkFrame.Hello(computerName(), APP_NAME)) }
        safely("proximity") { conn.send(LinkFrame.Proximity(proximityWanted)) }
        safely("activity") { if (userActive) sendActivity() }
        safely("link up") { onLinkUp?.invoke() }
    }

    private val listener = object : LinkConnection.Listener {
        override fun onFrame(connection: LinkConnection, frame: LinkFrame) {
            when (frame) {
                is LinkFrame.Hello -> {
                    val phone = device.phone ?: return
                    val name = frame.name.trim().take(64).ifEmpty { phone.name }
                    updateDevice { d -> d.phone?.let { d.copy(phone = it.copy(name = name)) } ?: d }
                    _state.value = LinkState.Connected(name)
                }
                is LinkFrame.ProximityState -> _proximityState.value = frame.state
                is LinkFrame.ProximitySetup -> _proximitySetup.tryEmit(frame)
                is LinkFrame.Profile -> {
                    onProfile(frame)
                    frameListener?.invoke(frame)
                }
                is LinkFrame.Bye -> {
                    when (frame.reason) {
                        LinkFrame.BYE_UNPAIRED -> forgetPhone(Notice.PHONE_UNPAIRED)
                        LinkFrame.BYE_PAUSED -> _notice.value = Notice.PHONE_PAUSED
                    }
                    connection.close()
                }
                else -> frameListener?.invoke(frame)
            }
        }

        override fun onClosed(connection: LinkConnection, error: Throwable?) {
            if (error != null) {
                System.err.println("SinusChat link: closed: $error")
                error.stackTrace.take(8).forEach { System.err.println("    at $it") }
            }
            if (this@PhoneLink.connection !== connection) return
            this@PhoneLink.connection = null
            onLinkDown?.invoke()
            _searchingSince.value = System.currentTimeMillis()
            val phone = device.phone ?: return
            _state.value = LinkState.Searching(phone.name, false)
            wake.offer(Unit)
        }
    }
}
