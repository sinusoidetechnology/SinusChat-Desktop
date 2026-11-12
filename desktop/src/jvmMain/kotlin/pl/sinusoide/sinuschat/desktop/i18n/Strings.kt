package pl.sinusoide.sinuschat.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    PL("pl", "Polski"),
    EN("en", "English"),
    CS("cs", "Čeština"),
    UK("uk", "Українська"),
    DE("de", "Deutsch"),
    ZH("zh-TW", "繁體中文");

    val strings: Strings
        get() = when (this) {
            PL -> Strings.PL
            EN -> Strings.EN
            CS -> Strings.CS
            UK -> Strings.UK
            DE -> Strings.DE
            ZH -> Strings.ZH
        }

    val chat: ChatStrings
        get() = when (this) {
            PL -> ChatStrings.PL
            EN -> ChatStrings.EN
            CS -> ChatStrings.CS
            UK -> ChatStrings.UK
            DE -> ChatStrings.DE
            ZH -> ChatStrings.ZH
        }

    val conn: ConnStrings
        get() = when (this) {
            PL -> ConnStrings.PL
            EN -> ConnStrings.EN
            CS -> ConnStrings.CS
            UK -> ConnStrings.UK
            DE -> ConnStrings.DE
            ZH -> ConnStrings.ZH
        }

    val media: MediaStrings
        get() = when (this) {
            PL -> MediaStrings.PL
            EN -> MediaStrings.EN
            CS -> MediaStrings.CS
            UK -> MediaStrings.UK
            DE -> MediaStrings.DE
            ZH -> MediaStrings.ZH
        }

    val settings: SettingsStrings
        get() = when (this) {
            PL -> SettingsStrings.PL
            EN -> SettingsStrings.EN
            CS -> SettingsStrings.CS
            UK -> SettingsStrings.UK
            DE -> SettingsStrings.DE
            ZH -> SettingsStrings.ZH
        }

    val locale: java.util.Locale get() = java.util.Locale.forLanguageTag(code)

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        fun system(): AppLanguage {
            val lang = Locale.getDefault(Locale.Category.DISPLAY).language
            return if (lang == "zh") ZH else fromCode(lang) ?: EN
        }

        fun effective(chosen: String?): AppLanguage = fromCode(chosen) ?: system()
    }
}

class Strings(
    val welcomeGo: String,
    val languageTitle: String,
    val languageSub: String,
    val next: String,
    val back: String,
    val pairTitle: String,
    val pairSub: String,
    val pairStep1: String,
    val pairStep2: String,
    val pairStep3: String,
    val pairWifi: String,
    val pairSearching: String,
    val pairNearby: (Int) -> String,
    val doneTitle: String,
    val doneSub: (String) -> String,
    val doneGo: String,
    val searching: (String) -> String,
    val searchingHint: String,
    val otherPhone: String,
    val rejectedTitle: String,
    val rejectedHint: String,
    val reconnect: String,
    val connected: (String) -> String,
    val unpair: String,
    val noticeUnpaired: String,
    val ok: String
) {
    companion object {
        val PL = Strings(
            welcomeGo = "Zaczynamy",
            languageTitle = "Język aplikacji",
            languageSub = "Możesz to zmienić w każdej chwili w Ustawieniach.",
            next = "Dalej",
            back = "Wstecz",
            pairTitle = "Połącz z telefonem",
            pairSub = "Telefon pozostaje głównym urządzeniem. Komputer pokazuje Twoje rozmowy i wysyła wiadomości przez telefon.",
            pairStep1 = "Otwórz SinusChat na telefonie.",
            pairStep2 = "Wejdź w Ustawienia, a potem Połączone urządzenia.",
            pairStep3 = "Stuknij Połącz urządzenie i zeskanuj ten kod.",
            pairWifi = "Telefon i komputer muszą być w tej samej sieci Wi-Fi.",
            pairSearching = "Szukam telefonu w sieci…",
            pairNearby = { "Telefony z SinusChat w sieci: $it. Czekam na zeskanowanie kodu…" },
            doneTitle = "Wszystko gotowe",
            doneSub = { if (it.isBlank()) "Komputer jest połączony z telefonem. Możesz pisać." else "Komputer jest połączony z telefonem $it. Możesz pisać." },
            doneGo = "Zaczynamy",
            searching = { "Łączę z telefonem $it…" },
            searchingHint = "Sprawdź, czy telefon jest w tej samej sieci Wi-Fi i czy SinusChat na nim działa.",
            otherPhone = "Połącz z innym telefonem",
            rejectedTitle = "Telefon nie rozpoznaje tego komputera",
            rejectedHint = "Komputer mógł zostać odłączony w telefonie. Połącz go ponownie, skanując nowy kod.",
            reconnect = "Połącz ponownie",
            connected = { "Połączono z telefonem $it" },
            unpair = "Odłącz komputer",
            noticeUnpaired = "Telefon odłączył ten komputer. Zeskanuj kod ponownie, aby go połączyć.",
            ok = "OK"
        )

        val EN = Strings(
            welcomeGo = "Get started",
            languageTitle = "App language",
            languageSub = "You can change this any time in Settings.",
            next = "Next",
            back = "Back",
            pairTitle = "Connect your phone",
            pairSub = "Your phone stays the main device. The computer shows your chats and sends messages through the phone.",
            pairStep1 = "Open SinusChat on your phone.",
            pairStep2 = "Go to Settings, then Linked devices.",
            pairStep3 = "Tap Link a device and scan this code.",
            pairWifi = "The phone and the computer must be on the same Wi-Fi network.",
            pairSearching = "Looking for your phone on the network…",
            pairNearby = { "SinusChat phones on the network: $it. Waiting for the code to be scanned…" },
            doneTitle = "All set",
            doneSub = { if (it.isBlank()) "The computer is linked to your phone. You can start chatting." else "The computer is linked to $it. You can start chatting." },
            doneGo = "Let's go",
            searching = { "Connecting to $it…" },
            searchingHint = "Check that the phone is on the same Wi-Fi network and that SinusChat is running on it.",
            otherPhone = "Connect a different phone",
            rejectedTitle = "The phone doesn't recognise this computer",
            rejectedHint = "The computer may have been unlinked on the phone. Link it again by scanning a new code.",
            reconnect = "Link again",
            connected = { "Connected to $it" },
            unpair = "Unlink computer",
            noticeUnpaired = "The phone unlinked this computer. Scan the code again to link it.",
            ok = "OK"
        )

        val CS = Strings(
            welcomeGo = "Začínáme",
            languageTitle = "Jazyk aplikace",
            languageSub = "Můžete to kdykoli změnit v Nastavení.",
            next = "Další",
            back = "Zpět",
            pairTitle = "Propojte telefon",
            pairSub = "Telefon zůstává hlavním zařízením. Počítač zobrazuje vaše konverzace a odesílá zprávy přes telefon.",
            pairStep1 = "Otevřete SinusChat v telefonu.",
            pairStep2 = "Přejděte do Nastavení a pak do Propojená zařízení.",
            pairStep3 = "Klepněte na Propojit zařízení a naskenujte tento kód.",
            pairWifi = "Telefon a počítač musí být ve stejné síti Wi-Fi.",
            pairSearching = "Hledám telefon v síti…",
            pairNearby = { "Telefony se SinusChat v síti: $it. Čekám na naskenování kódu…" },
            doneTitle = "Vše je připraveno",
            doneSub = { if (it.isBlank()) "Počítač je propojen s telefonem. Můžete psát." else "Počítač je propojen s telefonem $it. Můžete psát." },
            doneGo = "Začínáme",
            searching = { "Připojuji se k telefonu $it…" },
            searchingHint = "Zkontrolujte, zda je telefon ve stejné síti Wi-Fi a zda v něm běží SinusChat.",
            otherPhone = "Propojit jiný telefon",
            rejectedTitle = "Telefon tento počítač nezná",
            rejectedHint = "Počítač mohl být v telefonu odpojen. Propojte ho znovu naskenováním nového kódu.",
            reconnect = "Propojit znovu",
            connected = { "Připojeno k telefonu $it" },
            unpair = "Odpojit počítač",
            noticeUnpaired = "Telefon odpojil tento počítač. Pro opětovné propojení naskenujte kód znovu.",
            ok = "OK"
        )

        val UK = Strings(
            welcomeGo = "Почнімо",
            languageTitle = "Мова застосунку",
            languageSub = "Це можна будь-коли змінити в Налаштуваннях.",
            next = "Далі",
            back = "Назад",
            pairTitle = "Під'єднайте телефон",
            pairSub = "Телефон залишається основним пристроєм. Комп'ютер показує ваші чати й надсилає повідомлення через телефон.",
            pairStep1 = "Відкрийте SinusChat на телефоні.",
            pairStep2 = "Перейдіть у Налаштування, а потім у Пов’язані пристрої.",
            pairStep3 = "Торкніться Під’єднати пристрій і скануйте цей код.",
            pairWifi = "Телефон і комп'ютер мають бути в тій самій мережі Wi-Fi.",
            pairSearching = "Шукаю телефон у мережі…",
            pairNearby = { "Телефони із SinusChat у мережі: $it. Чекаю на сканування коду…" },
            doneTitle = "Усе готово",
            doneSub = { if (it.isBlank()) "Комп'ютер під'єднано до телефону. Можете писати." else "Комп'ютер під'єднано до телефону $it. Можете писати." },
            doneGo = "Почнімо",
            searching = { "Під'єднуюся до телефону $it…" },
            searchingHint = "Перевірте, чи телефон у тій самій мережі Wi-Fi і чи працює на ньому SinusChat.",
            otherPhone = "Під'єднати інший телефон",
            rejectedTitle = "Телефон не розпізнає цей комп'ютер",
            rejectedHint = "Можливо, комп'ютер від'єднали на телефоні. Під'єднайте його знову, сканувавши новий код.",
            reconnect = "Під'єднати знову",
            connected = { "Під'єднано до телефону $it" },
            unpair = "Від'єднати комп'ютер",
            noticeUnpaired = "Телефон від'єднав цей комп'ютер. Скануйте код ще раз, щоб під'єднати його.",
            ok = "OK"
        )

        val DE = Strings(
            welcomeGo = "Los geht’s",
            languageTitle = "App-Sprache",
            languageSub = "Du kannst das jederzeit in den Einstellungen ändern.",
            next = "Weiter",
            back = "Zurück",
            pairTitle = "Handy verbinden",
            pairSub = "Dein Handy bleibt das Hauptgerät. Der Computer zeigt deine Chats und sendet Nachrichten über das Handy.",
            pairStep1 = "Öffne SinusChat auf dem Handy.",
            pairStep2 = "Gehe zu Einstellungen und dann zu Verknüpfte Geräte.",
            pairStep3 = "Tippe auf Gerät verbinden und scanne diesen Code.",
            pairWifi = "Handy und Computer müssen im selben WLAN sein.",
            pairSearching = "Suche das Handy im Netzwerk…",
            pairNearby = { "Handys mit SinusChat im Netzwerk: $it. Warte auf das Scannen des Codes…" },
            doneTitle = "Alles bereit",
            doneSub = { if (it.isBlank()) "Der Computer ist mit deinem Handy verbunden. Du kannst loslegen." else "Der Computer ist mit $it verbunden. Du kannst loslegen." },
            doneGo = "Los geht’s",
            searching = { "Verbinde mit $it…" },
            searchingHint = "Prüfe, ob das Handy im selben WLAN ist und SinusChat darauf läuft.",
            otherPhone = "Anderes Handy verbinden",
            rejectedTitle = "Das Handy erkennt diesen Computer nicht",
            rejectedHint = "Der Computer wurde vielleicht auf dem Handy getrennt. Verbinde ihn erneut, indem du einen neuen Code scannst.",
            reconnect = "Erneut verbinden",
            connected = { "Verbunden mit $it" },
            unpair = "Computer trennen",
            noticeUnpaired = "Das Handy hat diesen Computer getrennt. Scanne den Code erneut, um ihn zu verbinden.",
            ok = "OK"
        )

        val ZH = Strings(
            welcomeGo = "開始使用",
            languageTitle = "應用程式語言",
            languageSub = "你可以隨時在設定中變更。",
            next = "下一步",
            back = "返回",
            pairTitle = "連結你的手機",
            pairSub = "手機仍是主要裝置。電腦會顯示你的聊天，並透過手機傳送訊息。",
            pairStep1 = "在手機上開啟 SinusChat。",
            pairStep2 = "前往設定，再點選已連結的裝置。",
            pairStep3 = "點選連結裝置，然後掃描這個條碼。",
            pairWifi = "手機和電腦必須連上同一個 Wi-Fi 網路。",
            pairSearching = "正在網路上尋找你的手機…",
            pairNearby = { "網路上有 $it 支執行 SinusChat 的手機。正在等待掃描條碼…" },
            doneTitle = "一切就緒",
            doneSub = { if (it.isBlank()) "電腦已連結到你的手機，可以開始聊天了。" else "電腦已連結到 $it，可以開始聊天了。" },
            doneGo = "開始吧",
            searching = { "正在連線到 $it…" },
            searchingHint = "請確認手機連上同一個 Wi-Fi 網路，而且手機上的 SinusChat 正在執行。",
            otherPhone = "連結其他手機",
            rejectedTitle = "手機無法辨識這台電腦",
            rejectedHint = "這台電腦可能已在手機上取消連結。請掃描新的條碼重新連結。",
            reconnect = "重新連結",
            connected = { "已連線到 $it" },
            unpair = "取消連結電腦",
            noticeUnpaired = "手機已取消連結這台電腦。請再次掃描條碼以重新連結。",
            ok = "確定"
        )
    }
}

val LocalStrings = staticCompositionLocalOf { Strings.PL }
