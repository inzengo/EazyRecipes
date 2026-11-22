package nl.eazyrecipes.importer

import nl.eazyrecipes.data.MainIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoDurationTest {
    @Test
    fun durations() {
        assertEquals(30, parseIsoDurationMinutes("PT0H30M"))
        assertEquals(100, parseIsoDurationMinutes("PT1H40M"))
        assertEquals(90, parseIsoDurationMinutes("PT90M"))
        assertEquals(60, parseIsoDurationMinutes("PT1H"))
        assertEquals(1500, parseIsoDurationMinutes("P1DT1H"))
        assertEquals(45, parseIsoDurationMinutes("P0DT0H45M"))
        assertEquals(2, parseIsoDurationMinutes("PT90S"))
        assertEquals(90, parseIsoDurationMinutes("pt1.5h"))
    }

    @Test
    fun `leeg, nul of onzin wordt null`() {
        assertNull(parseIsoDurationMinutes(null))
        assertNull(parseIsoDurationMinutes(""))
        assertNull(parseIsoDurationMinutes("PT0H0M"))
        assertNull(parseIsoDurationMinutes("P"))
        assertNull(parseIsoDurationMinutes("PT"))
        assertNull(parseIsoDurationMinutes("30 minuten"))
    }
}

class MainIngredientGuesserTest {
    private fun guess(category: String? = null, diets: List<String> = emptyList(), keywords: List<String> = emptyList(), ingredients: List<String> = emptyList()) =
        MainIngredientGuesser.guess(category, diets, keywords, ingredients)

    @Test fun zoet() = assertEquals(MainIngredient.ZOET, guess(category = "Nagerechten", ingredients = listOf("200 gr kip")))
    @Test fun `rijst is geen ijs`() = assertEquals(MainIngredient.VEGA, guess(category = "Rijstgerechten", ingredients = listOf("rijst", "paprika")))
    @Test fun vegan() = assertEquals(MainIngredient.VEGAN, guess(diets = listOf("https://schema.org/VeganDiet")))
    @Test fun vis() = assertEquals(MainIngredient.VIS, guess(ingredients = listOf("300 gr zalmfilet", "1 citroen")))
    @Test fun `paella met chorizo is vis en vlees`() = assertEquals(
        listOf(MainIngredient.VIS, MainIngredient.VLEES),
        MainIngredientGuesser.guessAll(null, emptyList(), emptyList(), listOf("300 g paellarijst", "200 g gamba's", "100 g chorizo")),
    )
    @Test fun vlees() = assertEquals(MainIngredient.VLEES, guess(ingredients = listOf("500 gr rundergehakt")))
    @Test fun `ketchup is geen kip`() = assertEquals(MainIngredient.VEGA, guess(ingredients = listOf("2 el ketchup")))
    @Test fun `vega kipstukjes zijn vega`() = assertEquals(MainIngredient.VEGA, guess(ingredients = listOf("200 gr vega kipstukjes")))
    @Test fun `vegetarisch dieet wint van vlees-woorden`() =
        assertEquals(MainIngredient.VEGA, guess(diets = listOf("https://schema.org/VegetarianDiet"), ingredients = listOf("vegetarische worst", "ham-kaas smaak")))
}
