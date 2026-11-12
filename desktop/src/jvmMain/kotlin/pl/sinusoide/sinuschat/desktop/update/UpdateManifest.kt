package pl.sinusoide.sinuschat.desktop.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Serializable
data class UpdatePackage(
    val url: String,
    val sha256: String,
    val size: Long,
    val name: String = ""
)

@Serializable
data class UpdateManifest(
    val app: String,
    val version: String,
    val published: Long = 0L,
    val changelog: String? = null,
    val packages: Map<String, UpdatePackage> = emptyMap()
)

object UpdateManifests {

    const val APP = "SinusChat-Desktop"
    const val PUBLIC_KEY = "MCowBQYDK2VwAyEAPiRafwKKWSIc/2FVznp0GgoKmS+lPZAzcanPRVPdIrI="
    const val MAX_PACKAGE_BYTES = 800L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val SHA = Regex("^[0-9a-f]{64}$")

    fun verify(data: ByteArray, signature: String, publicKey: String = PUBLIC_KEY): Boolean = runCatching {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(key)
        sig.update(data)
        sig.verify(Base64.getDecoder().decode(signature.trim()))
    }.getOrDefault(false)

    fun parse(data: ByteArray, signature: String, publicKey: String = PUBLIC_KEY): UpdateManifest? {
        if (!verify(data, signature, publicKey)) return null
        val manifest = runCatching { json.decodeFromString(UpdateManifest.serializer(), String(data, Charsets.UTF_8)) }.getOrNull() ?: return null
        if (manifest.app != APP || parts(manifest.version) == null) return null
        val packages = manifest.packages.filterValues { p ->
            p.url.startsWith("https://") && SHA.matches(p.sha256.lowercase()) && p.size in 1..MAX_PACKAGE_BYTES
        }
        return manifest.copy(packages = packages)
    }

    private fun parts(v: String): List<Int>? {
        val raw = v.trim().split('.')
        if (raw.isEmpty() || raw.size > 4) return null
        return raw.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    }

    fun compare(a: String, b: String): Int {
        val pa = parts(a) ?: return 0
        val pb = parts(b) ?: return 0
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(remote: String, local: String): Boolean = parts(remote) != null && compare(remote, local) > 0

    fun platformKeys(
        os: String = System.getProperty("os.name", ""),
        arch: String = System.getProperty("os.arch", ""),
        hasDpkg: Boolean = File("/usr/bin/dpkg").exists(),
        hasRpm: Boolean = File("/usr/bin/rpm").exists(),
        setupInstalled: Boolean = installedBySetup()
    ): List<String> {
        val o = os.lowercase()
        val arm = arch.lowercase().let { it == "aarch64" || it == "arm64" }
        return when {
            o.startsWith("windows") -> if (setupInstalled) listOf("windows-x64-setup") else listOf("windows-x64")
            o.startsWith("mac") || o.contains("darwin") -> if (arm) listOf("mac-arm64", "mac-x64") else listOf("mac-x64")
            o.contains("linux") -> {
                val cpu = if (arm) "arm64" else "x64"
                val order = when {
                    hasDpkg -> listOf("deb", "rpm")
                    hasRpm -> listOf("rpm", "deb")
                    else -> listOf("deb", "rpm")
                }
                order.map { "linux-$cpu-$it" }
            }
            else -> emptyList()
        }
    }

    fun installedBySetup(): Boolean = runCatching {
        val dir = System.getProperty("jpackage.app-path")?.let { File(it).absoluteFile.parentFile } ?: return false
        pl.sinusoide.sinuschat.desktop.installer.InstallerCore.isInstalledBySetup(dir)
    }.getOrDefault(false)

    fun packageFor(manifest: UpdateManifest, keys: List<String> = platformKeys()): UpdatePackage? =
        keys.firstNotNullOfOrNull { manifest.packages[it] }
}
