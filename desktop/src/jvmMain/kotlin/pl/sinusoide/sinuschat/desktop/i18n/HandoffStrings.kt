package pl.sinusoide.sinuschat.desktop.i18n

class HandoffStrings(
    val account: String,
    val privacy: String,
    val internet: String,
    val backup: String,
    val report: String,
    val hint: String,
    val offline: String,
    val privacyRow: String,
    val notifyLocal: String
) {
    companion object {
        val PL = HandoffStrings(
            account = "Aby zmienić ustawienia konta, zajrzyj do telefonu",
            privacy = "Aby zmienić ustawienia prywatności konta, zajrzyj do telefonu",
            internet = "Aby zmienić ustawienia internetu, zajrzyj do telefonu",
            backup = "Aby zrobić lub przywrócić kopię zapasową, zajrzyj do telefonu",
            report = "Aby zgłosić problem z aplikacją na komputer, zajrzyj do telefonu",
            hint = "Gdy otworzysz SinusChat na telefonie, od razu przeniesie Cię do tej sekcji.",
            offline = "Połącz telefon z komputerem, żeby przejść do tej sekcji na telefonie.",
            privacyRow = "Zmień ustawienia prywatności konta",
            notifyLocal = "Te ustawienia dotyczą tylko tego komputera i nie synchronizują się z telefonem."
        )

        val EN = HandoffStrings(
            account = "To change your account settings, check your phone",
            privacy = "To change your account privacy settings, check your phone",
            internet = "To change your internet settings, check your phone",
            backup = "To make or restore a backup, check your phone",
            report = "To report a problem with the computer app, check your phone",
            hint = "When you open SinusChat on your phone, it will take you straight to this section.",
            offline = "Connect your phone to this computer to open this section on the phone.",
            privacyRow = "Change account privacy settings",
            notifyLocal = "These settings apply only to this computer and don't sync with your phone."
        )

        val CS = HandoffStrings(
            account = "Nastavení účtu změníte v telefonu",
            privacy = "Nastavení soukromí účtu změníte v telefonu",
            internet = "Nastavení internetu změníte v telefonu",
            backup = "Zálohu vytvoříte nebo obnovíte v telefonu",
            report = "Problém s aplikací pro počítač nahlásíte v telefonu",
            hint = "Až v telefonu otevřete SinusChat, rovnou vás přenese do této sekce.",
            offline = "Připojte telefon k počítači, abyste tuto sekci otevřeli v telefonu.",
            privacyRow = "Změnit nastavení soukromí účtu",
            notifyLocal = "Tato nastavení platí jen pro tento počítač a nesynchronizují se s telefonem."
        )

        val UK = HandoffStrings(
            account = "Щоб змінити налаштування облікового запису, перейдіть до телефона",
            privacy = "Щоб змінити налаштування приватності облікового запису, перейдіть до телефона",
            internet = "Щоб змінити налаштування інтернету, перейдіть до телефона",
            backup = "Щоб створити або відновити резервну копію, перейдіть до телефона",
            report = "Щоб повідомити про проблему з програмою для комп’ютера, перейдіть до телефона",
            hint = "Коли ви відкриєте SinusChat на телефоні, він одразу перенесе вас до цього розділу.",
            offline = "Підключіть телефон до комп’ютера, щоб відкрити цей розділ на телефоні.",
            privacyRow = "Змінити налаштування приватності облікового запису",
            notifyLocal = "Ці налаштування стосуються лише цього комп’ютера й не синхронізуються з телефоном."
        )

        val DE = HandoffStrings(
            account = "Kontoeinstellungen änderst du auf deinem Handy",
            privacy = "Datenschutzeinstellungen des Kontos änderst du auf deinem Handy",
            internet = "Interneteinstellungen änderst du auf deinem Handy",
            backup = "Backups erstellst oder stellst du auf deinem Handy wieder her",
            report = "Probleme mit der Computer-App meldest du auf deinem Handy",
            hint = "Wenn du SinusChat auf dem Handy öffnest, landest du direkt in diesem Bereich.",
            offline = "Verbinde dein Handy mit dem Computer, um diesen Bereich auf dem Handy zu öffnen.",
            privacyRow = "Datenschutzeinstellungen des Kontos ändern",
            notifyLocal = "Diese Einstellungen gelten nur für diesen Computer und werden nicht mit dem Handy synchronisiert."
        )

        val ZH = HandoffStrings(
            account = "要變更帳號設定，請查看你的手機",
            privacy = "要變更帳號的隱私設定，請查看你的手機",
            internet = "要變更網路設定，請查看你的手機",
            backup = "要建立或還原備份，請查看你的手機",
            report = "要回報電腦版應用程式的問題，請查看你的手機",
            hint = "在手機上開啟 SinusChat 時，會直接帶你前往這個區塊。",
            offline = "請將手機連接到這台電腦，才能在手機上開啟這個區塊。",
            privacyRow = "變更帳號隱私設定",
            notifyLocal = "這些設定只適用於這台電腦，不會與手機同步。"
        )

        fun of(chat: ChatStrings): HandoffStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
