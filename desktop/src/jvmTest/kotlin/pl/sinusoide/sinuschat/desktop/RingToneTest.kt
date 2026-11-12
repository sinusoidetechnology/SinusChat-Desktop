package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.call.RingTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingToneTest {

    @Test
    fun appSoundsDecode() {
        for (name in listOf("sc_ring_bit", "sc_ring_bit_melodia", "sc_ring_puls", "sc_ringback_puls")) {
            assertTrue(RingTone.decodedLength(name) > 48_000, "nie zdekodowano $name")
        }
    }

    @Test
    fun followsPhoneChoice() {
        assertEquals("sc_ring_bit", RingTone.soundFor(RingTone.Kind.INCOMING, "bit"))
        assertEquals("sc_ring_bit_melodia", RingTone.soundFor(RingTone.Kind.INCOMING, "bit_melodia"))
        assertEquals("sc_ring_puls", RingTone.soundFor(RingTone.Kind.INCOMING, "puls"))
        assertEquals("sc_ring_bit", RingTone.soundFor(RingTone.Kind.INCOMING, "system"))
        assertEquals("sc_ringback_puls", RingTone.soundFor(RingTone.Kind.RINGBACK, ""))
        assertEquals(0.25f, RingTone.gain(-1))
    }
}
