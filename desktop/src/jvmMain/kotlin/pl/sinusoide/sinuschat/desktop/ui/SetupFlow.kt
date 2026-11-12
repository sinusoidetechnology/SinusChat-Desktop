package pl.sinusoide.sinuschat.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings
import pl.sinusoide.sinuschat.desktop.ui.anim.Greeting
import pl.sinusoide.sinuschat.desktop.ui.anim.IconDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.IconDrawing
import pl.sinusoide.sinuschat.desktop.ui.anim.LogoDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import pl.sinusoide.sinuschat.desktop.ui.anim.RevealOrigin
import pl.sinusoide.sinuschat.desktop.ui.anim.WifiDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.riseIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon

enum class SetupStep { WELCOME, LANGUAGE, PAIR, DONE }

class SetupActions(
    val welcomed: () -> Unit = {},
    val backToWelcome: () -> Unit = {},
    val chooseLanguage: (AppLanguage) -> Unit = {},
    val languageDone: () -> Unit = {},
    val backToLanguage: () -> Unit = {},
    val finish: (RevealOrigin) -> Unit = {},
    val help: () -> Unit = {}
)

@Composable
fun SetupFlow(step: SetupStep, link: LinkState, language: AppLanguage, actions: SetupActions, issue: String? = null) {
    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(260, delayMillis = 80)) togetherWith fadeOut(tween(200)) },
        modifier = Modifier.fillMaxSize().background(SinusColors.background)
    ) { s ->
        when (s) {
            SetupStep.WELCOME -> WelcomeStep(language, actions.welcomed)
            SetupStep.LANGUAGE -> key(language) {
                LanguageStep(language, actions.chooseLanguage, actions.languageDone, actions.backToWelcome)
            }
            SetupStep.PAIR -> PairStep(link as? LinkState.Pairing, actions.backToLanguage, actions.help, issue)
            SetupStep.DONE -> DoneStep(phoneNameOf(link), actions.finish)
        }
    }
}

private fun phoneNameOf(link: LinkState): String = when (link) {
    is LinkState.Connected -> link.phoneName
    is LinkState.Searching -> link.phoneName
    is LinkState.Pairing -> ""
}

@Composable
fun SetupButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (hovered) SinusColors.primary.copy(alpha = 0.88f) else SinusColors.primary)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IconAction(icon: String, description: String, tint: Color = SinusColors.text, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (hovered) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon(icon, tint, Modifier.size(24.dp), description)
    }
}

@Composable
private fun BackAction(onBack: () -> Unit) {
    IconAction("ic_back", LocalStrings.current.back, modifier = Modifier.padding(12.dp), onClick = onBack)
}

@Composable
private fun Modifier.fadeAfter(delayMs: Int, durationMs: Int): Modifier {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(durationMs, delayMs)) }
    return graphicsLayer { alpha = k.value }
}

@Composable
private fun CenteredScroll(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 26.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { content() }
        }
    }
}

@Composable
private fun StepHead(title: String, sub: String?) {
    Text(
        title,
        color = SinusColors.text, fontSize = 25.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
    )
    if (!sub.isNullOrEmpty()) Text(
        sub,
        color = SinusColors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp).widthIn(max = 460.dp)
    )
}

@Composable
private fun WelcomeStep(language: AppLanguage, onNext: () -> Unit) {
    val s = LocalStrings.current
    val logoMs = LogoDraw.DRAW_MS.toInt()
    Column(
        Modifier.fillMaxSize().riseIn().padding(horizontal = 26.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.8f))
        LogoDraw(Modifier.size(176.dp))
        Text(
            "SinusChat",
            color = SinusColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).riseIn(delayMs = logoMs - 350, durationMs = 500, distanceDp = 8f)
        )
        Greeting(
            color = SinusColors.text,
            firstLanguage = language.code,
            startAfterMs = logoMs + 250L,
            modifier = Modifier.padding(top = 22.dp).width(420.dp).height(128.dp)
        )
        Spacer(Modifier.weight(1f))
        SetupButton(
            s.welcomeGo,
            Modifier.widthIn(max = 380.dp).fillMaxWidth().fadeAfter(logoMs + 500, 450),
            onNext
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LanguageStep(
    current: AppLanguage,
    onChoose: (AppLanguage) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val drawing = remember { IconDrawing.language() }
    Box(Modifier.fillMaxSize().riseIn()) {
        CenteredScroll {
            IconDraw(drawing, SinusColors.text, SinusColors.primary, Modifier.fillMaxWidth().height(170.dp))
            StepHead(s.languageTitle, s.languageSub)
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                for (lang in AppLanguage.entries) {
                    LanguageOption(lang.displayName, lang == current) {
                        if (lang != current) onChoose(lang) else onNext()
                    }
                }
                Spacer(Modifier.height(4.dp))
                SetupButton(s.next, Modifier.fillMaxWidth(), onNext)
            }
        }
        BackAction(onBack)
    }
}

@Composable
private fun LanguageOption(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .background(if (hovered && !selected) SinusColors.surfaceHigh else SinusColors.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) SinusColors.primary else SinusColors.border, shape)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 16.dp, top = 15.dp, end = 14.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = if (selected) SinusColors.primary else SinusColors.text,
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (selected) SinusIcon("ic_check", SinusColors.primary, Modifier.size(22.dp))
    }
}

@Composable
private fun PairStep(current: LinkState.Pairing?, onBack: () -> Unit, onHelp: () -> Unit, issue: String?) {
    val s = LocalStrings.current
    var last by remember { mutableStateOf(current) }
    if (current != null) last = current
    val pairing = last

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxSize().padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(Modifier.widthIn(max = 470.dp).weight(1f, fill = false).riseIn()) {
                WifiDraw(SinusColors.text, SinusColors.primary, Modifier.size(150.dp).padding(bottom = 4.dp))
                Text(s.pairTitle, color = SinusColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    s.pairSub,
                    color = SinusColors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 22.dp)
                )
                PairInstruction(1, s.pairStep1, 350)
                PairInstruction(2, s.pairStep2, 500)
                PairInstruction(3, s.pairStep3, 650)
                Text(
                    s.pairWifi,
                    color = SinusColors.textSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 24.dp).fadeAfter(800, 400)
                )
                val nearby = pairing?.phonesNearby ?: 0
                Text(
                    if (nearby == 0) s.pairSearching else s.pairNearby(nearby),
                    color = SinusColors.textSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp).fadeAfter(900, 400)
                )
                TroubleLink(issue, onHelp, Modifier.padding(top = 10.dp).fadeAfter(1000, 400))
            }
            Spacer(Modifier.width(56.dp))
            if (pairing != null) {
                val k = remember { Animatable(0f) }
                LaunchedEffect(Unit) { k.animateTo(1f, tween(520, 260, Motion.enter)) }
                QrCode(
                    pairing.code,
                    Modifier.size(300.dp).graphicsLayer {
                        alpha = k.value
                        scaleX = 0.92f + 0.08f * k.value
                        scaleY = 0.92f + 0.08f * k.value
                    }
                )
            }
        }
        BackAction(onBack)
    }
}

@Composable
private fun PairInstruction(number: Int, text: String, delayMs: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp).riseIn(delayMs = delayMs, durationMs = 380, distanceDp = 10f)
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(SinusColors.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = SinusColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = SinusColors.text, fontSize = 16.sp)
    }
}

@Composable
private fun DoneStep(phoneName: String, onFinish: (RevealOrigin) -> Unit) {
    val s = LocalStrings.current
    var logo by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.fillMaxSize().riseIn().padding(horizontal = 26.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.8f))
        LogoDraw(
            Modifier.padding(vertical = 10.dp).size(176.dp)
                .onGloballyPositioned { logo = it.boundsInRoot() }
        )
        StepHead(s.doneTitle, s.doneSub(phoneName))
        Spacer(Modifier.weight(1f))
        SetupButton(s.doneGo, Modifier.widthIn(max = 380.dp).fillMaxWidth()) {
            onFinish(RevealOrigin(logo.center, minOf(logo.width, logo.height) * 0.3f))
        }
        Spacer(Modifier.height(20.dp))
    }
}
