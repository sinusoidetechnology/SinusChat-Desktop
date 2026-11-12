package pl.sinusoide.sinuschat.desktop.net

import java.util.Base64
import java.util.concurrent.TimeUnit

object FirewallCheck {

    data class Result(val blockRules: Int, val publicNetwork: Boolean, val firewallOn: Boolean) {
        val blocked: Boolean get() = firewallOn && blockRules > 0
    }

    val isWindows: Boolean get() = System.getProperty("os.name", "").startsWith("Windows")

    fun programPath(): String = ProcessHandle.current().info().command().orElse("")

    private fun quoted(s: String) = "'" + s.replace("'", "''") + "'"

    private fun encoded(script: String): String =
        Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))

    private fun powershell(vararg args: String, timeoutSec: Long): Pair<Int, String>? = try {
        val p = ProcessBuilder(listOf("powershell.exe", "-NoProfile", "-NonInteractive") + args)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else p.exitValue() to out
    } catch (_: Exception) {
        null
    }

    fun parse(output: String): Result? {
        val line = output.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("B=") } ?: return null
        val values = line.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1).trim().toIntOrNull()
        }.toMap()
        return Result(values["B"] ?: 0, (values["P"] ?: 0) > 0, (values["O"] ?: 0) > 0)
    }

    fun check(): Result? {
        if (!isWindows) return null
        val path = programPath().ifEmpty { return null }
        val script = """
            ${'$'}p = ${quoted(path)}
            ${'$'}r = @(Get-NetFirewallApplicationFilter -Program ${'$'}p -ErrorAction SilentlyContinue | Get-NetFirewallRule -ErrorAction SilentlyContinue)
            ${'$'}b = @(${'$'}r | Where-Object { ${'$'}_.Enabled -eq 'True' -and ${'$'}_.Action -eq 'Block' }).Count
            ${'$'}a = @(${'$'}r | Where-Object { ${'$'}_.Enabled -eq 'True' -and ${'$'}_.Action -eq 'Allow' -and ${'$'}_.Direction -eq 'Inbound' -and "${'$'}(${'$'}_.Profile)" -match 'Any|Public' } | Where-Object { ${'$'}f = ${'$'}_ | Get-NetFirewallPortFilter -ErrorAction SilentlyContinue; ("${'$'}(${'$'}f.Protocol)" -match '^(UDP|Any)${'$'}') -and ((@(${'$'}f.LocalPort) -contains '5353') -or (@(${'$'}f.LocalPort) -contains 'Any')) }).Count
            ${'$'}n = @(Get-NetConnectionProfile -ErrorAction SilentlyContinue | Where-Object { ${'$'}_.NetworkCategory -eq 'Public' }).Count
            ${'$'}o = @(Get-NetFirewallProfile -ErrorAction SilentlyContinue | Where-Object { ${'$'}_.Enabled -eq 'True' }).Count
            if (${'$'}a -gt 0 -or ${'$'}o -eq 0) { ${'$'}n = 0 }
            "B=${'$'}b;P=${'$'}n;O=${'$'}o"
        """.trimIndent()
        val (_, out) = powershell("-EncodedCommand", encoded(script), timeoutSec = 40) ?: return null
        return parse(out)
    }

    fun fixScript(path: String): String = """
        ${'$'}p = ${quoted(path)}
        Get-NetFirewallApplicationFilter -Program ${'$'}p -ErrorAction SilentlyContinue | Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { ${'$'}_.Action -eq 'Block' } | Remove-NetFirewallRule -ErrorAction SilentlyContinue
        Get-NetFirewallRule -DisplayName 'SinusChat (mDNS)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
        New-NetFirewallRule -DisplayName 'SinusChat (mDNS)' -Direction Inbound -Program ${'$'}p -Protocol UDP -LocalPort 5353 -Action Allow -Profile Any | Out-Null
    """.trimIndent()

    fun fix(): Boolean {
        if (!isWindows) return false
        val path = programPath().ifEmpty { return false }
        val inner = encoded(fixScript(path))
        val outer = "Start-Process powershell.exe -Verb RunAs -Wait -WindowStyle Hidden " +
            "-ArgumentList '-NoProfile','-NonInteractive','-EncodedCommand','$inner'"
        val result = powershell("-Command", outer, timeoutSec = 180) ?: return false
        return result.first == 0
    }

    fun openSettings() {
        runCatching {
            ProcessBuilder("control.exe", "/name", "Microsoft.WindowsFirewall", "/page", "pageConfigureApps").start()
        }
    }

    fun openNetworkSettings() {
        pl.sinusoide.sinuschat.desktop.system.Platform.openSettings(pl.sinusoide.sinuschat.desktop.system.Platform.Settings.NETWORK)
    }
}
