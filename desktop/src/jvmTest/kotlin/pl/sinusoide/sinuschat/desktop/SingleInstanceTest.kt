package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.system.SingleInstance
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleInstanceTest {

    @Test
    fun secondLaunchWakesFirstOne() {
        val dir = Files.createTempDirectory("sc-instance").toFile()
        val first = assertNotNull(SingleInstance.acquire(dir))
        val shown = CountDownLatch(1)
        first.listen { shown.countDown() }
        assertNull(SingleInstance.acquire(dir))
        assertTrue(shown.await(5, TimeUnit.SECONDS))
        first.release()
        val again = assertNotNull(SingleInstance.acquire(dir))
        again.release()
        dir.deleteRecursively()
    }
}
