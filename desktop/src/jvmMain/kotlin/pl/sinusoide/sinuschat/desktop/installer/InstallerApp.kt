package pl.sinusoide.sinuschat.desktop.installer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.AppVersion
import pl.sinusoide.sinuschat.desktop.appIcon
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.TermsStrings
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.anim.Greeting
import pl.sinusoide.sinuschat.desktop.ui.anim.IconDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.IconDrawing
import pl.sinusoide.sinuschat.desktop.ui.anim.LogoDraw
import pl.sinusoide.sinuschat.desktop.ui.anim.SineBar
import pl.sinusoide.sinuschat.desktop.ui.anim.SineRing
import pl.sinusoide.sinuschat.desktop.ui.anim.riseIn
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import pl.sinusoide.sinuschat.desktop.ui.settings.Flag
import pl.sinusoide.sinuschat.desktop.update.UpdateManifests
import java.io.File

private val cyan = Color(0xFF2AE0F5)
private val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private enum class Step { WELCOME, TERMS, LANGUAGE, OPTIONS, RUNNING, INSTALLING, FAILED, DONE, CHOICE, CONFIRM, REMOVING, REMOVED, PREPARING }

private enum class Art { NONE, DOCUMENT, LANGUAGE, INSTALL, QUESTION, BIN }

enum class InstallerMode { INSTALL, UPDATE, UNINSTALL, REINSTALL }

object InstallerApp {

    fun handles(args: Array<String>): Boolean = pl.sinusoide.sinuschat.desktop.system.Platform.isWindows && args.any { it == "--setup" || it == "--uninstall" }

    fun run(args: Array<String>) {
        val mode = when {
            "--uninstall" in args -> InstallerMode.UNINSTALL
            "--update" in args -> InstallerMode.UPDATE
            "--reinstall" in args -> InstallerMode.REINSTALL
            else -> InstallerMode.INSTALL
        }
        val language = args.firstOrNull { it.startsWith("--language=") }?.substringAfter('=')?.let { AppLanguage.fromCode(it) }
        application {
            InstallerWindow(mode, language, ::exitApplication)
        }
    }
}

private class Model(val mode: InstallerMode, language: AppLanguage?) {
    val source = InstallerCore.sourceDir()
    val existing = InstallerCore.installed()
    val version = AppVersion.name
    var language by mutableStateOf(language ?: AppLanguage.system())
    var step by mutableStateOf(
        when (mode) {
            InstallerMode.UNINSTALL -> Step.CHOICE
            InstallerMode.UPDATE, InstallerMode.REINSTALL -> Step.INSTALLING
            InstallerMode.INSTALL -> Step.WELCOME
        }
    )
    var agreed by mutableStateOf(false)
    var chosen by mutableStateOf(existing?.dir ?: InstallerCore.defaultDir())
    var desktop by mutableStateOf(existing == null || InstallerCore.hasDesktopShortcut())
    var start by mutableStateOf(true)
    var removeData by mutableStateOf(false)
    var binDone by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var phase by mutableStateOf(0)
    val needed: Long by lazy { source?.let { InstallerCore.sizeOf(it) } ?: 0L }

    val target: File get() = if (existing != null && InstallerCore.same(existing.dir, chosen)) existing.dir else InstallerCore.resolveTarget(chosen)
    val updating: Boolean get() = existing != null && InstallerCore.same(existing.dir, target)
}

private fun artFor(step: Step): Art = when (step) {
    Step.TERMS -> Art.DOCUMENT
    Step.LANGUAGE -> Art.LANGUAGE
    Step.OPTIONS, Step.RUNNING, Step.INSTALLING, Step.FAILED, Step.PREPARING -> Art.INSTALL
    Step.CHOICE -> Art.QUESTION
    Step.CONFIRM, Step.REMOVING -> Art.BIN
    Step.WELCOME, Step.DONE, Step.REMOVED -> Art.NONE
}

@Composable
private fun InstallerWindow(mode: InstallerMode, language: AppLanguage?, exit: () -> Unit) {
    val model = remember { Model(mode, language) }
    val s = InstallerStrings.of(model.language)
    val compact = mode == InstallerMode.UPDATE
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = if (compact) DpSize(460.dp, 250.dp) else DpSize(820.dp, 540.dp)
    )
    val finish = {
        if (mode == InstallerMode.REINSTALL) model.source?.let { InstallerCore.scheduleTempCleanup(it) }
        exit()
    }
    val busy = model.step == Step.INSTALLING || model.step == Step.REMOVING || model.step == Step.PREPARING
    Window(
        onCloseRequest = { if (!busy) finish() },
        state = state,
        title = if (mode == InstallerMode.UNINSTALL) s.uninstallWindowTitle else s.windowTitle,
        icon = remember { appIcon() },
        undecorated = true,
        transparent = true,
        resizable = false
    ) {
        LaunchedEffect(Unit) {
            window.isAlwaysOnTop = true
            window.toFront()
            window.requestFocus()
            delay(600)
            window.isAlwaysOnTop = false
        }
        SinusTheme {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(SinusColors.backgroundSolid)
                    .border(1.dp, SinusColors.border, RoundedCornerShape(14.dp))
            ) {
                if (compact) {
                    WindowDraggableArea { UpdateBody(model, s, exit) }
                } else {
                    WindowDraggableArea(Modifier.fillMaxSize()) { Frame(model, s, finish) }
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TitleButton("ic_minimize") { state.isMinimized = true }
                        if (!busy) TitleButton("ic_close", finish)
                    }
                }
            }
        }
    }
}

@Composable
private fun Frame(model: Model, s: InstallerStrings, finish: () -> Unit) {
    val art = artFor(model.step)
    AnimatedContent(
        targetState = art == Art.NONE,
        transitionSpec = { fadeIn(tween(320, delayMillis = 60)) togetherWith fadeOut(tween(200)) },
        modifier = Modifier.fillMaxSize()
    ) { full ->
        if (full) {
            Body(model, s, finish)
        } else {
            Row(Modifier.fillMaxSize()) {
                SidePanel(model, art)
                Box(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp, end = 36.dp, top = 44.dp, bottom = 28.dp)) {
                    Body(model, s, finish)
                }
            }
        }
    }
}

@Composable
private fun TitleButton(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        if (icon == "ic_minimize") {
            val color = SinusColors.textSecondary
            Canvas(Modifier.size(14.dp)) {
                drawLine(color, Offset(1f, size.height / 2f), Offset(size.width - 1f, size.height / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        } else {
            SinusIcon(icon, SinusColors.textSecondary, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SidePanel(model: Model, art: Art) {
    Box(
        Modifier.width(290.dp).fillMaxHeight().background(
            Brush.verticalGradient(0f to Color(0xFF071D26), 0.6f to Color(0xFF04121A), 1f to Color(0xFF000000))
        )
    ) {
        AnimatedContent(
            targetState = art,
            transitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier.fillMaxSize()
        ) { a ->
            val m = Modifier.fillMaxSize().padding(40.dp)
            when (a) {
                Art.DOCUMENT -> DocumentDraw(Color.White, m)
                Art.LANGUAGE -> IconDraw(remember { IconDrawing.language() }, Color.White, cyan, m)
                Art.INSTALL -> InstallDraw(Color.White, looping = model.step != Step.OPTIONS, modifier = m)
                Art.QUESTION -> QuestionDraw(Color.White, cyan, m)
                Art.BIN -> BinDraw(Color.White, model.binDone, m)
                Art.NONE -> Spacer(m)
            }
        }
        Text(
            "SinusChat-Desktop ${model.version}", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
        )
    }
}

@Composable
private fun Body(model: Model, s: InstallerStrings, finish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val app = model.language.strings
    val terms = remember(model.language) { TermsStrings.of(model.language.chat) }

    fun runInstall() {
        model.step = Step.INSTALLING
        model.progress = 0f
        model.phase = 0
        scope.launch {
            val target = model.target
            if (InstallerCore.running(target).isNotEmpty()) {
                model.step = Step.RUNNING
                while (withContext(Dispatchers.IO) { InstallerCore.running(target).isNotEmpty() }) delay(800)
                model.step = Step.INSTALLING
            }
            val ok = withContext(Dispatchers.IO) { performInstall(model, target) }
            model.step = if (ok) Step.DONE else Step.FAILED
        }
    }

    LaunchedEffect(Unit) {
        if (model.mode == InstallerMode.REINSTALL && model.step == Step.INSTALLING) {
            val target = model.existing?.dir ?: InstallerCore.defaultDir()
            model.chosen = target
            model.desktop = InstallerCore.hasDesktopShortcut()
            var waited = 0
            while (withContext(Dispatchers.IO) { InstallerCore.running(target).isNotEmpty() }) {
                delay(500)
                waited += 500
                if (waited >= 20_000) withContext(Dispatchers.IO) { InstallerCore.closeRunning(target) }
            }
            val ok = withContext(Dispatchers.IO) { performInstall(model, target) }
            model.step = if (ok) Step.DONE else Step.FAILED
        }
    }

    AnimatedContent(
        targetState = model.step,
        transitionSpec = { (fadeIn(tween(240)) + slideInHorizontally(tween(320, easing = ease)) { it / 10 }) togetherWith fadeOut(tween(140)) },
        modifier = Modifier.fillMaxSize()
    ) { step ->
        when (step) {
            Step.WELCOME -> Welcome(model) { model.step = Step.TERMS }
            Step.TERMS -> Page(
                buttons = {
                    Btn(app.back, primary = false) { model.step = Step.WELCOME }
                    Btn(app.next, primary = true, enabled = model.agreed) {
                        runCatching { pl.sinusoide.sinuschat.desktop.legal.TermsGate.accept() }
                        model.step = Step.LANGUAGE
                    }
                }
            ) {
                Title(terms.title)
                Desc(terms.sub)
                Spacer(Modifier.height(18.dp))
                Terms(model, terms)
            }
            Step.LANGUAGE -> Page(
                buttons = {
                    Btn(app.back, primary = false) { model.step = Step.TERMS }
                    Btn(app.next, primary = true) { model.step = Step.OPTIONS }
                }
            ) {
                Title(app.languageTitle)
                Desc(app.languageSub)
                Spacer(Modifier.height(20.dp))
                Languages(model)
            }
            Step.OPTIONS -> {
                val free = remember(model.chosen) { InstallerCore.freeSpace(model.target) }
                val enough = free < 0 || free > model.needed + (50L shl 20)
                val safe = InstallerCore.isSafeTarget(model.target, model.source)
                Page(
                    buttons = {
                        Btn(app.back, primary = false) { model.step = Step.LANGUAGE }
                        Btn(
                            when {
                                !model.updating -> s.install
                                UpdateManifests.compare(model.existing!!.version, model.version) == 0 -> s.reinstall
                                else -> s.update
                            },
                            primary = true, enabled = enough && safe && model.source != null
                        ) { runInstall() }
                    }
                ) {
                    Title(s.optionsTitle)
                    Desc(s.optionsDesc)
                    Spacer(Modifier.height(18.dp))
                    Label(s.folder)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            model.target.absolutePath, color = SinusColors.textPrimary, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SinusColors.card)
                                .border(1.dp, SinusColors.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Btn(s.change, primary = false, compact = true) { pickFolder(s.chooseFolder, model.chosen)?.let { model.chosen = it } }
                    }
                    Text(
                        if (enough) s.space(InstallerCore.formatSize(model.needed), InstallerCore.formatSize(free)) else s.noSpace,
                        color = if (enough) SinusColors.textSecondary else SinusColors.danger, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Label(s.shortcuts)
                    Check(s.startShortcut, model.start) { model.start = it }
                    Check(s.desktopShortcut, model.desktop) { model.desktop = it }
                    model.existing?.let { e ->
                        val c = UpdateManifests.compare(e.version, model.version)
                        Notice(if (c < 0) s.existingOlder(e.version) else if (c == 0) s.existingSame else s.existingNewer(e.version), warn = c > 0)
                    }
                }
            }
            Step.RUNNING -> Center {
                Hero("ic_warning", SinusColors.warning)
                Title(s.runningTitle, center = true, top = 18)
                Desc(s.runningDesc, center = true)
                Spacer(Modifier.height(20.dp))
                Btn(s.closeApp, primary = true) { InstallerCore.closeRunning(model.target) }
            }
            Step.INSTALLING -> Center {
                SineRing(if (model.phase == 0) model.progress else null, Modifier.size(96.dp), color = cyan, disc = Color.Transparent)
                Title(if (model.updating || model.mode == InstallerMode.REINSTALL) s.updating else s.installing, center = true, top = 22)
                Desc(if (model.phase == 0) s.copying((model.progress * 100).toInt()) else s.finishing, center = true)
            }
            Step.PREPARING -> Center {
                SineRing(model.progress, Modifier.size(96.dp), color = cyan, disc = Color.Transparent)
                Title(s.preparing, center = true, top = 22)
                Desc(s.copying((model.progress * 100).toInt()), center = true)
            }
            Step.FAILED -> Page(
                buttons = {
                    Btn(s.cancel, primary = false, onClick = finish)
                    if (model.mode != InstallerMode.REINSTALL) Btn(s.retry, primary = true) { runInstall() }
                }
            ) {
                Spacer(Modifier.height(24.dp))
                Hero("ic_warning", SinusColors.danger)
                Title(s.failedTitle, top = 18)
                Desc(s.failedDesc)
            }
            Step.DONE -> Finished(
                title = app.doneTitle,
                desc = when {
                    model.mode == InstallerMode.REINSTALL -> s.reinstalledDesc
                    model.existing != null -> s.updatedDesc
                    else -> s.doneDesc
                },
                primary = app.doneGo,
                secondary = s.close,
                onPrimary = {
                    runCatching { InstallerCore.launch(model.target, model.language.code) }
                    finish()
                },
                onSecondary = finish
            )
            Step.CHOICE -> Page(
                buttons = { Btn(s.cancel, primary = false, onClick = finish) }
            ) {
                Title(s.choiceTitle)
                Desc(s.choiceDesc(model.existing?.version ?: model.version))
                Spacer(Modifier.height(20.dp))
                Option("ic_delete_app", SinusColors.danger, s.removeOption, s.removeOptionDesc) { model.step = Step.CONFIRM }
                Spacer(Modifier.height(10.dp))
                Option("ic_install_desktop", SinusColors.primary, s.reinstall, s.reinstallOptionDesc) {
                    val source = model.source ?: return@Option
                    model.step = Step.PREPARING
                    model.progress = 0f
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { InstallerCore.startReinstall(source, model.language.code) { model.progress = it } }.isSuccess
                        }
                        if (ok) finish() else model.step = Step.FAILED
                    }
                }
            }
            Step.CONFIRM -> Page(
                buttons = {
                    Btn(app.back, primary = false) { model.step = Step.CHOICE }
                    Btn(s.uninstall, primary = true, danger = true) {
                        model.step = Step.REMOVING
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                InstallerCore.sourceDir()?.let { InstallerCore.closeRunning(it) }
                                InstallerCore.removeShortcuts()
                                InstallerCore.unregister()
                            }
                            delay(400)
                            model.binDone = true
                            delay(1900)
                            model.step = Step.REMOVED
                        }
                    }
                }
            ) {
                Title(s.uninstallTitle)
                Desc(s.uninstallDesc)
                Spacer(Modifier.height(18.dp))
                Check(s.removeData, model.removeData) { model.removeData = it }
                Text(s.removeDataDesc, color = SinusColors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 44.dp))
            }
            Step.REMOVING -> Center {
                SineRing(null, Modifier.size(96.dp), color = cyan, disc = Color.Transparent)
                Title(s.uninstalling, center = true, top = 22)
            }
            Step.REMOVED -> Finished(
                title = s.uninstalledTitle,
                desc = s.uninstalledDesc,
                primary = s.close,
                secondary = null,
                onPrimary = {
                    InstallerCore.sourceDir()?.let { InstallerCore.scheduleRemoval(it, model.removeData) }
                    finish()
                },
                onSecondary = {}
            )
        }
    }
}

private fun performInstall(model: Model, target: File): Boolean = runCatching {
    val source = model.source ?: return false
    model.phase = 0
    InstallerCore.install(source, target) { model.progress = it }
    model.phase = 1
    InstallerCore.register(target, model.version)
    InstallerCore.shortcuts(target, model.desktop, model.start)
    model.chosen = target
    true
}.getOrDefault(false)

@Composable
private fun Welcome(model: Model, onNext: () -> Unit) {
    val app = model.language.strings
    val logoMs = LogoDraw.DRAW_MS.toInt()
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.7f))
        LogoDraw(Modifier.size(156.dp))
        Text(
            "SinusChat", color = SinusColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp).riseIn(delayMs = logoMs - 350, durationMs = 500, distanceDp = 8f)
        )
        Greeting(
            color = SinusColors.text,
            firstLanguage = model.language.code,
            startAfterMs = logoMs + 250L,
            modifier = Modifier.padding(top = 16.dp).width(420.dp).height(112.dp)
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.widthIn(max = 380.dp).fillMaxWidth().fadeAfter(logoMs + 500, 450)) {
            BigButton(app.welcomeGo, Modifier.fillMaxWidth(), onNext)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Finished(title: String, desc: String, primary: String, secondary: String?, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    Column(Modifier.fillMaxSize().riseIn().padding(horizontal = 26.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.7f))
        LogoDraw(Modifier.padding(vertical = 8.dp).size(156.dp))
        Text(title, color = SinusColors.text, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(
            desc, color = SinusColors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 460.dp)
        )
        Spacer(Modifier.weight(1f))
        BigButton(primary, Modifier.widthIn(max = 380.dp).fillMaxWidth(), onPrimary)
        if (secondary != null) {
            Text(
                secondary, color = SinusColors.textSecondary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onSecondary)
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Modifier.fadeAfter(delayMs: Int, durationMs: Int): Modifier {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(durationMs, delayMs)) }
    return graphicsLayer { alpha = k.value }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(SinusColors.radius(26.dp))).background(SinusColors.primary)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Terms(model: Model, terms: TermsStrings) {
    val shape = RoundedCornerShape(SinusColors.radius(18.dp))
    val docs = "https://sinusoide.pl/dokumentacja.html?lang=" + if (model.language == AppLanguage.PL) "pl" else "en"
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
            .border(if (model.agreed) 2.dp else 1.dp, if (model.agreed) SinusColors.primary else SinusColors.border, shape)
            .clickable { model.agreed = !model.agreed }.pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
    ) {
        Checkbox(
            model.agreed, onCheckedChange = { model.agreed = it },
            colors = CheckboxDefaults.colors(checkedColor = SinusColors.primary, uncheckedColor = SinusColors.textSecondary, checkmarkColor = Color.White)
        )
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(terms.agree, color = SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Text(text, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Option(icon: String, color: Color, title: String, desc: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(SinusColors.radius(18.dp))
    Row(
        Modifier.fillMaxWidth().clip(shape).background(SinusColors.card).border(1.dp, SinusColors.border, shape)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(SinusColors.radius(14.dp))).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { SinusIcon(icon, color, Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SinusColors.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        SinusIcon("ic_chevron_right", SinusColors.textSecondary, Modifier.size(22.dp))
    }
}

@Composable
private fun UpdateBody(model: Model, s: InstallerStrings, exit: () -> Unit, run: Boolean = true) {
    LaunchedEffect(Unit) {
        if (!run) return@LaunchedEffect
        val target = model.existing?.dir ?: InstallerCore.defaultDir()
        model.chosen = target
        model.start = true
        model.desktop = InstallerCore.hasDesktopShortcut()
        var waited = 0
        while (withContext(Dispatchers.IO) { InstallerCore.running(target).isNotEmpty() }) {
            model.phase = 2
            delay(500)
            waited += 500
            if (waited >= 20_000) withContext(Dispatchers.IO) { InstallerCore.closeRunning(target) }
        }
        model.phase = 0
        val ok = withContext(Dispatchers.IO) { performInstall(model, target) }
        if (ok) {
            runCatching { InstallerCore.launch(target, null) }
            exit()
        } else {
            model.step = Step.FAILED
        }
    }
    Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
        UpdateHero(failed = model.step == Step.FAILED)
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            if (model.step == Step.FAILED) {
                Text(s.failedTitle, color = SinusColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(s.failedDesc, color = SinusColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 12.dp)) { Btn(s.close, primary = true, compact = true, onClick = exit) }
            } else {
                Text(s.updating, color = SinusColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (model.phase) {
                        2 -> s.waitingApp
                        1 -> s.finishing
                        else -> s.copying((model.progress * 100).toInt())
                    },
                    color = SinusColors.textSecondary, fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp)
                )
                SineBar(if (model.phase == 0) model.progress else null, Modifier.padding(top = 12.dp).fillMaxWidth().height(18.dp))
            }
        }
    }
}

@Composable
private fun UpdateHero(failed: Boolean) {
    val scale = remember { Animatable(0.6f) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(420, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))) }
    LaunchedEffect(failed) {
        if (failed) return@LaunchedEffect
        delay(260)
        while (true) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, tween(1100, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
            delay(250)
        }
    }
    val color = if (failed) SinusColors.danger else SinusColors.primary
    Box(
        Modifier.size(88.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(CircleShape).background(color.copy(alpha = 0x26 / 255f)),
        contentAlignment = Alignment.Center
    ) {
        SinusIcon(if (failed) "ic_warning" else "ic_update", color, Modifier.size(44.dp).graphicsLayer { rotationZ = if (failed) 0f else rotation.value })
    }
}

private fun pickFolder(title: String, current: File): File? = runCatching {
    javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName())
    val chooser = javax.swing.JFileChooser(current.takeIf { it.exists() } ?: current.parentFile)
    chooser.dialogTitle = title
    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}.getOrNull()

@Composable
private fun Page(buttons: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth()) { content() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) { buttons() }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

@Composable
private fun Title(text: String, center: Boolean = false, top: Int = 0) {
    Text(
        text, color = SinusColors.textPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp,
        textAlign = if (center) TextAlign.Center else null,
        modifier = Modifier.padding(top = top.dp)
    )
}

@Composable
private fun Desc(text: String, center: Boolean = false) {
    Text(
        text, color = SinusColors.textSecondary, fontSize = 14.5.sp, lineHeight = 21.sp,
        textAlign = if (center) TextAlign.Center else null,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = SinusColors.primary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
}

@Composable
private fun Hero(icon: String, color: Color) {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(460, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))) }
    Box(
        Modifier.size(72.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }.clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) { SinusIcon(icon, color, Modifier.size(36.dp)) }
}

@Composable
private fun Languages(model: Model) {
    val rows = AppLanguage.entries.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (lang in row) {
                    val selected = lang == model.language
                    Row(
                        Modifier.width(196.dp).clip(RoundedCornerShape(SinusColors.radius(14.dp)))
                            .background(if (selected) SinusColors.primary.copy(alpha = 0.18f) else SinusColors.card)
                            .border(if (selected) 2.dp else 1.dp, if (selected) SinusColors.primary else SinusColors.border, RoundedCornerShape(SinusColors.radius(14.dp)))
                            .clickable { model.language = lang }.pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Flag(lang.code, Modifier.size(24.dp, 16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            lang.displayName, color = if (selected) SinusColors.primary else SinusColors.textPrimary, fontSize = 14.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) SinusIcon("ic_check", SinusColors.primary, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, warn: Boolean) {
    val color = if (warn) SinusColors.warning else SinusColors.primary
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SinusIcon(if (warn) "ic_warning" else "ic_info", color, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = SinusColors.textPrimary, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun Check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.pointerHoverIcon(PointerIcon.Hand).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked, onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = SinusColors.primary, uncheckedColor = SinusColors.textSecondary, checkmarkColor = Color.White)
        )
        Text(label, color = SinusColors.textPrimary, fontSize = 14.5.sp)
    }
}

@Composable
private fun Btn(label: String, primary: Boolean, enabled: Boolean = true, compact: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        !primary -> Color.Transparent
        danger -> SinusColors.danger
        else -> SinusColors.primary
    }
    val shape = RoundedCornerShape(SinusColors.radius(22.dp))
    Text(
        label, color = if (primary) Color.White else SinusColors.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }.clip(shape).background(bg)
            .then(if (!primary) Modifier.border(1.dp, SinusColors.border, shape) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 9.dp else 12.dp)
    )
}

@Composable
internal fun InstallerPreview(step: String, language: AppLanguage, binDone: Boolean = false) {
    val model = remember {
        Model(InstallerMode.INSTALL, language).also {
            it.step = Step.valueOf(step)
            it.progress = 0.42f
            it.agreed = true
            it.binDone = binDone
        }
    }
    val s = InstallerStrings.of(language)
    SinusTheme {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(SinusColors.backgroundSolid).border(1.dp, SinusColors.border, RoundedCornerShape(14.dp))) {
            Frame(model, s) {}
            Row(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp)) {
                TitleButton("ic_minimize") {}
                TitleButton("ic_close") {}
            }
        }
    }
}

@Composable
internal fun UpdatePreview(language: AppLanguage) {
    val model = remember { Model(InstallerMode.UPDATE, language).also { it.progress = 0.42f } }
    SinusTheme {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(SinusColors.backgroundSolid).border(1.dp, SinusColors.border, RoundedCornerShape(14.dp))) {
            UpdateBody(model, InstallerStrings.of(language), {}, run = false)
        }
    }
}
