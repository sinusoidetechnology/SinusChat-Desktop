package pl.sinusoide.sinuschat.desktop.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import pl.sinusoide.sinuschat.desktop.ui.anim.Motion
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings
import pl.sinusoide.sinuschat.desktop.i18n.SettingsStrings
import pl.sinusoide.sinuschat.desktop.security.LockManager
import pl.sinusoide.sinuschat.desktop.security.WindowsHello
import pl.sinusoide.sinuschat.desktop.ui.SinusColors
import pl.sinusoide.sinuschat.desktop.ui.ThemeBackdrop
import pl.sinusoide.sinuschat.desktop.ui.icons.SinusIcon
import kotlin.random.Random

enum class PinMode { SET, UNLOCK, CONFIRM, SET_DURESS, CONFIRM_DURESS }

private val dangerRed = Color(0xFFE5484D)
private val okGreen = Color(0xFF3FB97A)

private val decelerate = Easing { t -> 1f - (1f - t) * (1f - t) }
private val accelerateDecelerate = Easing { t -> (kotlin.math.cos((t + 1f) * Math.PI) / 2.0 + 0.5).toFloat() }
private val overshootPop = Easing { t -> Motion.overshoot(t, 3f) }

private val digitKeys = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9'
)

private val letters = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ"
)

fun waitText(ms: Long, s: SettingsStrings): String {
    val total = (ms + 999L) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val sec = total % 60L
    return when {
        h > 0 -> s.waitHm(h, m)
        m > 0 -> s.waitMs(m, sec)
        else -> s.waitS(sec)
    }
}

@Composable
fun PinScreen(
    lock: LockManager,
    mode: PinMode,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onWipe: (exceeded: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    user: PinUser? = null
) {
    val s = LocalSettingsStrings.current
    val config by lock.config.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(if (mode == PinMode.SET || mode == PinMode.SET_DURESS) s.min4 else "") }
    var messageError by remember { mutableStateOf(false) }
    var setAutoConfirm by remember { mutableStateOf(config.autoConfirm) }
    var weakPending by remember { mutableStateOf(false) }
    var weakAccepted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }
    var lockout by remember { mutableLongStateOf(if (mode == PinMode.UNLOCK) lock.lockoutRemainingMs() else 0L) }
    val sparks = remember { mutableStateListOf<Pair<Long, Float>>() }

    val autoConfirm = if (mode == PinMode.SET) setAutoConfirm else config.autoConfirm
    val silent = config.silent && config.autoConfirm
    val confirming = mode == PinMode.UNLOCK || mode == PinMode.CONFIRM || mode == PinMode.CONFIRM_DURESS
    val helloUsable = config.biometric && (mode == PinMode.UNLOCK || mode == PinMode.CONFIRM)

    fun submitLength(): Int {
        if (silent || !autoConfirm) return 0
        val pinLen = config.pinLength
        return when (mode) {
            PinMode.UNLOCK -> when {
                pinLen == 0 -> 0
                !config.hasDuress -> pinLen
                config.duressLength == 0 -> 0
                else -> maxOf(pinLen, config.duressLength)
            }
            PinMode.CONFIRM -> pinLen
            PinMode.CONFIRM_DURESS -> config.duressLength
            else -> 0
        }
    }

    val needsNext = lockout <= 0L && when (mode) {
        PinMode.UNLOCK, PinMode.CONFIRM, PinMode.CONFIRM_DURESS -> !(silent || submitLength() > 0)
        PinMode.SET, PinMode.SET_DURESS -> !(autoConfirm && firstPin.isNotEmpty())
    }

    fun showError(text: String) {
        message = text
        messageError = true
        pin = ""
        shake++
    }

    fun finishWith(action: () -> Unit) {
        busy = true
        success = true
        scope.launch {
            delay(430)
            action()
        }
    }

    fun wrong() {
        val attempts = lock.registerFailure()
        if (config.wipeOnFail && attempts >= LockManager.MAX_ATTEMPTS) {
            onWipe(true)
            return
        }
        showError(if (config.wipeOnFail) s.attemptErrorWipe(attempts, LockManager.MAX_ATTEMPTS) else s.attemptError(attempts))
        lockout = lock.lockoutRemainingMs()
    }

    fun checkPin() {
        val entered = pin
        if (lock.verifyDuress(entered)) {
            onWipe(false)
            return
        }
        if (lock.verifyPin(entered)) finishWith(onDone) else wrong()
    }

    fun next() {
        if (busy || lockout > 0L) return
        if (pin.length < LockManager.MIN_PIN) {
            showError(s.min4)
            return
        }
        when (mode) {
            PinMode.SET -> if (firstPin.isEmpty()) {
                if (lock.verifyDuress(pin)) {
                    showError(s.pinSameAsDuress)
                } else if (pin != weakAccepted && LockManager.isWeak(pin)) {
                    weakPending = true
                } else {
                    firstPin = pin
                    pin = ""
                    message = ""
                    messageError = false
                }
            } else if (pin == firstPin) {
                val chosen = pin
                lock.setPin(chosen)
                lock.setAutoConfirm(setAutoConfirm)
                finishWith(onDone)
            } else {
                showError(s.matchError)
            }
            PinMode.UNLOCK -> checkPin()
            PinMode.CONFIRM -> if (lock.verifyPin(pin)) finishWith(onDone) else showError(s.incorrectPin)
            PinMode.SET_DURESS -> if (firstPin.isEmpty()) {
                if (lock.verifyPin(pin)) {
                    showError(s.duressSameError)
                } else {
                    firstPin = pin
                    pin = ""
                    message = ""
                    messageError = false
                }
            } else if (pin == firstPin) {
                lock.setDuress(pin)
                finishWith(onDone)
            } else {
                showError(s.matchError)
            }
            PinMode.CONFIRM_DURESS -> if (lock.verifyDuress(pin)) finishWith(onDone) else showError(s.incorrectDuress)
        }
    }

    fun autoCheck() {
        val reached = submitLength().let { it > 0 && pin.length >= it }
        when (mode) {
            PinMode.UNLOCK -> if (lock.verifyPin(pin) || lock.verifyDuress(pin) || reached) checkPin()
            PinMode.CONFIRM -> if (lock.verifyPin(pin) || reached) next()
            PinMode.CONFIRM_DURESS -> if (lock.verifyDuress(pin) || reached) next()
            PinMode.SET, PinMode.SET_DURESS -> if (firstPin.isNotEmpty() && pin.length == firstPin.length) next()
        }
    }

    fun digit(c: Char) {
        if (busy || lockout > 0L || pin.length >= LockManager.MAX_PIN) return
        if (messageError) {
            messageError = false
            message = if (mode == PinMode.SET || mode == PinMode.SET_DURESS) s.min4 else ""
        }
        pin += c
        if (!config.showDots) sparks += System.nanoTime() to Random.nextFloat()
        if (autoConfirm || (silent && confirming)) autoCheck()
    }

    fun delete() {
        if (busy || pin.isEmpty()) return
        pin = pin.dropLast(1)
    }

    fun hello() {
        if (busy || !helloUsable) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { WindowsHello.verify(s.helloPrompt) }
            busy = false
            if (ok) finishWith(onDone)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (mode == PinMode.UNLOCK && helloUsable && lockout <= 0L) hello()
    }

    LaunchedEffect(lockout > 0L) {
        while (lockout > 0L) {
            delay(250)
            lockout = lock.lockoutRemainingMs()
        }
    }

    val title = when {
        lockout > 0L -> s.lockoutTitle
        mode == PinMode.SET -> if (firstPin.isEmpty()) s.setPinTitle else s.repeatPinTitle
        mode == PinMode.UNLOCK -> s.unlockTitle
        mode == PinMode.CONFIRM -> s.confirmPinTitle
        mode == PinMode.SET_DURESS -> if (firstPin.isEmpty()) s.setDuressTitle else s.repeatDuressTitle
        else -> s.confirmDuressTitle
    }
    val description = when {
        lockout > 0L -> s.lockoutDesc(waitText(lockout, s))
        mode == PinMode.SET -> if (firstPin.isEmpty()) s.setPinDesc else s.repeatPinDesc
        mode == PinMode.UNLOCK -> s.unlockDesc
        mode == PinMode.CONFIRM -> s.confirmPinDesc
        mode == PinMode.SET_DURESS -> s.setDuressDesc
        else -> s.confirmDuressDesc
    }

    Box(
        modifier.fillMaxSize().background(SinusColors.backgroundSolid)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val d = digitKeys[e.key]
                when {
                    weakPending -> false
                    d != null -> { digit(d); true }
                    e.key == Key.Backspace -> { delete(); true }
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> { next(); true }
                    e.key == Key.Escape && onCancel != null -> { onCancel(); true }
                    else -> false
                }
            }
    ) {
        if (mode == PinMode.UNLOCK && SinusColors.palette.key == pl.sinusoide.sinuschat.desktop.ui.Themes.RETRO) {
            val rs = pl.sinusoide.sinuschat.desktop.i18n.RetroLockStrings.of(pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings.current)
            RetroLogin(
                user = user,
                hint = if (lockout > 0L) description else rs.hint,
                placeholder = rs.placeholder,
                pinLength = pin.length,
                showDots = config.showDots,
                message = if (lockout > 0L) "" else message,
                error = messageError,
                shake = shake,
                canSubmit = pin.length >= LockManager.MIN_PIN && !busy,
                active = lockout <= 0L,
                showHello = helloUsable,
                helloLabel = s.useHello,
                onFieldClick = { runCatching { focus.requestFocus() } },
                onNext = { next() },
                onHello = { hello() }
            )
        } else {
        if (SinusColors.palette.backdrop) ThemeBackdrop(Modifier.fillMaxSize())
        val glow by animateFloatAsState(if (success) 1f else 0f, tween(450, easing = accelerateDecelerate))
        val leave by animateFloatAsState(if (success) 1f else 0f, tween(300, easing = decelerate))
        val actionsAlpha by animateFloatAsState(if (success) 0f else 1f, tween(200))
        val glowColor = SinusColors.primary
        Canvas(Modifier.fillMaxSize()) {
            if (glow <= 0.01f) return@Canvas
            val radius = maxOf(size.width, size.height) * 0.6f
            drawRect(
                Brush.radialGradient(
                    0f to glowColor.copy(alpha = 90f * glow / 255f),
                    1f to glowColor.copy(alpha = 0f),
                    center = Offset(size.width / 2f, size.height * 0.3f),
                    radius = radius
                )
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            val active = lockout <= 0L
            val showAuto = active && mode == PinMode.SET && firstPin.isEmpty()
            val fullHeight = maxHeight
            val wide = maxWidth >= 760.dp && maxHeight < 780.dp
            val ringSize = if (maxHeight < 720.dp) 76.dp else 104.dp
            val extras = (if (active && needsNext) 72.dp else 0.dp) + (if (showAuto) 86.dp else 0.dp)
            val header = if (wide) 52.dp else 52.dp + ringSize + 118.dp
            val keysRoom = maxHeight - header - 58.dp - 52.dp - extras - 8.dp
            val keySize = (keysRoom / 4.55f).coerceIn(42.dp, 66.dp)

            val top: @Composable () -> Unit = {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.CenterStart) {
                    if (onCancel != null) {
                        KeyButton(42.dp, glass = true, onClick = onCancel) {
                            SinusIcon("ic_back", SinusColors.textPrimary, Modifier.size(22.dp), s.back)
                        }
                    }
                }
            }
            val intro: @Composable () -> Unit = {
                LockRing(
                    danger = mode == PinMode.SET_DURESS || mode == PinMode.CONFIRM_DURESS || lockout > 0L,
                    success = success,
                    shake = shake,
                    ringSize = ringSize
                )
                Text(
                    title, color = SinusColors.textPrimary, fontSize = if (ringSize < 100.dp) 23.sp else 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    description, color = SinusColors.textSecondary, fontSize = 14.5.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            val field: @Composable () -> Unit = {
                Spacer(Modifier.height(10.dp))
                PinField(pin.length, config.showDots, s.placeholder, messageError, success, sparks)
                Text(
                    message, color = if (messageError) dangerRed else SinusColors.textSecondary, fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 20.dp).padding(top = 10.dp, bottom = 12.dp)
                )
            }
            val keypad: @Composable () -> Unit = {
                Box(
                    Modifier.graphicsLayer {
                        alpha = 1f - leave
                        translationY = 40.dp.toPx() * leave
                    }
                ) {
                    Keypad(
                        showHello = helloUsable,
                        helloLabel = s.useHello,
                        keySize = keySize,
                        onDigit = ::digit,
                        onDelete = ::delete,
                        onHello = ::hello
                    )
                }
            }
            val actions: @Composable () -> Unit = {
                Column(Modifier.graphicsLayer { alpha = actionsAlpha }, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showAuto) {
                        Row(
                            Modifier.widthIn(max = 380.dp).padding(top = 14.dp).clip(RoundedCornerShape(SinusColors.radius(16.dp)))
                                .background(SinusColors.card).clickable { setAutoConfirm = !setAutoConfirm }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.autoConfirm, color = SinusColors.textPrimary, fontSize = 15.sp)
                                Text(s.autoConfirmDesc, color = SinusColors.textSecondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = setAutoConfirm,
                                onCheckedChange = { setAutoConfirm = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = SinusColors.primary, checkedThumbColor = Color.White)
                            )
                        }
                    }
                    if (active && needsNext) {
                        val ready = pin.length >= LockManager.MIN_PIN
                        val label = if ((mode == PinMode.SET || mode == PinMode.SET_DURESS) && firstPin.isNotEmpty()) s.save else s.next
                        Box(
                            Modifier.padding(top = 16.dp).widthIn(max = 380.dp).fillMaxWidth().alpha(if (ready) 1f else 0.4f)
                                .clip(RoundedCornerShape(SinusColors.radius(28.dp))).background(SinusColors.primary)
                                .clickable(enabled = ready) { next() }
                                .pointerHoverIcon(if (ready) PointerIcon.Hand else PointerIcon.Default)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (wide && active) {
                Column(Modifier.fillMaxSize()) {
                    top()
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            intro()
                            field()
                            actions()
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            keypad()
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    top()
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = fullHeight - 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        intro()
                        if (active) {
                            field()
                            keypad()
                            actions()
                        }
                        Spacer(Modifier.height(52.dp))
                    }
                }
            }
        }
        }
        if (weakPending) {
            AlertDialog(
                onDismissRequest = { weakPending = false; pin = "" },
                containerColor = SinusColors.card,
                title = { Text(s.weakTitle, color = SinusColors.textPrimary) },
                text = {
                    Column {
                        Text(s.weakDesc, color = SinusColors.textSecondary)
                        Text(s.weakHint, color = SinusColors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        weakPending = false
                        pin = ""
                    }) { Text(s.weakChange, color = SinusColors.primary) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        weakPending = false
                        weakAccepted = pin
                        next()
                    }) { Text(s.weakKeep, color = SinusColors.textSecondary) }
                }
            )
        }
    }
}

@Composable
private fun LockRing(danger: Boolean, success: Boolean, shake: Int, ringSize: Dp = 104.dp) {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing))
    )
    val grow by animateFloatAsState(if (success) 1f else 0f, tween(420, easing = decelerate))
    val arcColor = when {
        success -> okGreen
        danger -> dangerRed
        else -> SinusColors.primary
    }
    val iconScale = remember { Animatable(1f) }
    val iconAlpha = remember { Animatable(1f) }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(success) {
        if (!success) {
            opened = false
            iconScale.snapTo(1f)
            iconAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        coroutineScope {
            launch { iconScale.animateTo(0.6f, tween(140, easing = accelerateDecelerate)) }
            launch { iconAlpha.animateTo(0f, tween(140, easing = accelerateDecelerate)) }
        }
        opened = true
        coroutineScope {
            launch { iconScale.animateTo(1.1f, tween(220, easing = overshootPop)) }
            launch { iconAlpha.animateTo(1f, tween(220, easing = overshootPop)) }
        }
    }
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        tilt.snapTo(0f)
        tilt.animateTo(0f, keyframes {
            durationMillis = 420
            -14f at 84
            12f at 168
            -8f at 252
            5f at 336
        })
    }
    val flow = SinusColors.palette.backdrop
    val fill = SinusColors.card
    val rimTop = if (flow) Color(0x40FFFFFF) else SinusColors.border
    val rimBottom = if (flow) Color(0x0AFFFFFF) else SinusColors.border
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val r = size.minDimension / 2f - 6.dp.toPx()
            val c = center
            val halo = r * 1.6f
            drawCircle(
                Brush.radialGradient(
                    0f to arcColor.copy(alpha = ((if (flow) 70f else 45f) + 90f * grow) / 255f),
                    1f to arcColor.copy(alpha = 0f),
                    center = c, radius = halo
                ),
                radius = halo, center = c
            )
            drawCircle(fill, r, c)
            drawCircle(
                Brush.verticalGradient(listOf(rimTop, rimBottom), startY = c.y - r, endY = c.y + r),
                r, c, style = Stroke(1.2.dp.toPx())
            )
            val sweep = 110f + 250f * grow
            val pad = 5.dp.toPx()
            rotate(angle, c) {
                drawArc(
                    Brush.sweepGradient(0f to Color.Transparent, sweep / 360f to arcColor, 1f to Color.Transparent, center = c),
                    startAngle = 0f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(c.x - r - pad, c.y - r - pad), size = Size((r + pad) * 2, (r + pad) * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        SinusIcon(
            if (opened) "ic_lock_open" else "ic_lock",
            if (opened) okGreen else SinusColors.textPrimary,
            Modifier.size(ringSize * 0.33f).rotate(tilt.value).graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
                alpha = iconAlpha.value.coerceIn(0f, 1f)
            }
        )
    }
}

@Composable
private fun PinField(
    length: Int,
    showDots: Boolean,
    placeholder: String,
    error: Boolean,
    success: Boolean,
    sparks: MutableList<Pair<Long, Float>>
) {
    val rimColor by animateColorAsState(if (error) dangerRed else SinusColors.border)
    val shape = RoundedCornerShape(SinusColors.radius(29.dp))
    val rim = if (success) Brush.verticalGradient(listOf(okGreen, lerp(SinusColors.border, okGreen, 0.8f))) else SolidColor(rimColor)
    Box(
        Modifier.size(264.dp, 58.dp).clip(shape).background(SinusColors.card).border(if (success) 2.5.dp else 1.3.dp, rim, shape),
        contentAlignment = Alignment.Center
    ) {
        if (length == 0) {
            Text(placeholder, color = SinusColors.textPrimary.copy(alpha = 0.43f), fontSize = 15.sp)
        }
        if (showDots) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(length) { i -> key(i) { Dot() } }
            }
        } else {
            for (spark in sparks.toList()) {
                key(spark.first) { Spark(spark.second) { sparks.remove(spark) } }
            }
        }
    }
}

@Composable
private fun Dot() {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(200)) }
    val glow = SinusColors.primary
    val core = if (SinusColors.palette.dark) Color.White else SinusColors.primary
    Canvas(Modifier.size(21.dp, 40.dp)) {
        val a = appear.value
        val c = center
        val glowR = (13f + 9f * (1f - a)).dp.toPx() * a
        if (glowR > 0f) {
            drawCircle(
                Brush.radialGradient(0f to glow.copy(alpha = 0.45f * a), 1f to glow.copy(alpha = 0f), center = c, radius = glowR),
                radius = glowR, center = c
            )
        }
        drawCircle(core, 3.2.dp.toPx() * a, c)
    }
}

@Composable
private fun Spark(x: Float, onGone: () -> Unit) {
    val life = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        life.animateTo(1f, tween(620))
        onGone()
    }
    val accent = SinusColors.primary
    val core = if (SinusColors.palette.dark) Color.White else SinusColors.primary
    Canvas(Modifier.fillMaxSize()) {
        val t = life.value
        val grow = minOf(1f, t * 2.2f)
        val alpha = 1f - t
        val c = Offset(size.width * (0.2f + 0.6f * x), size.height / 2f)
        val r = 5.dp.toPx() * grow
        if (r > 0f) {
            drawCircle(
                Brush.radialGradient(0f to accent.copy(alpha = 0.66f * alpha), 1f to accent.copy(alpha = 0f), center = c, radius = r * 4f),
                radius = r * 4f, center = c
            )
            drawCircle(core.copy(alpha = alpha), r * 0.55f, c)
        }
    }
}

@Composable
private fun KeyButton(size: Dp, glass: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val fill = SinusColors.card
    val bg = when {
        !glass -> Color.Transparent
        pressed -> SinusColors.primary.copy(alpha = 0.35f)
        hovered -> SinusColors.primary.copy(alpha = 0.18f)
        else -> fill
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .then(if (glass) Modifier.border(1.dp, SinusColors.border, CircleShape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun Keypad(showHello: Boolean, helloLabel: String, keySize: Dp, onDigit: (Char) -> Unit, onDelete: () -> Unit, onHello: () -> Unit) {
    val rows = listOf("123", "456", "789")
    val gap = keySize * 0.18f
    val across = keySize * 0.36f
    Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(across)) {
                for (c in row) DigitKey(c, keySize, onDigit)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(across)) {
            if (showHello) {
                KeyButton(keySize, glass = false, onClick = onHello) {
                    SinusIcon("ic_fingerprint", SinusColors.primary, Modifier.size(28.dp), helloLabel)
                }
            } else {
                Spacer(Modifier.size(keySize))
            }
            DigitKey('0', keySize, onDigit)
            KeyButton(keySize, glass = false, onClick = onDelete) {
                SinusIcon("ic_backspace", SinusColors.textPrimary, Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun DigitKey(c: Char, size: Dp, onDigit: (Char) -> Unit) {
    KeyButton(size, glass = true, onClick = { onDigit(c) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val big = (size.value * 0.41f).sp
            Text(c.toString(), color = SinusColors.textPrimary, fontSize = big, lineHeight = big * 1.1f)
            val l = letters[c]
            if (l != null) {
                Text(l, color = SinusColors.textPrimary.copy(alpha = 0.6f), fontSize = 9.5.sp, lineHeight = 11.sp, letterSpacing = 1.sp)
            }
        }
    }
}
