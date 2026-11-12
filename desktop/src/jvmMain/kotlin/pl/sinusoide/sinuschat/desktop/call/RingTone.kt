package pl.sinusoide.sinuschat.desktop.call

import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

class RingTone {

    enum class Kind { INCOMING, RINGBACK }

    private class Pcm(val format: AudioFormat, val data: ByteArray)

    companion object {
        private const val DEFAULT_VOLUME = 50
        private val cache = ConcurrentHashMap<String, Pcm>()

        fun soundFor(kind: Kind, ring: String): String = when (kind) {
            Kind.RINGBACK -> "sc_ringback_puls"
            Kind.INCOMING -> when (ring) {
                "bit_melodia" -> "sc_ring_bit_melodia"
                "puls" -> "sc_ring_puls"
                else -> "sc_ring_bit"
            }
        }

        fun gain(percent: Int): Float {
            val f = (if (percent < 0) DEFAULT_VOLUME else percent).coerceIn(0, 100) / 100f
            return f * f
        }

        private fun load(name: String): Pcm? {
            cache[name]?.let { return it }
            val pcm = runCatching {
                val raw = RingTone::class.java.getResourceAsStream("/sounds/$name.ogg") ?: return null
                AudioSystem.getAudioInputStream(BufferedInputStream(raw)).use { src ->
                    val base = src.format
                    val target = AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels,
                        base.channels * 2, base.sampleRate, false
                    )
                    AudioSystem.getAudioInputStream(target, src).use { Pcm(target, it.readAllBytes()) }
                }
            }.getOrNull() ?: return null
            cache[name] = pcm
            return pcm
        }

        fun decodedLength(name: String): Int = load(name)?.data?.size ?: 0
    }

    @Volatile private var thread: Thread? = null

    fun start(kind: Kind, ring: String = "", volume: Int = -1) {
        stop()
        val t = Thread({ play(soundFor(kind, ring), gain(volume)) }, "call-tone").apply { isDaemon = true }
        thread = t
        t.start()
    }

    fun stop() {
        val t = thread ?: return
        thread = null
        t.interrupt()
    }

    private fun play(name: String, gain: Float) {
        if (gain <= 0f) return
        val pcm = load(name) ?: return
        val data = scaled(pcm.data, gain)
        val line: SourceDataLine = try {
            AudioSystem.getSourceDataLine(pcm.format).apply { open(pcm.format) }
        } catch (_: Exception) {
            return
        }
        try {
            line.start()
            val chunk = (pcm.format.frameSize * (pcm.format.sampleRate / 20).toInt()).coerceAtLeast(pcm.format.frameSize)
            while (thread === Thread.currentThread()) {
                var off = 0
                while (off < data.size) {
                    if (thread !== Thread.currentThread()) return
                    val n = minOf(chunk, data.size - off)
                    line.write(data, off, n)
                    off += n
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { line.stop() }
            runCatching { line.flush() }
            runCatching { line.close() }
        }
    }

    private fun scaled(src: ByteArray, gain: Float): ByteArray {
        if (gain >= 0.999f) return src
        val out = ByteArray(src.size)
        var i = 0
        while (i + 1 < src.size) {
            val s = (src[i].toInt() and 0xFF) or (src[i + 1].toInt() shl 8)
            val v = (s * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (v and 0xFF).toByte()
            out[i + 1] = (v shr 8 and 0xFF).toByte()
            i += 2
        }
        return out
    }
}
