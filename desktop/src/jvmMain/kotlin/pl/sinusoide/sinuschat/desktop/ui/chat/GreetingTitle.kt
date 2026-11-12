package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

internal object Greeting {
    private val startedAt = System.currentTimeMillis()
    private const val WINDOW_MS = 90_000L
    @Volatile var done = false

    fun due(): Boolean = !done && System.currentTimeMillis() - startedAt < WINDOW_MS

    fun pick(templates: Map<String, String>, name: String, now: LocalDateTime = LocalDateTime.now(), random: Random = Random.Default): String? {
        val hour = now.hour
        val keys = mutableListOf<String>()
        if (hour < 6 || hour >= 22) keys += "night"
        if (hour in 6..11) keys += "morning"
        if (hour in 12..17) keys += "afternoon"
        if (hour in 18..21) keys += "evening"
        if (hour >= 16) keys += "day_status"
        when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> keys += "monday"
            DayOfWeek.TUESDAY -> if (random.nextBoolean()) keys += "tuesday"
            DayOfWeek.WEDNESDAY -> if (random.nextBoolean()) keys += "wednesday"
            DayOfWeek.THURSDAY -> keys += "thursday"
            DayOfWeek.FRIDAY -> {
                keys += "friday"
                if (random.nextBoolean()) keys += "friday_feeling"
            }
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> {
                keys += if (now.dayOfWeek == DayOfWeek.SATURDAY) "saturday" else "sunday"
                if (random.nextBoolean()) keys += "weekend"
            }
            else -> Unit
        }
        keys += listOf("hey", "hi", "how_going", "welcome", "whats_new")
        val options = keys.mapNotNull { templates[it] }
        if (options.isEmpty()) return null
        return options[random.nextInt(options.size)].replace("%s", name)
    }
}

@Composable
internal fun GreetingTitle(title: String, name: String?, templates: Map<String, String>, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    val alpha = remember { Animatable(1f) }
    val scroll = rememberScrollState()
    val speed = with(LocalDensity.current) { 70.dp.toPx() }
    val first = name?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotEmpty() }
    LaunchedEffect(first) {
        if (first == null || !Greeting.due()) return@LaunchedEffect
        val text = Greeting.pick(templates, first) ?: return@LaunchedEffect
        Greeting.done = true
        alpha.snapTo(0f)
        shown = text
        scroll.scrollTo(0)
        alpha.animateTo(1f, tween(500))
        val travel = scroll.maxValue
        if (travel > 2) {
            delay(900)
            val ms = (travel / speed * 1000f).toInt().coerceIn(900, 3200)
            scroll.animateScrollTo(travel, tween(ms, easing = FastOutSlowInEasing))
            delay(1400)
        } else {
            delay(3000)
        }
        alpha.animateTo(0f, tween(500))
        shown = null
        scroll.scrollTo(0)
        alpha.animateTo(1f, tween(500))
    }
    val greeting = shown
    Box(modifier.clipToBounds().alpha(alpha.value)) {
        if (greeting != null) {
            Box(Modifier.horizontalScroll(scroll, enabled = false)) {
                Text(
                    greeting, color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false
                )
            }
        } else {
            Text(
                title, color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
