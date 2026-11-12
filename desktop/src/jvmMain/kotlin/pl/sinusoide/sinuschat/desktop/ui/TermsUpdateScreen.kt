package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.TermsStrings
import pl.sinusoide.sinuschat.desktop.i18n.TermsUpdateStrings
import pl.sinusoide.sinuschat.desktop.installer.DocumentDraw
import pl.sinusoide.sinuschat.desktop.legal.TermsGate

@Composable
fun TermsUpdateScreen(language: AppLanguage, onAccept: () -> Unit, onQuit: () -> Unit) {
    val s = remember(language) { TermsUpdateStrings.of(language.chat) }
    val terms = remember(language) { TermsStrings.of(language.chat) }
    val first = remember { TermsGate.accepted() == null }
    val newest = remember { TermsGate.newest() }
    var agreed by remember { mutableStateOf(false) }
    var declined by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(SinusColors.backgroundSolid)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        if (SinusColors.palette.backdrop) ThemeBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier.widthIn(max = 580.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DocumentDraw(SinusColors.textPrimary, Modifier.size(150.dp))
            Text(
                if (first) s.firstTitle else s.title,
                color = SinusColors.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                if (first) s.firstDesc else s.desc,
                color = SinusColors.textSecondary, fontSize = 14.5.sp, lineHeight = 21.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
            )
            if (!first) {
                Text(
                    s.date(formatDate(newest, s.locale)),
                    color = SinusColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(14.dp))
                        .background(SinusColors.primary.copy(alpha = 0.13f)).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            AgreeCard(agreed, terms, language) { agreed = it }
            AnimatedVisibility(declined, enter = fadeIn(tween(220))) {
                Text(
                    s.declined, color = SinusColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                GateButton(if (declined) s.close else s.decline, primary = false) {
                    if (declined) onQuit() else declined = true
                }
                GateButton(s.accept, primary = true, enabled = agreed) {
                    TermsGate.accept()
                    onAccept()
                }
            }
        }
    }
}

private fun formatDate(version: String, locale: String): String = runCatching {
    java.time.LocalDate.parse(version).format(
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
            .withLocale(java.util.Locale.forLanguageTag(locale))
    )
}.getOrDefault(version)

@Composable
private fun AgreeCard(agreed: Boolean, terms: TermsStrings, language: AppLanguage, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(18.dp))
    val docs = TermsGate.DOCS + "?lang=" + if (language == AppLanguage.PL) "pl" else "en"
    val link = terms.docsLink
    val full = terms.docs(link)
    val at = full.indexOf(link)
    val text = buildAnnotatedString {
        if (at < 0) {
            append(full)
        } else {
            append(full.substring(0, at))
            withLink(LinkAnnotation.Url(docs, TextLinkStyles(SpanStyle(color = SinusColors.primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)))) {
                append(link)
            }
            append(full.substring(at + link.length))
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(SinusColors.card)
            .border(if (agreed) 2.dp else 1.dp, if (agreed) SinusColors.primary else SinusColors.border, shape)
            .clickable { onChange(!agreed) }.pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        Checkbox(
            agreed, onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = SinusColors.primary, uncheckedColor = SinusColors.textSecondary, checkmarkColor = Color.White)
        )
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(terms.agree, color = SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text(text, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun GateButton(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Text(
        label, color = if (primary) Color.White else SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }.clip(shape)
            .background(if (primary) SinusColors.primary else Color.Transparent)
            .then(if (!primary) Modifier.border(1.dp, SinusColors.border, shape) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}
