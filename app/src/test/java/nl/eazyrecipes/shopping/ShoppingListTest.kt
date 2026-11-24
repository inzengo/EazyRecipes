package nl.eazyrecipes.shopping

import nl.eazyrecipes.ui.pantry.PantryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListTest {

    private fun recipe(title: String, vararg lines: String, base: Int? = null, servings: Int? = null) =
        ShoppingRecipe(title.lowercase(), title, base, servings, lines.toList())

    private fun ShoppingListState.open(): List<ShoppingLine> = sections.flatMap { it.lines }
    private fun ShoppingListState.line(name: String) = (open() + done + have).single { it.name == name }

    @Test
    fun `gelijke ingredienten worden opgeteld, enkel- en meervoud samen`() {
        val s = ShoppingList.build(
            listOf(
                recipe("Kip kerrie", "3 uien", "2-3 tomaten", "200 gr kipfilet"),
                recipe("Soep", "1 ui", "1 tomaat", "0,4 kg kipfilet"),
            )
        )
        with(s.line("uien")) {
            assertEquals("4", amount)
            assertEquals(Aisle.GROENTE, aisle)
            assertEquals(listOf("Kip kerrie", "Soep"), sources.map { it.recipeTitle })
        }
        assertEquals("3-4", s.line("tomaten").amount)
        assertEquals("600 g", s.line("kipfilet").amount)
        assertEquals(Aisle.VLEES, s.line("kipfilet").aisle)
        assertEquals(3, s.toBuy)
    }

    @Test
    fun `koude boter, boter of margarine en boter om in te vetten zijn allemaal boter`() {
        val s = ShoppingList.build(listOf(recipe("Taart", "150 gr boter of margarine", "boter (om in te vetten)", "60 gr koude boter", "verse peterselie")))
        assertEquals("210 g", s.line("boter").amount)
        assertEquals(3, s.line("boter").sources.size)
        assertEquals(Aisle.GROENTE, s.line("peterselie").aisle)
        assertEquals("kleine", ShoppingList.shoppingName("kleine"))
    }

    @Test
    fun `meervouden`() {
        fun same(a: String, b: String) = assertTrue("$a ~ $b", b in ShoppingList.singularCandidates(a) || a in ShoppingList.singularCandidates(b))
        same("tomaten", "tomaat")
        same("bonen", "boon")
        same("wortelen", "wortel")
        same("wortels", "wortel")
        same("eieren", "ei")
        same("citroenen", "citroen")
        same("rode uien", "rode ui")
        same("bessen", "bes")
        // Alleen het laatste woord: "rode ui" is geen "ui".
        assertTrue("ui" !in ShoppingList.singularCandidates("rode uien"))
    }

    @Test
    fun `eenheden en porties`() {
        val s = ShoppingList.build(
            listOf(
                recipe("Pasta", "300 g tomaten", "1 blikje tomaten", "1 el olijfolie", "250 ml room", "2 teentjes knoflook", base = 2, servings = 4),
                recipe("Saus", "5 dl room", "1 teentje knoflook", "½ citroen", "1½ komkommer"),
            )
        )
        // Porties 2 → 4: alles keer twee. Wat niet op te tellen is komt erachter.
        assertEquals("600 g + 2 blikjes", s.line("tomaten").amount)
        assertEquals("1 l", s.line("room").amount)
        assertEquals("5 teentjes", s.line("knoflook").amount)
        // Stuks koop je heel.
        assertEquals("1", s.line("citroen").amount)
        assertEquals("2", s.line("komkommer").amount)
        // Olie is basis.
        assertEquals(HaveReason.BASIS, s.line("olijfolie").have)
        assertEquals("1,1 kg", ShoppingList.build(listOf(recipe("A", "600 g bloem", "500 gram bloem"))).line("bloem").amount)
        assertEquals("2 pakken", ShoppingList.build(listOf(recipe("A", "1 pak melk", "1 pak melk"))).line("melk").amount)
    }

    @Test
    fun `basis, in huis, naar smaak en kopjes`() {
        val s = ShoppingList.build(
            listOf(recipe("Kip", "Voor de saus:", "zout en peper", "peterselie naar smaak", "koriander (optioneel)", "300 g kipfilet, in blokjes", "1 ui")),
            pantryKeys = setOf("kip"),
            pantryCustom = listOf("korian"),
        )
        assertEquals(listOf("peterselie", "ui"), s.open().map { it.name })
        assertEquals(HaveReason.BASIS, s.line("zout en peper").have)
        assertEquals(HaveReason.IN_HUIS, s.line("kipfilet").have)
        assertEquals(HaveReason.IN_HUIS, s.line("koriander").have)
        // De bron laat de regel zien zoals in het recept, met wat erachter staat.
        assertEquals("300 g kipfilet, in blokjes", s.line("kipfilet").sources.single().text)
        assertTrue(s.open().none { it.name.endsWith(":") })
    }

    @Test
    fun `afvinken en eigen items`() {
        val first = ShoppingList.build(listOf(recipe("A", "2 uien", "1 courgette")))
        val key = first.line("uien").key
        val s = ShoppingList.build(
            listOf(recipe("A", "2 uien", "1 courgette")),
            extras = listOf(ExtraItem("1", "wc-papier", false), ExtraItem("2", "2 pakken melk", true)),
            checked = setOf(key),
        )
        assertEquals(listOf("uien", "melk"), s.done.map { it.name })
        assertEquals("2 pakken", s.line("melk").amount)
        assertEquals("2", s.extrasId("melk"))
        assertEquals(Aisle.ZUIVEL, s.line("melk").aisle)
        assertEquals(Aisle.OVERIG, s.line("wc-papier").aisle)
        // Afdelingen in winkelvolgorde: groente vóór overig.
        assertEquals(listOf(Aisle.GROENTE, Aisle.OVERIG), s.sections.map { it.aisle })
    }

    private fun ShoppingListState.extrasId(name: String) = line(name).extraId

    @Test
    fun `afdelingen`() {
        assertEquals(Aisle.KRUIDEN, Aisles.of("gerookt paprikapoeder"))
        assertEquals(Aisle.ZUIVEL, Aisles.of("roomkaas"))
        assertEquals(Aisle.VOORRAAD, Aisles.of("pindakaas"))
        assertEquals(Aisle.VOORRAAD, Aisles.of("kippenbouillon"))
        assertEquals(Aisle.GROENTE, Aisles.of("verse tijm"))
        assertEquals(Aisle.OVERIG, Aisles.of("wc-papier"))
        // Elk item uit "In huis" heeft een afdeling.
        assertEquals(emptyList<String>(), PantryCatalog.map { it.key }.filter { it !in Aisles.byPantryKey })
    }

    @Test
    fun `als tekst`() {
        val s = ShoppingList.build(listOf(recipe("Kip kerrie", "3 uien", "200 g kipfilet", "zout")))
        assertEquals(
            """
            Boodschappen

            Groente & fruit
            • 3 uien

            Vlees & vis
            • 200 g kipfilet

            Voor: Kip kerrie

            Gedeeld met EazyRecipes
            """.trimIndent(),
            ShoppingList.asText(s),
        )
    }
}
