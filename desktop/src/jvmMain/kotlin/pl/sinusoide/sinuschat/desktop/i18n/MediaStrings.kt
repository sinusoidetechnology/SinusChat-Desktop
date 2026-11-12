package pl.sinusoide.sinuschat.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf

class MediaStrings(
    val save: String,
    val saveAs: String,
    val saved: (String) -> String,
    val open: String,
    val attach: String,
    val dropHere: String,
    val viewOnceGroups: String,
    val viewOnceMediaOnly: String,
    val original: String,
    val originalOn: String,
    val originalOff: String,
    val tooBig: String,
    val gone: String,
    val failed: String,
    val remove: String,
    val captionHint: String,
    val voiceFailed: String,
    val close: String
) {
    companion object {
        val PL = MediaStrings(
            save = "Zapisz",
            saveAs = "Zapisz jako…",
            saved = { "Zapisano: $it" },
            open = "Otwórz",
            attach = "Dołącz plik",
            dropHere = "Upuść pliki, aby je wysłać",
            viewOnceGroups = "Pliki jednorazowe działają tylko w rozmowach prywatnych",
            viewOnceMediaOnly = "Jednorazowo można wysłać tylko zdjęcia i filmy",
            original = "Oryginalna jakość",
            originalOn = "Oryginalna jakość: wyślę bez kompresji, w pełnej rozdzielczości",
            originalOff = "Standardowa jakość: mniejszy plik, szybsza wysyłka",
            tooBig = "Plik jest za duży. Maksymalnie 150 MB.",
            gone = "Ten plik nie jest już dostępny.",
            failed = "Nie udało się pobrać pliku.",
            remove = "Usuń",
            captionHint = "Dodaj podpis…",
            voiceFailed = "Nie udało się odtworzyć wiadomości głosowej.",
            close = "Zamknij"
        )

        val EN = MediaStrings(
            save = "Save",
            saveAs = "Save as…",
            saved = { "Saved: $it" },
            open = "Open",
            attach = "Attach a file",
            dropHere = "Drop files to send them",
            viewOnceGroups = "View-once files only work in private chats",
            viewOnceMediaOnly = "Only photos and videos can be sent as view-once",
            original = "Original quality",
            originalOn = "Original quality: sent uncompressed, in full resolution",
            originalOff = "Standard quality: smaller file, faster sending",
            tooBig = "The file is too large. 150 MB maximum.",
            gone = "This file is no longer available.",
            failed = "Couldn't download the file.",
            remove = "Remove",
            captionHint = "Add a caption…",
            voiceFailed = "Couldn't play the voice message.",
            close = "Close"
        )

        val CS = MediaStrings(
            save = "Uložit",
            saveAs = "Uložit jako…",
            saved = { "Uloženo: $it" },
            open = "Otevřít",
            attach = "Připojit soubor",
            dropHere = "Přetáhněte soubory sem a odešlou se",
            viewOnceGroups = "Jednorázové soubory fungují jen v soukromých konverzacích",
            viewOnceMediaOnly = "Jednorázově lze odeslat jen fotky a videa",
            original = "Původní kvalita",
            originalOn = "Původní kvalita: odešle se bez komprese v plném rozlišení",
            originalOff = "Standardní kvalita: menší soubor, rychlejší odeslání",
            tooBig = "Soubor je příliš velký. Nejvýše 150 MB.",
            gone = "Tento soubor už není dostupný.",
            failed = "Soubor se nepodařilo stáhnout.",
            remove = "Odebrat",
            captionHint = "Přidat popisek…",
            voiceFailed = "Hlasovou zprávu se nepodařilo přehrát.",
            close = "Zavřít"
        )

        val UK = MediaStrings(
            save = "Зберегти",
            saveAs = "Зберегти як…",
            saved = { "Збережено: $it" },
            open = "Відкрити",
            attach = "Прикріпити файл",
            dropHere = "Перетягніть файли сюди, щоб надіслати",
            viewOnceGroups = "Одноразові файли працюють лише в приватних розмовах",
            viewOnceMediaOnly = "Одноразово можна надсилати лише фото й відео",
            original = "Оригінальна якість",
            originalOn = "Оригінальна якість: надсилається без стиснення, у повній роздільності",
            originalOff = "Стандартна якість: менший файл, швидше надсилання",
            tooBig = "Файл завеликий. Максимум 150 МБ.",
            gone = "Цей файл більше недоступний.",
            failed = "Не вдалося завантажити файл.",
            remove = "Прибрати",
            captionHint = "Додати підпис…",
            voiceFailed = "Не вдалося відтворити голосове повідомлення.",
            close = "Закрити"
        )

        val DE = MediaStrings(
            save = "Speichern",
            saveAs = "Speichern unter …",
            saved = { "Gespeichert: $it" },
            open = "Öffnen",
            attach = "Datei anhängen",
            dropHere = "Dateien hier ablegen, um sie zu senden",
            viewOnceGroups = "Dateien zur Einmalansicht funktionieren nur in privaten Chats",
            viewOnceMediaOnly = "Zur Einmalansicht kannst du nur Fotos und Videos senden",
            original = "Originalqualität",
            originalOn = "Originalqualität: wird unkomprimiert in voller Auflösung gesendet",
            originalOff = "Standardqualität: kleinere Datei, schnelleres Senden",
            tooBig = "Die Datei ist zu groß. Maximal 150 MB.",
            gone = "Diese Datei ist nicht mehr verfügbar.",
            failed = "Die Datei konnte nicht geladen werden.",
            remove = "Entfernen",
            captionHint = "Beschriftung hinzufügen …",
            voiceFailed = "Die Sprachnachricht konnte nicht abgespielt werden.",
            close = "Schließen"
        )

        val ZH = MediaStrings(
            save = "儲存",
            saveAs = "另存新檔…",
            saved = { "已儲存：$it" },
            open = "開啟",
            attach = "附加檔案",
            dropHere = "將檔案拖放到這裡即可傳送",
            viewOnceGroups = "單次檢視檔案僅適用於私人聊天",
            viewOnceMediaOnly = "只有相片和影片可以作為單次檢視傳送",
            original = "原始畫質",
            originalOn = "原始畫質：不壓縮，以完整解析度傳送",
            originalOff = "標準畫質：檔案較小，傳送較快",
            tooBig = "檔案太大，上限為 150 MB。",
            gone = "這個檔案已無法取得。",
            failed = "無法下載檔案。",
            remove = "移除",
            captionHint = "新增說明文字…",
            voiceFailed = "無法播放語音訊息。",
            close = "關閉"
        )
    }
}

val LocalMediaStrings = staticCompositionLocalOf { MediaStrings.PL }
