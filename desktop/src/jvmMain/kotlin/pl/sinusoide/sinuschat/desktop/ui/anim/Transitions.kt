package pl.sinusoide.sinuschat.desktop.ui.anim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

object Anim {
    val barIn: EnterTransition = expandVertically(tween(220, easing = Motion.enter)) + fadeIn(tween(200))
    val barOut: ExitTransition = shrinkVertically(tween(180, easing = Motion.ease)) + fadeOut(tween(150))
    val popIn: EnterTransition = fadeIn(tween(200)) + scaleIn(tween(200, easing = Motion.enter), initialScale = 0.6f)
    val popOut: ExitTransition = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.6f)
    val dialogIn: EnterTransition = fadeIn(tween(250)) + scaleIn(tween(250, easing = Motion.enter), initialScale = 0.9f)
    val dialogOut: ExitTransition = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.95f)
    val toastIn: EnterTransition = fadeIn(tween(220)) + slideInVertically(tween(260, easing = Motion.enter)) { it / 2 }
    val toastOut: ExitTransition = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
}

@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

@Composable
fun Modifier.dialogIn(durationMs: Int = 250): Modifier {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(durationMs, easing = Motion.enter)) }
    return graphicsLayer {
        alpha = k.value
        val s = 0.94f + 0.06f * k.value
        scaleX = s
        scaleY = s
    }
}

@Composable
fun Reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit, content = content)
}
