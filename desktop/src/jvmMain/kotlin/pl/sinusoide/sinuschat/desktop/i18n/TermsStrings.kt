package pl.sinusoide.sinuschat.desktop.i18n

class TermsStrings(
    val title: String,
    val sub: String,
    val agree: String,
    val docs: (String) -> String,
    val docsLink: String
) {
    companion object {
        val PL = TermsStrings(
            title = "Zanim zaczniemy…",
            sub = "Musisz potwierdzić, że zapoznałeś się z regulaminem, polityką prywatności i licencją oraz że się na nie zgadzasz.",
            agree = "Zapoznałem się z regulaminem, polityką prywatności i licencją, akceptuję je i mam ukończone 16 lat (albo zgodę rodzica lub opiekuna).",
            docs = { a0: String -> "Regulamin, politykę prywatności i licencję przeczytasz " + a0.toString() + "." },
            docsLink = "na naszej stronie"
        )

        val EN = TermsStrings(
            title = "Before we start…",
            sub = "You need to confirm that you have read the terms of service, the privacy policy and the licence, and that you agree to them.",
            agree = "I have read and accept the terms of service, the privacy policy and the licence, and I am 16 or older (or have a parent's or guardian's consent).",
            docs = { a0: String -> "You can read the terms of service, the privacy policy and the licence " + a0.toString() + "." },
            docsLink = "on our website"
        )

        val CS = TermsStrings(
            title = "Než začneme…",
            sub = "Musíte potvrdit, že jste se seznámili s podmínkami služby, zásadami ochrany soukromí a licencí a že s nimi souhlasíte.",
            agree = "Seznámil(a) jsem se s podmínkami služby, zásadami ochrany soukromí a licencí, přijímám je a je mi alespoň 16 let (nebo mám souhlas rodiče či zákonného zástupce).",
            docs = { a0: String -> "Podmínky služby, zásady ochrany soukromí a licenci si přečtete " + a0.toString() + "." },
            docsLink = "na našem webu"
        )

        val UK = TermsStrings(
            title = "Перш ніж почати…",
            sub = "Потрібно підтвердити, що ви ознайомилися з умовами використання, політикою конфіденційності та ліцензією і погоджуєтеся з ними.",
            agree = "Я ознайомився(-лася) з умовами використання, політикою конфіденційності та ліцензією, приймаю їх і мені виповнилося 16 років (або маю згоду батьків чи опікуна).",
            docs = { a0: String -> "Умови використання, політику конфіденційності та ліцензію можна прочитати " + a0.toString() + "." },
            docsLink = "на нашому сайті"
        )

        val DE = TermsStrings(
            title = "Bevor wir anfangen …",
            sub = "Du musst bestätigen, dass du die Nutzungsbedingungen, die Datenschutzerklärung und die Lizenz gelesen hast und ihnen zustimmst.",
            agree = "Ich habe die Nutzungsbedingungen, die Datenschutzerklärung und die Lizenz gelesen, akzeptiere sie und bin mindestens 16 Jahre alt (oder habe die Zustimmung eines Elternteils oder Erziehungsberechtigten).",
            docs = { a0: String -> "Die Nutzungsbedingungen, die Datenschutzerklärung und die Lizenz findest du " + a0.toString() + "." },
            docsLink = "auf unserer Website"
        )

        val ZH = TermsStrings(
            title = "開始之前…",
            sub = "你需要確認已閱讀服務條款、隱私權政策和授權，並同意這些內容。",
            agree = "我已閱讀並接受服務條款、隱私權政策和授權，且我已年滿 16 歲（或已取得父母或監護人的同意）。",
            docs = { a0: String -> "你可以" + a0.toString() + "閱讀服務條款、隱私權政策和授權。" },
            docsLink = "在我們的網站上"
        )

        fun of(chat: ChatStrings): TermsStrings = when (chat) {
            ChatStrings.PL -> PL
            ChatStrings.CS -> CS
            ChatStrings.UK -> UK
            ChatStrings.DE -> DE
            ChatStrings.ZH -> ZH
            else -> EN
        }
    }
}
