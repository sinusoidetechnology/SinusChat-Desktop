package pl.sinusoide.sinuschat.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.i18n.LocalChatStrings
import pl.sinusoide.sinuschat.desktop.i18n.LocalStrings
import pl.sinusoide.sinuschat.desktop.ui.MainScreen
import pl.sinusoide.sinuschat.desktop.ui.SearchingScreen
import pl.sinusoide.sinuschat.desktop.ui.SetupActions
import pl.sinusoide.sinuschat.desktop.ui.SetupFlow
import pl.sinusoide.sinuschat.desktop.ui.SetupStep
import pl.sinusoide.sinuschat.desktop.ui.SinusTheme
import pl.sinusoide.sinuschat.desktop.ui.anim.RevealOrigin
import pl.sinusoide.sinuschat.desktop.ui.anim.circleReveal
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.ChatSummary
import pl.sinusoide.sinuschat.link.LinkFrame
import pl.sinusoide.sinuschat.link.LinkIdentity
import pl.sinusoide.sinuschat.link.PairingCode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenPreviewTest {

    private val out = File("build/preview").apply { mkdirs() }

    private fun frames(width: Int, height: Int, atMs: List<Long>, pace: Boolean = false, content: @Composable () -> Unit): List<ByteArray> {
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        val shots = ArrayList<ByteArray>()
        try {
            scene.render(0L)
            var t = 0L
            for (ms in atMs) {
                while (t < ms) {
                    t = minOf(ms, t + 16)
                    if (pace) Thread.sleep(16)
                    scene.render(t * 1_000_000L + 1_000_000L)
                }
                val image = scene.render(t * 1_000_000L + 1_000_000L)
                image.encodeToData(EncodedImageFormat.PNG)?.let { shots += it.bytes }
            }
        } finally {
            scene.close()
        }
        return shots
    }

    private fun render(name: String, atMs: List<Long>, lang: AppLanguage = AppLanguage.PL, pace: Boolean = false, content: @Composable () -> Unit) {
        val shots = frames(1180, 760, atMs, pace) {
            SinusTheme {
                CompositionLocalProvider(
                    LocalStrings provides lang.strings,
                    LocalChatStrings provides lang.chat,
                    pl.sinusoide.sinuschat.desktop.i18n.LocalMediaStrings provides lang.media,
                    pl.sinusoide.sinuschat.desktop.i18n.LocalSettingsStrings provides lang.settings
                ) { content() }
            }
        }
        shots.forEachIndexed { i, bytes -> File(out, "$name-${atMs[i]}.png").writeBytes(bytes) }
    }

    @Test
    fun appIconLoads() {
        assertEquals(512f, appIcon().intrinsicSize.width)
    }

    @Test
    fun renderSetup() {
        val code = PairingCode.create(LinkIdentity.generate().publicBytes).encode()
        val pairing = LinkState.Pairing(code, 1)
        render("welcome-en", listOf(4500), AppLanguage.EN) {
            SetupFlow(SetupStep.WELCOME, pairing, AppLanguage.EN, SetupActions())
        }
        render("language", listOf(2600)) {
            SetupFlow(SetupStep.LANGUAGE, pairing, AppLanguage.PL, SetupActions())
        }
        render("pair", listOf(1500)) {
            SetupFlow(SetupStep.PAIR, pairing, AppLanguage.PL, SetupActions())
        }
        render("done", listOf(2500)) {
            SetupFlow(SetupStep.DONE, LinkState.Connected("Pixel 8"), AppLanguage.PL, SetupActions())
        }
        render("searching-rejected", listOf(300)) { SearchingScreen(LinkState.Searching("Pixel 8", true)) {} }
    }

    private fun sampleStore(): ChatStore {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-preview").toString())
        val link = PhoneLink(object : Discovery {
            override fun start() {}
            override fun refresh() {}
            override fun candidates() = emptyList<Discovery.Candidate>()
            override fun stop() {}
        })
        val store = ChatStore(link)
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val me = "me"
        val ania = "a".repeat(64)
        val conv = "${ania}_$me"
        val group = "g_team"
        val chats = listOf(
            ChatSummary(conv, "Ania", peer = ania, color = "#E0578B", lastText = "To widzimy się jutro o 10?", lastAt = now - 60_000, unread = 2, online = true),
            ChatSummary(group, "Projekt SinusChat", isGroup = true, color = "#3FB97A", lastText = "Wrzuciłem nową wersję", lastAt = now - 3_600_000, lastFromMe = true, lastStatus = "read"),
            ChatSummary("b_me", "Bartek", color = "#E67E22", lastKind = ChatMessage.KIND_PHOTO, lastAt = now - day, muted = true, unread = 5),
            ChatSummary("c_me", "Mama", color = "#9B6BE0", lastText = "Zadzwoń jak będziesz mógł", lastAt = now - 3 * day, lastFromMe = false)
        )
        val msgs = listOf(
            ChatMessage("m0", conv, "system", kind = ChatMessage.KIND_SYSTEM, text = "Wiadomości są szyfrowane end-to-end", at = now - day - 7_200_000),
            ChatMessage("m1", conv, ania, text = "Hej! Masz chwilę?", at = now - day - 3_600_000),
            ChatMessage("m2", conv, me, mine = true, text = "Jasne, co tam?", at = now - day - 3_500_000, status = "read"),
            ChatMessage("m3", conv, ania, kind = ChatMessage.KIND_PHOTO, fileName = "IMG_2041.jpg", at = now - 600_000, reactions = mapOf(me to "❤️")),
            ChatMessage("m4", conv, ania, text = "Zobacz to zdjęcie z wczoraj", at = now - 590_000),
            ChatMessage("m5", conv, me, mine = true, text = "Piękne! Wysyłam Ci zaraz plan na jutro, dopisz jeśli czegoś brakuje.", at = now - 300_000, status = "delivered", replyTo = "m4", replyText = "Zobacz to zdjęcie z wczoraj", edited = true),
            ChatMessage("m6", conv, ania, text = "To widzimy się jutro o 10?", at = now - 60_000),
            ChatMessage("m7", conv, me, mine = true, text = "Tak, będę", at = now - 30_000, status = "pending")
        )
        link.frameListener?.invoke(LinkFrame.Chats(chats))
        link.frameListener?.invoke(LinkFrame.PhoneState(false))
        store.open(conv)
        link.frameListener?.invoke(LinkFrame.Messages(conv, msgs, LinkFrame.MODE_LATEST, more = false))
        link.frameListener?.invoke(LinkFrame.TypingIn(group, "Kuba", true))
        return store
    }

    @Test
    fun renderChat() {
        val store = sampleStore()
        render("chat", listOf(400)) {
            MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
        }
        render("chat-offline-en", listOf(400), AppLanguage.EN) {
            MainScreen(LinkState.Searching("Pixel 8", false), store, AppLanguage.EN, false, {}, {})
        }
        render("reveal", listOf(2500)) {
            Box(Modifier.fillMaxSize()) {
                SetupFlow(SetupStep.DONE, LinkState.Connected("Pixel 8"), AppLanguage.PL, SetupActions())
                Box(Modifier.fillMaxSize().circleReveal(RevealOrigin(Offset(590f, 270f), 53f)) { 0.3f }) {
                    MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
                }
            }
        }
        store.close()
        render("chat-empty", listOf(300)) {
            MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
        }
        link(store).frameListener?.invoke(LinkFrame.PhoneState(true))
        render("chat-locked", listOf(300)) {
            MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
        }
    }

    @Test
    fun renderConnectionHelp() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-help").toString())
        val link = PhoneLink(object : Discovery {
            override fun start() {}
            override fun refresh() {}
            override fun candidates() = emptyList<Discovery.Candidate>()
            override fun stop() {}
        })
        val firewall = pl.sinusoide.sinuschat.desktop.net.FirewallMonitor(
            checker = { pl.sinusoide.sinuschat.desktop.net.FirewallCheck.Result(2, publicNetwork = true, firewallOn = true) },
            fixer = { false }
        )
        firewall.check()
        val end = System.currentTimeMillis() + 5_000
        while (firewall.state.value !is pl.sinusoide.sinuschat.desktop.net.FirewallMonitor.Ui.Done && System.currentTimeMillis() < end) Thread.sleep(20)
        link.setManualAddress(ManualAddress("192.168.1.23", 47321))
        val code = PairingCode.create(LinkIdentity.generate().publicBytes).encode()
        for (lang in listOf(AppLanguage.PL, AppLanguage.DE)) {
            render("pair-trouble-${lang.code}", listOf(1600), lang) {
                CompositionLocalProvider(pl.sinusoide.sinuschat.desktop.i18n.LocalConnStrings provides lang.conn) {
                    SetupFlow(SetupStep.PAIR, LinkState.Pairing(code, 0), lang, SetupActions(),
                        issue = pl.sinusoide.sinuschat.desktop.ui.issueText(firewall))
                }
            }
            render("help-${lang.code}", listOf(300), lang) {
                CompositionLocalProvider(pl.sinusoide.sinuschat.desktop.i18n.LocalConnStrings provides lang.conn) {
                    pl.sinusoide.sinuschat.desktop.ui.ConnectionHelpDialog(link, firewall) {}
                }
            }
        }
        link.setManualAddress(null)
        SecureStore.delete("device.bin")
    }

    private fun picture(w: Int, h: Int, from: java.awt.Color, to: java.awt.Color, sun: Boolean = false): ByteArray {
        val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = java.awt.GradientPaint(0f, 0f, from, 0f, h.toFloat(), to)
        g.fillRect(0, 0, w, h)
        if (sun) {
            g.color = java.awt.Color(255, 214, 120)
            g.fillOval(w / 2 - h / 8, h / 2 - h / 8, h / 4, h / 4)
        }
        g.color = java.awt.Color(20, 30, 40, 200)
        g.fillPolygon(intArrayOf(0, w / 3, w / 2, 3 * w / 4, w, w, 0), intArrayOf(h * 3 / 4, h / 2, h * 2 / 3, h / 2, h * 3 / 4, h, h), 7)
        g.dispose()
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    @Test
    fun renderMedia() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-media-preview").toString())
        val link = PhoneLink(object : Discovery {
            override fun start() {}
            override fun refresh() {}
            override fun candidates() = emptyList<Discovery.Candidate>()
            override fun stop() {}
        })
        val blobs = pl.sinusoide.sinuschat.desktop.chat.BlobCache()
        val store = ChatStore(link, pl.sinusoide.sinuschat.desktop.chat.ChatCache(), blobs)
        val now = System.currentTimeMillis()
        val me = "me"
        val ania = "a".repeat(64)
        val conv = "${ania}_$me"
        val sunset = picture(640, 420, java.awt.Color(255, 120, 80), java.awt.Color(70, 40, 120), sun = true)
        blobs.put("t:p1", sunset)
        blobs.put("f:p1", sunset)
        blobs.put("t:a1", picture(400, 400, java.awt.Color(80, 170, 230), java.awt.Color(20, 80, 60)))
        blobs.put("t:a2", picture(400, 400, java.awt.Color(240, 200, 90), java.awt.Color(200, 80, 60), sun = true))
        blobs.put("t:a3", picture(400, 300, java.awt.Color(60, 60, 110), java.awt.Color(10, 10, 30)))
        blobs.put("t:v1", picture(640, 360, java.awt.Color(40, 140, 120), java.awt.Color(10, 40, 60)))
        blobs.put("t:u1", picture(640, 420, java.awt.Color(120, 200, 160), java.awt.Color(30, 90, 120)))
        val chats = listOf(
            ChatSummary(conv, "Ania", peer = ania, color = "#E0578B", lastKind = ChatMessage.KIND_VOICE, lastAt = now - 60_000, online = true),
            ChatSummary("g_team", "Projekt SinusChat", isGroup = true, color = "#3FB97A", lastText = "Wrzuciłem nową wersję", lastAt = now - 3_600_000)
        )
        val msgs = listOf(
            ChatMessage("p1", conv, ania, kind = ChatMessage.KIND_PHOTO, fileName = "IMG_2041.jpg", text = "Zachód słońca nad jeziorem", at = now - 900_000, hasThumb = true, size = 2_400_000),
            ChatMessage("a1", conv, me, mine = true, kind = ChatMessage.KIND_PHOTO, at = now - 800_000, albumId = "alb", hasThumb = true, status = "read"),
            ChatMessage("a2", conv, me, mine = true, kind = ChatMessage.KIND_PHOTO, at = now - 800_000, albumId = "alb", hasThumb = true, text = "Z wakacji", status = "read"),
            ChatMessage("a3", conv, me, mine = true, kind = ChatMessage.KIND_PHOTO, at = now - 800_000, albumId = "alb", hasThumb = true, status = "read"),
            ChatMessage("v1", conv, ania, kind = ChatMessage.KIND_VIDEO, fileName = "VID_0003.mp4", at = now - 600_000, hasThumb = true, durationMs = 42_000, size = 18_300_000),
            ChatMessage("f1", conv, me, mine = true, kind = ChatMessage.KIND_FILE, fileName = "Umowa najmu 2026.pdf", at = now - 300_000, size = 245_000, status = "delivered"),
            ChatMessage("o1", conv, ania, kind = ChatMessage.KIND_PHOTO, viewOnce = true, at = now - 200_000),
            ChatMessage("vo1", conv, ania, kind = ChatMessage.KIND_VOICE, at = now - 60_000, durationMs = 14_000),
            ChatMessage("u1", conv, me, mine = true, kind = ChatMessage.KIND_PHOTO, fileName = "las.jpg", at = now - 5_000, hasThumb = true, status = ChatMessage.STATUS_UPLOADING)
        )
        link.frameListener?.invoke(LinkFrame.Chats(chats))
        store.open(conv)
        link.frameListener?.invoke(LinkFrame.Messages(conv, msgs, LinkFrame.MODE_LATEST, more = false))
        listOf("p1", "a1", "a2", "a3", "v1", "u1").forEach { store.media.requestThumb(conv, it) }
        val end = System.currentTimeMillis() + 5_000
        while (store.media.thumbs.value.size < 6 && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(6, store.media.thumbs.value.size)
        link.frameListener?.invoke(LinkFrame.SendProgress(conv, mapOf("u1" to 0.45f)))

        val dir = Files.createTempDirectory("sinuschat-staged").toFile()
        val staged = File(dir, "widok.png").apply { writeBytes(picture(300, 200, java.awt.Color(90, 160, 220), java.awt.Color(30, 60, 90))) }
        val doc = File(dir, "Faktura 10-2026.pdf").apply { writeBytes(ByteArray(48_000)) }
        kotlin.test.assertNotNull(pl.sinusoide.sinuschat.desktop.ui.chat.previewOf(staged, 160))
        val once = File(dir, "tylko-raz.png").apply { writeBytes(picture(300, 300, java.awt.Color(230, 120, 160), java.awt.Color(60, 30, 90), sun = true)) }
        store.uploads.stage(conv, listOf(staged, once, doc))
        store.uploads.staged.value[conv].orEmpty().let { list ->
            store.uploads.toggleOriginal(conv, list[0].id)
            store.uploads.toggleViewOnce(conv, list[1].id)
        }

        render("chat-media", listOf(600), pace = true) {
            MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
        }
        render("chat-media-en", listOf(600), AppLanguage.EN, pace = true) {
            MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.EN, false, {}, {})
        }
        val top = msgs.take(4).map { it.copy(conv = "g_team") }
        store.open("g_team")
        link.frameListener?.invoke(LinkFrame.Messages("g_team", top, LinkFrame.MODE_LATEST, more = false))
        render("chat-media-photos", listOf(600)) {
            MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
        }
        store.open(conv)
        render("media-viewer", listOf(600)) {
            pl.sinusoide.sinuschat.desktop.ui.chat.ImageViewer(store, msgs[0]) {}
        }
        render("drop-hint", listOf(200)) {
            Box(Modifier.fillMaxSize()) {
                MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
                pl.sinusoide.sinuschat.desktop.ui.chat.DropHint()
            }
        }
        store.uploads.staged.value[conv].orEmpty().forEach { store.uploads.unstage(conv, it.id) }
        store.shutdown()
    }

    @Test
    fun renderSettingsLockAndThemes() {
        val store = sampleStore()
        val link = link(store)
        val lock = pl.sinusoide.sinuschat.desktop.security.LockManager("lock-preview.bin")
        render("settings", listOf(300)) {
            pl.sinusoide.sinuschat.desktop.ui.settings.SettingsScreen(link, lock, AppLanguage.PL, {}, {}, {})
        }
        lock.setPin("482916")
        lock.setBiometric(true)
        render("pin-unlock", listOf(700)) {
            pl.sinusoide.sinuschat.desktop.ui.lock.PinScreen(lock, pl.sinusoide.sinuschat.desktop.ui.lock.PinMode.UNLOCK, {})
        }
        render("pin-set-zh", listOf(700), AppLanguage.ZH) {
            pl.sinusoide.sinuschat.desktop.ui.lock.PinScreen(lock, pl.sinusoide.sinuschat.desktop.ui.lock.PinMode.SET, {}, onCancel = {})
        }
        for (key in pl.sinusoide.sinuschat.desktop.ui.Themes.ORDER) {
            pl.sinusoide.sinuschat.desktop.ui.SinusColors.palette = pl.sinusoide.sinuschat.desktop.ui.Themes.of(key)
            render("theme-$key", listOf(400)) {
                Box(Modifier.fillMaxSize()) {
                    if (pl.sinusoide.sinuschat.desktop.ui.SinusColors.palette.backdrop) {
                        pl.sinusoide.sinuschat.desktop.ui.ThemeBackdrop(Modifier.fillMaxSize())
                    }
                    MainScreen(LinkState.Connected("Pixel 8"), store, AppLanguage.PL, false, {}, {})
                }
            }
        }
        pl.sinusoide.sinuschat.desktop.ui.SinusColors.palette = pl.sinusoide.sinuschat.desktop.ui.Themes.dark
        lock.wipe()
        store.shutdown()
    }

    private fun link(store: ChatStore): PhoneLink {
        val f = ChatStore::class.java.getDeclaredField("link").apply { isAccessible = true }
        return f.get(store) as PhoneLink
    }
}
