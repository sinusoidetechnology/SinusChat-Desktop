package pl.sinusoide.sinuschat.desktop.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

class VoicePlayback {

    data class State(val msg: String, val positionMs: Long, val durationMs: Long, val playing: Boolean)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    private val ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "voice-tick").apply { isDaemon = true } }
    private var clip: Clip? = null
    private var tick: ScheduledFuture<*>? = null
    private val cache = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 8
    }

    @Synchronized
    fun cached(msg: String): ByteArray? = cache[msg]

    @Synchronized
    fun play(msg: String, wav: ByteArray): Boolean {
        cache[msg] = wav
        stopInternal()
        return try {
            val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))
            val c = AudioSystem.getClip()
            c.open(stream)
            c.addLineListener { e ->
                if (e.type == LineEvent.Type.STOP && clip === c && c.framePosition >= c.frameLength - 1) finished(msg)
            }
            clip = c
            c.start()
            val duration = c.microsecondLength / 1000
            _state.value = State(msg, 0, duration, true)
            tick = ticker.scheduleAtFixedRate({ update(msg) }, 50, 100, TimeUnit.MILLISECONDS)
            true
        } catch (_: Exception) {
            stopInternal()
            false
        }
    }

    @Synchronized
    fun toggle(msg: String): Boolean {
        val c = clip ?: return false
        if (_state.value?.msg != msg) return false
        if (c.isRunning) {
            c.stop()
            _state.value = _state.value?.copy(playing = false)
        } else {
            if (c.framePosition >= c.frameLength - 1) c.framePosition = 0
            c.start()
            _state.value = _state.value?.copy(playing = true)
        }
        return true
    }

    @Synchronized
    fun seek(msg: String, fraction: Float) {
        val c = clip ?: return
        if (_state.value?.msg != msg) return
        c.framePosition = (c.frameLength * fraction.coerceIn(0f, 1f)).toInt()
        update(msg)
    }

    @Synchronized
    fun stop() = stopInternal()

    @Synchronized
    fun positionNow(msg: String): Long? {
        val c = clip ?: return null
        if (_state.value?.msg != msg) return null
        return c.microsecondPosition / 1000
    }

    private fun update(msg: String) {
        val c = clip ?: return
        val s = _state.value ?: return
        if (s.msg != msg) return
        _state.value = s.copy(positionMs = c.microsecondPosition / 1000, playing = c.isRunning)
    }

    @Synchronized
    private fun finished(msg: String) {
        if (_state.value?.msg != msg) return
        stopInternal()
    }

    private fun stopInternal() {
        tick?.cancel(false)
        tick = null
        clip?.let { runCatching { it.stop() }; runCatching { it.close() } }
        clip = null
        _state.value = null
    }
}
