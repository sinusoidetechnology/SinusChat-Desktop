package pl.sinusoide.sinuschat.desktop.i18n

class StoredStrings(
    val deleteAll: String,
    val deleteAllQ: String,
    val deleteOne: String,
    val deleted: String,
    val empty: String,
    val localNote: String,
    val older: (Int) -> String
) {
    companion object {
        val PL = StoredStrings(
            deleteAll = "Usuń wszystkie z tego komputera",
            deleteAllQ = "Usunąć te pliki z tego komputera?",
            deleteOne = "Usuń z komputera",
            deleted = "Usunięto z komputera",
            empty = "Brak plików zapisanych na tym komputerze",
            localNote = "Pliki znikną tylko z tego komputera. Wiadomości zostaną w czacie i na telefonie.",
            older = { "Starsze pliki, których czatu nie ma już w pamięci: $it" }
        )
        val EN = StoredStrings(
            deleteAll = "Delete all from this computer",
            deleteAllQ = "Delete these files from this computer?",
            deleteOne = "Delete from computer",
            deleted = "Deleted from the computer",
            empty = "No files saved on this computer",
            localNote = "Files are removed only from this computer. The messages stay in the chat and on the phone.",
            older = { "Older files whose chat is no longer in memory: $it" }
        )
        val CS = StoredStrings(
            deleteAll = "Smazat vše z tohoto počítače",
            deleteAllQ = "Smazat tyto soubory z tohoto počítače?",
            deleteOne = "Smazat z počítače",
            deleted = "Smazáno z počítače",
            empty = "V tomto počítači nejsou uložené žádné soubory",
            localNote = "Soubory zmizí jen z tohoto počítače. Zprávy zůstanou v chatu i v telefonu.",
            older = { "Starší soubory, jejichž chat už není v paměti: $it" }
        )
        val UK = StoredStrings(
            deleteAll = "Видалити все з цього комп’ютера",
            deleteAllQ = "Видалити ці файли з цього комп’ютера?",
            deleteOne = "Видалити з комп’ютера",
            deleted = "Видалено з комп’ютера",
            empty = "На цьому комп’ютері немає збережених файлів",
            localNote = "Файли зникнуть лише з цього комп’ютера. Повідомлення залишаться в чаті й на телефоні.",
            older = { "Старші файли, чату яких уже немає в пам’яті: $it" }
        )
        val DE = StoredStrings(
            deleteAll = "Alle von diesem Computer löschen",
            deleteAllQ = "Diese Dateien von diesem Computer löschen?",
            deleteOne = "Vom Computer löschen",
            deleted = "Vom Computer gelöscht",
            empty = "Auf diesem Computer sind keine Dateien gespeichert",
            localNote = "Die Dateien verschwinden nur von diesem Computer. Die Nachrichten bleiben im Chat und auf dem Handy.",
            older = { "Ältere Dateien, deren Chat nicht mehr im Speicher ist: $it" }
        )
        val ZH = StoredStrings(
            deleteAll = "從這台電腦刪除全部",
            deleteAllQ = "要從這台電腦刪除這些檔案嗎？",
            deleteOne = "從電腦刪除",
            deleted = "已從電腦刪除",
            empty = "這台電腦上沒有儲存的檔案",
            localNote = "檔案只會從這台電腦移除。訊息會保留在聊天和手機上。",
            older = { "聊天已不在記憶體中的較舊檔案：$it" }
        )

        fun of(chat: ChatStrings): StoredStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
