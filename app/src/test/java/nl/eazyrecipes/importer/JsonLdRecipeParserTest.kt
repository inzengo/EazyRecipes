package nl.eazyrecipes.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonLdRecipeParserTest {

    private fun page(name: String): String =
        checkNotNull(javaClass.getResource("/pages/$name")) { "fixture $name ontbreekt" }.readText(Charsets.UTF_8)

    // --- echte pagina's (ingekort tot de JSON-LD, zie de kop van elk bestand) ---

    @Test
    fun `leukerecepten kip kerrie`() {
        val url = "https://www.leukerecepten.nl/recepten/kip-kerrie/"
        val r = requireNotNull(JsonLdRecipeParser.parse(page("leukerecepten-kip-kerrie.html"), url))

        assertEquals("Kip kerrie: romig en vers gemaakt", r.title)
        assertEquals(url, r.sourceUrl)
        assertEquals("https://www.leukerecepten.nl/app/uploads/2025/07/kip-kerrie-recept.jpg", r.imageUrl)
        assertEquals(2, r.servings)
        assertEquals("2 personen", r.yieldText)
        assertEquals(30, r.prepMinutes)
        assertNull("PT0H0M betekent: onbekend", r.cookMinutes)
        assertEquals(30, r.totalMinutes)
        assertEquals("Hoofdgerechten", r.category)
        assertEquals("Aziatische recepten", r.cuisine)
        assertEquals("vlees", r.categoryIds.firstOrNull())

        assertEquals(13, r.ingredients.size)
        assertEquals("150 gr rijst", r.ingredients.first())
        assertTrue("125 ml crème fraîche" in r.ingredients)

        assertEquals(3, r.steps.size)
        assertTrue(r.steps.all { it.section == "Bereiding" })
        assertTrue(r.steps[0].text.startsWith("Kook de rijst gaar volgens de verpakking."))
        assertTrue("geen witruimte aan het eind", r.steps.none { it.text != it.text.trim() })

        assertTrue("Crème fraîche" in r.tags)
        assertTrue("Hoofdgerechten" in r.tags)
        assertEquals("tags zijn uniek", r.tags.size, r.tags.distinctBy { it.lowercase() }.size)
    }

    @Test
    fun `leukerecepten appelkruimeltaart met FAQ in hetzelfde blok`() {
        val r = requireNotNull(
            JsonLdRecipeParser.parse(page("leukerecepten-appelkruimeltaart.html"), "https://www.leukerecepten.nl/recepten/appelkruimeltaart/")
        )
        assertEquals(10, r.servings)
        assertEquals("10 stukken", r.yieldText)
        assertEquals(30, r.prepMinutes)
        assertEquals(40, r.cookMinutes)
        assertEquals("totalTime van de bron wint van prep+cook", 100, r.totalMinutes)
        assertEquals("Bakrecepten", r.category)
        assertEquals("zoet", r.categoryIds.firstOrNull())
        assertEquals(14, r.ingredients.size)
        assertEquals("250 gr zelfrijzend bakmeel", r.ingredients.first())
        assertEquals("de bron heeft 11 HowToSteps, waarvan 2 alleen \"\\n\" bevatten", 9, r.steps.size)
        assertTrue(r.steps.none { it.text.isBlank() })
    }

    // --- varianten die andere sites gebruiken ---

    private fun html(vararg jsonLd: String) =
        "<html><head>" + jsonLd.joinToString("") { "<script type=\"application/ld+json\">$it</script>" } + "</head></html>"

    @Test
    fun `geen recept geeft null`() {
        assertNull(JsonLdRecipeParser.parse("<html><body>hoi</body></html>"))
        assertNull(JsonLdRecipeParser.parse(html("""{"@type":"WebPage","name":"x"}""")))
    }

    @Test
    fun `ongeldig blok wordt overgeslagen, volgende blok telt`() {
        val r = JsonLdRecipeParser.parse(html("{kapot", """{"@type":"Recipe","name":"Soep"}"""))
        assertEquals("Soep", r?.title)
    }

    @Test
    fun `type als lijst, array op topniveau, instructies als strings`() {
        val r = requireNotNull(
            JsonLdRecipeParser.parse(
                html(
                    """[{"@type":"Organization","name":"x"},
                       {"@type":["Recipe","NewsArticle"],"name":"Pasta &amp; pesto",
                        "recipeYield":4,"image":{"@type":"ImageObject","url":"https://x.nl/a.jpg"},
                        "recipeInstructions":["Kook de pasta.","Roer de pesto erdoor."],
                        "keywords":["pasta","snel"],"suitableForDiet":"https://schema.org/VegetarianDiet"}]"""
                ),
                "https://www.x.nl/pasta",
            )
        )
        assertEquals("Pasta & pesto", r.title)
        assertEquals(4, r.servings)
        assertEquals("https://x.nl/a.jpg", r.imageUrl)
        assertEquals(listOf(ParsedStep("Kook de pasta."), ParsedStep("Roer de pesto erdoor.")), r.steps)
        assertEquals("vega", r.categoryIds.firstOrNull())
    }

    @Test
    fun `instructies als één tekst met HTML, meerdere secties`() {
        val single = JsonLdRecipeParser.parse(
            html("""{"@type":"Recipe","name":"A","recipeInstructions":"<p>Stap een.</p><p>Stap twee.</p>"}""")
        )
        assertEquals(listOf("Stap een.", "Stap twee."), single?.steps?.map { it.text })

        val sections = JsonLdRecipeParser.parse(
            html(
                """{"@type":"Recipe","name":"B","recipeInstructions":[
                    {"@type":"HowToSection","name":"Saus","itemListElement":[{"@type":"HowToStep","text":"Fruit de ui."}]},
                    {"@type":"HowToSection","name":"Pasta","itemListElement":[{"@type":"HowToStep","text":"Kook."},{"@type":"HowToStep","text":"Giet af."}]}
                ]}"""
            )
        )
        assertEquals(
            listOf(ParsedStep("Fruit de ui.", "Saus"), ParsedStep("Kook.", "Pasta"), ParsedStep("Giet af.", "Pasta")),
            sections?.steps,
        )
    }

    @Test
    fun `recept in graph met rauwe regeleinden in strings`() {
        val r = JsonLdRecipeParser.parse(
            html("{\"@graph\":[{\"@type\":\"Recipe\",\"name\":\"Regel\nbreuk\",\"totalTime\":\"PT1H15M\"}]}")
        )
        assertEquals("Regel breuk", r?.title)
        assertEquals(75, r?.totalMinutes)
    }

    @Test
    fun `zonder totalTime telt prep plus cook`() {
        val r = JsonLdRecipeParser.parse(html("""{"@type":"Recipe","name":"C","prepTime":"PT10M","cookTime":"PT20M"}"""))
        assertEquals(30, r?.totalMinutes)
    }
}
