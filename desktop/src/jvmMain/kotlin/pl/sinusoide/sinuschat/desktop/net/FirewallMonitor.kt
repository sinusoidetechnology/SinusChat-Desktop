package pl.sinusoide.sinuschat.desktop.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

class FirewallMonitor(
    private val checker: () -> FirewallCheck.Result? = { FirewallCheck.check() },
    private val fixer: () -> Boolean = { FirewallCheck.fix() }
) {

    sealed interface Ui {
        data object Idle : Ui
        data object Checking : Ui
        data class Done(val result: FirewallCheck.Result?) : Ui
        data object Fixing : Ui
        data class Fixed(val ok: Boolean, val result: FirewallCheck.Result?) : Ui
    }

    private val _state = MutableStateFlow<Ui>(Ui.Idle)
    val state: StateFlow<Ui> = _state

    val issue: Boolean
        get() = when (val s = _state.value) {
            is Ui.Done -> s.result?.let { it.blocked || it.publicNetwork } == true
            is Ui.Fixed -> s.result?.let { it.blocked || it.publicNetwork } == true
            else -> false
        }

    fun check(force: Boolean = false) {
        val s = _state.value
        if (s is Ui.Checking || s is Ui.Fixing) return
        if (!force && (s is Ui.Done || s is Ui.Fixed)) return
        _state.value = Ui.Checking
        thread(isDaemon = true, name = "firewall-check") { _state.value = Ui.Done(runCatching(checker).getOrNull()) }
    }

    fun fix(onFixed: () -> Unit = {}) {
        if (_state.value is Ui.Fixing) return
        _state.value = Ui.Fixing
        thread(isDaemon = true, name = "firewall-fix") {
            val ok = runCatching(fixer).getOrDefault(false)
            val after = runCatching(checker).getOrNull()
            _state.value = Ui.Fixed(ok && after?.blocked != true, after)
            if (ok) onFixed()
        }
    }
}
