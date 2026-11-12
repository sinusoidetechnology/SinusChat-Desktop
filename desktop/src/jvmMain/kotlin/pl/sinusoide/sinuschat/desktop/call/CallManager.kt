package pl.sinusoide.sinuschat.desktop.call

import androidx.compose.ui.graphics.ImageBitmap
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCSdpType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.link.LinkFrame
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class CallManager(private val link: PhoneLink) {

    enum class Phase { INCOMING, CALLING, CONNECTING, CONNECTED, RECONNECTING, ENDED }

    data class CallUi(
        val id: String,
        val conv: String,
        val name: String,
        val video: Boolean,
        val incoming: Boolean,
        val phase: Phase,
        val connectedAt: Long = 0L,
        val micOn: Boolean = true,
        val cameraOn: Boolean = false,
        val remoteVideo: Boolean = false,
        val endReason: String? = null,
        val sharing: Boolean = false,
        val remoteSharing: Boolean = false
    )

    class RemoteFrame(val image: ImageBitmap, val rotation: Int)

    companion object {
        const val ERROR_OFFLINE = "offline"
        const val ERROR_NO_CAMERA = "no_camera"
        const val ERROR_SHARE = "share_failed"
        const val ERROR_SHARE_WAIT = "share_wait"
        private const val RING_MS = 50_000L
        private const val CONNECT_MS = 30_000L
        private const val RECONNECT_MS = 12_000L
        private const val ENDED_MS = 2_500L
        private const val STALL_MS = 2_500L
    }

    private val _state = MutableStateFlow<CallUi?>(null)
    val state: StateFlow<CallUi?> = _state

    private val _remote = MutableStateFlow<RemoteFrame?>(null)
    val remote: StateFlow<RemoteFrame?> = _remote

    private val _local = MutableStateFlow<ImageBitmap?>(null)
    val local: StateFlow<ImageBitmap?> = _local

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "call").apply { isDaemon = true } }
    private val tone = RingTone()
    private var engine: CallEngine? = null
    private var timeout: ScheduledFuture<*>? = null
    @Volatile private var lastRemoteFrame = 0L
    @Volatile private var remoteCamOff = false

    init {
        exec.scheduleWithFixedDelay({ watchRemote() }, 1, 1, TimeUnit.SECONDS)
    }

    private fun active(): CallUi? = _state.value?.takeIf { it.phase != Phase.ENDED }

    fun start(conv: String, name: String, video: Boolean) = exec.execute {
        if (active() != null) return@execute
        val id = UUID.randomUUID().toString()
        reset()
        _state.value = CallUi(id, conv, name, video, incoming = false, phase = Phase.CONNECTING, cameraOn = video)
        if (!link.send(LinkFrame.CallStart(id, conv, video))) {
            finish(id, ERROR_OFFLINE)
            return@execute
        }
        armTimeout(id, CONNECT_MS + RING_MS)
    }

    fun accept() = exec.execute {
        val s = active() ?: return@execute
        if (s.phase != Phase.INCOMING) return@execute
        tone.stop()
        _state.value = s.copy(phase = Phase.CONNECTING, cameraOn = s.video && CallEngine.hasCamera())
        if (!link.send(LinkFrame.CallAccept(s.id))) {
            finish(s.id, ERROR_OFFLINE)
            return@execute
        }
        armTimeout(s.id, CONNECT_MS)
    }

    fun decline() = exec.execute {
        val s = active() ?: return@execute
        if (s.phase != Phase.INCOMING) return@execute
        link.send(LinkFrame.CallEnd(s.id, LinkFrame.CALL_DECLINED))
        finish(s.id, LinkFrame.CALL_DECLINED)
    }

    fun hangup() = exec.execute {
        val s = active() ?: return@execute
        if (s.phase == Phase.INCOMING) {
            link.send(LinkFrame.CallEnd(s.id, LinkFrame.CALL_DECLINED))
            finish(s.id, LinkFrame.CALL_DECLINED)
            return@execute
        }
        val connected = s.connectedAt > 0L
        val seconds = if (connected) ((System.currentTimeMillis() - s.connectedAt) / 1000L).toInt() else 0
        link.send(LinkFrame.CallEnd(s.id, LinkFrame.CALL_HANGUP, seconds, connected))
        finish(s.id, LinkFrame.CALL_HANGUP)
    }

    fun toggleMic() = exec.execute {
        val s = active() ?: return@execute
        val on = !s.micOn
        engine?.setMic(on)
        _state.value = s.copy(micOn = on)
    }

    fun toggleCamera() = exec.execute {
        val s = active() ?: return@execute
        val e = engine ?: return@execute
        if (s.sharing) {
            stopShareNow(s, e, camera = true)
            return@execute
        }
        if (!e.hasVideo) {
            when (e.attachCamera()) {
                CallEngine.Attach.FAILED -> {
                    _notice.value = ERROR_NO_CAMERA
                    return@execute
                }
                CallEngine.Attach.NEEDS_OFFER -> reoffer(s.id, screen = false)
                CallEngine.Attach.REPLACED -> signal(s.id, "call_screen", buildJsonObject {
                    put("on", false)
                    put("camera", true)
                })
            }
            _state.value = s.copy(cameraOn = true, video = true)
            return@execute
        }
        val on = !s.cameraOn
        e.setCamera(on)
        if (!on) _local.value = null
        _state.value = s.copy(cameraOn = on)
        signal(s.id, "call_screen", buildJsonObject {
            put("on", false)
            put("camera", on)
        })
    }

    fun shareTargets(): List<pl.sinusoide.sinuschat.desktop.call.ShareTarget> = CallEngine.shareTargets()

    fun canShare(): Boolean = active()?.phase == Phase.CONNECTED

    fun startShare(target: ShareTarget) = exec.execute {
        val s = active() ?: return@execute
        val e = engine ?: return@execute
        if (s.phase != Phase.CONNECTED) {
            _notice.value = ERROR_SHARE_WAIT
            return@execute
        }
        when (e.startShare(target)) {
            CallEngine.Attach.FAILED -> {
                _notice.value = ERROR_SHARE
                return@execute
            }
            CallEngine.Attach.NEEDS_OFFER -> reoffer(s.id, screen = true)
            CallEngine.Attach.REPLACED -> signal(s.id, "call_screen", buildJsonObject { put("on", true) })
        }
        _local.value = null
        _state.value = s.copy(sharing = true, video = true)
    }

    fun stopShare() = exec.execute {
        val s = active() ?: return@execute
        val e = engine ?: return@execute
        if (s.sharing) stopShareNow(s, e, camera = s.cameraOn)
    }

    private fun stopShareNow(s: CallUi, e: CallEngine, camera: Boolean) {
        val cam = camera && e.hasVideo
        e.stopShare(cam)
        _state.value = s.copy(sharing = false, cameraOn = cam)
        signal(s.id, "call_screen", buildJsonObject {
            put("on", false)
            put("camera", cam)
        })
    }

    private fun reoffer(id: String, screen: Boolean) {
        val e = engine ?: return
        e.createOffer({ sdp ->
            signal(id, "call_reoffer", buildJsonObject {
                put("sdpType", "offer")
                put("sdpDescription", sdp)
                put("isVideo", true)
                if (screen) put("screen", true)
            })
        }, { err -> System.err.println("reoffer: $err") })
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun onLinkDown() = exec.execute {
        val s = active() ?: return@execute
        if (s.phase == Phase.INCOMING || s.phase == Phase.CONNECTING || s.phase == Phase.CALLING) finish(s.id, ERROR_OFFLINE)
    }

    fun onFrame(frame: LinkFrame): Boolean {
        when (frame) {
            is LinkFrame.CallRing -> exec.execute { onRing(frame) }
            is LinkFrame.CallSetup -> exec.execute { onSetup(frame) }
            is LinkFrame.CallSignal -> exec.execute { onSignal(frame) }
            is LinkFrame.CallEnd -> exec.execute { onEnd(frame) }
            else -> return false
        }
        return true
    }

    private fun onRing(f: LinkFrame.CallRing) {
        if (active() != null) return
        reset()
        _state.value = CallUi(f.call, f.conv, f.name, f.video, incoming = true, phase = Phase.INCOMING)
        tone.start(RingTone.Kind.INCOMING, f.ring, f.volume)
        armTimeout(f.call, RING_MS)
    }

    private fun onSetup(f: LinkFrame.CallSetup) {
        val s = active()?.takeIf { it.id == f.call } ?: return
        val e = CallEngine(
            onIce = { c -> exec.execute { sendIce(s.id, c) } },
            onState = { st -> exec.execute { onIceState(s.id, st) } },
            onRemoteFrame = { img, rotation -> onRemoteFrame(s.id, img, rotation) },
            onLocalFrame = { img -> if (_state.value?.id == s.id) _local.value = img }
        )
        engine = e
        try {
            e.open(f.ice, f.relayOnly)
        } catch (ex: Exception) {
            System.err.println("WebRTC: ${ex.message}")
            failAndNotify(s.id)
            return
        }
        val camera = s.video && e.startVideo()
        _state.value = s.copy(cameraOn = camera, name = s.name.ifEmpty { f.name })
        if (f.caller) {
            e.createOffer({ sdp ->
                signal(s.id, "call_offer", buildJsonObject {
                    put("sdpType", "offer")
                    put("sdpDescription", sdp)
                    put("isVideo", s.video)
                })
                exec.execute {
                    val cur = active()?.takeIf { it.id == s.id } ?: return@execute
                    if (cur.phase == Phase.CONNECTING) {
                        _state.value = cur.copy(phase = Phase.CALLING)
                        if (f.ringback) tone.start(RingTone.Kind.RINGBACK, volume = 100)
                    }
                }
            }, { err ->
                System.err.println("offer: $err")
                exec.execute { failAndNotify(s.id) }
            })
        } else {
            val offer = f.offer ?: return failAndNotify(s.id)
            e.setRemote(RTCSdpType.OFFER, offer, {
                e.createAnswer({ sdp ->
                    signal(s.id, "call_answer", buildJsonObject {
                        put("sdpType", "answer")
                        put("sdpDescription", sdp)
                    })
                }, { err ->
                    System.err.println("answer: $err")
                    exec.execute { failAndNotify(s.id) }
                })
            }, { err ->
                System.err.println("remote offer: $err")
                exec.execute { failAndNotify(s.id) }
            })
        }
    }

    private fun onSignal(f: LinkFrame.CallSignal) {
        val s = active()?.takeIf { it.id == f.call } ?: return
        val e = engine ?: return
        val body = runCatching { Json.parseToJsonElement(f.body).jsonObject }.getOrNull() ?: return
        when (f.type) {
            "call_answer", "call_reanswer" -> body.str("sdpDescription")?.let { sdp ->
                e.setRemote(RTCSdpType.ANSWER, sdp, {}, { err -> System.err.println("answer: $err") })
            }
            "call_ice" -> {
                val candidate = body.str("candidate") ?: return
                e.addIce(RTCIceCandidate(body.str("sdpMid") ?: "", (body["sdpMLineIndex"] as? JsonPrimitive)?.intOrNull ?: 0, candidate))
            }
            "call_reoffer" -> {
                val sdp = body.str("sdpDescription") ?: return
                val screen = (body["screen"] as? JsonPrimitive)?.booleanOrNull == true
                val video = (body["isVideo"] as? JsonPrimitive)?.booleanOrNull == true
                if (screen || video) remoteCamOff = false
                if (screen) setRemoteSharing(s.id, true)
                e.setRemote(RTCSdpType.OFFER, sdp, {
                    e.createAnswer({ answer ->
                        signal(s.id, "call_reanswer", buildJsonObject {
                            put("sdpType", "answer")
                            put("sdpDescription", answer)
                        })
                    }, { err -> System.err.println("reanswer: $err") })
                }, { err -> System.err.println("reoffer: $err") })
            }
            "call_screen" -> {
                val on = (body["on"] as? JsonPrimitive)?.booleanOrNull == true
                val camera = (body["camera"] as? JsonPrimitive)?.booleanOrNull ?: true
                remoteCamOff = !on && !camera
                setRemoteSharing(s.id, on)
                if (remoteCamOff) {
                    _remote.value = null
                    _state.update { it?.takeIf { c -> c.id == s.id }?.copy(remoteVideo = false) ?: it }
                }
            }
        }
    }

    private fun setRemoteSharing(id: String, on: Boolean) {
        engine?.detectBlack = !on
        _state.update { it?.takeIf { c -> c.id == id }?.copy(remoteSharing = on) ?: it }
    }

    private fun onEnd(f: LinkFrame.CallEnd) {
        val s = active()?.takeIf { it.id == f.call } ?: return
        finish(s.id, f.reason.ifEmpty { LinkFrame.CALL_PEER_HUNG_UP })
    }

    private fun onIceState(id: String, st: RTCIceConnectionState) {
        val s = active()?.takeIf { it.id == id } ?: return
        when (st) {
            RTCIceConnectionState.CONNECTED, RTCIceConnectionState.COMPLETED -> {
                tone.stop()
                timeout?.cancel(false)
                _state.value = s.copy(phase = Phase.CONNECTED, connectedAt = if (s.connectedAt > 0L) s.connectedAt else System.currentTimeMillis())
            }
            RTCIceConnectionState.DISCONNECTED -> {
                if (s.phase != Phase.CONNECTED) return
                _state.value = s.copy(phase = Phase.RECONNECTING)
                armTimeout(id, RECONNECT_MS, LinkFrame.CALL_FAILED)
            }
            RTCIceConnectionState.FAILED -> failAndNotify(id)
            else -> Unit
        }
    }

    private fun onRemoteFrame(id: String, img: ImageBitmap, rotation: Int) {
        val s = _state.value ?: return
        if (s.id != id || s.phase == Phase.ENDED) return
        lastRemoteFrame = System.currentTimeMillis()
        if (remoteCamOff) return
        _remote.value = RemoteFrame(img, rotation)
        if (!s.remoteVideo) _state.update { it?.takeIf { c -> c.id == id }?.copy(remoteVideo = true) ?: it }
    }

    private fun watchRemote() {
        val s = active() ?: return
        if (s.remoteVideo && System.currentTimeMillis() - lastRemoteFrame > STALL_MS) {
            _remote.value = null
            _state.update { it?.takeIf { c -> c.id == s.id }?.copy(remoteVideo = false) ?: it }
        }
    }

    private fun sendIce(id: String, c: RTCIceCandidate) {
        signal(id, "call_ice", buildJsonObject {
            put("sdpMid", c.sdpMid)
            put("sdpMLineIndex", c.sdpMLineIndex)
            put("candidate", c.sdp)
        })
    }

    private fun signal(id: String, type: String, body: JsonObject) {
        if (_state.value?.id != id) return
        link.send(LinkFrame.CallSignal(id, type, body.toString()))
    }

    private fun armTimeout(id: String, ms: Long, reason: String = LinkFrame.CALL_NO_ANSWER) {
        timeout?.cancel(false)
        timeout = exec.schedule({
            val s = active()?.takeIf { it.id == id } ?: return@schedule
            if (s.phase == Phase.CONNECTED) return@schedule
            if (s.phase == Phase.INCOMING) {
                finish(id, LinkFrame.CALL_NO_ANSWER)
                return@schedule
            }
            link.send(LinkFrame.CallEnd(id, LinkFrame.CALL_HANGUP, 0, false))
            finish(id, reason)
        }, ms, TimeUnit.MILLISECONDS)
    }

    private fun failAndNotify(id: String) {
        val s = active()?.takeIf { it.id == id } ?: return
        val connected = s.connectedAt > 0L
        val seconds = if (connected) ((System.currentTimeMillis() - s.connectedAt) / 1000L).toInt() else 0
        link.send(LinkFrame.CallEnd(id, LinkFrame.CALL_FAILED, seconds, connected))
        finish(id, LinkFrame.CALL_FAILED)
    }

    private fun finish(id: String, reason: String) {
        val s = _state.value?.takeIf { it.id == id } ?: return
        if (s.phase == Phase.ENDED) return
        tone.stop()
        timeout?.cancel(false)
        engine?.close()
        engine = null
        _remote.value = null
        _local.value = null
        _state.value = s.copy(phase = Phase.ENDED, endReason = reason, remoteVideo = false)
        exec.schedule({
            if (_state.value?.id == id && _state.value?.phase == Phase.ENDED) _state.value = null
        }, ENDED_MS, TimeUnit.MILLISECONDS)
    }

    private fun reset() {
        lastRemoteFrame = 0L
        remoteCamOff = false
        _remote.value = null
        _local.value = null
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
