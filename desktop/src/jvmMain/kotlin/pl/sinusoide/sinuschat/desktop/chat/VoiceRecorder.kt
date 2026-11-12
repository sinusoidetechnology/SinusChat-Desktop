package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.sinusoide.sinuschat.link.LinkFrame
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.abs

class VoiceRecorder {

    class Take(val wav: ByteArray, val durationMs: Long, val silent: Boolean)

    private val rate = LinkFrame.VOICE_RATE
    private val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
    private val maxBytes = LinkFrame.VOICE_MAX_MS / 1000L * rate * 2L

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    @Volatile var startedAt = 0L
        private set
    @Volatile private var line: TargetDataLine? = null
    @Volatile private var buffer: ByteArrayOutputStream? = null
    @Volatile private var peak = 0
    @Volatile private var full = false
    @Volatile var onLimit: (() -> Unit)? = null
    private var reader: Thread? = null

    @Synchronized
    fun start(): Boolean {
        if (line != null) return false
        return try {
            val l = AudioSystem.getTargetDataLine(format)
            l.open(format, rate / 5 * 2)
            l.start()
            val out = ByteArrayOutputStream(1 shl 20)
            buffer = out
            line = l
            peak = 0
            full = false
            startedAt = System.currentTimeMillis()
            _recording.value = true
            reader = thread(isDaemon = true, name = "voice-recorder") { pump(l, out) }
            true
        } catch (e: Exception) {
            line = null
            buffer = null
            _recording.value = false
            false
        }
    }

    private fun pump(l: TargetDataLine, out: ByteArrayOutputStream) {
        val chunk = ByteArray(rate / 20 * 2)
        while (line === l) {
            val n = runCatching { l.read(chunk, 0, chunk.size) }.getOrDefault(-1)
            if (n < 0) break
            if (n == 0) continue
            out.write(chunk, 0, n)
            var max = 0
            var i = 0
            while (i + 1 < n) {
                val v = abs(((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)).toShort().toInt())
                if (v > max) max = v
                i += 2
            }
            if (max > peak) peak = max
            _level.value = (max / 32768f).coerceIn(0f, 1f)
            if (out.size() >= maxBytes && !full) {
                full = true
                onLimit?.invoke()
                break
            }
        }
    }

    @Synchronized
    fun stop(): Take? {
        val l = line ?: return null
        line = null
        runCatching { l.stop() }
        runCatching { l.close() }
        runCatching { reader?.join(800) }
        reader = null
        val pcm = buffer?.toByteArray() ?: ByteArray(0)
        buffer = null
        _recording.value = false
        _level.value = 0f
        val ms = pcm.size * 1000L / (rate * 2L)
        return Take(wav(pcm), ms, peak < SILENCE)
    }

    fun cancel() {
        stop()
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun i32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun i16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        i32(36 + pcm.size)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        i32(16)
        i16(1)
        i16(1)
        i32(rate)
        i32(rate * 2)
        i16(2)
        i16(16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        i32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    private companion object {
        const val SILENCE = 24
    }
}
