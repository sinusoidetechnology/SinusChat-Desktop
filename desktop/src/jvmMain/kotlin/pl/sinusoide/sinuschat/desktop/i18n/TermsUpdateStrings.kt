package pl.sinusoide.sinuschat.desktop.i18n

class TermsUpdateStrings(
    val title: String,
    val desc: String,
    val firstTitle: String,
    val firstDesc: String,
    val date: (String) -> String,
    val accept: String,
    val decline: String,
    val declined: String,
    val close: String,
    val locale: String
) {
    companion object {
        val PL = TermsUpdateStrings(
            title = "Zaktualizowaliśmy warunki",
            desc = "Zmieniliśmy regulamin, politykę prywatności lub licencję. Żeby dalej korzystać z SinusChat, przeczytaj nowe warunki i potwierdź, że je akceptujesz.",
            firstTitle = "Zanim zaczniemy…",
            firstDesc = "Żeby korzystać z SinusChat, przeczytaj regulamin, politykę prywatności i licencję oraz potwierdź, że je akceptujesz.",
            date = { "Obowiązują od $it" },
            accept = "Akceptuję",
            decline = "Nie akceptuję",
            declined = "Bez akceptacji warunków nie możesz korzystać z SinusChat. Możesz tu wrócić i zaakceptować je w każdej chwili.",
            close = "Zamknij aplikację",
            locale = "pl"
        )

        val EN = TermsUpdateStrings(
            title = "We have updated our terms",
            desc = "We changed the terms of service, the privacy policy or the licence. To keep using SinusChat, read the new terms and confirm that you accept them.",
            firstTitle = "Before we start…",
            firstDesc = "To use SinusChat, read the terms of service, the privacy policy and the licence and confirm that you accept them.",
            date = { "In effect from $it" },
            accept = "I accept",
            decline = "I do not accept",
            declined = "You cannot use SinusChat without accepting the terms. You can come back and accept them at any time.",
            close = "Close the app",
            locale = "en"
        )

        val CS = TermsUpdateStrings(
            title = "Aktualizovali jsme podmínky",
            desc = "Změnili jsme podmínky služby, zásady ochrany soukromí nebo licenci. Abyste mohli SinusChat dál používat, přečtěte si nové podmínky a potvrďte, že je přijímáte.",
            firstTitle = "Než začneme…",
            firstDesc = "Abyste mohli SinusChat používat, přečtěte si podmínky služby, zásady ochrany soukromí a licenci a potvrďte, že je přijímáte.",
            date = { "Platí od $it" },
            accept = "Přijímám",
            decline = "Nepřijímám",
            declined = "Bez přijetí podmínek nemůžete SinusChat používat. Kdykoli se sem můžete vrátit a přijmout je.",
            close = "Zavřít aplikaci",
            locale = "cs"
        )

        val UK = TermsUpdateStrings(
            title = "Ми оновили умови",
            desc = "Ми змінили умови використання, політику конфіденційності або ліцензію. Щоб і далі користуватися SinusChat, прочитайте нові умови та підтвердьте, що приймаєте їх.",
            firstTitle = "Перш ніж почати…",
            firstDesc = "Щоб користуватися SinusChat, прочитайте умови використання, політику конфіденційності та ліцензію і підтвердьте, що приймаєте їх.",
            date = { "Діють з $it" },
            accept = "Приймаю",
            decline = "Не приймаю",
            declined = "Без прийняття умов ви не можете користуватися SinusChat. Ви можете повернутися сюди й прийняти їх будь-коли.",
            close = "Закрити застосунок",
            locale = "uk"
        )

        val DE = TermsUpdateStrings(
            title = "Wir haben unsere Bedingungen aktualisiert",
            desc = "Wir haben die Nutzungsbedingungen, die Datenschutzerklärung oder die Lizenz geändert. Um SinusChat weiter zu nutzen, lies die neuen Bedingungen und bestätige, dass du sie akzeptierst.",
            firstTitle = "Bevor wir loslegen…",
            firstDesc = "Um SinusChat zu nutzen, lies die Nutzungsbedingungen, die Datenschutzerklärung und die Lizenz und bestätige, dass du sie akzeptierst.",
            date = { "Gültig ab $it" },
            accept = "Ich akzeptiere",
            decline = "Ich akzeptiere nicht",
            declined = "Ohne Zustimmung zu den Bedingungen kannst du SinusChat nicht nutzen. Du kannst jederzeit hierher zurückkehren und sie akzeptieren.",
            close = "App schließen",
            locale = "de"
        )

        val ZH = TermsUpdateStrings(
            title = "我們更新了條款",
            desc = "我們變更了服務條款、隱私權政策或授權條款。若要繼續使用 SinusChat，請閱讀新條款並確認你接受。",
            firstTitle = "開始之前…",
            firstDesc = "若要使用 SinusChat，請閱讀服務條款、隱私權政策和授權條款，並確認你接受。",
            date = { "自 $it 起生效" },
            accept = "我接受",
            decline = "我不接受",
            declined = "不接受條款就無法使用 SinusChat。你可以隨時回到這裡接受條款。",
            close = "關閉應用程式",
            locale = "zh-TW"
        )

        fun of(chat: ChatStrings): TermsUpdateStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
