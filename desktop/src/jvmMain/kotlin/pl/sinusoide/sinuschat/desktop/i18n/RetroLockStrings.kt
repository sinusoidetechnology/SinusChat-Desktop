package pl.sinusoide.sinuschat.desktop.i18n

class RetroLockStrings(
    val hint: String,
    val placeholder: String
) {
    companion object {
        val PL = RetroLockStrings(
            hint = "Aby rozpocząć, wpisz swój PIN",
            placeholder = "Wpisz PIN"
        )

        val EN = RetroLockStrings(
            hint = "To begin, enter your PIN",
            placeholder = "Enter PIN"
        )

        val CS = RetroLockStrings(
            hint = "Pro začátek zadejte svůj PIN",
            placeholder = "Zadejte PIN"
        )

        val UK = RetroLockStrings(
            hint = "Щоб почати, введіть свій PIN",
            placeholder = "Введіть PIN"
        )

        val DE = RetroLockStrings(
            hint = "Geben Sie zum Starten Ihre PIN ein",
            placeholder = "PIN eingeben"
        )

        val ZH = RetroLockStrings(
            hint = "請輸入 PIN 碼以開始",
            placeholder = "輸入 PIN 碼"
        )

        fun of(chat: ChatStrings): RetroLockStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
