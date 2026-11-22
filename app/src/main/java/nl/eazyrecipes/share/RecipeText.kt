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
}
