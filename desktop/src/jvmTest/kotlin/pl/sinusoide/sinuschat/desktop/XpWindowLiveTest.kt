package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.Themes
import pl.sinusoide.sinuschat.desktop.ui.xp.XpWindow
import pl.sinusoide.sinuschat.desktop.ui.xp.usableBounds
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XpWindowLiveTest {

    @Test
    fun shapeAndMaximize() {
        if (System.getenv("SINUSCHAT_XP_LIVE") == null) return
        val log = StringBuilder()
        var shaped = false
        var undecorated = false
        var usable: java.awt.Rectangle? = null
        var maxed: java.awt.Rectangle? = null
        var shapeWhenMax: Boolean? = null
        SinusColors.palette = Themes.retro
        application(exitProcessOnExit = false) {
            val state = rememberWindowState(size = DpSize(520.dp, 320.dp))
            Window(onCloseRequest = ::exitApplication, undecorated = true, state = state, title = "SinusChat", icon = appIcon()) {
                SinusTheme {
                    XpWindow("SinusChat", appIcon(), state, true, {}) {
                        Box(Modifier.fillMaxSize().background(SinusColors.backgroundSolid))
                    }
                }
                LaunchedEffect(Unit) {
                    delay(1200)
                    shaped = window.shape != null
                    undecorated = window.isUndecorated
                    usable = usableBounds(window)
                    window.maximizedBounds = usable
                    state.placement = WindowPlacement.Maximized
                    delay(1200)
                    maxed = window.bounds
                    shapeWhenMax = window.shape != null
                    state.placement = WindowPlacement.Floating
                    delay(500)
                    exitApplication()
                }
            }
        }
        SinusColors.palette = Themes.dark
        log.append("shaped=$shaped undecorated=$undecorated usable=$usable maxed=$maxed shapeWhenMax=$shapeWhenMax")
        File("build/preview/xp").mkdirs()
        File("build/preview/xp/live.txt").writeText(log.toString())
        assertTrue(shaped)
        assertTrue(undecorated)
        assertEquals(false, shapeWhenMax)
        assertEquals(usable, maxed)
    }
}
