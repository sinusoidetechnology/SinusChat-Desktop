package pl.sinusoide.sinuschat.desktop.ui.chat

import androidx.compose.ui.graphics.Color
import pl.sinusoide.sinuschat.desktop.i18n.ChatStrings
import pl.sinusoide.sinuschat.link.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object ChatFormat {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(hm)

    fun listTime(ms: Long, s: ChatStrings, locale: Locale): String {
        if (ms <= 0L) return ""
        val d = date(ms)
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(d, today)
        return when {
            days <= 0 -> time(ms)
            days == 1L -> s.yesterday
            days < 7 -> d.format(DateTimeFormatter.ofPattern("EEE", locale)).replaceFirstChar { it.titlecase(locale) }
            d.year == today.year -> d.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日" else "d MMM", locale))
            else -> d.format(DateTimeFormatter.ofPattern("dd.MM.yy", locale))
        }
    }

    fun dayLabel(d: LocalDate, locale: Locale): String {
        val thisYear = d.year == LocalDate.now(zone).year
        val pattern = when (locale.language) {
            "en" -> if (thisYear) "MMMM d" else "MMMM d, y"
            "cs", "de" -> if (thisYear) "d. MMMM" else "d. MMMM y"
            "uk" -> if (thisYear) "d MMMM" else "d MMMM y 'р.'"
            "zh" -> if (thisYear) "M月d日" else "y年M月d日"
            else -> if (thisYear) "d MMMM" else "d MMMM y"
        }
        return d.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun listPreview(kind: String, text: String, s: ChatStrings): String = when (kind) {
        ChatMessage.KIND_TEXT, ChatMessage.KIND_SYSTEM -> text
        else -> kindLabel(kind, null, s) ?: text
    }.replace('\n', ' ')

    fun kindLabel(kind: String, fileName: String?, s: ChatStrings): String? = when (kind) {
        ChatMessage.KIND_PHOTO -> s.photo
        ChatMessage.KIND_VIDEO -> s.video
        ChatMessage.KIND_VOICE -> s.voice
        ChatMessage.KIND_LOCATION -> s.location
        ChatMessage.KIND_FILE -> fileName?.takeIf { it.isNotBlank() } ?: s.attachment
        else -> null
    }

    fun preview(kind: String, text: String, fileName: String?, s: ChatStrings): String {
        val label = kindLabel(kind, fileName, s)
        return when {
            label != null && text.isNotBlank() -> "$label · $text"
            label != null -> label
            else -> text
        }.replace('\n', ' ')
    }

    fun kindIcon(kind: String): String? = when (kind) {
        ChatMessage.KIND_PHOTO -> "ic_image"
        ChatMessage.KIND_VIDEO -> "ic_video"
        ChatMessage.KIND_VOICE -> "ic_mic"
        ChatMessage.KIND_LOCATION -> "ic_location"
        ChatMessage.KIND_FILE -> "ic_attach"
        else -> null
    }

    fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    fun color(hex: String?): Color = try {
        val h = hex.orEmpty().removePrefix("#")
        when (h.length) {
            6 -> Color(0xFF000000 or h.toLong(16))
            8 -> Color(h.toLong(16))
            else -> Color(0xFF5288C1)
        }
    } catch (_: Exception) {
        Color(0xFF5288C1)
    }
}
