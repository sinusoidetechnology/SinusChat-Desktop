package pl.sinusoide.sinuschat.desktop.i18n

class LangStrings(
    val heroDesc: String,
    val heroSystem: String,
    val section: String,
    val system: String,
    val systemDesc: (String) -> String,
    val builtin: String,
    val polish: String,
    val english: String,
    val czech: String,
    val ukrainian: String,
    val german: String,
    val chinese: String,
    val footer: String,
    val changeQ: (String) -> String,
    val changeDesc: String,
    val changeBtn: String,
    val cancel: String
) {
    companion object {
        val PL = LangStrings(
            heroDesc = "Menu i napisy w aplikacji są w tym języku",
            heroSystem = "Zgodnie z językiem telefonu",
            section = "Dostępne",
            system = "Język systemu",
            systemDesc = { a0: String -> "Jak w telefonie — teraz: " + a0.toString() },
            builtin = "Wbudowany",
            polish = "Polski",
            english = "Angielski",
            czech = "Czeski",
            ukrainian = "Ukraiński",
            german = "Niemiecki",
            chinese = "Chiński tradycyjny",
            footer = "Zmienia się tylko język menu i napisów. Tłumaczenie wiadomości znajdziesz w Ustawienia → Tłumaczenie.",
            changeQ = { a0: String -> "Zmienić język na " + a0.toString() + "?" },
            changeDesc = "Ekran główny uruchomi się ponownie w nowym języku. Czaty i ustawienia zostają bez zmian.",
            changeBtn = "Zmień język",
            cancel = "Anuluj"
        )

        val EN = LangStrings(
            heroDesc = "Menus and labels in the app use this language",
            heroSystem = "Following your phone's language",
            section = "Available",
            system = "System language",
            systemDesc = { a0: String -> "Same as your phone — currently: " + a0.toString() },
            builtin = "Built-in",
            polish = "Polish",
            english = "English",
            czech = "Czech",
            ukrainian = "Ukrainian",
            german = "German",
            chinese = "Traditional Chinese",
            footer = "Only menus and labels change. To translate messages, go to Settings → Translation.",
            changeQ = { a0: String -> "Change language to " + a0.toString() + "?" },
            changeDesc = "The main screen will reload in the new language. Your chats and settings stay the same.",
            changeBtn = "Change language",
            cancel = "Cancel"
        )

        val CS = LangStrings(
            heroDesc = "Nabídky a popisky v aplikaci jsou v tomto jazyce",
            heroSystem = "Podle jazyka telefonu",
            section = "Dostupné",
            system = "Jazyk systému",
            systemDesc = { a0: String -> "Jako v telefonu — teď: " + a0.toString() },
            builtin = "Vestavěný",
            polish = "Polština",
            english = "Angličtina",
            czech = "Čeština",
            ukrainian = "Ukrajinština",
            german = "Němčina",
            chinese = "Tradiční čínština",
            footer = "Mění se jen jazyk nabídek a popisků. Překlad zpráv najdete v Nastavení → Překlad.",
            changeQ = { a0: String -> "Změnit jazyk na " + a0.toString() + "?" },
            changeDesc = "Hlavní obrazovka se znovu spustí v novém jazyce. Chaty a nastavení zůstanou beze změny.",
            changeBtn = "Změnit jazyk",
            cancel = "Zrušit"
        )

        val UK = LangStrings(
            heroDesc = "Меню й написи в застосунку цією мовою",
            heroSystem = "Відповідно до мови телефона",
            section = "Доступні",
            system = "Мова системи",
            systemDesc = { a0: String -> "Як у телефоні — зараз: " + a0.toString() },
            builtin = "Вбудована",
            polish = "Польська",
            english = "Англійська",
            czech = "Чеська",
            ukrainian = "Українська",
            german = "Німецька",
            chinese = "Традиційна китайська",
            footer = "Змінюється лише мова меню й написів. Переклад повідомлень — у Налаштування → Переклад.",
            changeQ = { a0: String -> "Змінити мову на " + a0.toString() + "?" },
            changeDesc = "Головний екран перезапуститься новою мовою. Чати й налаштування залишаться без змін.",
            changeBtn = "Змінити мову",
            cancel = "Скасувати"
        )

        val DE = LangStrings(
            heroDesc = "Menüs und Beschriftungen der App sind in dieser Sprache",
            heroSystem = "Entsprechend der Sprache des Handys",
            section = "Verfügbar",
            system = "Systemsprache",
            systemDesc = { a0: String -> "Wie auf dem Handy – gerade: " + a0.toString() },
            builtin = "Integriert",
            polish = "Polnisch",
            english = "Englisch",
            czech = "Tschechisch",
            ukrainian = "Ukrainisch",
            german = "Deutsch",
            chinese = "Traditionelles Chinesisch",
            footer = "Nur die Sprache der Menüs und Beschriftungen ändert sich. Nachrichten übersetzt du unter Einstellungen → Übersetzung.",
            changeQ = { a0: String -> "Sprache zu " + a0.toString() + " ändern?" },
            changeDesc = "Der Startbildschirm startet in der neuen Sprache neu. Chats und Einstellungen bleiben unverändert.",
            changeBtn = "Sprache ändern",
            cancel = "Abbrechen"
        )

        val ZH = LangStrings(
            heroDesc = "應用程式中的選單和標籤會使用此語言",
            heroSystem = "依照手機的語言",
            section = "可用的語言",
            system = "系統語言",
            systemDesc = { a0: String -> "與手機相同，目前為：" + a0.toString() },
            builtin = "內建",
            polish = "波蘭文",
            english = "英文",
            czech = "捷克文",
            ukrainian = "烏克蘭文",
            german = "德文",
            chinese = "繁體中文",
            footer = "只會變更選單和標籤。若要翻譯訊息，請前往「設定」→「翻譯」。",
            changeQ = { a0: String -> "要將語言變更為" + a0.toString() + "嗎？" },
            changeDesc = "主畫面將以新語言重新載入。你的聊天和設定不會改變。",
            changeBtn = "變更語言",
            cancel = "取消"
        )

        fun of(chat: ChatStrings): LangStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
