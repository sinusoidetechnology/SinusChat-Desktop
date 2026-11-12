package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.i18n.AppLanguage
import pl.sinusoide.sinuschat.desktop.ui.anim.greetingOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingOrderTest {

    private val words = listOf("pl" to "hej", "en" to "hello", "cs" to "ahoj", "uk" to "привіт", "de" to "hallo", "zh-TW" to "你好")

    @Test
    fun onlyAppLanguagesAndOwnLanguageFirst() {
        for (lang in AppLanguage.entries) {
            for (cycle in 0L until 6L) {
                val order = greetingOrder(lang.code, cycle, 42L)
                assertEquals(words.size, order.size)
                assertEquals(words.indices.toSet(), order.toSet())
                assertEquals(lang.code, words[order.first()].first)
            }
        }
        assertEquals(words.map { it.first }.toSet(), AppLanguage.entries.map { it.code }.toSet())
    }

    @Test
    fun restIsShuffledBetweenCycles() {
        val tails = (0L until 20L).map { greetingOrder("en", it, 7L).drop(1) }.toSet()
        kotlin.test.assertTrue(tails.size > 1)
    }
}
