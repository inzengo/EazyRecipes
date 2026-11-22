package nl.eazyrecipes.ui.pantry

import nl.eazyrecipes.data.RecipeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryMatcherTest {

    private fun items(line: String) = checkNotNull(PantryMatcher.analyze(line)) { "regel telt niet mee: $line" }.items

    private fun recipe(title: String, vararg lines: String) =
        RecipeEntity(id = title, title = title, createdAt = 0, updatedAt = 0) to lines.toList()

    @Test
    fun `hele woorden - kippenbouillon is bouillon, geen kip`() {
        assertEquals(setOf("bouillon"), items("500 ml kippenbouillon"))
        assertEquals(setOf("kip"), items("300 gr kipfilet, in blokjes"))
        assertEquals(setOf("kip"), items("4 kippendijen"))
        assertEquals(emptySet<String>(), items("1 tl paprikapoeder"))
        assertEquals(setOf("paprika"), items("2 rode paprika's"))
    }

    @Test
    fun `zonder hoofdletters en accenten`() {
        assertEquals(setOf("creme-fraiche"), items("2 el Crème Fraîche"))
        assertEquals(setOf("mais"), items("1 blikje maïs"))
        assertEquals(setOf("tomaat"), items("1 blik TOMATENBLOKJES"))
    }

    @Test
    fun `lijkt-op-gevallen - zoete aardappel, zure room, samenstellingen`() {
        assertEquals(setOf("zoete-aardappel"), items("2 zoete aardappels"))
        assertEquals(setOf("aardappel"), items("1 kg aardappelen"))
        assertEquals(setOf("creme-fraiche"), items("100 ml zure room"))
        assertEquals(setOf("kip"), items("4 kippendijfilets"))
        assertEquals(setOf("bouillon"), items("1 groentebouillontablet"))
        assertEquals(setOf("spekjes"), items("100 g spekblokjes"))
    }

    @Test
    fun `lente-ui is geen ui`() {
        assertEquals(setOf("lente-ui"), items("1 lente-uitje"))
        assertEquals(setOf("lente-ui"), items("2 lente-uitjes"))
        assertEquals(setOf("ui"), items("1 rode ui, gesnipperd"))
    }

    @Test
    fun `zout, peper, olie, water, naar smaak, optioneel en kopjes tellen niet mee`() {
        listOf(
            "zout en peper", "versgemalen zwarte peper", "2 el olijfolie", "Olie om in te bakken", "200 ml water",
            "Peterselie naar smaak", "Optioneel: 50 g pijnboompitten", "50 g pijnboompitten (optioneel)", "Voor de saus:", "  ",
        ).forEach { assertNull(it, PantryMatcher.analyze(it)) }
        listOf("een snufje zout", "flinke snuf zout", "een scheut olie", "peper uit de molen", "zout en peper uit de molen")
            .forEach { assertNull(it, PantryMatcher.analyze(it)) }
        assertEquals("na de komma telt ook mee", "Peper", PantryMatcher.analyze("Peper, zout en kipkruiden")?.name)
        // Rode peper is een pepertje, geen basis.
        assertEquals("rode peper", PantryMatcher.analyze("1 rode peper")?.name)
    }

    @Test
    fun `naam zonder hoeveelheid en eenheid`() {
        mapOf(
            "200 gr kipfilet" to "kipfilet",
            "1 teentje knoflook, geperst" to "knoflook",
            "2-3 tomaten" to "tomaten",
            "2 tot 3 tomaten" to "tomaten",
            "½ citroen" to "citroen",
            "400ml kokosmelk" to "kokosmelk",
            "1,5 kg aardappels (kruimig)" to "aardappels",
            "2 x 100 g zalmfilet" to "zalmfilet",
            "eieren" to "eieren",
        ).forEach { (raw, name) -> assertEquals(raw, name, PantryMatcher.ingredientName(raw)) }
    }

    @Test
    fun `score - aandeel regels, minstens een treffer, hoogste eerst, maximaal 12`() {
        val index = PantryMatcher.index(
            listOf(
                recipe("Shakshuka", "4 eieren", "1 blik tomatenblokjes", "1 ui", "1 rode paprika", "2 tenen knoflook", "75 g feta", "zout"),
                recipe("Omelet", "3 eieren", "scheutje melk", "peper en zout"),
                recipe("Zalm", "2 zalmfilets", "1 citroen"),
            )
        )
        val result = PantryMatcher.match(index, setOf("eieren", "ui", "tomaat", "knoflook", "paprika"), emptyList())
        assertEquals(listOf("Shakshuka", "Omelet"), result.map { it.recipe.title })
        val shakshuka = result.first()
        assertEquals(5, shakshuka.haveCount)
        assertEquals(6, shakshuka.total) // "zout" telt niet mee
        assertEquals(listOf("feta"), shakshuka.missing)
        assertEquals(listOf("melk"), result[1].missing)

        val many = PantryMatcher.index((1..20).map { recipe("R$it", "1 ui") })
        assertEquals(12, PantryMatcher.match(many, setOf("ui"), emptyList()).size)
        assertTrue(PantryMatcher.match(index, emptySet(), emptyList()).isEmpty())
    }

    @Test
    fun `eigen woorden matchen op het begin van een woord`() {
        val index = PantryMatcher.index(listOf(recipe("Salade", "250 g halloumiblokjes", "1 komkommer"), recipe("Soep", "1 prei")))
        val result = PantryMatcher.match(index, emptySet(), listOf("Halloumi"))
        assertEquals(listOf("Salade"), result.map { it.recipe.title })
        assertEquals(listOf("komkommer"), result.single().missing)
        // Midden in een woord telt niet.
        assertTrue(PantryMatcher.match(index, emptySet(), listOf("loumi")).isEmpty())
    }

    @Test
    fun `chips - alleen items uit je recepten, meest gebruikt eerst`() {
        val index = PantryMatcher.index(
            listOf(
                recipe("A", "1 ui", "200 g rijst"),
                recipe("B", "2 uien", "1 ui extra", "kipfilet"),
                recipe("C", "1 rode ui", "rijst"),
                recipe("D", "1 prei"),
            )
        )
        assertEquals(listOf("ui", "rijst", "prei", "kip"), PantryMatcher.usedItems(index).map { it.key })
    }

    @Test
    fun `de lijst heeft unieke sleutels en zo'n 50 items`() {
        val keys = PantryCatalog.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.size >= 50)
    }
}
