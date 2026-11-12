package pl.sinusoide.sinuschat.desktop

import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import pl.sinusoide.sinuschat.desktop.chat.AudioSink
import pl.sinusoide.sinuschat.desktop.chat.VideoPlayback
import pl.sinusoide.sinuschat.desktop.security.FileSafety
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoPlaybackTest {

    private class RealTimeSink(private val rate: Int, private val channels: Int) : AudioSink {
        private val lock = Any()
        private val capacity = rate * channels
        private var filled = 0
        @Volatile private var running = false
        @Volatile var written = 0L

        init {
            Thread {
                var last = System.nanoTime()
                while (true) {
                    Thread.sleep(5)
                    val now = System.nanoTime()
                    val drained = ((now - last) / 1e9 * rate * channels * 2).toInt() and 3.inv()
                    last = now
                    if (running) synchronized(lock) { filled = maxOf(0, filled - drained) }
                }
            }.apply { isDaemon = true }.start()
        }

        override val bufferBytes: Int get() = capacity
        override fun available(): Int = synchronized(lock) { capacity - filled }
        override fun write(data: ByteArray, off: Int, len: Int) {
            synchronized(lock) { filled += len }
            written += len
        }
        override fun start() { running = true }
        override fun stop() { running = false }
        override fun flush() { synchronized(lock) { filled = 0 } }
        override fun close() {}
    }

    private val dir = Files.createTempDirectory("sinus-video").toFile()
    private val players = mutableListOf<VideoPlayback>()

    @AfterTest
    fun cleanup() {
        players.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun sample(width: Int = 642, height: Int = 362, seconds: Int = 6): File {
        val file = File(dir, "sample.mp4")
        val r = FFmpegFrameRecorder(file, width, height, 2)
        r.format = "mp4"
        r.videoCodec = avcodec.AV_CODEC_ID_MPEG4
        r.audioCodec = avcodec.AV_CODEC_ID_AAC
        r.frameRate = 25.0
        r.sampleRate = 44_100
        r.pixelFormat = avutil.AV_PIX_FMT_YUV420P
        r.videoBitrate = 800_000
        r.start()
        val frame = Frame(width, height, Frame.DEPTH_UBYTE, 3)
        val perFrame = 44_100 / 25
        var t = 0
        for (i in 0 until seconds * 25) {
            val buf = frame.image[0] as ByteBuffer
            for (y in 0 until height) for (x in 0 until width) {
                val o = y * frame.imageStride + x * 3
                buf.put(o, (x + i * 4).toByte())
                buf.put(o + 1, y.toByte())
                buf.put(o + 2, (i * 8).toByte())
            }
            r.record(frame)
            val pcm = ShortBuffer.allocate(perFrame * 2)
            repeat(perFrame) {
                val v = (sin(2 * PI * 440 * t++ / 44_100.0) * 8000).toInt().toShort()
                pcm.put(v)
                pcm.put(v)
            }
            pcm.flip()
            r.recordSamples(44_100, 2, pcm)
        }
        r.stop()
        r.release()
        return file
    }

    private fun waitFor(timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !check()) Thread.sleep(10)
    }

    @Test
    fun playsSeeksPausesAndEnds() {
        var sink: RealTimeSink? = null
        val p = VideoPlayback(sample()) { rate, ch -> RealTimeSink(rate, ch).also { sink = it } }.also { players += it }
        p.start(play = true)
        waitFor { p.state.value.ready && p.picture.value != null }
        assertTrue(p.state.value.ready, "not ready: ${p.state.value}")
        assertEquals(6000L, p.state.value.durationMs, "duration")
        val pic = assertNotNull(p.picture.value)
        assertEquals(642, pic.width)
        assertEquals(362, pic.height)
        assertEquals(642 * 362 * 4, pic.pixels.size)

        Thread.sleep(1500)
        assertTrue(p.state.value.positionMs in 1000..2000, "position after 1.5 s: ${p.state.value.positionMs}")
        assertTrue((sink?.written ?: 0L) > 44_100 * 4, "audio not written")

        p.seek(4000)
        Thread.sleep(500)
        assertTrue(p.state.value.positionMs in 4100..4900, "position after seek: ${p.state.value.positionMs}")
        assertTrue((p.picture.value?.timeUs ?: 0L) in 4_000_000..4_900_000, "frame after seek: ${p.picture.value?.timeUs}")

        p.pause()
        val paused = p.state.value.positionMs
        Thread.sleep(500)
        assertFalse(p.state.value.playing)
        assertTrue(kotlin.math.abs(p.state.value.positionMs - paused) < 80, "moved while paused")

        p.seek(1000)
        waitFor(3000) { (p.picture.value?.timeUs ?: 0L) in 900_000..1_200_000 }
        assertTrue((p.picture.value?.timeUs ?: 0L) in 900_000..1_200_000, "paused seek frame: ${p.picture.value?.timeUs}")

        p.play()
        waitFor(9000) { p.state.value.ended }
        assertTrue(p.state.value.ended, "did not end: ${p.state.value}")
        assertFalse(p.state.value.playing)

        p.play()
        Thread.sleep(600)
        assertTrue(p.state.value.playing && p.state.value.positionMs in 200..1200, "replay: ${p.state.value}")
    }

    @Test
    fun positionMovesSmoothlyFrameByFrame() {
        var sink: RealTimeSink? = null
        val p = VideoPlayback(sample()) { rate, ch -> RealTimeSink(rate, ch).also { sink = it } }.also { players += it }
        p.start(play = true)
        waitFor { p.state.value.ready && p.picture.value != null }
        Thread.sleep(400)
        val reads = mutableListOf<Long>()
        repeat(90) {
            reads += p.positionNow()
            Thread.sleep(16)
        }
        val steps = reads.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it >= 0 }, "went backwards: $steps")
        assertTrue(steps.count { it == 0L } < 10, "stood still too often: $steps")
        assertTrue(steps.all { it <= 60 }, "jumped: ${steps.filter { it > 60 }}")
        assertTrue(reads.last() - reads.first() in 1100..1900, "covered ${reads.last() - reads.first()} ms")
        p.pause()
        val paused = p.positionNow()
        Thread.sleep(200)
        assertEquals(paused, p.positionNow())
    }

    @Test
    fun scalesDownAndWorksWithoutSound() {
        val p = VideoPlayback(sample(), maxSide = 320) { _, _ -> null }.also { players += it }
        p.start(play = false)
        waitFor { p.picture.value != null }
        val pic = assertNotNull(p.picture.value)
        assertEquals(320, pic.width)
        assertTrue(pic.height in 178..182, "height ${pic.height}")
        assertTrue(p.state.value.positionMs < 60 && !p.state.value.playing)
        p.play()
        Thread.sleep(1000)
        assertTrue(p.state.value.positionMs in 700..1400, "clock without audio: ${p.state.value.positionMs}")
    }

    @Test
    fun reportsBrokenFile() {
        val bad = File(dir, "broken.mp4").apply { writeBytes(ByteArray(5000) { (it * 7 + 3).toByte() }) }
        val p = VideoPlayback(bad) { _, _ -> null }.also { players += it }
        p.start()
        waitFor(5000) { p.state.value.error != null }
        assertNotNull(p.state.value.error)
        assertFalse(p.state.value.ready)
        assertEquals(VideoPlayback.Failure.NOT_VIDEO, p.state.value.failure)
        assertTrue("5000 B" in p.state.value.error.orEmpty(), p.state.value.error)
    }

    @Test
    fun brokenMp4IsAFormatProblem() {
        val bytes = ByteArray(5000) { (it * 7 + 3).toByte() }
        "\u0000\u0000\u0000\u0018ftypmp42".toByteArray(Charsets.ISO_8859_1).copyInto(bytes)
        val bad = File(dir, "cut.mp4").apply { writeBytes(bytes) }
        val p = VideoPlayback(bad) { _, _ -> null }.also { players += it }
        p.start()
        waitFor(5000) { p.state.value.error != null }
        assertEquals(VideoPlayback.Failure.FORMAT, p.state.value.failure, p.state.value.error)
    }

    @Test
    fun playsFromMemoryWithSeeking() {
        val data = sample().readBytes()
        val p = VideoPlayback(VideoPlayback.Source.Memory(data), sinkFactory = { _, _ -> null }).also { players += it }
        p.start(play = true)
        waitFor { p.state.value.ready && p.picture.value != null }
        assertEquals(6000L, p.state.value.durationMs)
        Thread.sleep(800)
        assertTrue(p.state.value.positionMs in 500..1300, "memory position: ${p.state.value.positionMs}")
        p.seek(4500)
        Thread.sleep(400)
        assertTrue((p.picture.value?.timeUs ?: 0L) in 4_400_000..5_100_000, "memory seek frame: ${p.picture.value?.timeUs}")
        assertNull(p.state.value.error)
    }

    @Test
    fun runsCloseCallbackAfterRelease() {
        var closedCalls = 0
        val p = VideoPlayback(sample()) { _, _ -> null }
        p.onClosed = { closedCalls++ }
        p.start(play = false)
        waitFor { p.picture.value != null }
        p.close()
        waitFor(3000) { closedCalls > 0 }
        assertEquals(1, closedCalls)
    }

    @Test
    fun flagsDangerousFiles() {
        for (name in listOf("faktura.pdf.exe", "skrypt.PS1", "setup.exe. ", "makro.docm", "skrot.lnk", "x.vbs", "obraz.iso")) {
            assertTrue(FileSafety.isRisky(name), name)
        }
        for (name in listOf("zdjecie.jpg", "film.mp4", "notatka.txt", "README", "archiwum.zip", "dokument.pdf")) {
            assertFalse(FileSafety.isRisky(name), name)
        }
    }

    @Test
    fun sandboxConfigIsIsolated() {
        val host = File(dir, "a&b <c> 'd'")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(FileSafety.sandboxConfig(host).byteInputStream())
        fun tag(name: String) = doc.getElementsByTagName(name).item(0)?.textContent
        assertEquals(host.absolutePath, tag("HostFolder"))
        assertEquals("true", tag("ReadOnly"))
        assertEquals("Disable", tag("Networking"))
        assertEquals("Disable", tag("ClipboardRedirection"))
    }

    @Test
    fun marksSavedFilesAsDownloaded() {
        val file = File(dir, "pobrany.exe").apply { writeText("x") }
        FileSafety.markFromInternet(file)
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val zone = File(file.absolutePath + ":Zone.Identifier").readText()
            assertTrue("ZoneId=3" in zone, zone)
        }
        assertEquals("x", file.readText())
    }
}
