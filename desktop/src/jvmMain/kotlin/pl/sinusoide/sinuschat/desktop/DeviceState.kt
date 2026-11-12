package pl.sinusoide.sinuschat.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.sinusoide.sinuschat.link.LinkCrypto
import pl.sinusoide.sinuschat.link.LinkIdentity
import java.util.Base64

@Serializable
data class PairedPhone(
    val key: String,
    val name: String,
    val host: String? = null,
    val port: Int = 0,
    val pairedAt: Long,
    val userName: String = "",
    val userColor: String = "",
    val userAvatar: String = ""
) {
    val keyBytes: ByteArray get() = Base64.getDecoder().decode(key)
    val id: String get() = LinkCrypto.fingerprint(keyBytes)
}

@Serializable
data class WindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val maximized: Boolean = false
) {
    fun visibleOn(screens: List<java.awt.Rectangle>): Boolean {
        if (width < 200f || height < 150f) return false
        val grip = java.awt.Rectangle((x + 40f).toInt(), y.toInt(), (width - 80f).coerceAtLeast(40f).toInt(), 40)
        return screens.any { it.intersects(grip) }
    }
}

@Serializable
data class NotifyPrefs(
    val messages: Boolean = true,
    val groups: Boolean = true,
    val preview: String = PREVIEW_FULL,
    val inApp: Boolean = true,
    val quiet: Boolean = false,
    val quietFrom: Int = 22 * 60,
    val quietTo: Int = 7 * 60,
    val quietCalls: Boolean = true,
    val updates: Boolean = true,
    val updateSnooze: String? = null,
    val updateSnoozeUntil: Long = 0L,
    val pendingVersion: String? = null,
    val pendingFile: String? = null,
    val pendingSha: String? = null,
    val pendingTries: Int = 0,
    val background: Boolean = true,
    val backgroundHint: Boolean = false
) {
    fun quietAt(minutes: Int): Boolean = quiet && when {
        quietFrom == quietTo -> false
        quietFrom < quietTo -> minutes in quietFrom until quietTo
        else -> minutes >= quietFrom || minutes < quietTo
    }

    fun allowsMessage(conv: String, muted: Boolean, appInUse: Boolean, minutes: Int): Boolean {
        if (muted || quietAt(minutes)) return false
        val group = conv.startsWith("g_") || conv.startsWith("group:")
        if (if (group) !groups else !messages) return false
        if (appInUse && !inApp) return false
        return true
    }

    fun allowsCallAlert(minutes: Int): Boolean = !quietAt(minutes) || quietCalls

    fun previewFor(phone: String): String {
        val rank = listOf(PREVIEW_FULL, PREVIEW_NAME, PREVIEW_HIDDEN)
        return rank[maxOf(rank.indexOf(phone).coerceAtLeast(0), rank.indexOf(preview).coerceAtLeast(0))]
    }

    companion object {
        const val PREVIEW_FULL = "full"
        const val PREVIEW_NAME = "name"
        const val PREVIEW_HIDDEN = "hidden"

        fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

@Serializable
data class DeviceState(
    val privateKey: String,
    val publicKey: String,
    val phone: PairedPhone? = null,
    val welcomed: Boolean = false,
    val languageChosen: Boolean = false,
    val setupDone: Boolean = false,
    val language: String? = null,
    val manualHost: String? = null,
    val manualPort: Int = 0,
    val theme: String? = null,
    val safetyPrompts: Boolean = true,
    val window: WindowBounds? = null,
    val notify: NotifyPrefs = NotifyPrefs()
) {
    fun manual(): ManualAddress? =
        manualHost?.takeIf { it.isNotBlank() && manualPort in 1..65535 }?.let { ManualAddress(it, manualPort) }

    fun identity(): LinkIdentity =
        LinkIdentity.restore(Base64.getDecoder().decode(privateKey), Base64.getDecoder().decode(publicKey))

    companion object {
        private const val FILE = "device.bin"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): DeviceState {
            val stored = SecureStore.read(FILE)?.let {
                runCatching { json.decodeFromString(serializer(), String(it, Charsets.UTF_8)) }.getOrNull()
            }
            if (stored != null) return stored
            val id = LinkIdentity.generate()
            val enc = Base64.getEncoder()
            return DeviceState(enc.encodeToString(id.privateBytes), enc.encodeToString(id.publicBytes)).also { it.save() }
        }
    }

    fun save() {
        SecureStore.write(FILE, json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8))
    }
}
