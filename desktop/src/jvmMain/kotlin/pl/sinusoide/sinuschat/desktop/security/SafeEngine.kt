package pl.sinusoide.sinuschat.desktop.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.URI

object SafeEngine {

    enum class Risk { OPEN_LINK, OPEN_FILE, SAVE_FILE, DANGEROUS_FILE }

    class Ask(
        val risk: Risk,
        val subject: String,
        val onProceed: () -> Unit,
        val onCancel: (() -> Unit)?,
        val onSandbox: (() -> Unit)? = null
    )

    @Volatile var enabled = true

    private val _pending = MutableStateFlow<Ask?>(null)
    val pending: StateFlow<Ask?> = _pending

    fun guard(risk: Risk, subject: String?, onCancel: (() -> Unit)? = null, onProceed: () -> Unit) {
        if (!enabled) {
            onProceed()
            return
        }
        show(Ask(risk, subject.orEmpty(), onProceed, onCancel))
    }

    fun warnDangerous(subject: String?, onSandbox: (() -> Unit)?, onCancel: (() -> Unit)? = null, onProceed: () -> Unit) {
        show(Ask(Risk.DANGEROUS_FILE, subject.orEmpty(), onProceed, onCancel, onSandbox))
    }

    fun openLink(url: String, open: (String) -> Unit) {
        val run = { runCatching { open(url) }.isSuccess }
        if (isExempt(url)) run() else guard(Risk.OPEN_LINK, hostOf(url)) { run() }
    }

    fun proceed(ask: Ask) {
        if (!_pending.compareAndSet(ask, null)) return
        ask.onProceed()
    }

    fun sandbox(ask: Ask) {
        val action = ask.onSandbox ?: return
        if (!_pending.compareAndSet(ask, null)) return
        action()
    }

    fun cancel(ask: Ask) {
        if (!_pending.compareAndSet(ask, null)) return
        ask.onCancel?.invoke()
    }

    private fun show(ask: Ask) {
        val previous = _pending.value
        _pending.value = ask
        previous?.onCancel?.invoke()
    }

    private val sinusoidePages = mapOf(
        "ko-fi.com" to listOf("/sinusoide"),
        "patreon.com" to listOf("/cw/sinusoidetechnology", "/sinusoidetechnology")
    )

    fun isExempt(url: String): Boolean {
        val uri = runCatching { URI(url).normalize() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        if (uri.rawUserInfo != null || (uri.port != -1 && uri.port != 80 && uri.port != 443)) return false
        val host = uri.host?.lowercase()?.trimEnd('.')?.removePrefix("www.") ?: return false
        if (host == "sinusoide.pl" || host.endsWith(".sinusoide.pl")) return true
        val path = (uri.path ?: "").lowercase().trimEnd('/')
        return sinusoidePages[host]?.any { path == it || path.startsWith("$it/") } == true
    }

    fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
}
