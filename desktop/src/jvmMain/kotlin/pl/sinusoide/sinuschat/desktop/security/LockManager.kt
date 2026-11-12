package pl.sinusoide.sinuschat.desktop.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.sinusoide.sinuschat.desktop.SecureStore
import pl.sinusoide.sinuschat.link.LinkCrypto
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
data class LockConfig(
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val pinLength: Int = 0,
    val duressSalt: String? = null,
    val duressHash: String? = null,
    val duressLength: Int = 0,
    val autoConfirm: Boolean = true,
    val showDots: Boolean = true,
    val silent: Boolean = false,
    val relockMs: Long = LockManager.RELOCK_DEFAULT,
    val lockOnHide: Boolean = false,
    val lockOnWake: Boolean = true,
    val lockOnDisconnect: Boolean = false,
    val proximity: Boolean = false,
    val proximityRange: Int = 0,
    val proximityCalibration: Int = 0,
    val biometric: Boolean = false,
    val wipeOnFail: Boolean = false,
    val failCount: Int = 0,
    val lockoutEnd: Long = 0L,
    val lockoutDuration: Long = 0L
) {
    val hasPin: Boolean get() = pinHash != null
    val hasDuress: Boolean get() = duressHash != null
}

class LockManager(private val file: String = FILE, private val clock: () -> Long = System::currentTimeMillis) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private val _config = MutableStateFlow(load())
    val config: StateFlow<LockConfig> = _config

    private val _locked = MutableStateFlow(_config.value.hasPin)
    val locked: StateFlow<Boolean> = _locked

    @Volatile private var backgroundSince = 0L

    private fun load(): LockConfig =
        SecureStore.read(file)?.let { runCatching { json.decodeFromString(LockConfig.serializer(), String(it, Charsets.UTF_8)) }.getOrNull() }
            ?: LockConfig()

    private fun update(change: (LockConfig) -> LockConfig) {
        synchronized(lock) {
            val next = change(_config.value)
            if (next == _config.value) return
            _config.value = next
            if (next == LockConfig()) SecureStore.delete(file)
            else SecureStore.write(file, json.encodeToString(LockConfig.serializer(), next).toByteArray(Charsets.UTF_8))
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun seal(pin: String): Pair<String, String> {
        val salt = LinkCrypto.randomBytes(16)
        val enc = Base64.getEncoder()
        return enc.encodeToString(salt) to enc.encodeToString(derive(pin, salt))
    }

    private fun matches(pin: String, salt: String?, hash: String?, length: Int): Boolean {
        if (salt == null || hash == null || pin.length != length) return false
        val dec = Base64.getDecoder()
        return runCatching { MessageDigest.isEqual(derive(pin, dec.decode(salt)), dec.decode(hash)) }.getOrDefault(false)
    }

    fun verifyPin(pin: String): Boolean = _config.value.let { matches(pin, it.pinSalt, it.pinHash, it.pinLength) }

    fun verifyDuress(pin: String): Boolean = _config.value.let { matches(pin, it.duressSalt, it.duressHash, it.duressLength) }

    fun setPin(pin: String) {
        val (salt, hash) = seal(pin)
        update { it.copy(pinSalt = salt, pinHash = hash, pinLength = pin.length, failCount = 0, lockoutEnd = 0L, lockoutDuration = 0L) }
    }

    fun setDuress(pin: String) {
        val (salt, hash) = seal(pin)
        update { it.copy(duressSalt = salt, duressHash = hash, duressLength = pin.length) }
    }

    fun removeDuress() = update { it.copy(duressSalt = null, duressHash = null, duressLength = 0) }

    fun removePin() {
        update {
            it.copy(
                pinSalt = null, pinHash = null, pinLength = 0,
                duressSalt = null, duressHash = null, duressLength = 0,
                biometric = false, wipeOnFail = false, failCount = 0, lockoutEnd = 0L, lockoutDuration = 0L
            )
        }
        _locked.value = false
    }

    fun setAutoConfirm(on: Boolean) = update { it.copy(autoConfirm = on) }
    fun setShowDots(on: Boolean) = update { it.copy(showDots = on) }
    fun setSilent(on: Boolean) = update { it.copy(silent = on) }
    fun setBiometric(on: Boolean) = update { it.copy(biometric = on) }
    fun setWipeOnFail(on: Boolean) = update { it.copy(wipeOnFail = on, failCount = 0) }
    fun setRelockMs(ms: Long) = update { it.copy(relockMs = ms.takeIf { v -> v in RELOCK_OPTIONS } ?: RELOCK_DEFAULT) }
    fun setLockOnHide(on: Boolean) = update { it.copy(lockOnHide = on) }
    fun setLockOnWake(on: Boolean) = update { it.copy(lockOnWake = on) }
    fun setLockOnDisconnect(on: Boolean) = update { it.copy(lockOnDisconnect = on) }
    fun setProximity(on: Boolean) = update { it.copy(proximity = on) }
    fun setProximityRange(range: Int) = update { it.copy(proximityRange = range.coerceIn(0, 2)) }
    fun setProximityCalibration(rssi: Int) = update { it.copy(proximityCalibration = rssi.coerceIn(-100, -20)) }

    fun failCount(): Int = _config.value.failCount

    fun registerFailure(): Int {
        var attempts = 0
        update {
            attempts = it.failCount + 1
            val wait = lockoutFor(attempts)
            if (wait > 0) it.copy(failCount = attempts, lockoutEnd = clock() + wait, lockoutDuration = wait)
            else it.copy(failCount = attempts)
        }
        return attempts
    }

    fun lockoutRemainingMs(): Long {
        val c = _config.value
        if (c.lockoutDuration <= 0L) return 0L
        val now = clock()
        val remaining = if (now < c.lockoutEnd - c.lockoutDuration) c.lockoutDuration else c.lockoutEnd - now
        if (remaining <= 0L) {
            update { it.copy(lockoutEnd = 0L, lockoutDuration = 0L) }
            return 0L
        }
        return remaining.coerceAtMost(c.lockoutDuration)
    }

    fun unlock() {
        update { it.copy(failCount = 0, lockoutEnd = 0L, lockoutDuration = 0L) }
        _locked.value = false
        backgroundSince = 0L
    }

    fun lockNow() {
        if (_config.value.hasPin) _locked.value = true
    }

    fun onBackground() {
        if (backgroundSince == 0L) backgroundSince = clock()
    }

    fun onForeground() {
        val since = backgroundSince
        backgroundSince = 0L
        val after = _config.value.relockMs
        if (since != 0L && after != RELOCK_NEVER && clock() - since >= after) lockNow()
    }

    fun onHidden() {
        if (_config.value.lockOnHide) lockNow()
    }

    fun onClockGap(gapMs: Long) {
        if (gapMs >= SLEEP_GAP_MS && _config.value.lockOnWake) lockNow()
    }

    fun onPhoneLost() {
        if (_config.value.lockOnDisconnect) lockNow()
    }

    fun wipe() {
        update { LockConfig() }
        _locked.value = false
        backgroundSince = 0L
    }

    companion object {
        const val FILE = "lock.bin"
        const val MIN_PIN = 4
        const val MAX_PIN = 12
        const val MAX_ATTEMPTS = 10
        const val RELOCK_DEFAULT = 30_000L
        const val RELOCK_NEVER = -1L
        const val SLEEP_GAP_MS = 60_000L
        private const val ITERATIONS = 120_000
        private const val LOCKOUT_AFTER = 5
        private const val LOCKOUT_STEP_MS = 5 * 60_000L
        private const val LOCKOUT_MAX_MS = 24 * 3600_000L

        val RELOCK_OPTIONS = listOf(15_000L, 30_000L, 60_000L, 120_000L, 180_000L, 300_000L, 600_000L, 1_800_000L, RELOCK_NEVER)

        fun lockoutFor(attempts: Int): Long =
            if (attempts < LOCKOUT_AFTER) 0L
            else minOf((attempts - LOCKOUT_AFTER + 1) * LOCKOUT_STEP_MS, LOCKOUT_MAX_MS)

        fun isWeak(pin: String): Boolean {
            if (pin.length < 4 || pin.any { !it.isDigit() }) return false
            return pin in COMMON || fewDigits(pin) || steps(pin, 1) || steps(pin, 2) ||
                periodic(pin) || palindrome(pin) || year(pin)
        }

        private fun fewDigits(pin: String): Boolean = pin.length <= 8 && pin.toSet().size <= 2

        private fun steps(pin: String, step: Int): Boolean {
            val d = pin.map { it - '0' }
            fun follows(delta: Int) = (1 until d.size).all { (d[it] - d[it - 1] + 10) % 10 == (delta + 10) % 10 }
            return follows(step) || follows(-step)
        }

        private fun periodic(pin: String): Boolean =
            (1..minOf(4, pin.length / 2)).any { p -> pin.indices.all { pin[it] == pin[it % p] } }

        private fun palindrome(pin: String): Boolean = pin.length <= 6 && pin == pin.reversed()

        private fun year(pin: String): Boolean = pin.length == 4 && pin.toInt() in 1900..2039

        private val COMMON = setOf(
            "1004", "1230", "1231", "1123", "1313", "1414", "1515", "2121", "2323", "4545",
            "0007", "0070", "0707", "1101", "1201", "2525", "2512", "1225", "0911", "0119",
            "3141", "2718", "5683", "7669", "1314", "0520", "5200", "4200", "0420", "1337",
            "2137", "0666", "6660", "1998", "8008", "5150", "7355", "1379", "1397", "7931",
            "2115", "1410", "0997",
            "9731", "1793", "3179", "7913", "3197", "1590", "0951", "3570", "0753", "2580",
            "0852", "8520", "5280", "1470", "0741", "7410", "0147", "3690", "0963", "9630",
            "0369", "1478", "8741", "1236", "6321", "3214", "4123", "1254", "4521", "7896",
            "6987", "9874", "4789", "7895", "5987", "1596", "6951", "7536", "6357", "1593",
            "3951", "7532", "2357", "4569", "9654", "2583", "2587", "1452", "3256",
            "13579", "97531", "24680", "08642", "12358", "11235", "31415", "27182", "10203",
            "14789", "98741", "25802", "80085", "11211", "52013", "52131", "00700", "12300",
            "159753", "357951", "951357", "753159", "147258", "258369", "369258", "852741",
            "147852", "963852", "741852", "852963", "789456", "456123", "123654", "321654",
            "147369", "369147", "789654", "987456", "123789", "789123", "456789", "741963",
            "159357", "357159", "258456", "142536", "102030", "100200", "112233", "332211",
            "121314", "131415", "246810", "135790", "112358", "314159", "271828", "520520",
            "520131", "131420", "147741", "159951", "753951", "951753", "258852", "369963",
            "123000", "000123", "100100", "110110", "101101", "123987", "987123", "654123",
            "696969", "420420", "133713", "213721", "000007", "007007", "121121", "070707",
            "1234567", "7654321", "5201314", "7355608", "5318008", "1357924", "8675309",
            "12345678", "87654321", "11223344", "44332211", "12344321", "13572468", "24681357",
            "14725836", "15975346", "12121212", "11112222", "10203040", "01234567", "76543210",
            "20002000", "19901990", "31415926", "27182818", "11235813", "25802580", "21372137"
        )
    }
}
