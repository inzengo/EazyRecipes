package nl.eazyrecipes.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRecipeParserTest {

    @Test
    fun `kookboekpagina met kopjes en afgebroken zinnen`() {
        val ocr = """
            Pasta met spinazie en feta
            Snel doordeweeks gerecht
            4 personen
            30 minuten
            Ingrediënten
            • 400 g penne
            • 2 tenen knoflook
            • 300 g spinazie
            • 200 g feta, in
            blokjes
            zout en peper
            Bereiding
            Kook de pasta volgens de aanwijzingen op de
            verpakking. Fruit intussen de knoflook in een
            scheutje olie.
            Voeg de spinazie toe en laat slinken. Roer
            de feta erdoor.
            42
        """.trimIndent()

        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals("Pasta met spinazie en feta", r.title)
        assertEquals("Snel doordeweeks gerecht", r.description)
        assertEquals(4, r.servings)
        assertEquals(30, r.totalMinutes)
        assertEquals(listOf("400 g penne", "2 tenen knoflook", "300 g spinazie", "200 g feta, in blokjes", "zout en peper"), r.ingredients)
        assertEquals(
            listOf(
                "Kook de pasta volgens de aanwijzingen op de verpakking.",
                "Fruit intussen de knoflook in een scheutje olie.",
                "Voeg de spinazie toe en laat slinken.",
                "Roer de feta erdoor.",
            ).joinToString(" "),
            r.steps.joinToString(" ") { it.text },
        )
        // Een stap eindigt pas als een regel op een punt eindigt; midden in een regel kunnen we dat niet zien.
        assertEquals(2, r.steps.size)
    }

    @Test
    fun `genummerde stappen blijven bij elkaar, ook met meer zinnen`() {
        val ocr = """
            Shakshuka
            Ingredients
            2 tbsp olive oil
            6 eggs
            Method
            1. Heat the oil in a pan. Add the onion and
            cook until soft.
            2. Crack in the eggs and cover.
        """.trimIndent()
        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals("Shakshuka", r.title)
        assertEquals(listOf("2 tbsp olive oil", "6 eggs"), r.ingredients)
        assertEquals(
            listOf("Heat the oil in a pan. Add the onion and cook until soft.", "Crack in the eggs and cover."),
            r.steps.map { it.text },
        )
    }

    @Test
    fun `screenshot zonder kopjes en met statusbalk`() {
        val ocr = """
            12:41 ✦ 87%
            Romige tomatensoep
            1 ui
            2 blikken tomaten
            500 ml bouillon
            Snipper de ui en fruit hem zacht in de boter in een grote pan.
            Voeg de tomaten en bouillon toe en laat 20 minuten zachtjes koken.
        """.trimIndent()
        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals("Romige tomatensoep", r.title)
        assertEquals(listOf("1 ui", "2 blikken tomaten", "500 ml bouillon"), r.ingredients)
        assertEquals(2, r.steps.size)
        assertTrue(r.steps[1].text.startsWith("Voeg de tomaten"))
    }

    @Test
    fun `kopje met tekst erachter en woordafbreking`() {
        val r = PhotoRecipeParser.parse(
            """
            Pannenkoeken
            Ingrediënten: 250 g bloem
            3 eieren
            Bereiding: Klop alles glad en laat het beslag door-
            rusten.
            """.trimIndent(),
        )!!
        assertEquals(listOf("250 g bloem", "3 eieren"), r.ingredients)
        assertEquals(listOf("Klop alles glad en laat het beslag doorrusten."), r.steps.map { it.text })
    }

    @Test
    fun `alleen tekst zonder structuur wordt de bereiding`() {
        val r = PhotoRecipeParser.parse("Oma's appeltaart\nMeng alles en bak het een uur.")!!
        assertEquals("Oma's appeltaart", r.title)
        assertEquals(listOf("Meng alles en bak het een uur."), r.steps.map { it.text })
    }

    @Test
    fun `geen letters is geen recept`() {
        assertNull(PhotoRecipeParser.parse("12:41\n87%\n\n42"))
        assertNull(PhotoRecipeParser.parse(""))
    }

    /** Alle woorden van de invoer moeten ergens in het resultaat staan: we raken geen tekst kwijt. */
    private fun assertNothingLost(ocr: String, r: ParsedRecipe, except: Set<String> = emptySet()) {
        val out = (listOf(r.title, r.description.orEmpty()) + r.ingredients + r.steps.map { it.text }).joinToString(" ")
        val missing = Regex("""\p{L}{3,}""").findAll(ocr).map { it.value }.filter { it !in except && it !in out }.toList()
        assertTrue("kwijt: $missing", missing.isEmpty())
    }

    @Test
    fun `alleen een ingrediëntenkopje - de bereiding wordt toch stappen`() {
        val ocr = """
            Linzensoep
            Ingredients
            200 g red lentils
            1 onion
            Fry the onion in a little oil until soft and golden, about five minutes.
            Add the lentils and simmer until tender.
        """.trimIndent()
        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals(listOf("200 g red lentils", "1 onion"), r.ingredients)
        assertEquals(2, r.steps.size)
        assertNothingLost(ocr, r, except = setOf("Ingredients"))
    }

    @Test
    fun `alleen een bereidingskopje - de regels erboven worden ingrediënten`() {
        val ocr = """
            Guacamole
            2 avocado's
            1 limoen
            snufje zout
            Bereiding
            Prak de avocado en meng met het limoensap.
        """.trimIndent()
        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals("Guacamole", r.title)
        assertEquals(listOf("2 avocado's", "1 limoen", "snufje zout"), r.ingredients)
        assertNull(r.description)
    }

    @Test
    fun `screenshot zonder titel begint met ingrediënten`() {
        val r = PhotoRecipeParser.parse("200 g bloem\n2 eieren\nsnufje zout")!!
        assertEquals("", r.title)
        assertEquals(listOf("200 g bloem", "2 eieren", "snufje zout"), r.ingredients)
    }

    @Test
    fun `porties en tijd in de titel of een kopje`() {
        val r = PhotoRecipeParser.parse("Lasagne voor 4 personen\nIngrediënten (serves 6)\n1 ui\nBereiding: 45 minuten\nSnijd de ui.")!!
        assertEquals("Lasagne voor 4 personen", r.title)
        assertEquals(4, r.servings)
        assertEquals(45, r.totalMinutes)
        assertEquals(listOf("1 ui"), r.ingredients)

        val both = PhotoRecipeParser.parse("Curry\n4 personen · 30 minuten\nMeng alles.")!!
        assertEquals(4, both.servings)
        assertEquals(30, both.totalMinutes)
        assertNull(both.description)

        val titleWithTime = PhotoRecipeParser.parse("Pasta in 15 minuten\nKook de pasta.")!!
        assertEquals("Pasta in 15 minuten", titleWithTime.title)
    }

    @Test
    fun `cijfers, breuken en tijden midden in de tekst blijven staan`() {
        val r = PhotoRecipeParser.parse("Taart\nIngrediënten\n½\nbanaan\n2\neieren\nBereiding\n1:30 uur in de oven op 160 graden.\n12")!!
        assertEquals(listOf("½", "banaan", "2", "eieren"), r.ingredients)
        assertEquals(listOf("1:30 uur in de oven op 160 graden."), r.steps.map { it.text })
    }

    @Test
    fun `lange ingrediëntregel zonder kopjes is geen stap`() {
        val ocr = "Pesto\n50 g pijnboompitten\nEen handvol verse basilicumblaadjes, grof gescheurd, plus extra\n2 tenen knoflook"
        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals(3, r.ingredients.size)
        assertTrue(r.steps.isEmpty())
    }

    @Test
    fun `nieuwe hoeveelheid begint een nieuw ingrediënt, ook na een verbindingswoord`() {
        val r = PhotoRecipeParser.parse("Saus\nIngrediënten\n1 el olie en\n2 tenen knoflook")!!
        assertEquals(listOf("1 el olie en", "2 tenen knoflook"), r.ingredients)
    }

    @Test
    fun `kopwoord met een zin erachter is geen kopje`() {
        val r = PhotoRecipeParser.parse("Ui\nIngrediënten\n1 ui\nBereiding Snijd de ui fijn.")!!
        assertNothingLost("Bereiding Snijd de ui fijn.", r)
    }

    @Test
    fun `meer tijdregels - totaal wint, de rest blijft in de beschrijving`() {
        val ocr = "Ovenschotel\nPrep time: 15 mins\nCook time: 30 mins\nTotal time: 45 mins\nIngredients\n1 ui"
        val r = PhotoRecipeParser.parse(ocr)!!
        assertEquals(45, r.totalMinutes)
        assertNothingLost(ocr, r, except = setOf("Ingredients", "Total", "time", "mins"))
        assertTrue(r.description!!.contains("Prep time: 15 mins") && r.description!!.contains("Cook time: 30 mins"))

        val nl = PhotoRecipeParser.parse("Stamppot\nBereidingstijd: 15 min\nKooktijd: 30 min\nIngrediënten\n1 kg aardappels")!!
        assertEquals(15, nl.totalMinutes)
        assertEquals("Kooktijd: 30 min", nl.description)
    }

    @Test
    fun `altijd en tijdens zijn geen tijdkopje`() {
        val r = PhotoRecipeParser.parse("Risotto\nRoer tijdens 20 minuten\nIngrediënten\n1 ui")!!
        assertNull(r.totalMinutes)
        assertEquals("Roer tijdens 20 minuten", r.description)
    }

    @Test
    fun `titel met een eenheidswoord blijft de titel`() {
        listOf("Pak choi uit de wok", "St. Jacobsschelpen met boter", "Pasta met een bos basilicum").forEach { t ->
            val r = PhotoRecipeParser.parse("$t\n200 g pasta\n1 ui")!!
            assertEquals(t, r.title)
            assertEquals(listOf("200 g pasta", "1 ui"), r.ingredients)
        }
    }

    @Test
    fun `lange inleiding wordt niet ingekort`() {
        val intro = "Dit recept kreeg ik van mijn oma en ik maak het elk jaar met kerst. ".repeat(12).trim()
        val r = PhotoRecipeParser.parse("Kersttaart\n$intro\nIngrediënten\n1 ei")!!
        assertEquals(intro, r.description)
    }
}
