package pl.sinusoide.sinuschat.desktop.system

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.security.SecureRandom
import java.util.HexFormat
import kotlin.concurrent.thread

class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket,
    private val token: String
) {

    companion object {
        private const val SHOW = "show"

        fun acquire(dir: File): SingleInstance? {
            dir.mkdirs()
            val lockFile = File(dir, "instance.lock")
            val portFile = File(dir, "instance.port")
            val channel = RandomAccessFile(lockFile, "rw").channel
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                signal(portFile)
                return null
            }
            val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
            val token = HexFormat.of().formatHex(ByteArray(16).also { SecureRandom().nextBytes(it) })
            portFile.writeText("${server.localPort}\n$token")
            return SingleInstance(channel, lock, server, token)
        }

        private fun signal(portFile: File) {
            runCatching {
                val lines = portFile.readLines()
                val port = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: return
                val token = lines.getOrNull(1)?.trim() ?: return
                Socket(InetAddress.getLoopbackAddress(), port).use { s ->
                    s.soTimeout = 2000
                    s.getOutputStream().write("$token $SHOW\n".toByteArray())
                    s.getOutputStream().flush()
                }
            }
        }
    }

    fun listen(onShow: () -> Unit) {
        thread(isDaemon = true, name = "single-instance") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use { s ->
                        s.soTimeout = 2000
                        val line = s.getInputStream().bufferedReader().readLine()?.trim().orEmpty()
                        if (line == "$token $SHOW") onShow()
                    }
                }
            }
        }
    }

    fun release() {
        runCatching { server.close() }
        runCatching { lock.release() }
        runCatching { channel.close() }
    }
}
