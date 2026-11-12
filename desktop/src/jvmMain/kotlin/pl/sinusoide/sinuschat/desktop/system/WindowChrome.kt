package pl.sinusoide.sinuschat.desktop.system

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

object WindowChrome {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private const val WTA_NONCLIENT = 1
    private const val WTNCA_NODRAWCAPTION = 0x1
    private const val WTNCA_NODRAWICON = 0x2

    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private interface UxTheme : StdCallLibrary {
        fun SetWindowThemeAttribute(hwnd: Pointer, type: Int, attribute: Pointer, size: Int): Int
    }

    private interface User32 : StdCallLibrary {
        fun CreateIcon(instance: Pointer?, width: Int, height: Int, planes: Byte, bits: Byte, and: ByteArray, xor: ByteArray): Pointer?
        fun SendMessageW(hwnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    }

    private const val WM_SETICON = 0x0080
    private const val SWP_REFRESH = 0x1 or 0x2 or 0x4 or 0x10 or 0x20

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val dwm: Dwm? by lazy {
        if (!windows) null else runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
    }
    private val uxtheme: UxTheme? by lazy {
        if (!windows) null else runCatching { Native.load("uxtheme", UxTheme::class.java) }.getOrNull()
    }
    private val user32: User32? by lazy {
        if (!windows) null else runCatching { Native.load("user32", User32::class.java) }.getOrNull()
    }
    private val blankIcon: Pointer? by lazy {
        user32?.let { u -> runCatching { u.CreateIcon(null, 16, 16, 1, 1, ByteArray(32) { 0xFF.toByte() }, ByteArray(32)) }.getOrNull() }
    }

    private fun blankSmallIcon(hwnd: Pointer) {
        val u = user32 ?: return
        val icon = blankIcon ?: return
        runCatching { u.SendMessageW(hwnd, WM_SETICON, null, icon) }
    }

    private fun refreshFrame(hwnd: Pointer) {
        runCatching { user32?.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_REFRESH) }
    }

    private fun hideIcon(hwnd: Pointer): Boolean {
        val lib = uxtheme ?: return false
        val flags = WTNCA_NODRAWICON or WTNCA_NODRAWCAPTION
        val options = com.sun.jna.Memory(8).apply {
            setInt(0, flags)
            setInt(4, flags)
        }
        return runCatching { lib.SetWindowThemeAttribute(hwnd, WTA_NONCLIENT, options, 8) == 0 }.getOrDefault(false)
    }

    private fun colorRef(c: Color): Int {
        val r = (c.red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (c.green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (c.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        return r or (g shl 8) or (b shl 16)
    }

    fun apply(window: java.awt.Window, caption: Color, behind: Color, dark: Boolean): Boolean {
        val lib = dwm ?: return false
        if (!window.isDisplayable) return false
        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return false
        val solid = if (caption.alpha < 1f) caption.compositeOver(behind) else caption
        val ref = colorRef(solid.copy(alpha = 1f))
        return runCatching {
            val results = listOf(
                lib.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(if (dark) 1 else 0), 4),
                lib.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, IntByReference(ref), 4),
                lib.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, IntByReference(ref), 4),
                lib.DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, IntByReference(ref), 4)
            )
            val icon = hideIcon(hwnd)
            blankSmallIcon(hwnd)
            refreshFrame(hwnd)
            results.all { it == 0 } && icon
        }.getOrDefault(false)
    }
}
