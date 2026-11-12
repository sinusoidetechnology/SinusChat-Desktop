package pl.sinusoide.sinuschat.desktop

import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCSdpType
import pl.sinusoide.sinuschat.desktop.call.CallEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallEngineTest {

    @Test
    fun loopbackConnects() {
        val connectedA = CountDownLatch(1)
        val connectedB = CountDownLatch(1)
        val aRef = AtomicReference<CallEngine>()
        val bRef = AtomicReference<CallEngine>()
        val failure = AtomicReference<String?>(null)
        val frames = CountDownLatch(3)
        val a = CallEngine(
            onIce = { c -> bRef.get().addIce(c) },
            onState = { s -> if (s == RTCIceConnectionState.CONNECTED || s == RTCIceConnectionState.COMPLETED) connectedA.countDown() },
            onRemoteFrame = { _, _ -> },
            onLocalFrame = { }
        )
        val b = CallEngine(
            onIce = { c -> aRef.get().addIce(c) },
            onState = { s -> if (s == RTCIceConnectionState.CONNECTED || s == RTCIceConnectionState.COMPLETED) connectedB.countDown() },
            onRemoteFrame = { _, _ -> frames.countDown() },
            onLocalFrame = { }
        )
        aRef.set(a)
        bRef.set(b)
        try {
            a.open(emptyList(), false)
            b.open(emptyList(), false)
            a.createOffer({ offer ->
                b.setRemote(RTCSdpType.OFFER, offer, {
                    b.createAnswer({ answer ->
                        a.setRemote(RTCSdpType.ANSWER, answer, {}, { failure.set(it) })
                    }, { failure.set(it) })
                }, { failure.set(it) })
            }, { failure.set(it) })
            val ok = connectedA.await(20, TimeUnit.SECONDS) && connectedB.await(5, TimeUnit.SECONDS)
            assertTrue(ok, "ICE nie połączyło się: ${failure.get()}")
            val targets = CallEngine.shareTargets()
            val target = targets.firstOrNull { !it.window }
            assertTrue(target != null, "brak ekranów do udostępnienia")
            run {
                assertEquals(CallEngine.Attach.NEEDS_OFFER, a.startShare(target!!))
                a.createOffer({ offer ->
                    b.setRemote(RTCSdpType.OFFER, offer, {
                        b.createAnswer({ answer ->
                            a.setRemote(RTCSdpType.ANSWER, answer, {}, { failure.set(it) })
                        }, { failure.set(it) })
                    }, { failure.set(it) })
                }, { failure.set(it) })
                assertTrue(frames.await(15, TimeUnit.SECONDS), "brak obrazu ekranu: ${failure.get()}")
                a.stopShare(false)
                assertTrue(!a.sharing)
            }
        } finally {
            a.close()
            b.close()
        }
    }
}
