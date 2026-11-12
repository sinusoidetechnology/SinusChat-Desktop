package pl.sinusoide.sinuschat.desktop.security

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

object CaptureShield {

    private const val WDA_NONE = 0x0
    private const val WDA_MONITOR = 0x1
    private const val WDA_EXCLUDEFROMCAPTURE = 0x11

    private interface User32Affinity : StdCallLibrary {
        fun SetWindowDisplayAffinity(hwnd: Pointer, affinity: Int): Boolean
    }

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val user32: User32Affinity? by lazy {
        if (!windows) null else runCatching { Native.load("user32", User32Affinity::class.java) }.getOrNull()
    }

    @Volatile var window: java.awt.Window? = null
    private var holders = 0

    @Synchronized
    fun acquire() {
        holders++
        if (holders == 1) apply(true)
    }

    @Synchronized
    fun release() {
        if (holders == 0) return
        holders--
        if (holders == 0) apply(false)
    }

    private fun apply(on: Boolean) {
        val lib = user32 ?: return
        val w = window ?: return
        val hwnd = runCatching { Native.getWindowPointer(w) }.getOrNull() ?: return
        if (!on) {
            runCatching { lib.SetWindowDisplayAffinity(hwnd, WDA_NONE) }
            return
        }
        val ok = runCatching { lib.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE) }.getOrDefault(false)
        if (!ok) runCatching { lib.SetWindowDisplayAffinity(hwnd, WDA_MONITOR) }
    }
}
