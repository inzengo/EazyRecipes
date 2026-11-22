package nl.eazyrecipes.cook

import nl.eazyrecipes.ui.pantry.PantryCatalog
import nl.eazyrecipes.ui.pantry.pantryTokens

/** Woorden in een ingrediëntnaam die niets zeggen over wélk ingrediënt het is. */
private val noise = setOf(
    "verse", "vers", "rode", "rood", "gele", "groene", "witte", "zwarte", "grote", "groot", "kleine", "klein", "middelgrote",
    "fijn", "fijne", "grof", "gesneden", "gehakte", "geraspt", "geraspte", "gemalen", "gedroogde", "blokje", "blokjes",
    "stukjes", "plakjes", "naar", "smaak", "optioneel", "voor", "de", "het", "een", "en", "of", "met", "van", "in",
)

private fun commonPrefix(a: String, b: String): Int {
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return i
}

/** Zelfde woord, of meervoud/verkleinwoord ervan ("tomaten" ↔ "tomaat", "wortels" ↔ "wortel"). */
private fun sameWord(a: String, b: String): Boolean {
    if (a == b) return true
    val p = commonPrefix(a, b)
    return p >= 4 && p >= minOf(a.length, b.length) - 2 && kotlin.math.abs(a.length - b.length) <= 3
}

/** Woord → lijst-item uit "In huis" (alleen varianten van één woord): "uien" → ui, "kipfilet" → kip. */
private val catalogWord: Map<String, String> by lazy {
    PantryCatalog.flatMap { item -> item.variants.filter { it.size == 1 }.map { it[0] to item.key } }.toMap()
}

/**
 * Welke ingrediënten (index in [names]) noemt deze stap? Op woordniveau, zonder hoofdletters/accenten:
 * hetzelfde woord, een meervoud ervan, of hetzelfde item uit de "In huis"-lijst ("Bak de kip" ↔ "kipfilet").
 */
fun ingredientsInStep(step: String, names: List<String>): List<Int> {
    val words = pantryTokens(step)
    val stepItems = words.mapNotNull { catalogWord[it] }.toSet()
    return names.indices.filter { i ->
        pantryTokens(names[i]).filter { it.length >= 2 && it !in noise && it.none(Char::isDigit) }.any { n ->
            catalogWord[n]?.let { it in stepItems } == true || words.any { w -> sameWord(w, n) }
        }
    }
}
