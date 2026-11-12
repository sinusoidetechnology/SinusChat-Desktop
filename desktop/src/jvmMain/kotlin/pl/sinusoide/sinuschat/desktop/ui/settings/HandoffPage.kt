package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.HandoffStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.PhoneTap
import pl.sinusoide.sinuschat.link.LinkFrame

internal fun handoffTitle(hs: HandoffStrings, section: String): String = when (section) {
    LinkFrame.SECTION_ACCOUNT -> hs.account
    LinkFrame.SECTION_PRIVACY -> hs.privacy
    LinkFrame.SECTION_INTERNET -> hs.internet
    LinkFrame.SECTION_BACKUP -> hs.backup
    else -> hs.report
}

@Composable
internal fun HandoffPage(link: PhoneLink, store: ChatStore?, section: String) {
    val chat = LocalChatStrings.current
    val hs = remember(chat) { HandoffStrings.of(chat) }
    val state by link.state.collectAsState()
    val connected = state is LinkState.Connected
    LaunchedEffect(section, connected) { if (connected) store?.openOnPhone(section) }
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(196.dp).clip(CircleShape).background(SinusColors.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            PhoneTap(SinusColors.textPrimary, SinusColors.primary, Modifier.size(132.dp))
        }
        Text(
            handoffTitle(hs, section), color = SinusColors.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 28.sp, modifier = Modifier.padding(top = 26.dp, start = 24.dp, end = 24.dp)
        )
        Text(
            if (connected) hs.hint else hs.offline,
            color = if (connected) SinusColors.textSecondary else SinusColors.danger, fontSize = 14.sp, lineHeight = 20.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 32.dp, end = 32.dp)
                .clip(RoundedCornerShape(SinusColors.radius(12.dp)))
        )
    }
}
