package pl.sinusoide.sinuschat.desktop.i18n

class RunStrings(
    val section: String,
    val autostart: String,
    val autostartDesc: String,
    val autostartUnavailable: String,
    val background: String,
    val backgroundDesc: String,
    val trayOpen: String,
    val trayQuit: String,
    val hintTitle: String,
    val hintText: String
) {
    companion object {
        val PL = RunStrings(
            section = "Uruchamianie i działanie w tle",
            autostart = "Uruchamiaj razem z Windowsem",
            autostartDesc = "Po zalogowaniu SinusChat włączy się sam i poczeka w zasobniku obok zegara.",
            autostartUnavailable = "Dostępne w aplikacji zainstalowanej instalatorem.",
            background = "Działaj w tle po zamknięciu okna",
            backgroundDesc = "Po kliknięciu X SinusChat dalej odbiera wiadomości i połączenia. Całkiem zamkniesz go z ikony w zasobniku.",
            trayOpen = "Otwórz SinusChat",
            trayQuit = "Zamknij SinusChat",
            hintTitle = "SinusChat działa w tle",
            hintText = "Znajdziesz go w zasobniku obok zegara. Stamtąd możesz go też całkiem zamknąć."
        )

        val EN = RunStrings(
            section = "Startup and background",
            autostart = "Start with Windows",
            autostartDesc = "SinusChat starts by itself when you sign in and waits in the tray next to the clock.",
            autostartUnavailable = "Available in the app installed with the installer.",
            background = "Keep running after closing the window",
            backgroundDesc = "After you click X, SinusChat keeps receiving messages and calls. Quit it completely from the tray icon.",
            trayOpen = "Open SinusChat",
            trayQuit = "Quit SinusChat",
            hintTitle = "SinusChat is running in the background",
            hintText = "You'll find it in the tray next to the clock. You can also quit it completely from there."
        )

        val CS = RunStrings(
            section = "Spouštění a běh na pozadí",
            autostart = "Spouštět s Windows",
            autostartDesc = "Po přihlášení se SinusChat spustí sám a počká v oznamovací oblasti vedle hodin.",
            autostartUnavailable = "Dostupné v aplikaci nainstalované instalátorem.",
            background = "Běžet na pozadí po zavření okna",
            backgroundDesc = "Po kliknutí na X bude SinusChat dál přijímat zprávy a hovory. Úplně ho ukončíte z ikony v oznamovací oblasti.",
            trayOpen = "Otevřít SinusChat",
            trayQuit = "Ukončit SinusChat",
            hintTitle = "SinusChat běží na pozadí",
            hintText = "Najdete ho v oznamovací oblasti vedle hodin. Odtud ho můžete také úplně ukončit."
        )

        val UK = RunStrings(
            section = "Запуск і робота у фоні",
            autostart = "Запускати разом із Windows",
            autostartDesc = "Після входу SinusChat запуститься сам і чекатиме в області сповіщень біля годинника.",
            autostartUnavailable = "Доступно в застосунку, встановленому інсталятором.",
            background = "Працювати у фоні після закриття вікна",
            backgroundDesc = "Після натискання X SinusChat і далі прийматиме повідомлення та дзвінки. Повністю закрити його можна з піктограми в області сповіщень.",
            trayOpen = "Відкрити SinusChat",
            trayQuit = "Закрити SinusChat",
            hintTitle = "SinusChat працює у фоні",
            hintText = "Його можна знайти в області сповіщень біля годинника. Звідти його також можна повністю закрити."
        )

        val DE = RunStrings(
            section = "Autostart und Hintergrund",
            autostart = "Mit Windows starten",
            autostartDesc = "SinusChat startet nach der Anmeldung von selbst und wartet im Infobereich neben der Uhr.",
            autostartUnavailable = "Verfügbar in der mit dem Installationsprogramm installierten App.",
            background = "Nach dem Schließen im Hintergrund weiterlaufen",
            backgroundDesc = "Nach einem Klick auf X empfängt SinusChat weiter Nachrichten und Anrufe. Ganz beenden kannst du es über das Symbol im Infobereich.",
            trayOpen = "SinusChat öffnen",
            trayQuit = "SinusChat beenden",
            hintTitle = "SinusChat läuft im Hintergrund",
            hintText = "Du findest es im Infobereich neben der Uhr. Dort kannst du es auch ganz beenden."
        )

        val ZH = RunStrings(
            section = "啟動與背景執行",
            autostart = "隨 Windows 啟動",
            autostartDesc = "登入後 SinusChat 會自動啟動，並在時鐘旁的系統匣中待命。",
            autostartUnavailable = "僅適用於透過安裝程式安裝的應用程式。",
            background = "關閉視窗後繼續在背景執行",
            backgroundDesc = "按下 X 後，SinusChat 仍會繼續接收訊息與通話。要完全結束，請使用系統匣圖示。",
            trayOpen = "開啟 SinusChat",
            trayQuit = "結束 SinusChat",
            hintTitle = "SinusChat 正在背景執行",
            hintText = "你可以在時鐘旁的系統匣中找到它，也可以從那裡完全結束。"
        )

        fun of(chat: ChatStrings): RunStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
