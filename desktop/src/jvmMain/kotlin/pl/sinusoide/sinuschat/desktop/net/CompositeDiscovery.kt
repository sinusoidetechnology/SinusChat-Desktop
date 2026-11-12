package pl.sinusoide.sinuschat.desktop.net

import pl.sinusoide.sinuschat.desktop.Discovery

class CompositeDiscovery(private val parts: List<Discovery>) : Discovery {

    override fun start() = parts.forEach { runCatching { it.start() } }

    override fun refresh() = parts.forEach { runCatching { it.refresh() } }

    override fun candidates(): List<Discovery.Candidate> =
        parts.flatMap { runCatching { it.candidates() }.getOrDefault(emptyList()) }
            .sortedByDescending { it.seenAt }
            .distinctBy { it.host to it.port }

    override fun stop() = parts.forEach { runCatching { it.stop() } }
}
