package pl.sinusoide.sinuschat.desktop.security

import java.util.Base64
import java.util.concurrent.TimeUnit

object WindowsHello {

    enum class Availability { AVAILABLE, NOT_SET_UP, NONE }

    private val isWindows: Boolean get() = System.getProperty("os.name", "").startsWith("Windows")

    @Volatile private var cached: Availability? = null

    private val PRELUDE = """
${'$'}ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
${'$'}asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await(${'$'}op, [Type]${'$'}type) {
    ${'$'}task = ${'$'}asTask.MakeGenericMethod(${'$'}type).Invoke(${'$'}null, @(${'$'}op))
    ${'$'}task.Wait(-1) | Out-Null
    ${'$'}task.Result
}
[Windows.Security.Credentials.UI.UserConsentVerifier, Windows.Security.Credentials.UI, ContentType = WindowsRuntime] | Out-Null
"""

    private fun run(script: String, timeoutSec: Long): String? = try {
        val encoded = Base64.getEncoder().encodeToString((PRELUDE + script).toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else out
    } catch (_: Exception) {
        null
    }

    fun availability(refresh: Boolean = false): Availability {
        if (!refresh) cached?.let { return it }
        if (!isWindows) return Availability.NONE.also { cached = it }
        val out = run(
            "Await ([Windows.Security.Credentials.UI.UserConsentVerifier]::CheckAvailabilityAsync()) " +
                "([Windows.Security.Credentials.UI.UserConsentVerifierAvailability])",
            timeoutSec = 30
        ).orEmpty()
        val result = when {
            out.contains("Available") && !out.contains("NotConfigured") -> Availability.AVAILABLE
            out.contains("NotConfigured") || out.contains("DisabledByPolicy") -> Availability.NOT_SET_UP
            else -> Availability.NONE
        }
        cached = result
        return result
    }

    fun verify(message: String): Boolean {
        if (!isWindows) return false
        val text = "'" + message.replace("'", "''") + "'"
        val out = run(
            "Await ([Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync($text)) " +
                "([Windows.Security.Credentials.UI.UserConsentVerificationResult])",
            timeoutSec = 120
        ) ?: return false
        return out.lineSequence().any { it.trim() == "Verified" }
    }
}
