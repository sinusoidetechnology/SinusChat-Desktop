package pl.sinusoide.sinuschat.desktop.chat

import pl.sinusoide.sinuschat.link.ChatMessage
import java.io.File

object StorageUsage {

    enum class Kind { IMAGE, VIDEO, VOICE, DOCUMENT }

    class KindUsage(val kind: Kind, val count: Int, val bytes: Long)

    class ConvUsage(val conv: String, val count: Int, val bytes: Long)

    class Snapshot(
        val kinds: List<KindUsage>,
        val conversations: List<ConvUsage>,
        val mediaBytes: Long,
        val cacheBytes: Long,
        val appBytes: Long,
        val freeBytes: Long,
        val totalBytes: Long
    )

    fun kindOf(kind: String): Kind = when (kind) {
        ChatMessage.KIND_PHOTO -> Kind.IMAGE
        ChatMessage.KIND_VIDEO -> Kind.VIDEO
        ChatMessage.KIND_VOICE -> Kind.VOICE
        else -> Kind.DOCUMENT
    }

    fun snapshot(entries: List<MediaVault.Entry>, cacheBytes: Long, appBytes: Long, disk: File): Snapshot {
        val byKind = entries.groupBy { kindOf(it.kind) }
        val kinds = Kind.entries.map { k ->
            val list = byKind[k].orEmpty()
            KindUsage(k, list.size, list.sumOf { it.bytes })
        }
        val conversations = entries.groupBy { it.conv }
            .map { (conv, list) -> ConvUsage(conv, list.size, list.sumOf { it.bytes }) }
            .sortedByDescending { it.bytes }
        val media = entries.sumOf { it.bytes }
        return Snapshot(
            kinds = kinds,
            conversations = conversations,
            mediaBytes = media,
            cacheBytes = cacheBytes,
            appBytes = maxOf(appBytes, media + cacheBytes),
            freeBytes = runCatching { disk.usableSpace }.getOrDefault(0L),
            totalBytes = runCatching { disk.totalSpace }.getOrDefault(0L)
        )
    }

    fun folderBytes(dir: File): Long =
        if (!dir.exists()) 0L else runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}
