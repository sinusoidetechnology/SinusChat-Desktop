package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

interface AudioSink {
    val bufferBytes: Int
    fun available(): Int
    fun write(data: ByteArray, off: Int, len: Int)
    fun start()
    fun stop()
    fun flush()
    fun close()
}

class JavaSoundSink(rate: Int, channels: Int) : AudioSink {
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(AudioFormat(rate.toFloat(), 16, channels, true, false)).apply {
        open(format, rate * channels * 2 / 2)
    }
    override val bufferBytes: Int get() = line.bufferSize
    override fun available() = line.available()
    override fun write(data: ByteArray, off: Int, len: Int) {
        line.write(data, off, len)
    }
    override fun start() = line.start()
    override fun stop() = line.stop()
    override fun flush() = line.flush()
    override fun close() = line.close()
}

private fun systemSink(rate: Int, channels: Int): AudioSink? = runCatching { JavaSoundSink(rate, channels) }.getOrNull()

class VideoPlayback(
    private val source: Source,
    private val maxSide: Int = 1920,
    private val sinkFactory: (rate: Int, channels: Int) -> AudioSink? = ::systemSink
) {

    constructor(
        file: File,
        maxSide: Int = 1920,
        sinkFactory: (rate: Int, channels: Int) -> AudioSink? = ::systemSink
    ) : this(Source.Path(file), maxSide, sinkFactory)

    sealed interface Source {
        class Path(val file: File) : Source
        class Memory(val data: ByteArray) : Source
    }

    enum class Failure { LIBRARY, NOT_VIDEO, FORMAT, OTHER }

    var onClosed: (() -> Unit)? = null

    class Picture(val width: Int, val height: Int, val pixels: ByteArray, val timeUs: Long, internal val gen: Long)

    data class State(
        val ready: Boolean = false,
        val playing: Boolean = false,
        val ended: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
        val rotation: Int = 0,
        val error: String? = null,
        val failure: Failure? = null
    )

    private class Chunk(val data: ByteArray, val timeUs: Long, val endUs: Long, val gen: Long)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val _picture = MutableStateFlow<Picture?>(null)
    val picture: StateFlow<Picture?> = _picture

    private val frames = ArrayBlockingQueue<Picture>(6)
    private val chunks = ArrayBlockingQueue<Chunk>(48)
    private val seekTo = AtomicLong(-1L)
    @Volatile private var gen = 0L
    @Volatile private var closed = false
    @Volatile private var paused = true
    @Volatile private var eof = false
    @Volatile private var lastVideoUs = 0L
    @Volatile private var showNext = true
    @Volatile private var anchorUs = 0L
    @Volatile private var anchorNanos = System.nanoTime()
    @Volatile private var bytesPerSecond = 0
    @Volatile private var durationUs = 0L
    @Volatile private var threads: List<Thread> = emptyList()
    private val clockLock = Any()

    fun start(play: Boolean = true) {
        if (threads.isNotEmpty()) return
        paused = !play
        val decoder = Thread(::decodeLoop, "video-decode").apply { isDaemon = true }
        threads = listOf(decoder)
        decoder.start()
    }

    fun play() {
        if (closed) return
        val s = _state.value
        if (s.ended) {
            seek(0L)
        }
        synchronized(clockLock) {
            if (!paused) return
            anchorNanos = System.nanoTime()
            paused = false
        }
        sink?.start()
        _state.update { it.copy(playing = true, ended = false) }
    }

    fun pause() {
        val at: Long
        synchronized(clockLock) {
            if (paused) return
            anchorUs = clockUs()
            paused = true
            at = anchorUs
        }
        sink?.stop()
        _state.update { it.copy(playing = false, positionMs = (at.coerceIn(0L, max(durationUs, 0L)) / 1000L)) }
    }

    fun toggle() = if (paused || _state.value.ended) play() else pause()

    fun positionNow(): Long {
        if (_state.value.ended) return durationUs / 1000L
        val us = synchronized(clockLock) { clockUs() }
        return us.coerceIn(0L, max(durationUs, 0L)) / 1000L
    }

    fun seek(ms: Long) {
        val target = (ms * 1000L).coerceIn(0L, max(0L, durationUs))
        seekTo.set(target)
        synchronized(clockLock) {
            gen++
            anchorUs = target
            anchorNanos = System.nanoTime()
        }
        frames.clear()
        chunks.clear()
        eof = false
        showNext = true
        _state.update { it.copy(positionMs = target / 1000L, ended = false) }
    }

    fun close() {
        closed = true
        threads.forEach { it.interrupt() }
    }

    private fun clockUs(): Long {
        val base = anchorUs
        if (paused) return base
        return base + (System.nanoTime() - anchorNanos) / 1000L
    }

    @Volatile private var sink: AudioSink? = null

    private fun decodeLoop() {
        var grabber: FFmpegFrameGrabber? = null
        var opened = false
        try {
            avutil.av_log_set_level(avutil.AV_LOG_QUIET)
            val g = when (val src = source) {
                is Source.Path -> FFmpegFrameGrabber(src.file)
                is Source.Memory -> FFmpegFrameGrabber(ByteArrayInputStream(src.data), src.data.size)
            }
            grabber = g
            g.pixelFormat = avutil.AV_PIX_FMT_BGRA
            g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
            g.audioChannels = 2
            g.start()
            opened = true
            val srcW = g.imageWidth
            val srcH = g.imageHeight
            val side = max(srcW, srcH)
            if (side > maxSide && srcW > 0 && srcH > 0) {
                val k = maxSide.toDouble() / side
                g.imageWidth = (srcW * k).roundToInt().coerceAtLeast(2) and 1.inv()
                g.imageHeight = (srcH * k).roundToInt().coerceAtLeast(2) and 1.inv()
            }
            durationUs = max(0L, g.lengthInTime)
            val rotation = ((-g.displayRotation).roundToInt() % 360 + 360) % 360
            if (g.hasAudio() && g.sampleRate > 0) {
                val channels = g.audioChannels.coerceIn(1, 2)
                sink = sinkFactory(g.sampleRate, channels)
                bytesPerSecond = g.sampleRate * channels * 2
            }
            _state.update {
                it.copy(
                    ready = true, playing = !paused, durationMs = durationUs / 1000L,
                    width = if (srcW > 0) srcW else 0, height = if (srcH > 0) srcH else 0, rotation = rotation
                )
            }
            if (!paused) sink?.start()
            val audio = sink?.let { s -> Thread({ audioLoop(s) }, "video-audio").apply { isDaemon = true } }
            val present = Thread(::presentLoop, "video-present").apply { isDaemon = true }
            threads = listOfNotNull(Thread.currentThread(), audio, present)
            audio?.start()
            present.start()
            if (closed) return
            while (!closed) {
                val myGen = gen
                val target = seekTo.getAndSet(-1L)
                if (target >= 0L) {
                    runCatching { g.setTimestamp(target) }
                    frames.clear()
                    chunks.clear()
                    eof = false
                }
                if (eof) {
                    Thread.sleep(15)
                    continue
                }
                val frame = g.grab()
                if (frame == null) {
                    eof = true
                    continue
                }
                val image = frame.image
                val samples = frame.samples
                if (image != null && image.isNotEmpty() && frame.imageWidth > 0) {
                    val pic = picture(frame, myGen)
                    lastVideoUs = max(lastVideoUs, pic.timeUs)
                    offer(frames, pic) { myGen != gen }
                } else if (samples != null && samples.isNotEmpty() && sink != null) {
                    val data = pcm(samples[0] as ShortBuffer)
                    val bps = bytesPerSecond.coerceAtLeast(1)
                    val end = frame.timestamp + data.size * 1_000_000L / bps
                    offer(chunks, Chunk(data, frame.timestamp, end, myGen)) { myGen != gen }
                }
            }
        } catch (_: InterruptedException) {
        } catch (t: Throwable) {
            if (!closed) {
                val failure = when {
                    generateSequence(t) { it.cause }.any { it is LinkageError } -> Failure.LIBRARY
                    !opened && !looksLikeMedia(head()) -> Failure.NOT_VIDEO
                    !opened -> Failure.FORMAT
                    else -> Failure.OTHER
                }
                _state.update { it.copy(error = describe(t), failure = failure, playing = false) }
            }
        } finally {
            closed = true
            threads.filter { it !== Thread.currentThread() }.forEach { it.interrupt() }
            runCatching { grabber?.stop() }
            runCatching { grabber?.release() }
            runCatching { sink?.close() }
            runCatching { onClosed?.invoke() }
        }
    }

    private fun head(): ByteArray = when (val src = source) {
        is Source.Path -> runCatching { src.file.inputStream().use { it.readNBytes(64) } }.getOrDefault(ByteArray(0))
        is Source.Memory -> src.data.copyOf(minOf(64, src.data.size))
    }

    private fun describe(t: Throwable): String {
        val size = when (val src = source) {
            is Source.Path -> src.file.length()
            is Source.Memory -> src.data.size.toLong()
        }
        val name = (source as? Source.Path)?.file?.name ?: "memory"
        val chain = generateSequence(t) { it.cause }.take(4).joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }
        val bytes = head().take(16).joinToString(" ") { "%02x".format(it) }
        return "$chain | $name, $size B, [$bytes] | ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java ${System.getProperty("java.version")}"
    }

    private inline fun <T> offer(queue: ArrayBlockingQueue<T>, item: T, stale: () -> Boolean) {
        while (!closed && !stale()) {
            if (queue.offer(item, 20, TimeUnit.MILLISECONDS)) return
            if (seekTo.get() >= 0L) return
        }
    }

    private fun picture(frame: Frame, gen: Long): Picture {
        val w = frame.imageWidth
        val h = frame.imageHeight
        val src = (frame.image[0] as ByteBuffer).duplicate()
        val stride = frame.imageStride.takeIf { it >= w * 4 } ?: (w * 4)
        val row = w * 4
        val out = ByteArray(row * h)
        val base = src.position()
        for (y in 0 until h) {
            src.position(base + y * stride)
            src.get(out, y * row, row)
        }
        return Picture(w, h, out, frame.timestamp, gen)
    }

    private fun pcm(samples: ShortBuffer): ByteArray {
        val src = samples.duplicate()
        val out = ByteArray(src.remaining() * 2)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(src)
        return out
    }

    private fun audioLoop(s: AudioSink) {
        var lineGen = gen
        var writtenUntil = -1L
        try {
            while (!closed) {
                val c = chunks.poll(20, TimeUnit.MILLISECONDS) ?: continue
                if (c.gen != gen) continue
                if (c.gen != lineGen) {
                    s.stop()
                    s.flush()
                    if (!paused) s.start()
                    lineGen = c.gen
                    writtenUntil = -1L
                }
                var off = 0
                while (off < c.data.size && !closed && c.gen == gen) {
                    val room = s.available()
                    if (room <= 0) {
                        Thread.sleep(5)
                        continue
                    }
                    val n = minOf(room, c.data.size - off) and 3.inv()
                    if (n <= 0) {
                        Thread.sleep(2)
                        continue
                    }
                    s.write(c.data, off, n)
                    off += n
                    writtenUntil = c.timeUs + off.toLong() * 1_000_000L / bytesPerSecond.coerceAtLeast(1)
                    resync(s, writtenUntil, c.gen)
                }
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun resync(s: AudioSink, writtenUntil: Long, chunkGen: Long) {
        if (paused) return
        val buffered = (s.bufferBytes - s.available()).coerceAtLeast(0).toLong() * 1_000_000L / bytesPerSecond.coerceAtLeast(1)
        val heard = writtenUntil - buffered
        synchronized(clockLock) {
            if (paused || chunkGen != gen) return
            if (abs(heard - clockUs()) > 30_000L) {
                anchorUs = heard
                anchorNanos = System.nanoTime()
            }
        }
    }

    private fun presentLoop() {
        var lastReport = 0L
        try {
            while (!closed) {
                val head = frames.peek()
                if (head != null && head.gen != gen) {
                    frames.poll()
                    continue
                }
                val clock = clockUs()
                if (head != null && (showNext || (!paused && head.timeUs <= clock + 8_000L))) {
                    var pic = frames.poll() ?: continue
                    if (!showNext) {
                        while (true) {
                            val next = frames.peek() ?: break
                            if (next.gen == gen && next.timeUs <= clock) pic = frames.poll() ?: break else break
                        }
                    }
                    if (showNext) {
                        showNext = false
                        synchronized(clockLock) {
                            if (pic.gen == gen) {
                                anchorUs = pic.timeUs
                                anchorNanos = System.nanoTime()
                            }
                        }
                    }
                    _picture.value = pic
                } else if (head == null && eof && !paused && clock >= max(lastVideoUs, durationUs - 50_000L)) {
                    finish()
                } else {
                    val wait = if (head != null && !paused) ((head.timeUs - clock) / 1000L).coerceIn(1L, 8L) else 8L
                    Thread.sleep(wait)
                }
                val now = System.nanoTime()
                if (now - lastReport > 100_000_000L) {
                    lastReport = now
                    val pos = (clockUs().coerceIn(0L, max(durationUs, 0L)) / 1000L)
                    _state.update { if (it.ended) it else it.copy(positionMs = pos) }
                }
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun finish() {
        synchronized(clockLock) {
            anchorUs = durationUs
            paused = true
        }
        sink?.stop()
        _state.update { it.copy(playing = false, ended = true, positionMs = durationUs / 1000L) }
    }
}

internal fun looksLikeMedia(head: ByteArray): Boolean {
    fun at(offset: Int, text: String) =
        head.size >= offset + text.length && text.indices.all { head[offset + it] == text[it].code.toByte() }
    fun bytes(offset: Int, vararg b: Int) = head.size >= offset + b.size && b.indices.all { head[offset + it] == b[it].toByte() }
    if (head.size < 12) return false
    return at(4, "ftyp") || at(4, "moov") || at(4, "mdat") || at(4, "free") || at(4, "wide") || at(4, "skip") ||
        at(0, "RIFF") || at(0, "OggS") || at(0, "FLV") || at(0, "ID3") || at(0, "#!AMR") || at(0, "fLaC") ||
        bytes(0, 0x1A, 0x45, 0xDF, 0xA3) || bytes(0, 0x00, 0x00, 0x01, 0xBA) || bytes(0, 0x00, 0x00, 0x01, 0xB3) ||
        bytes(0, 0xFF, 0xD8, 0xFF) || (head[0] == 0x47.toByte() && head.size > 0) || bytes(0, 0x30, 0x26, 0xB2, 0x75)
}
