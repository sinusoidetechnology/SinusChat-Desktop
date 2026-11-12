package pl.sinusoide.sinuschat.desktop.i18n

class ActionStrings(
    val pin: String,
    val unpin: String,
    val pinnedDone: String,
    val unpinnedDone: String,
    val pinnedTitle: String,
    val noPinned: String,
    val pinnedMissing: String,
    val edit: String,
    val save: String,
    val chooseReaction: String,
    val faces: String,
    val gestures: String,
    val hearts: String,
    val others: String
) {
    companion object {
        val PL = ActionStrings(
            pin = "Przypnij",
            unpin = "Odepnij",
            pinnedDone = "Przypięto",
            unpinnedDone = "Odpięto",
            pinnedTitle = "Przypięte wiadomości",
            noPinned = "Brak przypiętych wiadomości",
            pinnedMissing = "Nie udało się znaleźć tej wiadomości w historii",
            edit = "Edytuj",
            save = "Zapisz",
            chooseReaction = "Wybierz reakcję",
            faces = "Buźki",
            gestures = "Gesty",
            hearts = "Serca",
            others = "Inne"
        )

        val EN = ActionStrings(
            pin = "Pin",
            unpin = "Unpin",
            pinnedDone = "Pinned",
            unpinnedDone = "Unpinned",
            pinnedTitle = "Pinned messages",
            noPinned = "No pinned messages",
            pinnedMissing = "Couldn't find this message in the history",
            edit = "Edit",
            save = "Save",
            chooseReaction = "Choose reaction",
            faces = "Smileys",
            gestures = "Gestures",
            hearts = "Hearts",
            others = "More"
        )

        val CS = ActionStrings(
            pin = "Připnout",
            unpin = "Odepnout",
            pinnedDone = "Připnuto",
            unpinnedDone = "Odepnuto",
            pinnedTitle = "Připnuté zprávy",
            noPinned = "Žádné připnuté zprávy",
            pinnedMissing = "Tuto zprávu se v historii nepodařilo najít",
            edit = "Upravit",
            save = "Uložit",
            chooseReaction = "Vyberte reakci",
            faces = "Smajlíci",
            gestures = "Gesta",
            hearts = "Srdce",
            others = "Další"
        )

        val UK = ActionStrings(
            pin = "Закріпити",
            unpin = "Відкріпити",
            pinnedDone = "Закріплено",
            unpinnedDone = "Відкріплено",
            pinnedTitle = "Закріплені повідомлення",
            noPinned = "Немає закріплених повідомлень",
            pinnedMissing = "Не вдалося знайти це повідомлення в історії",
            edit = "Редагувати",
            save = "Зберегти",
            chooseReaction = "Виберіть реакцію",
            faces = "Смайлики",
            gestures = "Жести",
            hearts = "Серця",
            others = "Інші"
        )

        val DE = ActionStrings(
            pin = "Anheften",
            unpin = "Loslösen",
            pinnedDone = "Angeheftet",
            unpinnedDone = "Losgelöst",
            pinnedTitle = "Angeheftete Nachrichten",
            noPinned = "Keine angehefteten Nachrichten",
            pinnedMissing = "Diese Nachricht wurde im Verlauf nicht gefunden",
            edit = "Bearbeiten",
            save = "Speichern",
            chooseReaction = "Reaktion auswählen",
            faces = "Smileys",
            gestures = "Gesten",
            hearts = "Herzen",
            others = "Weitere"
        )

        val ZH = ActionStrings(
            pin = "釘選",
            unpin = "取消釘選",
            pinnedDone = "已釘選",
            unpinnedDone = "已取消釘選",
            pinnedTitle = "已釘選的訊息",
            noPinned = "沒有已釘選的訊息",
            pinnedMissing = "在歷史紀錄中找不到這則訊息",
            edit = "編輯",
            save = "儲存",
            chooseReaction = "選擇回應",
            faces = "表情",
            gestures = "手勢",
            hearts = "愛心",
            others = "更多"
        )

        fun of(chat: ChatStrings): ActionStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
