package pl.sinusoide.sinuschat.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import pl.sinusoide.sinuschat.desktop.security.BleScanner
import pl.sinusoide.sinuschat.desktop.security.LockManager
import pl.sinusoide.sinuschat.desktop.security.ProximityGuard
import pl.sinusoide.sinuschat.desktop.security.ProximityStatus
import pl.sinusoide.sinuschat.desktop.security.ProximityTracker
import pl.sinusoide.sinuschat.link.LinkIdentity
import pl.sinusoide.sinuschat.link.LinkProximity
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProximityTest {

    @Test
    fun locksOnlyAfterPhoneWasNearAndThenStaysFar() {
        var now = 0L
        val t = ProximityTracker { now }
        t.seen(-90, now)
        val (r0, l0) = t.tick(1, now)
        assertEquals(ProximityStatus.FAR, r0.status)
        assertFalse(l0)
        now += ProximityTracker.FAR_MS * 2
        t.seen(-90, now)
        assertFalse(t.tick(1, now).second)

        repeat(7) { now += 1_000; t.seen(-60, now) }
        assertEquals(ProximityStatus.NEAR, t.tick(1, now).first.status)
        assertTrue(t.armed)

        var locked = false
        repeat(20) {
            now += 1_000
            t.seen(-92, now)
            if (t.tick(1, now).second) locked = true
        }
        assertTrue(locked)
        assertFalse(t.armed)
    }

    @Test
    fun locksWhenPhoneDisappears() {
        var now = 0L
        val t = ProximityTracker { now }
        repeat(5) { now += 500; t.seen(-55, now) }
        assertEquals(ProximityStatus.NEAR, t.tick(0, now).first.status)
        now += ProximityTracker.GONE_MS - 1_000
        assertFalse(t.tick(0, now).second)
        now += 2_000
        val (reading, lock) = t.tick(0, now)
        assertEquals(ProximityStatus.SEARCHING, reading.status)
        assertTrue(lock)
    }

    @Test
    fun rangeChangesThreshold() {
        var now = 0L
        val t = ProximityTracker { now }
        repeat(5) { now += 500; t.seen(-80, now) }
        assertEquals(ProximityStatus.FAR, t.tick(0, now).first.status)
        assertEquals(ProximityStatus.FAR, t.tick(1, now).first.status)
        assertEquals(ProximityStatus.NEAR, t.tick(2, now).first.status)
    }

    @Test
    fun calibrationSetsThresholdsAndDistance() {
        assertEquals(-65, ProximityTracker.threshold(0))
        val cal = -55
        assertEquals(-68, ProximityTracker.threshold(0, cal))
        assertEquals(-78, ProximityTracker.threshold(1, cal))
        assertEquals(-86, ProximityTracker.threshold(2, cal))
        assertEquals(0.5f, ProximityTracker.meters(-55, cal)!!, 0.01f)
        assertEquals(2.0f, ProximityTracker.meters(-70, cal)!!, 0.05f)
        assertNull(ProximityTracker.meters(-70, 0))

        var now = 0L
        val t = ProximityTracker { now }
        repeat(6) { now += 500; t.seen(-60, now) }
        assertEquals(ProximityStatus.NEAR, t.tick(0, now, cal).first.status)
        var locked = false
        repeat(20) {
            now += 500
            t.seen(-78, now)
            if (t.tick(0, now, cal).second) locked = true
        }
        assertTrue(locked)
    }

    @Test
    fun walkingAwayLocksCloseToTwoMeters() {
        val cal = -55
        var now = 0L
        val t = ProximityTracker { now }
        fun rssiAt(d: Float) = (cal - 10f * ProximityTracker.PATH_LOSS * kotlin.math.log10(d / ProximityTracker.CALIBRATION_METERS)).toInt()
        repeat(16) { now += 250; t.seen(rssiAt(0.5f), now); t.tick(0, now, cal) }
        assertTrue(t.armed)
        var actual = 0.5f
        var shownAtLock = -1f
        var actualAtLock = -1f
        while (now < 30_000L && shownAtLock < 0f) {
            now += 250
            actual += 0.6f * 0.25f
            t.seen(rssiAt(actual), now)
            val (reading, lock) = t.tick(0, now, cal)
            if (lock) {
                shownAtLock = ProximityTracker.meters(reading.rssi!!, cal)!!
                actualAtLock = actual
            }
        }
        assertTrue(shownAtLock in 1.8f..2.4f, "pokazane $shownAtLock m")
        assertTrue(actualAtLock < 3.0f, "faktycznie $actualAtLock m")
    }

    @Test
    fun medianIgnoresSpikes() {
        var now = 0L
        val t = ProximityTracker { now }
        listOf(-60, -61, -95, -59, -60).forEach { now += 300; t.seen(it, now) }
        assertEquals(-60, t.average(now))
    }

    @Test
    fun parsesHelperOutput() {
        val seen = BleScanner.parse("A -67 01a0b1c2d3e4f5061728394a") as BleScanner.Event.Seen
        assertEquals(-67, seen.rssi)
        assertEquals(12, seen.data.size)
        assertEquals(ProximityStatus.BLUETOOTH_OFF, (BleScanner.parse("E 800710DF") as BleScanner.Event.Failed).status)
        assertEquals(ProximityStatus.BLUETOOTH_OFF, (BleScanner.parse("E RadioNotAvailable") as BleScanner.Event.Failed).status)
        assertEquals(ProximityStatus.UNSUPPORTED, (BleScanner.parse("E NotSupported") as BleScanner.Event.Failed).status)
        assertEquals(BleScanner.Event.Started, BleScanner.parse("S Started"))
        assertNull(BleScanner.parse("A x 00"))
        assertNull(BleScanner.parse("A -50 0g"))
        assertNull(BleScanner.parse("smieci"))
    }

    @Test
    fun guardLocksWhenFakePhoneWalksAway() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-prox").toString())
        val phone = LinkIdentity.generate()
        val pc = LinkIdentity.generate()
        val key = LinkProximity.key(pc, phone.publicBytes)
        val phoneKey = LinkProximity.key(phone, pc.publicBytes)
        val lock = LockManager("lock-prox.bin")
        lock.setPin("482916")
        lock.unlock()
        val rssiRef = java.util.concurrent.atomic.AtomicInteger(-55)
        val scanner = object : BleScanner {
            @Volatile var running = false
            override fun start(onEvent: (BleScanner.Event) -> Unit) {
                running = true
                Thread {
                    onEvent(BleScanner.Event.Started)
                    while (running) {
                        onEvent(BleScanner.Event.Seen(rssiRef.get(), LinkProximity.payload(phoneKey, System.currentTimeMillis())))
                        onEvent(BleScanner.Event.Seen(-40, LinkProximity.payload(LinkProximity.key(LinkIdentity.generate(), pc.publicBytes), System.currentTimeMillis())))
                        Thread.sleep(200)
                    }
                }.apply { isDaemon = true }.start()
            }
            override fun stop() { running = false }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val guard = ProximityGuard(scope, lock, { key }, { scanner })
        try {
            guard.update(true, 1)
            val deadline = System.currentTimeMillis() + 6_000
            while (guard.reading.value.status != ProximityStatus.NEAR && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertEquals(ProximityStatus.NEAR, guard.reading.value.status)
            assertFalse(lock.locked.value)
            rssiRef.set(-95)
            val lockBy = System.currentTimeMillis() + ProximityTracker.FAR_MS + ProximityTracker.WINDOW_MS + 6_000
            while (!lock.locked.value && System.currentTimeMillis() < lockBy) Thread.sleep(200)
            assertTrue(lock.locked.value)
            guard.update(false, 1)
            assertEquals(ProximityStatus.OFF, guard.reading.value.status)
        } finally {
            guard.update(false, 1)
            scope.cancel()
            lock.wipe()
        }
    }
}
