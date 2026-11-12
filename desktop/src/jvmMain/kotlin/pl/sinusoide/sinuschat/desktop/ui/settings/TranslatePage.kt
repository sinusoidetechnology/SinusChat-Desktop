package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.TranslateStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.chat.langName
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.link.LinkFrame

private val mint = Color(0xFF2FA88F)

@Composable
internal fun TranslatePage(link: PhoneLink, store: ChatStore, onToast: (String) -> Unit) {
    val chat = LocalChatStrings.current
    val ts = remember(chat) { TranslateStrings.of(chat) }
    val info by store.trInfo.collectAsState()
    val chats by store.chats.collectAsState()
    val state by link.state.collectAsState()
    val connected = state is LinkState.Connected
    LaunchedEffect(connected) { if (connected) store.requestTranslateInfo() }
    fun nameOf(conv: String, fallback: String) = chats.firstOrNull { it.id == conv }?.title?.ifBlank { null } ?: fallback.ifBlank { chat.contact }

    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(20.dp))).background(SinusColors.card)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(mint.copy(alpha = 0x26 / 255f)), contentAlignment = Alignment.Center) {
            SinusIcon("ic_translate", mint, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(ts.heroTitle, color = SinusColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(ts.heroDesc, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(14.dp)))
            .background(SinusColors.primary.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon("ic_pc", SinusColors.primary, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(ts.phoneNote, color = SinusColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
    }
    val i = info
    if (i == null) {
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (connected) CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            Text(
                if (connected) ts.waiting else pl.sinusoide.sinuschat.desktop.i18n.HandoffStrings.of(chat).offline,
                color = SinusColors.textSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
            )
        }
        return
    }
    if (!i.supported) {
        Text(ts.unsupported, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        return
    }
    SectionTitle(ts.sectionOptions)
    Card {
        SwitchRow("ic_translate", mint, ts.enabled, ts.enabledDesc, i.enabled) { on -> store.translateSet(LinkFrame.TranslateSet(enabled = on)) }
        RowDivider()
        SwitchRow("ic_send", SinusColors.primary, ts.askSend, ts.askSendDesc, i.askSend, enabled = i.enabled) { on ->
            store.translateSet(LinkFrame.TranslateSet(askSend = on))
        }
    }
    SectionTitle(ts.sectionLangs)
    Card {
        i.langs.forEachIndexed { idx, l ->
            if (idx > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(SinusColors.textSecondary.copy(alpha = 0x1F / 255f)),
                    contentAlignment = Alignment.Center
                ) { Flag(if (l.code == "zh") "zh-TW" else l.code, Modifier.size(24.dp, 16.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(langName(ts, l.code), color = SinusColors.textPrimary, fontSize = 15.5.sp)
                    Text(
                        if (l.ready) ts.langReady(l.mb) else ts.langMissing(l.mb),
                        color = if (l.ready) mint else SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (l.ready) SinusIcon("ic_check", mint, Modifier.size(20.dp))
            }
        }
    }
    SectionTitle(ts.decisions)
    if (i.decisions.isEmpty()) {
        Text(ts.noDecisions, color = SinusColors.textSecondary, fontSize = 13.5.sp, modifier = Modifier.padding(start = 6.dp))
    } else {
        Card {
            i.decisions.forEachIndexed { idx, d ->
                if (idx > 0) RowDivider()
                val name = nameOf(d.conv, d.name)
                Row(
                    Modifier.fillMaxWidth().clickable { store.translateSet(LinkFrame.TranslateSet(conv = d.conv, lang = d.lang, yes = !d.yes)) }
                        .pointerHoverIcon(PointerIcon.Hand).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(if (d.yes) "ic_translate" else "ic_block", if (d.yes) mint else SinusColors.textSecondary)
                    Column(Modifier.weight(1f)) {
                        Text(name, color = SinusColors.textPrimary, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(langName(ts, d.lang) + " > " + langName(ts, i.appLang), color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(
                        if (d.yes) ts.translates else ts.notTranslates,
                        color = if (d.yes) mint else SinusColors.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background((if (d.yes) mint else SinusColors.textSecondary).copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
    SectionTitle(ts.sectionBlocked)
    if (i.blocked.isEmpty()) {
        Text(ts.blockedEmpty, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp))
    } else {
        Card {
            i.blocked.forEachIndexed { idx, b ->
                if (idx > 0) RowDivider()
                val name = nameOf(b.conv, b.name)
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile("ic_translate", SinusColors.textSecondary)
                    Text(name, color = SinusColors.textPrimary, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        ts.unblock, color = SinusColors.primary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                            store.translateSet(LinkFrame.TranslateSet(unblock = b.conv))
                            onToast(ts.unblocked(name))
                        }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
    Text(
        ts.footer, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp)
    )
}
