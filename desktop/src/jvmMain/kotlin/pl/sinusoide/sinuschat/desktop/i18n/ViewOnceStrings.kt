package pl.sinusoide.sinuschat.desktop.i18n

class ViewOnceStrings(
    val tapToOpen: String,
    val sent: String,
    val opened: String,
    val unavailable: String,
    val alreadyOpened: String,
    val closeHint: String,
    val senderCantOpen: String
) {
    companion object {
        val PL = ViewOnceStrings(
            tapToOpen = "Kliknij, aby otworzyć",
            sent = "Wysłano plik",
            opened = "Otworzono plik",
            unavailable = "Nie można otworzyć tego pliku",
            alreadyOpened = "Ten plik został już otwarty i usunięty",
            closeHint = "Po zamknięciu zdjęcie zniknie",
            senderCantOpen = "Plik jednorazowy może otworzyć tylko odbiorca"
        )
        val EN = ViewOnceStrings(
            tapToOpen = "Click to open",
            sent = "File sent",
            opened = "File opened",
            unavailable = "This file can't be opened",
            alreadyOpened = "This file was already opened and deleted",
            closeHint = "The photo disappears when you close it",
            senderCantOpen = "Only the recipient can open a view-once file"
        )
        val CS = ViewOnceStrings(
            tapToOpen = "Klikněte pro otevření",
            sent = "Soubor odeslán",
            opened = "Soubor otevřen",
            unavailable = "Tento soubor nelze otevřít",
            alreadyOpened = "Tento soubor už byl otevřen a smazán",
            closeHint = "Po zavření fotka zmizí",
            senderCantOpen = "Jednorázový soubor může otevřít jen příjemce"
        )
        val UK = ViewOnceStrings(
            tapToOpen = "Натисніть, щоб відкрити",
            sent = "Файл надіслано",
            opened = "Файл відкрито",
            unavailable = "Не вдається відкрити цей файл",
            alreadyOpened = "Цей файл уже відкрили й видалили",
            closeHint = "Після закриття фото зникне",
            senderCantOpen = "Одноразовий файл може відкрити лише одержувач"
        )
        val DE = ViewOnceStrings(
            tapToOpen = "Zum Öffnen klicken",
            sent = "Datei gesendet",
            opened = "Datei geöffnet",
            unavailable = "Diese Datei kann nicht geöffnet werden",
            alreadyOpened = "Diese Datei wurde bereits geöffnet und gelöscht",
            closeHint = "Nach dem Schließen verschwindet das Foto",
            senderCantOpen = "Nur der Empfänger kann eine Datei zur Einmalansicht öffnen"
        )
        val ZH = ViewOnceStrings(
            tapToOpen = "點擊開啟",
            sent = "檔案已傳送",
            opened = "檔案已開啟",
            unavailable = "無法開啟此檔案",
            alreadyOpened = "此檔案已被開啟並刪除",
            closeHint = "關閉後照片將會消失",
            senderCantOpen = "只有收件人可以開啟單次檢視檔案"
        )

        fun of(chat: ChatStrings): ViewOnceStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
