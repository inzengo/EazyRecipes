package nl.eazyrecipes.importer

import nl.eazyrecipes.data.MainIngredient

/**
 * Eerste gok voor de contextkleur (vlees/vis/vega/vegan/zoet). De gebruiker kan hem in het voorbeeldscherm aanpassen.
 * Volgorde: zoet (categorie) → dieet uit JSON-LD → vis → vlees → anders vega.
 */
object MainIngredientGuesser {

    private val sweetCategory = listOf(
        "nagerecht", "dessert", "taart", "gebak", "bakrecept", "koek", "cake", "zoet", "snoep",
        "toetje", "baking", "sweet",
    )

    private val fish = listOf(
        "vis", "zalm", "tonijn", "kabeljauw", "garnaal", "garnalen", "mosselen", "kibbeling", "pangasius",
        "tilapia", "makreel", "haring", "forel", "scampi", "inktvis", "ansjovis", "schelvis", "koolvis",
        "zeebaars", "dorade", "krab", "kreeft", "sardine", "fish", "salmon", "tuna", "shrimp", "prawn",
    )

    private val meat = listOf(
        "kip", "kipfilet", "kippendij", "gehakt", "rundergehakt", "rund", "biefstuk", "steak", "varken",
        "speklap", "spek", "spekjes", "bacon", "ham", "worst", "rookworst", "braadworst", "chorizo", "salami",
        "lam", "lams", "kalkoen", "eend", "kalfs", "ossenhaas", "hamburger", "shoarma", "pulled pork",
        "procureur", "riblappen", "sukade", "chicken", "beef", "pork", "lamb", "sausage", "pancetta",
        "prosciutto", "serranoham", "parmaham", "kippenbouillon", "runderbouillon", "kippengehakt",
    )

    /** "vega kipstukjes", "vegetarisch gehakt", "plantaardige worst": het woord ná de vega-aanduiding telt niet. */
    private val vegaQualified = Regex("""\b(vega|vegan|vegetarische?|plantaardige?)\s+[\p{L}-]+""")

    fun guess(
        category: String?,
        diets: List<String>,
        keywords: List<String>,
        ingredients: List<String>,
    ): MainIngredient {
        val cat = category.orEmpty().lowercase()
        if (sweetCategory.any { it in cat }) return MainIngredient.ZOET

        val d = diets.joinToString(" ").lowercase()
        if ("vegandiet" in d) return MainIngredient.VEGAN
        val kw = keywords.joinToString(" ").lowercase()
        if ("vegan" in kw) return MainIngredient.VEGAN

        val haystack = (keywords + ingredients).joinToString(" | ").lowercase().replace(vegaQualified, " ")
        if (fish.any { haystack.containsWord(it) }) return MainIngredient.VIS
        if ("vegetariandiet" in d) return MainIngredient.VEGA
        if (meat.any { haystack.containsWord(it) }) return MainIngredient.VLEES
        return MainIngredient.VEGA
    }

    /** Begin van een woord moet matchen ("kip" in "kipfilet" wel, in "ketchup" niet). */
    private fun String.containsWord(word: String): Boolean =
        Regex("""(?<![\p{L}])${Regex.escape(word)}""").containsMatchIn(this)
}
