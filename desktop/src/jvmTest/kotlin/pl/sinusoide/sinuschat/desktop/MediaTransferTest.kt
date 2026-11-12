package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.chat.ChatStore
import pl.sinusoide.sinuschat.desktop.chat.MediaStore
import pl.sinusoide.sinuschat.desktop.chat.UploadQueue
import pl.sinusoide.sinuschat.desktop.ui.chat.groupAlbums
import pl.sinusoide.sinuschat.link.ChatMessage
import pl.sinusoide.sinuschat.link.LinkBeacon
import pl.sinusoide.sinuschat.link.LinkConnection
import pl.sinusoide.sinuschat.link.LinkFrame
import pl.sinusoide.sinuschat.link.PairingCode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaTransferTest {

    private class Discover(private val phone: PhoneLinkFlowTest.FakePhone) : Discovery {
        override fun start() {}
        override fun refresh() {}
        override fun stop() {}
        override fun candidates() =
            listOf(Discovery.Candidate(LinkBeacon.create(phone.identity, phone.known.toList()), "127.0.0.1", phone.server.localPort, System.currentTimeMillis()))
    }

    private fun waitFor(timeoutMs: Long = 15_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(20)
        }
        throw AssertionError("timeout")
    }

    private fun stream(c: LinkConnection, req: String, name: String, data: ByteArray) {
        c.send(LinkFrame.MediaStart(req, name, "application/octet-stream", data.size.toLong()))
        var seq = 0
        var off = 0
        while (off < data.size) {
            val n = minOf(LinkFrame.CHUNK_BYTES, data.size - off)
            c.send(LinkFrame.MediaChunk(req, seq++, Base64.getEncoder().encodeToString(data.copyOfRange(off, off + n))))
            off += n
        }
        c.send(LinkFrame.MediaEnd(req))
    }

    @Test
    fun downloadsAndUploadsOverLink() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-media").toString())
        SecureStore.delete("device.bin")
        val phone = PhoneLinkFlowTest.FakePhone()
        val file = Random(7).nextBytes(LinkFrame.CHUNK_BYTES * 3 + 1234)
        val thumb = javaClass.getResourceAsStream("/sinuschat.png")!!.use { it.readBytes() }
        val uploads = ConcurrentHashMap<String, ByteArrayOutputStream>()
        val names = ConcurrentHashMap<String, String>()
        val commits = CopyOnWriteArrayList<LinkFrame.UploadCommit>()
        val cancels = CopyOnWriteArrayList<String>()
        var rejectNext: String? = null
        phone.handler = { c, f ->
            when (f) {
                is LinkFrame.MediaRequest -> when {
                    f.msg == "gone" -> c.send(LinkFrame.MediaError(f.req, LinkFrame.ERROR_GONE))
                    f.msg == "slow" -> c.send(LinkFrame.MediaStart(f.req, "slow.bin", "application/octet-stream", 10_000_000))
                    f.kind == LinkFrame.MEDIA_THUMB -> stream(c, f.req, "t.png", thumb)
                    else -> stream(c, f.req, "raport.pdf", file)
                }
                is LinkFrame.MediaCancel -> cancels += f.req
                is LinkFrame.UploadStart -> { uploads[f.req] = ByteArrayOutputStream(); names[f.req] = f.name }
                is LinkFrame.UploadChunk -> uploads[f.req]?.write(Base64.getDecoder().decode(f.data))
                is LinkFrame.UploadCommit -> {
                    commits += f
                    val err = rejectNext
                    c.send(LinkFrame.UploadResult(f.batch, err == null, err))
                }
                else -> {}
            }
        }

        val link = PhoneLink(Discover(phone))
        link.start()
        phone.pending = assertNotNull(PairingCode.decode((link.state.value as LinkState.Pairing).code))
        waitFor { link.isConnected }
        val store = ChatStore(link)
        val conv = "a".repeat(64) + "_me"

        var bytes: MediaStore.Result? = null
        assertTrue(store.media.fetch(conv, "m1", LinkFrame.MEDIA_FILE, null) { bytes = it })
        waitFor { bytes != null }
        val got = assertIs<MediaStore.Result.Bytes>(bytes)
        assertContentEquals(file, got.data)
        assertEquals("raport.pdf", got.name)

        val target = File(Files.createTempDirectory("sinuschat-save").toFile(), "raport.pdf")
        var saved: MediaStore.Result? = null
        store.media.fetch(conv, "m1", LinkFrame.MEDIA_FILE, target) { saved = it }
        waitFor { saved != null }
        assertIs<MediaStore.Result.Saved>(saved)
        assertContentEquals(file, target.readBytes())
        assertTrue(store.media.progress.value.isEmpty())

        var gone: MediaStore.Result? = null
        store.media.fetch(conv, "gone", LinkFrame.MEDIA_FILE, null) { gone = it }
        waitFor { gone != null }
        assertEquals(LinkFrame.ERROR_GONE, assertIs<MediaStore.Result.Failed>(gone).error)

        var slow: MediaStore.Result? = null
        store.media.fetch(conv, "slow", LinkFrame.MEDIA_FILE, null) { slow = it }
        waitFor { store.media.progress.value.containsKey("slow") }
        Thread.sleep(200)
        store.media.cancelFor("slow")
        assertEquals(MediaStore.ERROR_CANCELLED, assertIs<MediaStore.Result.Failed>(slow).error)
        waitFor { cancels.isNotEmpty() }

        store.media.requestThumb(conv, "p1")
        waitFor { store.media.thumbs.value.containsKey("p1") }

        val dir = Files.createTempDirectory("sinuschat-up").toFile()
        val photo = File(dir, "wakacje.jpg").apply { writeBytes(Random(3).nextBytes(LinkFrame.CHUNK_BYTES * 2 + 77)) }
        val doc = File(dir, "umowa.pdf").apply { writeBytes(Random(4).nextBytes(999)) }
        val huge = File(dir, "film.mp4")
        RandomAccessFile(huge, "rw").use { it.setLength(LinkFrame.MAX_UPLOAD_BYTES + 1) }
        val outcomes = CopyOnWriteArrayList<UploadQueue.Outcome>()
        val prev = store.uploads.onOutcome
        store.uploads.onOutcome = { prev?.invoke(it); outcomes += it }

        val rejected = store.uploads.stage(conv, listOf(photo, doc, huge))
        assertEquals(listOf(huge), rejected)
        assertEquals(2, store.uploads.staged.value[conv]?.size)
        store.open(conv)
        assertTrue(store.send("  Zdjęcia z wyjazdu  ", "m1"))
        assertTrue(store.uploads.staged.value[conv].isNullOrEmpty())
        waitFor { outcomes.size == 1 }
        assertIs<UploadQueue.Outcome.Done>(outcomes[0])
        val commit = commits.single()
        assertEquals(conv, commit.conv)
        assertEquals("Zdjęcia z wyjazdu", commit.caption)
        assertEquals("m1", commit.replyTo)
        assertEquals(listOf("wakacje.jpg", "umowa.pdf"), commit.reqs.map { names[it] })
        assertContentEquals(photo.readBytes(), uploads[commit.reqs[0]]!!.toByteArray())
        assertContentEquals(doc.readBytes(), uploads[commit.reqs[1]]!!.toByteArray())
        assertTrue(store.uploads.active.value.isEmpty())

        rejectNext = LinkFrame.ERROR_READ_ONLY
        store.uploads.stage(conv, listOf(doc))
        assertTrue(store.send("", null))
        waitFor { outcomes.size == 2 }
        assertEquals(LinkFrame.ERROR_READ_ONLY, assertIs<UploadQueue.Outcome.Failed>(outcomes[1]).error)

        val pasted = java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB)
        assertTrue(store.uploads.stageImage(conv, pasted))
        val staged = store.uploads.staged.value[conv]!!.single()
        assertTrue(staged.temp && staged.isImage && staged.file.isFile)
        store.uploads.unstage(conv, staged.id)
        assertTrue(!staged.file.exists())

        huge.delete()
        store.shutdown()
        link.stop()
        phone.server.close()
        SecureStore.delete("device.bin")
    }

    @Test
    fun thumbsAskedBeforeLinkLoadOnceConnected() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-retry").toString())
        SecureStore.delete("device.bin")
        val phone = PhoneLinkFlowTest.FakePhone()
        val png = javaClass.getResourceAsStream("/sinuschat.png")!!.use { it.readBytes() }
        val asked = CopyOnWriteArrayList<String>()
        phone.handler = { c, f ->
            if (f is LinkFrame.MediaRequest) {
                asked += "${f.msg}:${f.kind}"
                when {
                    f.kind == LinkFrame.MEDIA_THUMB && f.msg == "nothumb" -> c.send(LinkFrame.MediaError(f.req, LinkFrame.ERROR_FAILED))
                    f.kind == MediaStore.KIND_AVATAR -> c.send(LinkFrame.MediaError(f.req, LinkFrame.ERROR_FAILED))
                    else -> stream(c, f.req, "img.png", png)
                }
            }
        }

        val link = PhoneLink(Discover(phone))
        val store = ChatStore(link)
        val conv = "b".repeat(64) + "_me"
        store.media.requestThumb(conv, "early")
        store.media.requestThumb(conv, "nothumb", fallbackToFull = true)
        Thread.sleep(300)
        assertTrue(store.media.thumbs.value.isEmpty())

        link.start()
        phone.pending = assertNotNull(PairingCode.decode((link.state.value as LinkState.Pairing).code))
        waitFor { store.media.thumbs.value.keys.containsAll(listOf("early", "nothumb")) }
        assertTrue("nothumb:${LinkFrame.MEDIA_FILE}" in asked)

        repeat(4) { store.media.requestAvatar(conv, "peer$it") }
        waitFor { asked.count { it.endsWith(":" + MediaStore.KIND_AVATAR) } >= 3 }
        Thread.sleep(500)
        assertEquals(3, asked.count { it.endsWith(":" + MediaStore.KIND_AVATAR) })
        assertTrue(store.media.avatars.value.isEmpty())

        store.shutdown()
        link.stop()
        phone.server.close()
        SecureStore.delete("device.bin")
    }

    @Test
    fun pcUploadShowsLocalBubblesUntilPhoneSends() {
        System.setProperty("sinuschat.dir", Files.createTempDirectory("sinuschat-ghost").toString())
        SecureStore.delete("device.bin")
        val phone = PhoneLinkFlowTest.FakePhone()
        val starts = CopyOnWriteArrayList<LinkFrame.UploadStart>()
        val commits = CopyOnWriteArrayList<LinkFrame.UploadCommit>()
        val texts = CopyOnWriteArrayList<LinkFrame.SendText>()
        val release = java.util.concurrent.CountDownLatch(1)
        phone.handler = { c, f ->
            when (f) {
                is LinkFrame.UploadStart -> starts += f
                is LinkFrame.UploadCommit -> {
                    commits += f
                    val ids = f.reqs.indices.map { "real${commits.size}_$it" }
                    kotlin.concurrent.thread(isDaemon = true) {
                        if (commits.size == 1) release.await()
                        c.send(LinkFrame.UploadResult(f.batch, true, ids = ids))
                    }
                }
                is LinkFrame.SendText -> texts += f
                else -> {}
            }
        }
        val link = PhoneLink(Discover(phone))
        link.start()
        phone.pending = assertNotNull(PairingCode.decode((link.state.value as LinkState.Pairing).code))
        waitFor { link.isConnected }
        val store = ChatStore(link)
        val conv = "c".repeat(64) + "_me"
        val dir = Files.createTempDirectory("sinuschat-ghost-up").toFile()
        val image = javaClass.getResourceAsStream("/sinuschat.png")!!.use { it.readBytes() }
        val png = File(dir, "plaza.png").apply { writeBytes(image) }
        val doc = File(dir, "lista.pdf").apply { writeBytes(Random(5).nextBytes(2_000)) }

        store.open(conv)
        store.uploads.stage(conv, listOf(png, doc))
        val photoId = store.uploads.staged.value[conv]!!.first().id
        assertTrue(store.uploads.toggleOriginal(conv, photoId))
        assertTrue(store.send("Plaża", null))
        waitFor { store.messages.value.count { store.isLocalOnly(it.id) } == 2 }
        val local = store.messages.value.filter { store.isLocalOnly(it.id) }
        assertEquals(listOf(ChatMessage.KIND_PHOTO, ChatMessage.KIND_FILE), local.map { it.kind })
        assertEquals("Plaża", local[0].text)
        assertTrue(local.all { it.mine && it.status == ChatMessage.STATUS_UPLOADING })
        waitFor { store.media.thumbs.value.containsKey(local[0].id) }
        waitFor { commits.size == 1 }
        assertEquals(listOf(true, false), starts.map { it.original })
        val early = store.sendProgress(local[0], store.sending.value, store.uploads.itemProgress.value)
        assertTrue(early != null && early in 0f..0.3f)

        release.countDown()
        waitFor { store.media.vault.has(pl.sinusoide.sinuschat.desktop.chat.MediaVault.fileKey("real1_0")) }
        assertTrue(store.media.thumbs.value.containsKey("real1_0"))
        val real = local.mapIndexed { i, m -> m.copy(id = "real1_$i") }
        phone.connection!!.send(LinkFrame.Messages(conv, real))
        waitFor { store.messages.value.none { store.isLocalOnly(it.id) } && store.messages.value.any { it.id == "real1_0" } }
        assertEquals(0.3f, store.sendProgress(real[0], store.sending.value, store.uploads.itemProgress.value))
        phone.connection!!.send(LinkFrame.SendProgress(conv, mapOf("real1_0" to 0.5f)))
        waitFor { store.sending.value["real1_0"] != null }
        assertEquals(0.65f, store.sending.value["real1_0"]!!, 0.001f)

        store.uploads.stage(conv, listOf(png))
        val once = store.uploads.staged.value[conv]!!.single().id
        assertTrue(store.uploads.toggleViewOnce(conv, once))
        assertTrue(store.send("Tylko raz", null))
        waitFor { commits.size == 2 && texts.isNotEmpty() }
        assertEquals("", commits[1].caption)
        assertTrue(starts.last().viewOnce)
        assertEquals("Tylko raz", texts.single().text)

        store.shutdown()
        link.stop()
        phone.server.close()
        SecureStore.delete("device.bin")
    }

    @Test
    fun albumsGroupConsecutivePhotos() {
        fun photo(id: String, album: String?, sender: String = "a", viewOnce: Boolean = false) =
            ChatMessage(id, "c", sender, kind = ChatMessage.KIND_PHOTO, at = id.toLong(), albumId = album, viewOnce = viewOnce)
        val list = listOf(
            photo("1", "x"), photo("2", "x"), photo("3", "x"),
            ChatMessage("t", "c", "a", text = "hej", at = 3L),
            photo("4", "y"), photo("5", "y", sender = "b"),
            photo("6", "z", viewOnce = true), photo("7", "z", viewOnce = true),
            photo("8", null), photo("9", null)
        )
        val groups = groupAlbums(list).map { g -> g.map { it.id } }
        assertEquals(
            listOf(listOf("1", "2", "3"), listOf("t"), listOf("4"), listOf("5"), listOf("6"), listOf("7"), listOf("8"), listOf("9")),
            groups
        )
    }
}
