package pl.sinusoide.sinuschat.desktop.ui.settings

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.LangStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.anim.dialogIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color, rotation: Float = -PI.toFloat() / 2f) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.382f
        val a = rotation + i * PI.toFloat() / 5f
        val x = cx + rr * cos(a)
        val y = cy + rr * sin(a)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

@Composable
internal fun Flag(code: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(h * 0.12f))) }
        clipPath(clip) {
            when (code) {
                "pl" -> {
                    drawRect(Color.White, size = Size(w, h / 2f))
                    drawRect(Color(0xFFDC143C), Offset(0f, h / 2f), Size(w, h / 2f))
                }
                "de" -> {
                    drawRect(Color(0xFF000000), size = Size(w, h / 3f))
                    drawRect(Color(0xFFDD0000), Offset(0f, h / 3f), Size(w, h / 3f))
                    drawRect(Color(0xFFFFCE00), Offset(0f, 2 * h / 3f), Size(w, h / 3f))
                }
                "uk" -> {
                    drawRect(Color(0xFF0057B7), size = Size(w, h / 2f))
                    drawRect(Color(0xFFFFD700), Offset(0f, h / 2f), Size(w, h / 2f))
                }
                "cs" -> {
                    drawRect(Color.White, size = Size(w, h / 2f))
                    drawRect(Color(0xFFD7141A), Offset(0f, h / 2f), Size(w, h / 2f))
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w / 2f, h / 2f); lineTo(0f, h); close() }, Color(0xFF11457E))
                }
                "en" -> {
                    drawRect(Color(0xFF012169))
                    val diag = h * 0.2f
                    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), diag)
                    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), diag)
                    drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), diag / 3f)
                    drawLine(Color(0xFFC8102E), Offset(w, 0f), Offset(0f, h), diag / 3f)
                    drawRect(Color.White, Offset(w / 2f - h / 6f, 0f), Size(h / 3f, h))
                    drawRect(Color.White, Offset(0f, h / 2f - h / 6f), Size(w, h / 3f))
                    drawRect(Color(0xFFC8102E), Offset(w / 2f - h / 10f, 0f), Size(h / 5f, h))
                    drawRect(Color(0xFFC8102E), Offset(0f, h / 2f - h / 10f), Size(w, h / 5f))
                }
                "zh-TW" -> {
                    drawRect(Color(0xFFEE1C25))
                    val u = h / 20f
                    val yellow = Color(0xFFFFFF00)
                    star(u * 5f, u * 5f, u * 3f, yellow)
                    star(u * 10f, u * 2f, u, yellow, 0.6f)
                    star(u * 12f, u * 4f, u, yellow, 0.2f)
                    star(u * 12f, u * 7f, u, yellow, -0.2f)
                    star(u * 10f, u * 9f, u, yellow, -0.6f)
                }
                else -> drawRect(SinusColors.textMuted)
            }
        }
    }
}

@Composable
private fun FlagBubble(code: String, size: Dp, background: Color) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Flag(code, Modifier.size(size * 0.56f, size * 0.38f).shadow(1.dp, RoundedCornerShape(3.dp)))
    }
}

private fun localName(ls: LangStrings, lang: AppLanguage): String = when (lang) {
    AppLanguage.PL -> ls.polish
    AppLanguage.EN -> ls.english
    AppLanguage.CS -> ls.czech
    AppLanguage.UK -> ls.ukrainian
    AppLanguage.DE -> ls.german
    AppLanguage.ZH -> ls.chinese
}

private fun stringsFor(lang: AppLanguage): LangStrings = LangStrings.of(lang.chat)

@Composable
internal fun LanguagePage(link: PhoneLink, onOverlay: ((@Composable () -> Unit)?) -> Unit) {
    val s = LocalSettingsStrings.current
    val setup by link.setup.collectAsState()
    val chosen = setup.language
    val effective = AppLanguage.effective(chosen)
    val ls = remember(effective) { stringsFor(effective) }
    val system = AppLanguage.system()
    val followsSystem = chosen == null || AppLanguage.fromCode(chosen) == null
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(20.dp))).background(SinusColors.card)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlagBubble(effective.code, 72.dp, SinusColors.primary.copy(alpha = 0x26 / 255f))
        Spacer(Modifier.height(12.dp))
        Text(effective.displayName, color = SinusColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            if (followsSystem) s.langSystemDesc(system.displayName) else ls.heroDesc,
            color = SinusColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
        )
    }
    SectionTitle(ls.section)
    Card {
        LangOption(
            { IconTile("ic_language", SinusColors.primary) },
            s.langSystem, s.langSystemDesc(system.displayName), followsSystem
        ) { link.setLanguage(null) }
        for (lang in AppLanguage.entries) {
            RowDivider()
            val native = lang.displayName
            val local = localName(ls, lang)
            LangOption(
                { Box(Modifier.padding(end = 14.dp).size(40.dp).clip(RoundedCornerShape(SinusColors.radius(12.dp))).background(SinusColors.textSecondary.copy(alpha = 0x1F / 255f)), contentAlignment = Alignment.Center) {
                    Flag(lang.code, Modifier.size(24.dp, 16.dp))
                } },
                native, if (local == native) ls.builtin else local, !followsSystem && chosen == lang.code
            ) {
                if (lang == effective) link.setLanguage(lang.code)
                else onOverlay { ConfirmLanguage(effective, lang, link) { onOverlay(null) } }
            }
        }
    }
    Text(
        ls.footer, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp)
    )
}

@Composable
private fun LangOption(leading: @Composable () -> Unit, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !selected, onClick = onClick).pointerHoverIcon(if (selected) PointerIcon.Default else PointerIcon.Hand)
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(subtitle, color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .then(if (selected) Modifier.background(SinusColors.primary) else Modifier.border(2.dp, SinusColors.textSecondary.copy(alpha = 0.5f), CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) SinusIcon("ic_check", Color.White, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ConfirmLanguage(from: AppLanguage, to: AppLanguage, link: PhoneLink, onDismiss: () -> Unit) {
    val t = remember(to) { stringsFor(to) }
    val shape = RoundedCornerShape(SinusColors.radius(26.dp))
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().dialogIn().shadow(16.dp, shape).clip(shape)
                .background(SinusColors.card).border(1.dp, SinusColors.border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.alpha(0.6f)) { FlagBubble(from.code, 56.dp, SinusColors.textSecondary.copy(alpha = 0x1F / 255f)) }
                SinusIcon("ic_chevron_right", SinusColors.textSecondary, Modifier.padding(horizontal = 10.dp).size(24.dp))
                FlagBubble(to.code, 64.dp, SinusColors.primary.copy(alpha = 0x33 / 255f))
            }
            Spacer(Modifier.height(16.dp))
            Text(t.changeQ(to.displayName), color = SinusColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(t.changeDesc, color = SinusColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(26.dp))).background(SinusColors.primary)
                    .clickable {
                        onDismiss()
                        link.setLanguage(to.code)
                    }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t.changeBtn, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                t.cancel, color = SinusColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(SinusColors.radius(12.dp))).clickable(onClick = onDismiss)
                    .pointerHoverIcon(PointerIcon.Hand).padding(top = 14.dp, bottom = 4.dp)
            )
        }
    }
}
