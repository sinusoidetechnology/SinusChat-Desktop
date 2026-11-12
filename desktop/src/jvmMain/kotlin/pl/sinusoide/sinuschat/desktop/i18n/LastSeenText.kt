package pl.sinusoide.sinuschat.desktop.i18n

private enum class Unit { MINUTE, HOUR, DAY, MONTH, YEAR }

fun ChatStrings.lastSeenAgo(ageMs: Long): String {
    val age = ageMs.coerceAtLeast(0L)
    val minutes = (age / 60_000L).toInt()
    val hours = (age / 3_600_000L).toInt()
    val days = (age / 86_400_000L).toInt()
    val (n, unit) = when {
        minutes < 1 -> 0 to null
        minutes < 60 -> minutes to Unit.MINUTE
        hours < 24 -> hours to Unit.HOUR
        days < 30 -> days to Unit.DAY
        days < 365 -> days / 30 to Unit.MONTH
        else -> (days / 365).coerceAtLeast(1) to Unit.YEAR
    }
    return when (this) {
        ChatStrings.PL -> "Ostatnio aktywny/a " + (unit?.let { pl(n, it) } ?: "przed chwilą")
        ChatStrings.CS -> "Naposledy aktivní " + (unit?.let { cs(n, it) } ?: "právě teď")
        ChatStrings.UK -> "Був(-ла) у мережі " + (unit?.let { uk(n, it) } ?: "щойно")
        ChatStrings.DE -> "Zuletzt online " + (unit?.let { de(n, it) } ?: "gerade eben")
        ChatStrings.ZH -> "最後上線" + (unit?.let { zh(n, it) } ?: "剛剛")
        else -> "Last seen " + (unit?.let { en(n, it) } ?: "just now")
    }
}

private fun slavicForm(n: Int, oneOnlyForOne: Boolean): Int {
    val d = n % 10
    val h = n % 100
    return when {
        n == 1 -> 0
        !oneOnlyForOne && d == 1 && h != 11 -> 0
        d in 2..4 && h !in 12..14 -> 1
        else -> 2
    }
}

private fun pl(n: Int, u: Unit): String {
    val f = slavicForm(n, true)
    val w = when (u) {
        Unit.MINUTE -> listOf("minutę", "minuty", "minut")
        Unit.HOUR -> listOf("godzinę", "godziny", "godzin")
        Unit.DAY -> listOf("dzień", "dni", "dni")
        Unit.MONTH -> listOf("miesiąc", "miesiące", "miesięcy")
        Unit.YEAR -> listOf("rok", "lata", "lat")
    }
    return "$n ${w[f]} temu"
}

private fun uk(n: Int, u: Unit): String {
    val f = slavicForm(n, false)
    val w = when (u) {
        Unit.MINUTE -> listOf("хвилину", "хвилини", "хвилин")
        Unit.HOUR -> listOf("годину", "години", "годин")
        Unit.DAY -> listOf("день", "дні", "днів")
        Unit.MONTH -> listOf("місяць", "місяці", "місяців")
        Unit.YEAR -> listOf("рік", "роки", "років")
    }
    return "$n ${w[f]} тому"
}

private fun cs(n: Int, u: Unit): String {
    val f = when (n) { 1 -> 0; in 2..4 -> 1; else -> 2 }
    val w = when (u) {
        Unit.MINUTE -> listOf("minutou", "minutami", "minutami")
        Unit.HOUR -> listOf("hodinou", "hodinami", "hodinami")
        Unit.DAY -> listOf("dnem", "dny", "dny")
        Unit.MONTH -> listOf("měsícem", "měsíci", "měsíci")
        Unit.YEAR -> listOf("rokem", "lety", "lety")
    }
    return "před $n ${w[f]}"
}

private fun de(n: Int, u: Unit): String {
    val one = n == 1
    val w = when (u) {
        Unit.MINUTE -> if (one) "Minute" else "Minuten"
        Unit.HOUR -> if (one) "Stunde" else "Stunden"
        Unit.DAY -> if (one) "Tag" else "Tagen"
        Unit.MONTH -> if (one) "Monat" else "Monaten"
        Unit.YEAR -> if (one) "Jahr" else "Jahren"
    }
    return "vor $n $w"
}

private fun en(n: Int, u: Unit): String {
    val w = when (u) {
        Unit.MINUTE -> "minute"
        Unit.HOUR -> "hour"
        Unit.DAY -> "day"
        Unit.MONTH -> "month"
        Unit.YEAR -> "year"
    }
    return "$n $w${if (n == 1) "" else "s"} ago"
}

private fun zh(n: Int, u: Unit): String = when (u) {
    Unit.MINUTE -> "$n 分鐘前"
    Unit.HOUR -> "$n 小時前"
    Unit.DAY -> "$n 天前"
    Unit.MONTH -> "$n 個月前"
    Unit.YEAR -> "$n 年前"
}
