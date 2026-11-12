package pl.sinusoide.sinuschat.desktop

import androidx.compose.ui.graphics.Color
import pl.sinusoide.sinuschat.desktop.system.WindowChrome
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowChromeTest {

    @Test
    fun captionColorIsAccepted() {
        if (GraphicsEnvironment.isHeadless() || !System.getProperty("os.name").startsWith("Windows")) return
        var ok = false
        SwingUtilities.invokeAndWait {
            val frame = JFrame("SinusChat")
            frame.setBounds(-3000, -3000, 240, 120)
            frame.isVisible = true
            ok = WindowChrome.apply(frame, Color(0xFF17212B), Color(0xFF0E1621), dark = true)
            frame.dispose()
        }
        assertTrue(ok)
    }
}
