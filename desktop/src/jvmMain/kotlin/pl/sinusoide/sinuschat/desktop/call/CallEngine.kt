package pl.sinusoide.sinuschat.desktop.call

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCBundlePolicy
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCIceTransportPolicy
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCRtcpMuxPolicy
import dev.onvoid.webrtc.RTCRtpSender
import dev.onvoid.webrtc.RTCRtpTransceiver
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.MediaDevices
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoCaptureCapability
import dev.onvoid.webrtc.media.video.VideoDevice
import dev.onvoid.webrtc.media.video.VideoDesktopSource
import dev.onvoid.webrtc.media.video.VideoDeviceSource
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer
import dev.onvoid.webrtc.media.video.desktop.WindowCapturer
import dev.onvoid.webrtc.media.video.VideoFrame
import dev.onvoid.webrtc.media.video.VideoTrack
import dev.onvoid.webrtc.media.video.VideoTrackSink
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import pl.sinusoide.sinuschat.link.IceServer
import java.util.concurrent.atomic.AtomicBoolean

class CallEngine(
    private val onIce: (RTCIceCandidate) -> Unit,
    private val onState: (RTCIceConnectionState) -> Unit,
    private val onRemoteFrame: (ImageBitmap, Int) -> Unit,
    private val onLocalFrame: (ImageBitmap) -> Unit
) {

    companion object {
        private var shared: PeerConnectionFactory? = null

        @Synchronized
        private fun factory(): PeerConnectionFactory = shared ?: run {
            val adm = AudioDeviceModule()
            runCatching { MediaDevices.getDefaultAudioCaptureDevice()?.let { adm.setRecordingDevice(it) } }
            runCatching { MediaDevices.getDefaultAudioRenderDevice()?.let { adm.setPlayoutDevice(it) } }
            PeerConnectionFactory(adm).also { shared = it }
        }

        fun hasCamera(): Boolean = runCatching { MediaDevices.getVideoCaptureDevices().isNotEmpty() }.getOrDefault(false)

        fun shareTargets(): List<ShareTarget> {
            val screens = runCatching {
                val c = ScreenCapturer()
                try { c.desktopSources.map { ShareTarget(it.id, it.title.orEmpty(), false) } } finally { c.dispose() }
            }.getOrDefault(emptyList())
            val windows = runCatching {
                val c = WindowCapturer()
                try {
                    c.desktopSources.filter { !it.title.isNullOrBlank() }.map { ShareTarget(it.id, it.title, true) }
                } finally { c.dispose() }
            }.getOrDefault(emptyList())
            return screens + windows
        }

        private const val LOCAL_FRAME_MS = 66L
    }

    private var pc: RTCPeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoDeviceSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoSender: RTCRtpSender? = null
    private var screenSource: VideoDesktopSource? = null
    private var screenTrack: VideoTrack? = null
    @Volatile var detectBlack = true
    private var remoteVideo: VideoTrack? = null
    private val pending = mutableListOf<RTCIceCandidate>()
    private var remoteSet = false
    @Volatile private var closed = false
    private val remoteBusy = AtomicBoolean(false)
    private val localBusy = AtomicBoolean(false)
    @Volatile private var lastLocal = 0L

    val hasVideo: Boolean get() = videoTrack != null

    @Volatile var sharing = false
        private set

    enum class Attach { FAILED, REPLACED, NEEDS_OFFER }

    private val remoteSink = VideoTrackSink { frame ->
        if (closed || !remoteBusy.compareAndSet(false, true)) return@VideoTrackSink
        try {
            convert(frame, check = true)?.let { onRemoteFrame(it, frame.rotation) }
        } finally {
            remoteBusy.set(false)
        }
    }

    private val localSink = VideoTrackSink { frame ->
        val now = System.currentTimeMillis()
        if (closed || now - lastLocal < LOCAL_FRAME_MS || !localBusy.compareAndSet(false, true)) return@VideoTrackSink
        try {
            lastLocal = now
            convert(frame)?.let { onLocalFrame(it) }
        } finally {
            localBusy.set(false)
        }
    }

    private val observer = object : PeerConnectionObserver {
        override fun onIceCandidate(candidate: RTCIceCandidate) {
            if (!closed) onIce(candidate)
        }

        override fun onIceConnectionChange(state: RTCIceConnectionState) {
            if (!closed) onState(state)
        }

        override fun onTrack(transceiver: RTCRtpTransceiver) {
            val track = runCatching { transceiver.receiver.track }.getOrNull()
            if (track is VideoTrack && !closed) {
                remoteVideo?.removeSink(remoteSink)
                remoteVideo = track
                track.addSink(remoteSink)
            }
        }
    }

    fun open(ice: List<IceServer>, relayOnly: Boolean) {
        val f = factory()
        val config = RTCConfiguration().apply {
            iceServers = ice.map { s ->
                RTCIceServer().apply {
                    urls = s.urls
                    if (s.username.isNotEmpty()) {
                        username = s.username
                        password = s.credential
                    }
                }
            }
            iceTransportPolicy = if (relayOnly) RTCIceTransportPolicy.RELAY else RTCIceTransportPolicy.ALL
            bundlePolicy = RTCBundlePolicy.MAX_BUNDLE
            rtcpMuxPolicy = RTCRtcpMuxPolicy.REQUIRE
        }
        val peer = f.createPeerConnection(config, observer)
        pc = peer
        val source = f.createAudioSource(AudioOptions().apply {
            echoCancellation = true
            autoGainControl = true
            noiseSuppression = true
            highpassFilter = true
        })
        audioTrack = f.createAudioTrack("audio0", source).also { peer.addTrack(it, listOf("stream0")) }
    }

    fun startVideo(): Boolean = attachCamera() != Attach.FAILED

    fun attachCamera(): Attach {
        if (videoTrack != null) {
            setCamera(true)
            return Attach.REPLACED
        }
        val device = runCatching { MediaDevices.getVideoCaptureDevices().firstOrNull() }.getOrNull() ?: return Attach.FAILED
        return try {
            val source = VideoDeviceSource()
            source.setVideoCaptureDevice(device)
            capability(device)?.let { source.setVideoCaptureCapability(it) }
            source.start()
            val track = factory().createVideoTrack("video0", source)
            track.addSink(localSink)
            videoSource = source
            videoTrack = track
            val sender = videoSender
            if (sender != null) {
                if (!sharing) {
                    sender.replaceTrack(track)
                    disposeScreen()
                }
                Attach.REPLACED
            } else {
                videoSender = pc?.addTrack(track, listOf("stream0"))
                Attach.NEEDS_OFFER
            }
        } catch (e: Exception) {
            System.err.println("Kamera: ${e.message}")
            Attach.FAILED
        }
    }

    fun startShare(target: ShareTarget): Attach {
        val oldTrack = screenTrack
        val oldSource = screenSource
        return try {
            val source = VideoDesktopSource()
            source.setSourceId(target.id, target.window)
            source.setFrameRate(15)
            source.setMaxFrameSize(1920, 1080)
            source.start()
            val track = factory().createVideoTrack("screen0", source)
            runCatching { videoSource?.stop() }
            val sender = videoSender
            val result = if (sender != null) {
                sender.replaceTrack(track)
                Attach.REPLACED
            } else {
                videoSender = pc?.addTrack(track, listOf("stream0"))
                Attach.NEEDS_OFFER
            }
            screenSource = source
            screenTrack = track
            sharing = true
            runCatching { oldSource?.stop() }
            runCatching { oldTrack?.dispose() }
            runCatching { oldSource?.dispose() }
            result
        } catch (e: Exception) {
            System.err.println("Ekran: ${e.message}")
            Attach.FAILED
        }
    }

    fun stopShare(camera: Boolean) {
        if (!sharing) return
        sharing = false
        val cam = videoTrack
        if (cam != null) {
            runCatching { videoSender?.replaceTrack(cam) }
            setCamera(camera)
            disposeScreen()
        } else {
            runCatching { screenSource?.stop() }
            screenTrack?.isEnabled = false
        }
    }

    private fun disposeScreen() {
        val track = screenTrack ?: return
        val source = screenSource
        screenTrack = null
        screenSource = null
        runCatching { source?.stop() }
        runCatching { track.dispose() }
        runCatching { source?.dispose() }
    }

    private fun capability(device: VideoDevice): VideoCaptureCapability? {
        val caps = runCatching { MediaDevices.getVideoCaptureCapabilities(device) }.getOrNull().orEmpty()
        val best = caps.filter { it.width <= 1280 && it.height <= 720 && it.frameRate >= 15 }
            .maxWithOrNull(compareBy<VideoCaptureCapability> { it.width * it.height }.thenBy { minOf(it.frameRate, 30) })
            ?: return null
        return VideoCaptureCapability(best.width, best.height, minOf(best.frameRate, 30))
    }

    fun setCamera(on: Boolean) {
        val track = videoTrack ?: return
        try {
            if (on) videoSource?.start() else videoSource?.stop()
        } catch (_: Exception) {}
        track.isEnabled = on
    }

    fun setMic(on: Boolean) {
        audioTrack?.isEnabled = on
    }

    fun createOffer(done: (String) -> Unit, fail: (String) -> Unit) {
        val peer = pc ?: return fail("closed")
        peer.createOffer(RTCOfferOptions(), describe(peer, done, fail))
    }

    fun createAnswer(done: (String) -> Unit, fail: (String) -> Unit) {
        val peer = pc ?: return fail("closed")
        peer.createAnswer(RTCAnswerOptions(), describe(peer, done, fail))
    }

    private fun describe(peer: RTCPeerConnection, done: (String) -> Unit, fail: (String) -> Unit) =
        object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                peer.setLocalDescription(description, object : SetSessionDescriptionObserver {
                    override fun onSuccess() = done(description.sdp)
                    override fun onFailure(error: String) = fail(error)
                })
            }

            override fun onFailure(error: String) = fail(error)
        }

    fun setRemote(type: RTCSdpType, sdp: String, done: () -> Unit, fail: (String) -> Unit) {
        val peer = pc ?: return fail("closed")
        peer.setRemoteDescription(RTCSessionDescription(type, sdp), object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                synchronized(pending) {
                    remoteSet = true
                    pending.forEach { runCatching { peer.addIceCandidate(it) } }
                    pending.clear()
                }
                done()
            }

            override fun onFailure(error: String) = fail(error)
        })
    }

    fun addIce(candidate: RTCIceCandidate) {
        val peer = pc ?: return
        synchronized(pending) {
            if (remoteSet) runCatching { peer.addIceCandidate(candidate) } else pending += candidate
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { remoteVideo?.removeSink(remoteSink) }
        runCatching { videoTrack?.removeSink(localSink) }
        runCatching { videoSource?.stop() }
        runCatching { screenSource?.stop() }
        runCatching { pc?.close() }
        runCatching { screenTrack?.dispose() }
        runCatching { screenSource?.dispose() }
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioTrack?.dispose() }
        pc = null
        videoSender = null
        screenTrack = null
        screenSource = null
        videoTrack = null
        videoSource = null
        audioTrack = null
        remoteVideo = null
    }

    private fun isBlack(i420: dev.onvoid.webrtc.media.video.I420Buffer): Boolean {
        val w = i420.width
        val h = i420.height
        if (w < 8 || h < 8) return false
        val y = i420.dataY
        val stride = i420.strideY
        var min = 255
        var max = 0
        for (r in 1..7) for (c in 1..7) {
            val v = y.get((h * r / 8) * stride + w * c / 8).toInt() and 0xFF
            if (v > max) max = v
            if (v < min) min = v
        }
        return max <= 20 && max - min <= 2
    }

    private fun convert(frame: VideoFrame, check: Boolean = false): ImageBitmap? {
        val i420 = runCatching { frame.buffer.toI420() }.getOrNull() ?: return null
        return try {
            val w = i420.width
            val h = i420.height
            if (w <= 0 || h <= 0) return null
            if (check && detectBlack && isBlack(i420)) return null
            val bytes = ByteArray(w * h * 4)
            VideoBufferConverter.convertFromI420(i420, bytes, FourCC.ARGB)
            Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, w * 4).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { i420.release() }
        }
    }
}

data class ShareTarget(val id: Long, val title: String, val window: Boolean)
