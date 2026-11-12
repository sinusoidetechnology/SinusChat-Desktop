package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.security.LockManager
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LockManagerTest {

    @Test
    fun pinDuressLockoutAndRelock() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-lock").toString())
        var now = 1_000_000L
        val lock = LockManager("lock-test.bin") { now }
        assertFalse(lock.locked.value)

        lock.setPin("482916")
        assertTrue(lock.verifyPin("482916"))
        assertFalse(lock.verifyPin("482917"))
        assertFalse(lock.verifyPin("48291"))
        lock.setDuress("7305")
        assertTrue(lock.verifyDuress("7305"))
        assertFalse(lock.verifyPin("7305"))
        assertFalse(lock.verifyDuress("482916"))

        repeat(4) { lock.registerFailure() }
        assertEquals(0L, lock.lockoutRemainingMs())
        assertEquals(5, lock.registerFailure())
        assertEquals(5 * 60_000L, lock.lockoutRemainingMs())
        now += 60_000L
        assertEquals(4 * 60_000L, lock.lockoutRemainingMs())
        now += 5 * 60_000L
        assertEquals(0L, lock.lockoutRemainingMs())

        val reloaded = LockManager("lock-test.bin") { now }
        assertTrue(reloaded.locked.value)
        assertTrue(reloaded.verifyPin("482916"))
        reloaded.unlock()
        assertFalse(reloaded.locked.value)
        assertEquals(0, reloaded.failCount())

        reloaded.onBackground()
        now += 10_000L
        reloaded.onForeground()
        assertFalse(reloaded.locked.value)
        reloaded.onBackground()
        now += 31_000L
        reloaded.onForeground()
        assertTrue(reloaded.locked.value)

        reloaded.unlock()
        reloaded.onClockGap(5 * 60_000L)
        assertTrue(reloaded.locked.value)

        reloaded.wipe()
        assertFalse(reloaded.config.value.hasPin)
        assertFalse(reloaded.locked.value)
        assertNull(SecureStore.read("lock-test.bin"))
    }

    @Test
    fun lockTriggersFollowSettings() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-lock-opts").toString())
        var now = 1_000_000L
        val lock = LockManager("lock-opts.bin") { now }
        lock.setPin("482916")
        lock.unlock()

        lock.onHidden()
        lock.onPhoneLost()
        assertFalse(lock.locked.value)
        lock.onClockGap(LockManager.SLEEP_GAP_MS)
        assertTrue(lock.locked.value)

        lock.unlock()
        lock.setLockOnWake(false)
        lock.onClockGap(LockManager.SLEEP_GAP_MS * 10)
        assertFalse(lock.locked.value)

        lock.setLockOnHide(true)
        lock.onHidden()
        assertTrue(lock.locked.value)

        lock.unlock()
        lock.setLockOnDisconnect(true)
        lock.onPhoneLost()
        assertTrue(lock.locked.value)

        lock.unlock()
        lock.setRelockMs(LockManager.RELOCK_NEVER)
        lock.onBackground()
        now += 24 * 3600_000L
        lock.onForeground()
        assertFalse(lock.locked.value)

        val reloaded = LockManager("lock-opts.bin") { now }
        assertEquals(LockManager.RELOCK_NEVER, reloaded.config.value.relockMs)
        assertTrue(reloaded.config.value.lockOnHide)
        assertFalse(reloaded.config.value.lockOnWake)
        assertTrue(reloaded.config.value.lockOnDisconnect)
        reloaded.wipe()
    }

    @Test
    fun weakPinsAreDetected() {
        assertTrue(LockManager.isWeak("1234"))
        assertTrue(LockManager.isWeak("1990"))
        assertTrue(LockManager.isWeak("2580"))
        assertTrue(LockManager.isWeak("1111"))
        assertFalse(LockManager.isWeak("482916"))
        assertEquals(0L, LockManager.lockoutFor(4))
        assertEquals(10 * 60_000L, LockManager.lockoutFor(6))
        assertEquals(24 * 3600_000L, LockManager.lockoutFor(1_000))
    }
}
