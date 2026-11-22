package nl.eazyrecipes.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountsTest {

    private fun parts(raw: String) = IngredientParts.parse(raw)

    @Test
    fun `hoeveelheid, eenheid en naam`() {
        with(parts("200 gr kipfilet")) { assertEquals(200.0, low); assertEquals("gr", unit); assertEquals("kipfilet", name); assertEquals("200 gr", amountText) }
        with(parts("400ml kokosmelk")) { assertEquals(400.0, low); assertEquals("ml", unit); assertEquals("kokosmelk", name) }
        with(parts("4 eieren")) { assertEquals(4.0, low); assertNull(unit); assertEquals("eieren", name) }
        with(parts("2 groene paprika's")) { assertEquals(2.0, low); assertNull("g van groene is geen eenheid", unit); assertEquals("groene paprika's", name) }
        with(parts("1½ el olijfolie")) { assertEquals(1.5, low); assertEquals("el", unit) }
        with(parts("1 1/2 tl zout")) { assertEquals(1.5, low); assertEquals("tl", unit) }
        with(parts("½ citroen")) { assertEquals(0.5, low); assertEquals("citroen", name) }
        with(parts("1,5 kg aardappels")) { assertEquals(1.5, low); assertEquals("kg", unit) }
        with(parts("2-3 tomaten")) { assertEquals(2.0, low); assertEquals(3.0, high); assertEquals("tomaten", name) }
        with(parts("2 tot 3 teentjes knoflook")) { assertEquals(3.0, high); assertEquals("teentjes", unit); assertEquals("knoflook", name) }
        with(parts("2 à 3 tomaten")) { assertEquals(3.0, high); assertEquals("tomaten", name) }
        with(parts("2 of 3 uien")) { assertEquals(3.0, high); assertEquals("uien", name) }
        with(parts("1⁄2 tl zout")) { assertEquals(0.5, low); assertEquals("tl", unit); assertEquals("zout", name) }
        with(parts("1.000 gram bloem")) { assertEquals(1000.0, low); assertEquals("gram", unit) }
        with(parts("Eetlepel Eetlepels")) { assertNull(low) }
        with(parts("Zout en peper")) { assertNull(low); assertEquals("Zout en peper", name) }
        with(parts("kipfilet (ca. 200 g)")) { assertNull("alleen vooraan", low) }
        with(parts("4")) { assertNull("alleen een getal is geen ingrediënt", low) }
    }

    @Test
    fun `omrekenen`() {
        assertEquals("200 gr", parts("200 gr kipfilet").amount(1.0))
        assertEquals("1½ el", parts("1½ el olijfolie").amount(1.0))
        assertEquals("½", parts("0.5 blokje bouillon").amount(1.0)) // netjes geschreven
        assertEquals("112 g", parts("112 g boter").amount(1.0)) // maar niet afgerond
        assertEquals("1,3 kg", parts("1.3 kg aardappels").amount(1.0))
        assertEquals("⅛ tl", parts("⅛ tl nootmuskaat").amount(1.0))
        assertEquals("1000 gram", parts("1.000 gram bloem").amount(1.0))
        assertEquals("400 gr", parts("200 gr kipfilet").amount(2.0))
        assertEquals("150 gr", parts("200 gr kipfilet").amount(0.75))
        assertEquals("3", parts("2 uien").amount(1.5))
        assertEquals("¾ el", parts("1½ el olijfolie").amount(0.5))
        assertEquals("3-4½", parts("2-3 tomaten").amount(1.5))
        assertEquals("", parts("zout").amount(2.0))
    }

    @Test
    fun `nette getallen`() {
        assertEquals("1½", formatQuantity(1.5))
        assertEquals("⅓", formatQuantity(1.0 / 3))
        assertEquals("2", formatQuantity(1.98))
        assertEquals("13", formatQuantity(12.6))
        assertEquals("115", formatQuantity(112.5, "g"))
        assertEquals("38", formatQuantity(37.5, "g"))
        assertEquals("1,2", formatQuantity(1.2))
        assertEquals("0,05", formatQuantity(0.05))
    }
}
