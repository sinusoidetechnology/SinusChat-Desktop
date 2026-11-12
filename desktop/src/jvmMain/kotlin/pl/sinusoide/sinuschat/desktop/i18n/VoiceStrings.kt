package pl.sinusoide.sinuschat.desktop.i18n

class VoiceStrings(
    val record: String,
    val hint: String,
    val locked: String,
    val sending: String,
    val tooShort: String,
    val cantRecord: String,
    val micBlocked: String,
    val sendFailed: String,
    val limit: String
) {
    companion object {
        val PL = VoiceStrings(
            record = "Wiadomość głosowa",
            hint = "Kliknij, aby nagrywać, albo przytrzymaj i puść, aby od razu wysłać",
            locked = "Nagrywanie zablokowane",
            sending = "Wysyłanie wiadomości głosowej…",
            tooShort = "Za krótkie nagranie",
            cantRecord = "Nie można nagrać",
            micBlocked = "Mikrofon nic nie nagrał. Sprawdź, czy Windows pozwala aplikacjom używać mikrofonu.",
            sendFailed = "Błąd wysyłania głosowej",
            limit = "Osiągnięto maksymalną długość nagrania"
        )

        val EN = VoiceStrings(
            record = "Voice message",
            hint = "Click to record, or hold and release to send right away",
            locked = "Recording locked",
            sending = "Sending voice message…",
            tooShort = "Recording too short",
            cantRecord = "Can't record",
            micBlocked = "The microphone recorded nothing. Check that Windows lets apps use the microphone.",
            sendFailed = "Error sending voice message",
            limit = "Maximum recording length reached"
        )

        val CS = VoiceStrings(
            record = "Hlasová zpráva",
            hint = "Klikněte pro nahrávání, nebo podržte a pusťte pro okamžité odeslání",
            locked = "Nahrávání zamčeno",
            sending = "Odesílání hlasové zprávy…",
            tooShort = "Příliš krátká nahrávka",
            cantRecord = "Nelze nahrávat",
            micBlocked = "Mikrofon nic nenahrál. Zkontrolujte, zda Windows aplikacím povoluje mikrofon.",
            sendFailed = "Chyba odesílání hlasové zprávy",
            limit = "Dosažena maximální délka nahrávky"
        )

        val UK = VoiceStrings(
            record = "Голосове повідомлення",
            hint = "Клацніть, щоб записувати, або утримуйте й відпустіть, щоб одразу надіслати",
            locked = "Запис зафіксовано",
            sending = "Надсилання голосового повідомлення…",
            tooShort = "Запис надто короткий",
            cantRecord = "Не вдається записати",
            micBlocked = "Мікрофон нічого не записав. Перевірте, чи Windows дозволяє програмам використовувати мікрофон.",
            sendFailed = "Помилка надсилання голосового",
            limit = "Досягнуто максимальної тривалості запису"
        )

        val DE = VoiceStrings(
            record = "Sprachnachricht",
            hint = "Klicke zum Aufnehmen oder halte gedrückt und lass los, um sofort zu senden",
            locked = "Aufnahme gesperrt",
            sending = "Sprachnachricht wird gesendet…",
            tooShort = "Aufnahme zu kurz",
            cantRecord = "Aufnahme nicht möglich",
            micBlocked = "Das Mikrofon hat nichts aufgenommen. Prüfe, ob Windows Apps den Zugriff auf das Mikrofon erlaubt.",
            sendFailed = "Fehler beim Senden der Sprachnachricht",
            limit = "Maximale Aufnahmelänge erreicht"
        )

        val ZH = VoiceStrings(
            record = "語音訊息",
            hint = "按一下開始錄音，或按住後放開立即傳送",
            locked = "錄音已鎖定",
            sending = "正在傳送語音訊息…",
            tooShort = "錄音太短",
            cantRecord = "無法錄音",
            micBlocked = "麥克風沒有錄到任何聲音。請確認 Windows 允許應用程式使用麥克風。",
            sendFailed = "傳送語音訊息時發生錯誤",
            limit = "已達到錄音長度上限"
        )

        fun of(chat: ChatStrings): VoiceStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
