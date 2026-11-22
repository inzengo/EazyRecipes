package nl.eazyrecipes.importer

/**
 * Een recept zoals het uit een bron komt, vóór het opslaan.
 * Het voorbeeldscherm toont dit en laat alles bewerken; daarna maakt de repository er rijen van.
 */
data class ParsedRecipe(
    val title: String,
    val description: String? = null,
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    /** Aantal porties als getal, als dat te bepalen is (`"4 personen"` → 4). */
    val servings: Int? = null,
    /** De opbrengst zoals de bron die noemt, bv. `"10 stukken"`. */
    val yieldText: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val category: String? = null,
    val cuisine: String? = null,
    /** Categorieën (ids), de eerste is de hoofdsoort (kleur). Bij import de gok van [MainIngredientGuesser]. */
    val categoryIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val ingredients: List<String> = emptyList(),
    val steps: List<ParsedStep> = emptyList(),
)

data class ParsedStep(
    val text: String,
    /** Kop van de sectie, bv. `"Saus"`; `null` als de bron geen secties gebruikt. */
    val section: String? = null,
)
