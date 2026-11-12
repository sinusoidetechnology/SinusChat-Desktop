package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.ManualAddress
import pl.sinusoide.sinuschat.desktop.ManualResult
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.i18n.LocalConnStrings
import pl.sinusoide.sinuschat.desktop.net.FirewallCheck
import pl.sinusoide.sinuschat.desktop.net.FirewallMonitor
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon

private val warn = Color(0xFFE67E22)

@Composable
fun ConnectionHelpDialog(link: PhoneLink, firewall: FirewallMonitor, onDismiss: () -> Unit) {
    val s = LocalConnStrings.current
    val fw by firewall.state.collectAsState()
    val manual by link.manual.collectAsState()
    val manualResult by link.manualResult.collectAsState()
    var input by remember { mutableStateOf(manual?.toString().orEmpty()) }
    var invalid by remember { mutableStateOf(false) }

    fun submit() {
        val parsed = ManualAddress.parse(input)
        invalid = parsed == null
        if (parsed != null) link.setManualAddress(parsed)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp)
                .clip(RoundedCornerShape(20.dp)).background(SinusColors.card)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, color = SinusColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconAction("ic_close", s.close, SinusColors.textSecondary, onClick = onDismiss)
            }
            Spacer(Modifier.height(10.dp))

            when (val st = fw) {
                is FirewallMonitor.Ui.Checking -> ProgressLine(s.checking)
                is FirewallMonitor.Ui.Fixing -> ProgressLine(s.checking)
                is FirewallMonitor.Ui.Done -> FirewallSection(st.result, null, firewall, link)
                is FirewallMonitor.Ui.Fixed -> FirewallSection(st.result, st.ok, firewall, link)
                FirewallMonitor.Ui.Idle -> Unit
            }

            Spacer(Modifier.height(14.dp))
            Text(s.manualTitle, color = SinusColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SinusColors.surfaceHigh)
                        .border(1.dp, if (invalid) SinusColors.error else SinusColors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    if (input.isEmpty()) Text(s.manualPlaceholder, color = SinusColors.textSecondary, fontSize = 15.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it.take(64); invalid = false },
                        singleLine = true,
                        textStyle = TextStyle(color = SinusColors.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(SinusColors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(10.dp))
                SetupButton(s.connect, Modifier.width(130.dp)) { submit() }
            }
            Spacer(Modifier.height(6.dp))
            Text(s.manualHint, color = SinusColors.textSecondary, fontSize = 13.sp)
            val status = when {
                invalid -> s.invalid to SinusColors.error
                manual == null -> null
                manualResult == ManualResult.TRYING -> s.trying(manual.toString()) to SinusColors.textSecondary
                manualResult == ManualResult.TIMEOUT -> s.timeout to warn
                manualResult == ManualResult.REFUSED -> s.refused to warn
                manualResult == ManualResult.UNREACHABLE -> s.unreachable to warn
                manualResult == ManualResult.REJECTED -> s.rejected to warn
                else -> null
            }
            status?.let { (text, color) ->
                Spacer(Modifier.height(8.dp))
                Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (manual != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    s.forget, color = SinusColors.primary, fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        input = ""
                        link.setManualAddress(null)
                    }.pointerHoverIcon(PointerIcon.Hand)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(s.checklistTitle, color = SinusColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            for (line in listOf(s.checkWifi, s.checkApp, s.checkVpn)) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(SinusColors.primary))
                    Spacer(Modifier.width(10.dp))
                    Text(line, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = SinusColors.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = SinusColors.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun FirewallSection(result: FirewallCheck.Result?, fixed: Boolean?, firewall: FirewallMonitor, link: PhoneLink) {
    val s = LocalConnStrings.current
    if (fixed == true) {
        Text(s.firewallFixed, color = SinusColors.success, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
    } else if (fixed == false) {
        Text(s.firewallFailed, color = SinusColors.error, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
    }
    if (result == null) return
    if (result.blocked) {
        WarningBox(s.firewallTitle, s.firewallDesc) {
            SetupButton(s.firewallFix, Modifier.width(150.dp)) { firewall.fix { link.setManualAddress(link.manual.value) } }
            Spacer(Modifier.width(10.dp))
            Text(
                s.firewallOpen, color = SinusColors.primary, fontSize = 14.sp,
                modifier = Modifier.clickable { FirewallCheck.openSettings() }.pointerHoverIcon(PointerIcon.Hand)
            )
        }
        Spacer(Modifier.height(10.dp))
    }
    if (result.publicNetwork) {
        WarningBox(s.publicTitle, s.publicDesc) {
            Text(
                s.publicOpen, color = SinusColors.primary, fontSize = 14.sp,
                modifier = Modifier.clickable { FirewallCheck.openNetworkSettings() }.pointerHoverIcon(PointerIcon.Hand)
            )
        }
    }
}

@Composable
private fun WarningBox(title: String, text: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(warn.copy(alpha = 0.12f))
            .border(1.dp, warn.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SinusIcon("ic_warning", warn, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = SinusColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) { actions() }
    }
}

@Composable
fun issueText(firewall: FirewallMonitor): String? {
    val s = LocalConnStrings.current
    val st by firewall.state.collectAsState()
    val result = when (val v = st) {
        is FirewallMonitor.Ui.Done -> v.result
        is FirewallMonitor.Ui.Fixed -> v.result
        else -> null
    } ?: return null
    return when {
        result.blocked -> s.firewallTitle
        result.publicNetwork -> s.publicTitle
        else -> null
    }
}

@Composable
fun TroubleLink(issue: String?, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalConnStrings.current
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onHelp).pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (issue != null) {
            SinusIcon("ic_warning", warn, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(issue, color = warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text(s.trouble, color = SinusColors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
