package nl.eazyrecipes.share

import nl.eazyrecipes.data.RecipeDetails
import nl.eazyrecipes.recipe.IngredientParts
import nl.eazyrecipes.ui.formatMinutes

/**
 * Een recept als nette tekst voor WhatsApp, mail of notities. Met de porties zoals je ze nu hebt gekozen
 * ([servings], hoeveelheden omgerekend met [factor]) en de bronlink onderaan.
 */
object RecipeText {
    fun format(d: RecipeDetails, servings: Int? = d.recipe.servings, factor: Double = 1.0): String = buildString {
        val r = d.recipe
        appendLine(r.title)
        listOfNotNull(formatMinutes(r.totalMinutes), servings?.let { if (it == 1) "1 portie" else "$it porties" })
            .takeIf { it.isNotEmpty() }?.let { appendLine(it.joinToString(" · ")) }

        if (d.ingredients.isNotEmpty()) {
            appendLine()
            appendLine("Ingrediënten")
            d.ingredients.forEach { i ->
                val p = IngredientParts.parse(i.raw)
                appendLine("• " + listOf(p.amount(factor), p.name).filter { it.isNotEmpty() }.joinToString(" "))
            }
        }
        if (d.steps.isNotEmpty()) {
            appendLine()
            appendLine("Bereiding")
            var section: String? = null
            val multipleSections = d.steps.mapNotNull { it.sectionName }.distinct().size > 1
            d.steps.forEachIndexed { n, s ->
                if (multipleSections && s.sectionName != null && s.sectionName != section) {
                    section = s.sectionName
                    appendLine("— ${s.sectionName}")
                }
                appendLine("${n + 1}. ${s.text}")
            }
        }
        r.notes?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("Mijn notitie: $it") }
        r.sourceUrl?.let { appendLine(); appendLine("Bron: $it") }
        appendLine()
        append("Gedeeld met EazyRecipes")
    }

    /**
     * Kort [text] in tot hoogstens [limit] tekens, op een hele regel, voor apps met een berichtlimiet
     * (Discord: 2000). Onderaan komt te staan dat het ingekort is, met de bronlink als die er is.
     */
    fun fit(text: String, limit: Int, sourceUrl: String?): String {
        if (text.length <= limit) return text
        val tail = "\n…\n\nIngekort, te lang voor één bericht." +
            (sourceUrl?.let { "\nHet hele recept: $it" } ?: "\nDeel als bestand voor het hele recept.")
        val room = limit - tail.length
        if (room <= 0) return text.take(limit)
        val cut = text.lastIndexOf('\n', room).takeIf { it > 0 } ?: room
        return text.substring(0, cut).trimEnd() + tail
    }
}
