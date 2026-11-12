package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings
import pl.sinusoide.sinuschat.desktop.appIcon
import pl.sinusoide.sinuschat.desktop.ui.chat.ChatList
import pl.sinusoide.sinuschat.desktop.ui.chat.ChatView
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.desktop.ui.anim.Anim
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion

@Composable
fun MainScreen(
    state: LinkState,
    store: ChatStore,
    language: AppLanguage,
    focused: Boolean,
    onLanguage: (AppLanguage) -> Unit,
    onUnpair: () -> Unit,
    onHelp: () -> Unit = {},
    issue: String? = null,
    onSettings: () -> Unit = {}
) {
    if (state is LinkState.Searching && state.rejected) {
        SearchingScreen(state, onUnpair)
        return
    }
    val chats by store.chats.collectAsState()
    val hidePreviews by store.hidePreviews.collectAsState()
    val openConv by store.openConv.collectAsState()
    val locked by store.phoneLocked.collectAsState()
    val connected = state is LinkState.Connected

    Column(Modifier.fillMaxSize().background(SinusColors.background)) {
        AnimatedVisibility(visible = !connected, enter = Anim.barIn, exit = Anim.barOut) {
            StatusBanner(LocalChatStrings.current.offline, searching = true) {
                Spacer(Modifier.width(14.dp))
                TroubleLink(issue, onHelp)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ChatList(
                store = store,
                chats = chats,
                hidePreviews = hidePreviews,
                openConv = openConv,
                locale = language.locale,
                onOpen = store::open,
                actions = { IconAction("ic_settings", LocalSettingsStrings.current.settingsTitle, SinusColors.textSecondary, onClick = onSettings) },
                modifier = Modifier.width(340.dp).fillMaxHeight()
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(SinusColors.border))
            val pane = when {
                connected && locked -> PANE_LOCKED
                else -> openConv ?: PANE_EMPTY
            }
            AnimatedContent(
                targetState = pane,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(260, easing = Motion.enter)) { it / 24 }) togetherWith
                        fadeOut(tween(120))
                },
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { p ->
                when (p) {
                    PANE_LOCKED -> LockedPane()
                    PANE_EMPTY -> EmptyPane()
                    else -> ChatView(store, chats.firstOrNull { it.id == p }, p, focused && connected, language.locale)
                }
            }
        }
    }
}

private const val PANE_LOCKED = "\u0000locked"
private const val PANE_EMPTY = "\u0000empty"

@Composable
private fun StatusBanner(text: String, searching: Boolean, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(SinusColors.surfaceHigh).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (searching) {
            CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = SinusColors.text, fontSize = 13.sp)
        trailing()
    }
}

@Composable
private fun EmptyPane() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LogoStatic(Modifier.size(120.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            LocalChatStrings.current.selectChat,
            color = SinusColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(SinusColors.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LogoStatic(modifier: Modifier) {
    val icon = remember { appIcon() }
    Image(icon, null, modifier)
}

@Composable
private fun LockedPane() {
    val s = LocalChatStrings.current
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(SinusColors.card),
            contentAlignment = Alignment.Center
        ) {
            SinusIcon("ic_lock", SinusColors.primary, Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(s.phoneLockedTitle, color = SinusColors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            s.phoneLockedHint, color = SinusColors.textSecondary, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}
