package pl.sinusoide.sinuschat.desktop

import androidx.compose.ui.graphics.Color
import pl.sinusoide.sinuschat.desktop.system.WindowChrome
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test

class WindowChromeShotTest {

    @Test
    fun captureTitleBar() {
        if (System.getenv("SINUSCHAT_INSTALLER_ART") == null) return
        val out = File("build/preview").apply { mkdirs() }
        val icon = ImageIO.read(PhoneLink::class.java.getResourceAsStream("/sinuschat.png"))
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait {
            frame = JFrame("SinusChat")
            frame.iconImage = icon
            frame.isAlwaysOnTop = true
            frame.setBounds(200, 200, 420, 160)
            frame.isVisible = true
        }
        val robot = Robot()
        Thread.sleep(700)
        val bounds = Rectangle(frame.x, frame.y, frame.width, 48)
        ImageIO.write(robot.createScreenCapture(bounds), "png", File(out, "titlebar-before.png"))
        SwingUtilities.invokeAndWait { WindowChrome.apply(frame, Color(0xFF17212B), Color(0xFF0E1621), dark = true) }
        Thread.sleep(700)
        ImageIO.write(robot.createScreenCapture(bounds), "png", File(out, "titlebar-after.png"))
        SwingUtilities.invokeAndWait { frame.dispose() }
    }
}
