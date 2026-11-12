package pl.sinusoide.sinuschat.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import pl.sinusoide.sinuschat.desktop.security.CaptureShield
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureShieldTest {

    private interface Probe : StdCallLibrary {
        fun GetWindowDisplayAffinity(hwnd: Pointer, affinity: IntByReference): Boolean
    }

    @Test
    fun hidesWindowFromCapture() {
        if (GraphicsEnvironment.isHeadless() || !System.getProperty("os.name").startsWith("Windows")) return
        val probe = Native.load("user32", Probe::class.java)
        var frame: JFrame? = null
        SwingUtilities.invokeAndWait {
            frame = JFrame("shield").apply { setSize(200, 120); isVisible = true }
        }
        val f = frame!!
        try {
            CaptureShield.window = f
            val hwnd = Native.getWindowPointer(f)
            val value = IntByReference()
            CaptureShield.acquire()
            assertTrue(probe.GetWindowDisplayAffinity(hwnd, value))
            assertTrue(value.value != 0, "okno nie jest chronione")
            CaptureShield.release()
            assertTrue(probe.GetWindowDisplayAffinity(hwnd, value))
            assertEquals(0, value.value)
        } finally {
            CaptureShield.window = null
            SwingUtilities.invokeAndWait { f.dispose() }
        }
    }
}
