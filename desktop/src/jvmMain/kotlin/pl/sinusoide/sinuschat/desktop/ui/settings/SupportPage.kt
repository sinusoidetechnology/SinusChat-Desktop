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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.SupportStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon

private const val PATREON_URL = "https://www.patreon.com/cw/sinusoidetechnology"
private const val KOFI_URL = "https://ko-fi.com/sinusoide"
private const val PAGE_URL = "https://sinusoide.pl/wsparcie.html"
private const val RECIPIENT = "Błażej Stosio"
private const val ACCOUNT = "57 1140 2004 0000 3702 8433 6549"
private const val IBAN = "PL57 1140 2004 0000 3702 8433 6549"

private val sRed = Color(0xFFE5484D)
private val sOrange = Color(0xFFE67E22)
private val sGreen = Color(0xFF3FB97A)
private val sPurple = Color(0xFF9B6BE0)
private val sTeal = Color(0xFF26A5B8)

private fun copyText(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}

@Composable
internal fun SupportPage(onToast: (String) -> Unit) {
    val chat = LocalChatStrings.current
    val ss = remember(chat) { SupportStrings.of(chat) }
    val uri = LocalUriHandler.current
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(20.dp))).background(SinusColors.card)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(sRed.copy(alpha = 0x26 / 255f)), contentAlignment = Alignment.Center) {
            SinusIcon("ic_heart", sRed, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(ss.eyebrow.uppercase(), color = SinusColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp, textAlign = TextAlign.Center)
        Text(ss.heroTitle, color = SinusColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(ss.heroDesc, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
    SectionTitle(ss.sectionWays)
    MethodCard("ic_patreon", sOrange, ss.patreonTitle, ss.badgeMonthly, ss.patreonDesc, ss.patreonButton, ss.patreonNote) { uri.openUri(PATREON_URL) }
    Spacer(Modifier.height(12.dp))
    val fields = listOf(
        ss.bankRecipient to RECIPIENT,
        ss.bankAccount to ACCOUNT,
        ss.bankIban to IBAN,
        ss.bankTransferTitle to ss.bankTransferTitleValue
    )
    SupportCard {
        CardHeader("ic_document", sGreen, ss.bankTitle, ss.badgeFree)
        Body(ss.bankDesc, Modifier.padding(top = 10.dp))
        Column(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(14.dp)))
                .background(SinusColors.textPrimary.copy(alpha = 0x0F / 255f))
        ) {
            fields.forEachIndexed { i, (label, value) ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(SinusColors.divider))
                Row(
                    Modifier.fillMaxWidth().clickable { copyText(value); onToast(ss.copied) }.pointerHoverIcon(PointerIcon.Hand)
                        .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = SinusColors.textSecondary, fontSize = 11.5.sp)
                        Text(value, color = SinusColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        SinusIcon("ic_copy", SinusColors.primary, Modifier.size(20.dp), ss.copy)
                    }
                }
            }
        }
        ActionButton(ss.bankCopyAll, Modifier.padding(top = 14.dp)) {
            copyText(fields.joinToString("\n") { "${it.first}: ${it.second}" })
            onToast(ss.copied)
        }
        for (hint in listOf(ss.bankHintPl, ss.bankHintAbroad, ss.bankHintAmount, ss.bankHintNick)) Note(hint, Modifier.padding(top = 8.dp))
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(12.dp)))
                .background(sOrange.copy(alpha = 0x1F / 255f)).clickable { uri.openUri(PAGE_URL) }.pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            SinusIcon("ic_shield", sOrange, Modifier.padding(top = 1.dp, end = 10.dp).size(18.dp))
            Text(ss.bankWarning, color = SinusColors.textPrimary, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    MethodCard("ic_heart", sRed, ss.kofiTitle, ss.badgeOnce, ss.kofiDesc, ss.kofiButton, ss.kofiNote) { uri.openUri(KOFI_URL) }

    SectionTitle(ss.sectionCosts)
    Text(ss.costsIntro, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp))
    Card {
        val items = listOf(
            Triple("ic_harddrive", ss.costServer, ss.costServerDesc),
            Triple("ic_call_video_on", ss.costCalls, ss.costCallsDesc),
            Triple("ic_storage", ss.costFiles, ss.costFilesDesc),
            Triple("ic_notifications", ss.costPush, ss.costPushDesc),
            Triple("ic_internet", ss.costDomain, ss.costDomainDesc),
            Triple("ic_code", ss.costDev, ss.costDevDesc)
        )
        val colors = listOf(SinusColors.primary, sGreen, sPurple, sOrange, sTeal, sRed)
        items.forEachIndexed { i, (icon, title, desc) ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)) {
                IconTile(icon, colors[i])
                Column(Modifier.weight(1f)) {
                    Text(title, color = SinusColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(desc, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
    Text(
        ss.thanks, color = SinusColors.textSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 18.dp)
    )
}

@Composable
private fun SupportCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(18.dp))).background(SinusColors.card).padding(16.dp)) {
        content()
    }
}

@Composable
private fun MethodCard(icon: String, color: Color, title: String, badge: String, desc: String, button: String, note: String, onClick: () -> Unit) {
    SupportCard {
        CardHeader(icon, color, title, badge)
        Body(desc, Modifier.padding(top = 10.dp))
        ActionButton(button, Modifier.padding(top = 14.dp), onClick)
        Note(note, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun CardHeader(icon: String, color: Color, title: String, badge: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, color)
        Text(title, color = SinusColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            badge, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0x26 / 255f)).padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun Body(text: String, modifier: Modifier) {
    Text(text, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = modifier)
}

@Composable
private fun Note(text: String, modifier: Modifier) {
    Text(text, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = modifier.alpha(0.85f))
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(SinusColors.radius(14.dp))).background(SinusColors.primary)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = SinusColors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
