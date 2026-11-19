package nl.eazyrecipes.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialLinkTextTest {
    private val saved = setOf("https://www.leukerecepten.nl/recepten/kip-kerrie/")

    @Test fun `nieuwe link op het klembord wordt ingevuld`() =
        assertEquals("https://eefkooktzo.nl/soep/", initialLinkText("", "Kijk: https://eefkooktzo.nl/soep/", saved))

    @Test fun `link die al in de bibliotheek staat niet opnieuw invullen, ook niet met tracking erachter`() {
        assertEquals("", initialLinkText("", "https://www.leukerecepten.nl/recepten/kip-kerrie/", saved))
        assertEquals("", initialLinkText("", "https://www.leukerecepten.nl/recepten/kip-kerrie/?utm_source=x", saved))
    }

    @Test fun `per ongeluk gesloten - vorige tekst wint van het klembord`() =
        assertEquals("https://half.nl/rec", initialLinkText("https://half.nl/rec", "https://eefkooktzo.nl/soep/", saved))

    @Test fun `klembord zonder link of leeg`() {
        assertEquals("", initialLinkText("", "boodschappen: melk", saved))
        assertEquals("", initialLinkText("", null, saved))
    }
}
