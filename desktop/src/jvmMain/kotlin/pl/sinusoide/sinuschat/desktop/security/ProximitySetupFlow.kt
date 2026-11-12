package pl.sinusoide.sinuschat.desktop.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.sinusoide.sinuschat.desktop.LinkState
import pl.sinusoide.sinuschat.desktop.PhoneLink
import pl.sinusoide.sinuschat.link.LinkFrame

class ProximitySetupFlow(
    private val scope: CoroutineScope,
    private val link: PhoneLink,
    private val lock: LockManager,
    val guard: ProximityGuard
) {
    enum class Step { WAIT_PHONE, BLUETOOTH, NEARBY, SEARCH, CONNECTED, TEST, LOCKED, DISTANCE, DONE }

    private val _step = MutableStateFlow<Step?>(null)
    val step: StateFlow<Step?> = _step

    private val _stale = MutableStateFlow(false)
    val stale: StateFlow<Boolean> = _stale

    private var job: Job? = null
    private var staleJob: Job? = null
    private var lostJob: Job? = null

    val scanning: Boolean get() = _step.value.let { it != null && it != Step.DONE }

    fun begin() {
        if (_step.value != null) return
        set(Step.WAIT_PHONE)
        guard.onWouldLock = { onWouldLock() }
        lock.setProximityRange(0)
        val j = Job()
        job = j
        scope.launch(j) {
            link.state.collect { st ->
                val connected = st is LinkState.Connected
                val now = _step.value ?: return@collect
                if (connected) {
                    lostJob?.cancel()
                    lostJob = null
                    when (now) {
                        Step.WAIT_PHONE, Step.BLUETOOTH, Step.NEARBY, Step.SEARCH -> {
                            send(LinkFrame.SETUP_START)
                            armStale()
                        }
                        Step.CONNECTED -> send(LinkFrame.SETUP_CONNECTED)
                        Step.TEST -> send(LinkFrame.SETUP_TEST)
                        Step.LOCKED -> send(LinkFrame.SETUP_LOCKED)
                        Step.DISTANCE -> send(LinkFrame.SETUP_DISTANCE)
                        Step.DONE -> Unit
                    }
                } else if (now == Step.BLUETOOTH || now == Step.NEARBY || now == Step.SEARCH || now == Step.CONNECTED) {
                    if (lostJob == null) lostJob = scope.launch {
                        delay(LOST_GRACE_MS)
                        if (link.state.value !is LinkState.Connected && _step.value.let { it == Step.BLUETOOTH || it == Step.NEARBY || it == Step.SEARCH || it == Step.CONNECTED }) {
                            set(Step.WAIT_PHONE)
                        }
                        lostJob = null
                    }
                }
            }
        }
        scope.launch(j) {
            link.proximitySetup.collect { f -> onPhone(f.step) }
        }
        scope.launch(j) {
            val samples = ArrayList<Int>()
            while (true) {
                delay(SAMPLE_MS)
                val r = guard.reading.value
                val rssi = r.rssi.takeIf { r.status == ProximityStatus.NEAR || r.status == ProximityStatus.FAR }
                when (_step.value) {
                    Step.SEARCH -> if (rssi == null) samples.clear() else {
                        samples += rssi
                        if (samples.size >= CALIBRATION_SAMPLES) {
                            lock.setProximityCalibration(samples.sorted()[samples.size / 2])
                            samples.clear()
                            set(Step.CONNECTED)
                            send(LinkFrame.SETUP_CONNECTED)
                        }
                    }
                    Step.TEST -> {
                        val m = rssi?.let { ProximityTracker.meters(it, lock.config.value.proximityCalibration) }
                        send(LinkFrame.SETUP_SIGNAL, rssi ?: -100, m?.let { (it * 10).toInt() } ?: 0)
                    }
                    else -> samples.clear()
                }
            }
        }
    }

    private fun armStale() {
        staleJob?.cancel()
        _stale.value = false
        staleJob = scope.launch {
            delay(STALE_MS)
            if (_step.value == Step.WAIT_PHONE) _stale.value = true
        }
    }

    private fun set(s: Step?) {
        _step.value = s
        if (s != Step.WAIT_PHONE) _stale.value = false
    }

    private fun send(step: String, rssi: Int = 0, decimeters: Int = 0) {
        link.send(LinkFrame.ProximitySetup(step, rssi, decimeters))
    }

    private fun onPhone(s: String) {
        val now = _step.value ?: return
        val early = now == Step.WAIT_PHONE || now == Step.BLUETOOTH || now == Step.NEARBY || now == Step.SEARCH
        when (s) {
            LinkFrame.SETUP_BLUETOOTH -> if (early) set(Step.BLUETOOTH)
            LinkFrame.SETUP_NEARBY -> if (early) set(Step.NEARBY)
            LinkFrame.SETUP_READY -> if (early) set(Step.SEARCH)
            LinkFrame.SETUP_TEST -> if (now == Step.CONNECTED) set(Step.TEST)
            LinkFrame.SETUP_SKIP, LinkFrame.SETUP_DONE -> if (now != Step.DONE) done(notify = false)
            LinkFrame.SETUP_CANCEL -> close(notify = false)
        }
    }

    private fun onWouldLock() {
        if (_step.value != Step.TEST) return
        set(Step.LOCKED)
        send(LinkFrame.SETUP_LOCKED)
        scope.launch {
            delay(LOCKED_MS)
            if (_step.value == Step.LOCKED) set(Step.DISTANCE)
        }
    }

    fun distanceTo(rssi: Int): Float? = ProximityTracker.meters(rssi, lock.config.value.proximityCalibration)

    fun test() {
        if (_step.value != Step.CONNECTED) return
        set(Step.TEST)
        send(LinkFrame.SETUP_TEST)
    }

    fun skip() {
        if (_step.value != Step.CONNECTED) return
        send(LinkFrame.SETUP_SKIP)
        done(notify = false)
    }

    fun finish() {
        if (_step.value == Step.DONE) return
        done(notify = true)
    }

    private fun done(notify: Boolean) {
        lock.setProximity(true)
        set(Step.DONE)
        if (notify) send(LinkFrame.SETUP_DONE)
        guard.onWouldLock = null
    }

    fun close(notify: Boolean = true) {
        val was = _step.value ?: return
        if (was != Step.DONE && notify) send(LinkFrame.SETUP_CANCEL)
        job?.cancel()
        job = null
        staleJob?.cancel()
        lostJob?.cancel()
        lostJob = null
        guard.onWouldLock = null
        set(null)
    }

    companion object {
        const val STALE_MS = 30_000L
        const val LOST_GRACE_MS = 8_000L
        const val SAMPLE_MS = 700L
        const val CALIBRATION_SAMPLES = 5
        const val LOCKED_MS = 2_600L
    }
}
