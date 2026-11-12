package pl.sinusoide.sinuschat.desktop.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.sinusoide.sinuschat.desktop.system.Platform
import pl.sinusoide.sinuschat.link.LinkProximity
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue

enum class ProximityStatus { OFF, STARTING, UNSUPPORTED, BLUETOOTH_OFF, SEARCHING, NEAR, FAR }

data class ProximityReading(val status: ProximityStatus, val rssi: Int? = null)

class ProximityTracker(private val clock: () -> Long = System::currentTimeMillis) {

    private val samples = ArrayDeque<Pair<Long, Int>>()
    private var lastSeen = NEVER
    private var farSince = 0L
    private var near = false
    var armed = false
        private set

    fun reset() {
        samples.clear()
        lastSeen = NEVER
        farSince = 0L
        near = false
        armed = false
    }

    fun disarm() {
        armed = false
        farSince = 0L
    }

    fun seen(rssi: Int, at: Long = clock()) {
        samples.addLast(at to rssi)
        lastSeen = at
        trim(at)
    }

    private fun trim(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().first > WINDOW_MS) samples.removeFirst()
    }

    fun average(now: Long = clock()): Int? {
        trim(now)
        if (samples.isEmpty()) return null
        val sorted = samples.map { it.second }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun tick(range: Int, now: Long = clock(), calibration: Int = 0): Pair<ProximityReading, Boolean> {
        val threshold = threshold(range, calibration)
        val avg = average(now)
        if (lastSeen == NEVER || now - lastSeen >= GONE_MS || avg == null) {
            near = false
            val lock = armed && lastSeen != NEVER && now - lastSeen >= GONE_MS
            if (lock) disarm()
            return ProximityReading(ProximityStatus.SEARCHING) to lock
        }
        near = if (near) avg >= threshold else avg >= threshold + HYSTERESIS
        if (near) {
            armed = true
            farSince = 0L
            return ProximityReading(ProximityStatus.NEAR, avg) to false
        }
        if (farSince == 0L) farSince = now
        val lock = armed && now - farSince >= FAR_MS
        if (lock) disarm()
        return ProximityReading(ProximityStatus.FAR, avg) to lock
    }

    companion object {
        const val WINDOW_MS = 1_500L
        private const val NEVER = Long.MIN_VALUE
        const val FAR_MS = 1_000L
        const val GONE_MS = 12_000L
        const val HYSTERESIS = 4
        val THRESHOLDS = listOf(-65, -75, -85)
        val RANGE_METERS = listOf(2f, 5f, 10f)
        const val CALIBRATION_METERS = 0.5f
        const val PATH_LOSS = 2.5f
        const val TRIGGER = 0.9f

        fun threshold(range: Int, calibration: Int = 0): Int {
            val i = range.coerceIn(0, THRESHOLDS.lastIndex)
            if (calibration >= 0) return THRESHOLDS[i]
            val drop = 10f * PATH_LOSS * kotlin.math.log10(RANGE_METERS[i] * TRIGGER / CALIBRATION_METERS)
            return (calibration - drop).toInt()
        }

        fun meters(rssi: Int, calibration: Int): Float? {
            if (calibration >= 0) return null
            val d = CALIBRATION_METERS * Math.pow(10.0, (calibration - rssi) / (10.0 * PATH_LOSS)).toFloat()
            return d.coerceIn(0.1f, 50f)
        }
    }
}

interface BleScanner {
    sealed class Event {
        data class Seen(val rssi: Int, val data: ByteArray) : Event()
        data class Failed(val status: ProximityStatus) : Event()
        object Started : Event()
    }

    fun start(onEvent: (Event) -> Unit)
    fun stop()

    companion object {
        fun platform(): BleScanner? = if (Platform.isWindows) WindowsBleScanner() else null

        fun parse(line: String): Event? {
            val parts = line.trim().split(' ')
            return when (parts.firstOrNull()) {
                "S" -> Event.Started
                "A" -> {
                    val rssi = parts.getOrNull(1)?.toIntOrNull() ?: return null
                    val hex = parts.getOrNull(2) ?: return null
                    if (hex.length % 2 != 0 || hex.length > 64) return null
                    val data = runCatching { ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
                        ?: return null
                    Event.Seen(rssi, data)
                }
                "E" -> Event.Failed(
                    when (parts.getOrNull(1).orEmpty()) {
                        "800710DF", "RadioNotAvailable", "DisabledByUser" -> ProximityStatus.BLUETOOTH_OFF
                        else -> ProximityStatus.UNSUPPORTED
                    }
                )
                else -> null
            }
        }
    }
}

class WindowsBleScanner : BleScanner {

    @Volatile private var process: Process? = null

    private fun helper(): File? {
        val bytes = javaClass.getResourceAsStream("/ble/sinuschat-ble.exe")?.use { it.readBytes() } ?: return null
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)
        val dir = File(Platform.dataDir(), "bin").apply { mkdirs() }
        val exe = File(dir, "sinuschat-ble-$sha.exe")
        if (!exe.isFile || exe.length() != bytes.size.toLong()) {
            val tmp = File(dir, exe.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(exe)) {
                exe.delete()
                tmp.renameTo(exe)
            }
        }
        dir.listFiles()?.filter { it.name.startsWith("sinuschat-ble-") && it.name != exe.name }?.forEach { runCatching { it.delete() } }
        return exe.takeIf { it.isFile }
    }

    override fun start(onEvent: (BleScanner.Event) -> Unit) {
        val exe = runCatching { helper() }.getOrNull()
        if (exe == null) {
            onEvent(BleScanner.Event.Failed(ProximityStatus.UNSUPPORTED))
            return
        }
        val p = runCatching {
            ProcessBuilder(exe.absolutePath, "%04X".format(LinkProximity.COMPANY_ID)).redirectErrorStream(true).start()
        }.getOrNull()
        if (p == null) {
            onEvent(BleScanner.Event.Failed(ProximityStatus.UNSUPPORTED))
            return
        }
        process = p
        Thread({
            var failed = false
            runCatching {
                p.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val e = BleScanner.parse(line) ?: continue
                        if (e is BleScanner.Event.Failed) failed = true
                        onEvent(e)
                    }
                }
            }
            if (!failed && process === p) onEvent(BleScanner.Event.Failed(ProximityStatus.UNSUPPORTED))
        }, "sinuschat-ble").apply { isDaemon = true }.start()
    }

    override fun stop() {
        val p = process ?: return
        process = null
        runCatching { p.outputStream.close() }
        runCatching { if (!p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
    }
}

class ProximityGuard(
    private val scope: CoroutineScope,
    private val lock: LockManager,
    private val key: () -> ByteArray?,
    private val scanners: () -> BleScanner? = { BleScanner.platform() },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _reading = MutableStateFlow(ProximityReading(ProximityStatus.OFF))
    val reading: StateFlow<ProximityReading> = _reading

    private val events = ConcurrentLinkedQueue<BleScanner.Event>()
    private val tracker = ProximityTracker(clock)
    private var job: Job? = null
    @Volatile private var range = 0
    @Volatile private var calibration = 0
    @Volatile var dryRun = false
    @Volatile var onWouldLock: (() -> Unit)? = null

    fun rearm() {
        tracker.disarm()
    }

    fun update(enabled: Boolean, range: Int, calibration: Int = 0) {
        this.range = range
        this.calibration = calibration
        if (enabled && job == null) start()
        if (!enabled) stop()
    }

    private fun start() {
        job = scope.launch(Dispatchers.IO) {
            tracker.reset()
            while (isActive) {
                val scanner = scanners()
                if (scanner == null) {
                    _reading.value = ProximityReading(ProximityStatus.UNSUPPORTED)
                    delay(RETRY_MS)
                    continue
                }
                _reading.value = ProximityReading(ProximityStatus.STARTING)
                events.clear()
                scanner.start { events.add(it) }
                var failure: ProximityStatus? = null
                var cachedKey: ByteArray? = null
                try {
                    while (isActive && failure == null) {
                        while (true) {
                            val e = events.poll() ?: break
                            when (e) {
                                is BleScanner.Event.Failed -> failure = e.status
                                is BleScanner.Event.Seen -> {
                                    val k = cachedKey ?: key()?.also { cachedKey = it } ?: continue
                                    if (LinkProximity.matches(k, e.data, clock())) tracker.seen(e.rssi)
                                }
                                BleScanner.Event.Started -> Unit
                            }
                        }
                        if (failure != null) break
                        if (lock.locked.value && !dryRun) tracker.disarm()
                        val (reading, shouldLock) = tracker.tick(range, calibration = calibration)
                        _reading.value = reading
                        if (shouldLock) {
                            if (dryRun) onWouldLock?.invoke()
                            else if (!lock.locked.value) lock.lockNow()
                        }
                        delay(TICK_MS)
                        if (clock() % 60_000L < TICK_MS) cachedKey = null
                    }
                } finally {
                    scanner.stop()
                }
                tracker.disarm()
                _reading.value = ProximityReading(failure ?: ProximityStatus.UNSUPPORTED)
                delay(RETRY_MS)
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        tracker.reset()
        _reading.value = ProximityReading(ProximityStatus.OFF)
    }

    companion object {
        const val TICK_MS = 1_000L
        const val RETRY_MS = 10_000L
    }
}
