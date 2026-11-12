package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import pl.sinusoide.sinuschat.desktop.system.Platform

class Palette(
    val key: String,
    val dark: Boolean,
    val primary: Color,
    val background: Color,
    val backgroundSolid: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceVariant: Color,
    val card: Color,
    val border: Color,
    val bubbleMine: Color,
    val bubbleOther: Color,
    val onBubbleMine: Color,
    val timeMine: Color,
    val timeOther: Color,
    val mediaPlaceholder: Color,
    val text: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val divider: Color,
    val overlay: Color,
    val overlayStroke: Color,
    val backdrop: Boolean = false,
    val square: Boolean = false
)

object Themes {

    const val FLOW = "flow"
    const val DARK = "default"
    const val LIGHT = "light"
    const val SYSTEM = "dynamic"
    const val RETRO = "retro"

    val ORDER = listOf(FLOW, DARK, LIGHT, SYSTEM, RETRO)

    val dark = Palette(
        key = DARK, dark = true,
        primary = Color(0xFF5288C1),
        background = Color(0xFF0E1621), backgroundSolid = Color(0xFF0E1621),
        surface = Color(0xFF17212B), surfaceHigh = Color(0xFF202B36), surfaceVariant = Color(0xFF162431),
        card = Color(0xFF1D2A38), border = Color(0xFF233240),
        bubbleMine = Color(0xFF2B5278), bubbleOther = Color(0xFF182533), onBubbleMine = Color.White,
        timeMine = Color(0xFFB0D4F1), timeOther = Color(0xFF999999),
        mediaPlaceholder = Color(0xFF22303C),
        text = Color.White, textPrimary = Color(0xFFF5F5F5), textSecondary = Color(0xFF7F91A4),
        textMuted = Color(0xFF5E6D7E), textDisabled = Color(0xFF3E4B5E),
        divider = Color(0x14FFFFFF), overlay = Color(0x28FFFFFF), overlayStroke = Color(0x38FFFFFF)
    )

    val light = Palette(
        key = LIGHT, dark = false,
        primary = Color(0xFF5288C1),
        background = Color(0xFFD6DDE3), backgroundSolid = Color(0xFFD6DDE3),
        surface = Color.White, surfaceHigh = Color(0xFFE9EEF2), surfaceVariant = Color(0xFFE9EEF2),
        card = Color.White, border = Color(0xFFC5CDD4),
        bubbleMine = Color(0xFF2B5278), bubbleOther = Color.White, onBubbleMine = Color.White,
        timeMine = Color(0xFFB0D4F1), timeOther = Color(0xFF8A96A3),
        mediaPlaceholder = Color(0xFFC9D3DC),
        text = Color(0xFF0E1621), textPrimary = Color(0xFF0E1621), textSecondary = Color(0xFF475569),
        textMuted = Color(0xFF64748B), textDisabled = Color(0xFFA3AFBC),
        divider = Color(0x1A000000), overlay = Color(0x12000000), overlayStroke = Color(0x22000000)
    )

    val flow = Palette(
        key = FLOW, dark = true,
        primary = Color(0xFF5288C1),
        background = Color.Transparent, backgroundSolid = Color(0xFF04121A),
        surface = Color(0x12FFFFFF), surfaceHigh = Color(0x1FFFFFFF), surfaceVariant = Color(0x1AFFFFFF),
        card = Color(0x17FFFFFF), border = Color(0x2BFFFFFF),
        bubbleMine = Color(0x735288C1), bubbleOther = Color(0x21FFFFFF), onBubbleMine = Color.White,
        timeMine = Color(0xFFB0D4F1), timeOther = Color(0x99FFFFFF),
        mediaPlaceholder = Color(0x1FFFFFFF),
        text = Color.White, textPrimary = Color.White, textSecondary = Color(0xB8FFFFFF),
        textMuted = Color(0x85FFFFFF), textDisabled = Color(0x4DFFFFFF),
        divider = Color(0x1FFFFFFF), overlay = Color(0x28FFFFFF), overlayStroke = Color(0x38FFFFFF),
        backdrop = true
    )

    val retro = Palette(
        key = RETRO, dark = false,
        primary = Color(0xFF245EDB),
        background = Color(0xFFC7D7EE), backgroundSolid = Color(0xFFC7D7EE),
        surface = Color(0xFFDCE6F4), surfaceHigh = Color(0xFFC4D5EE), surfaceVariant = Color(0xFFB3C9EA),
        card = Color(0xFFE6EEF9), border = Color(0xFF7F9DB9),
        bubbleMine = Color(0xFFF3F7FD), bubbleOther = Color(0xFFDDE8F7), onBubbleMine = Color(0xFF10203A),
        timeMine = Color(0xFF4F6E9C), timeOther = Color(0xFF4F6E9C),
        mediaPlaceholder = Color(0xFFB3C9EA),
        text = Color(0xFF10203A), textPrimary = Color(0xFF10203A), textSecondary = Color(0xFF3D5E8C),
        textMuted = Color(0xFF5F7899), textDisabled = Color(0xFF9FB3CF),
        divider = Color(0x1A000000), overlay = Color(0x12000000), overlayStroke = Color(0x22000000),
        square = true
    )

    fun of(key: String?): Palette = when (key) {
        FLOW -> flow
        LIGHT -> light
        RETRO -> retro
        SYSTEM -> system() ?: dark
        else -> dark
    }

    val systemAvailable: Boolean by lazy { accent() != null }

    private val macAccents = mapOf(
        -1 to Color(0xFF8C8C8C), 0 to Color(0xFFFF5257), 1 to Color(0xFFF7821B), 2 to Color(0xFFFFC600),
        3 to Color(0xFF62BA46), 4 to Color(0xFF007AFF), 5 to Color(0xFFA550A7), 6 to Color(0xFFF74F9E)
    )

    private val gnomeAccents = mapOf(
        "blue" to Color(0xFF3584E4), "teal" to Color(0xFF2190A4), "green" to Color(0xFF3A944A), "yellow" to Color(0xFFC88800),
        "orange" to Color(0xFFED5B00), "red" to Color(0xFFE62D42), "pink" to Color(0xFFD56199), "purple" to Color(0xFF9141AC),
        "slate" to Color(0xFF6F8396)
    )

    private fun gsetting(key: String): String? =
        Platform.run("gsettings", "get", "org.gnome.desktop.interface", key)
            ?.takeIf { it.first == 0 }?.second?.trim()?.trim('\'')

    private fun accent(): Color? = when (Platform.os) {
        Platform.Os.WINDOWS -> runCatching {
            val abgr = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\DWM", "AccentColor")
            Color(red = abgr and 0xFF, green = (abgr shr 8) and 0xFF, blue = (abgr shr 16) and 0xFF)
        }.getOrNull()
        Platform.Os.MAC -> {
            val v = Platform.run("defaults", "read", "-g", "AppleAccentColor")
            macAccents[v?.takeIf { it.first == 0 }?.second?.trim()?.toIntOrNull() ?: 4] ?: macAccents[4]
        }
        Platform.Os.LINUX -> gnomeAccents[gsetting("accent-color") ?: "blue"] ?: gnomeAccents["blue"]
        Platform.Os.OTHER -> null
    }

    private fun systemDark(): Boolean = when (Platform.os) {
        Platform.Os.WINDOWS -> runCatching {
            Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme"
            ) == 0
        }.getOrDefault(true)
        Platform.Os.MAC -> Platform.run("defaults", "read", "-g", "AppleInterfaceStyle")?.second?.contains("Dark") == true
        Platform.Os.LINUX -> gsetting("color-scheme")?.contains("dark") ?: (gsetting("gtk-theme")?.contains("dark", ignoreCase = true) ?: true)
        Platform.Os.OTHER -> true
    }

    private fun mix(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t
    )

    private fun system(): Palette? {
        val accent = accent() ?: return null
        val base = if (systemDark()) dark else light
        return Palette(
            key = SYSTEM, dark = base.dark,
            primary = accent,
            background = base.background, backgroundSolid = base.backgroundSolid,
            surface = base.surface, surfaceHigh = base.surfaceHigh, surfaceVariant = base.surfaceVariant,
            card = base.card, border = base.border,
            bubbleMine = mix(accent, Color.Black, if (base.dark) 0.45f else 0.25f),
            bubbleOther = base.bubbleOther, onBubbleMine = Color.White,
            timeMine = mix(accent, Color.White, 0.6f), timeOther = base.timeOther,
            mediaPlaceholder = base.mediaPlaceholder,
            text = base.text, textPrimary = base.textPrimary, textSecondary = base.textSecondary,
            textMuted = base.textMuted, textDisabled = base.textDisabled,
            divider = base.divider, overlay = base.overlay, overlayStroke = base.overlayStroke
        )
    }
}

object SinusColors {
    var palette: Palette by mutableStateOf(Themes.dark)

    val primary: Color get() = palette.primary
    val background: Color get() = palette.background
    val backgroundSolid: Color get() = palette.backgroundSolid
    val surface: Color get() = palette.surface
    val surfaceHigh: Color get() = palette.surfaceHigh
    val surfaceVariant: Color get() = palette.surfaceVariant
    val card: Color get() = palette.card
    val border: Color get() = palette.border
    val bubbleMine: Color get() = palette.bubbleMine
    val bubbleOther: Color get() = palette.bubbleOther
    val onBubbleMine: Color get() = palette.onBubbleMine
    val mediaPlaceholder: Color get() = palette.mediaPlaceholder
    val text: Color get() = palette.text
    val textPrimary: Color get() = palette.textPrimary
    val textSecondary: Color get() = palette.textSecondary
    val textMuted: Color get() = palette.textMuted
    val textDisabled: Color get() = palette.textDisabled
    val divider: Color get() = palette.divider
    val onAccent = Color.White
    val error = Color(0xFFE74C3C)
    val success = Color(0xFF2ECC71)
    val online = Color(0xFF3FB97A)
    val active = Color(0xFF2ECC71)
    val warning = Color(0xFFE0A030)
    val danger = Color(0xFFE5484D)

    fun radius(r: Dp): Dp = if (palette.square) minOf(r, 2.dp) else r
}

private val spots = arrayOf(
    floatArrayOf(1.20f, 0.55f, 0.00f, 0.00f, 0.16f, 0.55f),
    floatArrayOf(0.90f, 0.45f, 0.55f, 0.06f, 0.08f, 0.60f),
    floatArrayOf(0.75f, 0.30f, 1.00f, 0.42f, 0.11f, 0.70f),
    floatArrayOf(0.85f, 0.30f, 0.05f, 0.80f, 0.10f, 0.70f),
    floatArrayOf(0.90f, 0.35f, 0.90f, 1.00f, 0.07f, 0.70f)
)

@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawRect(Brush.verticalGradient(0f to Color(0xFF07202B), 0.35f to Color(0xFF04121A), 1f to Color(0xFF04121A)))
        for (s in spots) {
            val c = Offset(w * s[2], h * s[3])
            val rx = w * s[0]
            val ry = h * s[1]
            val glow = Color(red = 42, green = 224, blue = 245, alpha = (255 * s[4]).toInt())
            scale(scaleX = rx / ry, scaleY = 1f, pivot = c) {
                drawCircle(
                    Brush.radialGradient(0f to glow, s[5] to glow.copy(alpha = 0f), center = c, radius = ry),
                    radius = ry, center = c
                )
            }
        }
    }
}

@Composable
fun SinusTheme(content: @Composable () -> Unit) {
    val p = SinusColors.palette
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.primary,
            onPrimary = SinusColors.onAccent,
            background = p.backgroundSolid,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.surfaceHigh,
            onSurfaceVariant = p.textSecondary,
            error = SinusColors.error
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            onPrimary = SinusColors.onAccent,
            background = p.backgroundSolid,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.surfaceHigh,
            onSurfaceVariant = p.textSecondary,
            error = SinusColors.error
        )
    }
    if (p.key == Themes.RETRO) {
        val f = pl.sinusoide.sinuschat.desktop.ui.xp.XpStyle.appFont
        val t = androidx.compose.material3.Typography()
        val retro = androidx.compose.material3.Typography(
            displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
            displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
            headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
            titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
            titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
            bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
            labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
            labelSmall = t.labelSmall.copy(fontFamily = f)
        )
        MaterialTheme(colorScheme = scheme, typography = retro, content = content)
    } else {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
