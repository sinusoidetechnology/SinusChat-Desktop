package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.update.UpdateManifests
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManifestTest {

    private val pair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(pair.public.encoded)
    private val sha = "a".repeat(64)

    private fun sign(data: ByteArray, key: KeyPair = pair): String {
        val s = Signature.getInstance("Ed25519")
        s.initSign(key.private)
        s.update(data)
        return Base64.getEncoder().encodeToString(s.sign())
    }

    private fun manifest(app: String = "SinusChat-Desktop", version: String = "1.1", url: String = "https://sinusoide.pl/pobierz/desktop/a.msi") = """
        {"app":"$app","version":"$version","published":1,"packages":{
          "windows-x64":{"url":"$url","sha256":"$sha","size":1000},
          "linux-x64-deb":{"url":"https://sinusoide.pl/a.deb","sha256":"$sha","size":1000}
        }}
    """.trimIndent().toByteArray()

    @Test
    fun acceptsSignedManifest() {
        val data = manifest()
        val m = assertNotNull(UpdateManifests.parse(data, sign(data), pub))
        assertEquals("1.1", m.version)
        assertEquals(2, m.packages.size)
    }

    @Test
    fun rejectsTamperedManifest() {
        val data = manifest()
        val sig = sign(data)
        val changed = String(data).replace("1.1", "9.9").toByteArray()
        assertNull(UpdateManifests.parse(changed, sig, pub))
    }

    @Test
    fun rejectsOtherKey() {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val data = manifest()
        assertNull(UpdateManifests.parse(data, sign(data, other), pub))
    }

    @Test
    fun rejectsOtherAppAndBadVersion() {
        val a = manifest(app = "SinusChat")
        assertNull(UpdateManifests.parse(a, sign(a), pub))
        val b = manifest(version = "1.1-beta")
        assertNull(UpdateManifests.parse(b, sign(b), pub))
    }

    @Test
    fun dropsInsecurePackages() {
        val data = manifest(url = "http://sinusoide.pl/a.msi")
        val m = assertNotNull(UpdateManifests.parse(data, sign(data), pub))
        assertNull(m.packages["windows-x64"])
        assertNotNull(m.packages["linux-x64-deb"])
    }

    @Test
    fun comparesVersions() {
        assertTrue(UpdateManifests.isNewer("1.1", "1.0"))
        assertTrue(UpdateManifests.isNewer("1.10", "1.9"))
        assertTrue(UpdateManifests.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateManifests.isNewer("1.0", "1.0"))
        assertFalse(UpdateManifests.isNewer("1.0.0", "1.0"))
        assertFalse(UpdateManifests.isNewer("0.9", "1.0"))
        assertFalse(UpdateManifests.isNewer("abc", "1.0"))
    }

    @Test
    fun picksPackageForPlatform() {
        assertEquals(listOf("windows-x64"), UpdateManifests.platformKeys("Windows 11", "amd64", setupInstalled = false))
        assertEquals(listOf("windows-x64-setup"), UpdateManifests.platformKeys("Windows 11", "amd64", setupInstalled = true))
        assertEquals(listOf("mac-arm64", "mac-x64"), UpdateManifests.platformKeys("Mac OS X", "aarch64"))
        assertEquals(listOf("linux-x64-rpm", "linux-x64-deb"), UpdateManifests.platformKeys("Linux", "amd64", hasDpkg = false, hasRpm = true))
        val data = manifest()
        val m = assertNotNull(UpdateManifests.parse(data, sign(data), pub))
        assertNotNull(UpdateManifests.packageFor(m, listOf("linux-x64-rpm", "linux-x64-deb")))
        assertNull(UpdateManifests.packageFor(m, listOf("mac-arm64")))
    }

    @Test
    fun versionComesFromBuild() {
        assertTrue(AppVersion.name != "0")
        assertEquals("SinusChat Desktop " + AppVersion.name, PhoneLink.APP_NAME)
    }

    @Test
    fun releaseManifestMatchesEmbeddedKey() {
        val json = File("build/update/desktop-latest.json")
        val sig = File("build/update/desktop-latest.json.sig")
        if (!json.exists() || !sig.exists()) return
        assertNotNull(UpdateManifests.parse(json.readBytes(), sig.readText()))
    }
}
