package pl.sinusoide.sinuschat.desktop.i18n

class SafeStrings(
    val linkTitle: (String) -> String,
    val linkMsg: String,
    val linkAction: String,
    val openFileTitle: (String) -> String,
    val openFileMsg: String,
    val openFileAction: String,
    val saveFileTitle: (String) -> String,
    val saveFileMsg: String,
    val saveFileAction: String,
    val whyTitle: String,
    val whyMsg: String,
    val thisFile: String,
    val section: String,
    val toggle: String,
    val toggleDesc: String
) {
    companion object {
        val PL = SafeStrings(
            linkTitle = { "SinusChat chce otworzyć stronę $it" },
            linkMsg = "Po kliknięciu przekieruje Cię do tej strony. Sinusoide nie chroni Cię po wyjściu poza SinusChat. Czy na pewno chcesz tam wejść?",
            linkAction = "Przejdź",
            openFileTitle = { "SinusChat chce otworzyć plik $it w innej aplikacji" },
            openFileMsg = "Plik wyjdzie poza SinusChat. Sinusoide nie chroni go w innej aplikacji, a ona może go odczytać, zapisać albo wysłać dalej. Otwieraj tylko pliki od osób, którym ufasz. Czy chcesz go otworzyć?",
            openFileAction = "Otwórz",
            saveFileTitle = { "SinusChat chce zapisać $it na tym komputerze" },
            saveFileMsg = "Plik trafi poza zaszyfrowane miejsce SinusChat, gdzie mogą go odczytać inne programy na tym komputerze. Sinusoide nie chroni go po zapisaniu. Czy chcesz go zapisać?",
            saveFileAction = "Zapisz",
            whyTitle = "Dlaczego widzę to powiadomienie?",
            whyMsg = "Możliwe, że to potencjalne zagrożenie, lecz nie musi nim być. Sinusoide chce, aby SinusChat było bezpieczne, dlatego informuje Cię o sytuacji, która ma się zdarzyć. Zachowaj szczególną ostrożność, widząc to powiadomienie. Możesz je wyłączyć w Ustawienia → Prywatność → Informuj o potencjalnym zagrożeniu.",
            thisFile = "ten plik",
            section = "Bezpieczeństwo",
            toggle = "Informuj o potencjalnym zagrożeniu",
            toggleDesc = "Ostrzeżenie, zanim SinusChat otworzy coś poza aplikacją albo zapisze plik na komputerze"
        )
        val EN = SafeStrings(
            linkTitle = { "SinusChat wants to open $it" },
            linkMsg = "You'll be taken to this website. Sinusoide can't protect you once you leave SinusChat. Are you sure you want to go there?",
            linkAction = "Open",
            openFileTitle = { "SinusChat wants to open $it in another app" },
            openFileMsg = "The file will leave SinusChat. Sinusoide can't protect it in another app, which may read, save or send it on. Only open files from people you trust. Do you want to open it?",
            openFileAction = "Open",
            saveFileTitle = { "SinusChat wants to save $it to this computer" },
            saveFileMsg = "The file will leave SinusChat's encrypted storage, where other programs on this computer can read it. Sinusoide can't protect it once saved. Do you want to save it?",
            saveFileAction = "Save",
            whyTitle = "Why am I seeing this?",
            whyMsg = "This may be a potential risk, but it doesn't have to be. Sinusoide wants SinusChat to be safe, so it tells you about what's about to happen. Take extra care when you see this message. You can turn it off in Settings → Privacy → Warn about potential risks.",
            thisFile = "this file",
            section = "Safety",
            toggle = "Warn about potential risks",
            toggleDesc = "A warning before SinusChat opens something outside the app or saves a file to this computer"
        )
        val CS = SafeStrings(
            linkTitle = { "SinusChat chce otevřít stránku $it" },
            linkMsg = "Po kliknutí budete přesměrováni na tuto stránku. Sinusoide vás nechrání, jakmile opustíte SinusChat. Opravdu tam chcete přejít?",
            linkAction = "Přejít",
            openFileTitle = { "SinusChat chce otevřít soubor $it v jiné aplikaci" },
            openFileMsg = "Soubor opustí SinusChat. Sinusoide ho v jiné aplikaci nechrání a ta ho může přečíst, uložit nebo poslat dál. Otevírejte jen soubory od lidí, kterým důvěřujete. Chcete ho otevřít?",
            openFileAction = "Otevřít",
            saveFileTitle = { "SinusChat chce uložit $it do tohoto počítače" },
            saveFileMsg = "Soubor se dostane mimo šifrované úložiště SinusChat, kde ho mohou číst jiné programy v tomto počítači. Sinusoide ho po uložení nechrání. Chcete ho uložit?",
            saveFileAction = "Uložit",
            whyTitle = "Proč vidím toto upozornění?",
            whyMsg = "Může jít o potenciální riziko, ale nemusí. Sinusoide chce, aby byl SinusChat bezpečný, a proto vás upozorňuje na to, co se chystá. Při tomto upozornění buďte obzvlášť opatrní. Vypnout ho můžete v Nastavení → Soukromí → Upozorňovat na možná rizika.",
            thisFile = "tento soubor",
            section = "Bezpečnost",
            toggle = "Upozorňovat na možná rizika",
            toggleDesc = "Varování dřív, než SinusChat otevře něco mimo aplikaci nebo uloží soubor do počítače"
        )
        val UK = SafeStrings(
            linkTitle = { "SinusChat хоче відкрити сайт $it" },
            linkMsg = "Після натискання вас буде перенаправлено на цей сайт. Sinusoide не захищає вас поза SinusChat. Ви справді хочете туди перейти?",
            linkAction = "Перейти",
            openFileTitle = { "SinusChat хоче відкрити файл $it в іншому застосунку" },
            openFileMsg = "Файл вийде за межі SinusChat. Sinusoide не захищає його в іншому застосунку, а той може його прочитати, зберегти чи переслати далі. Відкривайте лише файли від людей, яким довіряєте. Відкрити його?",
            openFileAction = "Відкрити",
            saveFileTitle = { "SinusChat хоче зберегти $it на цьому комп’ютері" },
            saveFileMsg = "Файл потрапить за межі зашифрованого сховища SinusChat, де його можуть прочитати інші програми на цьому комп’ютері. Sinusoide не захищає його після збереження. Зберегти його?",
            saveFileAction = "Зберегти",
            whyTitle = "Чому я бачу це сповіщення?",
            whyMsg = "Можливо, це потенційна загроза, але не обов’язково. Sinusoide хоче, щоб SinusChat був безпечним, тому повідомляє вас про те, що має статися. Будьте особливо обережні, коли бачите це сповіщення. Вимкнути його можна в Налаштування → Конфіденційність → Попереджати про потенційну загрозу.",
            thisFile = "цей файл",
            section = "Безпека",
            toggle = "Попереджати про потенційну загрозу",
            toggleDesc = "Попередження перед тим, як SinusChat відкриє щось поза застосунком або збереже файл на комп’ютері"
        )
        val DE = SafeStrings(
            linkTitle = { "SinusChat möchte die Seite $it öffnen" },
            linkMsg = "Du wirst zu dieser Website weitergeleitet. Sinusoide kann dich nicht schützen, sobald du SinusChat verlässt. Möchtest du die Seite wirklich öffnen?",
            linkAction = "Öffnen",
            openFileTitle = { "SinusChat möchte $it in einer anderen App öffnen" },
            openFileMsg = "Die Datei verlässt SinusChat. Sinusoide kann sie in einer anderen App nicht schützen, und diese App kann sie lesen, speichern oder weiterschicken. Öffne nur Dateien von Personen, denen du vertraust. Möchtest du sie öffnen?",
            openFileAction = "Öffnen",
            saveFileTitle = { "SinusChat möchte $it auf diesem Computer speichern" },
            saveFileMsg = "Die Datei verlässt den verschlüsselten Bereich von SinusChat, wo andere Programme auf diesem Computer sie lesen können. Sinusoide kann sie nach dem Speichern nicht schützen. Möchtest du sie speichern?",
            saveFileAction = "Speichern",
            whyTitle = "Warum sehe ich diesen Hinweis?",
            whyMsg = "Das kann ein mögliches Risiko sein, muss es aber nicht. Sinusoide möchte, dass SinusChat sicher ist, und informiert dich deshalb über das, was gleich passiert. Sei besonders vorsichtig, wenn du diesen Hinweis siehst. Du kannst ihn unter Einstellungen → Datenschutz → Vor möglichen Risiken warnen ausschalten.",
            thisFile = "diese Datei",
            section = "Sicherheit",
            toggle = "Vor möglichen Risiken warnen",
            toggleDesc = "Ein Hinweis, bevor SinusChat etwas außerhalb der App öffnet oder eine Datei auf dem Computer speichert"
        )
        val ZH = SafeStrings(
            linkTitle = { "SinusChat 想要開啟 $it" },
            linkMsg = "你將前往此網站。離開 SinusChat 後，Sinusoide 無法保護你。確定要前往嗎？",
            linkAction = "開啟",
            openFileTitle = { "SinusChat 想要在其他應用程式中開啟 $it" },
            openFileMsg = "檔案將離開 SinusChat。在其他應用程式中，Sinusoide 無法保護它，對方可能會讀取、儲存或轉傳。只開啟你信任的人傳來的檔案。要開啟嗎？",
            openFileAction = "開啟",
            saveFileTitle = { "SinusChat 想要將 $it 儲存到這部電腦" },
            saveFileMsg = "檔案將離開 SinusChat 的加密儲存空間，這部電腦上的其他程式都能讀取。儲存後 Sinusoide 無法保護它。要儲存嗎？",
            saveFileAction = "儲存",
            whyTitle = "為什麼會看到這個？",
            whyMsg = "這可能有潛在風險，但不一定。Sinusoide 希望 SinusChat 安全無虞，所以會告訴你即將發生的事。看到這則訊息時請格外小心。你可以在「設定」→「隱私」→「提醒潛在風險」中關閉它。",
            thisFile = "此檔案",
            section = "安全",
            toggle = "提醒潛在風險",
            toggleDesc = "在 SinusChat 開啟應用程式以外的內容或將檔案儲存到電腦之前發出提醒"
        )

        fun of(chat: ChatStrings): SafeStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
