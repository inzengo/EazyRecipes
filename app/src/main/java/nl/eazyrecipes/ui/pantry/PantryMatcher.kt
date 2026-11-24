package nl.eazyrecipes.ui.pantry

import nl.eazyrecipes.data.RecipeEntity
import java.text.Normalizer
import java.util.Locale

/** Kleine letters, zonder accenten, opgeknipt in woorden van letters/cijfers ("Crème-fraîche" → [creme, fraiche]). */
internal fun pantryTokens(text: String): List<String> =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(marks, "")
        .split(nonWord)
        .filter { it.isNotEmpty() }

private val marks = Regex("""\p{Mn}+""")
private val nonWord = Regex("""[^a-z0-9]+""")

/** Komt [phrase] als aaneengesloten reeks hele woorden voor in [tokens]? */
private fun List<String>.containsPhrase(phrase: List<String>, prefixLast: Boolean = false): Boolean {
    if (phrase.isEmpty() || phrase.size > size) return false
    for (start in 0..size - phrase.size) {
        val ok = phrase.indices.all { i ->
            val t = this[start + i]
            if (prefixLast && i == phrase.lastIndex) t.startsWith(phrase[i]) else t == phrase[i]
        }
        if (ok) return true
    }
    return false
}

/** Een ingrediëntregel die meetelt voor "In huis". */
data class PantryLine(
    val raw: String,
    /** Zonder hoeveelheid en eenheid, voor "Nog nodig" ("200 gr kipfilet" → "kipfilet"). */
    val name: String,
    /** Sleutels van [PantryCatalog]-items die in deze regel voorkomen. */
    val items: Set<String>,
    internal val tokens: List<String>,
)

/** Een recept met zijn meetellende regels; eenmalig berekend per stand van de bibliotheek. */
data class PantryRecipe(val recipe: RecipeEntity, val lines: List<PantryLine>)

data class PantryMatch(
    val recipe: RecipeEntity,
    /** Per meetellende regel: heb je hem in huis? */
    val have: List<Boolean>,
    val missing: List<String>,
) {
    val haveCount: Int get() = have.count { it }
    val total: Int get() = have.size
    val score: Double get() = if (total == 0) 0.0 else haveCount.toDouble() / total
}

object PantryMatcher {

    const val MAX_RESULTS = 12

    /** Woorden die samen een "basis"-regel vormen: zout, peper, olie, water en hun bijwoorden. */
    private val stapleWords = setOf(
        "zout", "zeezout", "grof", "fijn", "peper", "zwarte", "witte", "versgemalen", "gemalen", "vers", "verse",
        "olie", "olijfolie", "zonnebloemolie", "arachideolie", "frituurolie", "extra", "vierge", "neutrale", "plantaardige",
        "water", "kraanwater", "lauw", "lauwwarm", "warm", "koud", "heet", "kokend",
        "en", "of", "wat", "beetje", "om", "te", "in", "bakken", "frituren", "invetten", "voor", "de", "het", "een",
        "uit", "molen", "flinke", "royale", "klein", "kleine",
    )

    private val units = setOf(
        "g", "gr", "gram", "grams", "kg", "kilo", "mg", "ml", "cl", "dl", "l", "liter", "liters",
        "el", "tl", "eetlepel", "eetlepels", "theelepel", "theelepels", "lepel", "lepels", "kop", "kopje", "kopjes", "cup", "cups",
        "stuk", "stuks", "stukje", "stukjes", "blik", "blikje", "blikjes", "blikken", "pak", "pakje", "pakjes", "pakken",
        "zak", "zakje", "zakjes", "teen", "tenen", "teentje", "teentjes", "snuf", "snufje", "snufjes", "scheut", "scheutje", "scheutjes",
        "handje", "handjes", "handvol", "bos", "bosje", "bosjes", "takje", "takjes", "plak", "plakken", "plakje", "plakjes",
        "mespunt", "mespuntje", "bakje", "bakjes", "pot", "potje", "potjes", "fles", "flesje", "schep", "schepje", "schepjes",
        "stengel", "stengels", "ca", "circa", "±", "x", "à", "a",
    )

    private val quantity = Regex("""^[\d½¼¾⅓⅔⅛.,/–-]+(\p{L}*)\.?$""")

    /** De regel zonder hoeveelheid, eenheid en (toelichting), maar mét alles na een komma. */
    private fun withoutQuantity(raw: String): String {
        val words = raw.trim().split(Regex("""\s+""")).toMutableList()
        while (words.isNotEmpty()) {
            val w = words.first().lowercase(Locale.ROOT).trimEnd('.')
            val q = quantity.matchEntire(w)
            val isQuantity = q != null && (q.groupValues[1].isEmpty() || q.groupValues[1] in units)
            if (isQuantity || w in units || w == "tot") words.removeAt(0) else break
        }
        return words.joinToString(" ").replace(Regex("""\s*\(.*?\)"""), "").trim()
    }

    /** "200 gr kipfilet, in blokjes" → "kipfilet". Valt terug op de hele regel als er niets overblijft. */
    fun ingredientName(raw: String): String =
        withoutQuantity(raw).substringBefore(',').trim().ifEmpty { raw.trim() }

    /** `null` als de regel niet meetelt: basis (zout, peper, olie, water), "naar smaak", optioneel of een kopje ("Voor de saus:"). */
    fun analyze(raw: String): PantryLine? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.endsWith(':')) return null
        val tokens = pantryTokens(trimmed)
        if (tokens.isEmpty() || "optioneel" in tokens || tokens.containsPhrase(listOf("naar", "smaak"))) return null
        val name = ingredientName(trimmed)
        // Hele regel (ook na de komma): "peper, zout en kipkruiden" is geen basis.
        val words = pantryTokens(withoutQuantity(trimmed)).filter { w -> w.none(Char::isDigit) }
        if (words.isEmpty() || words.all { it in stapleWords || it in units }) return null
        val items = PantryCatalog
            .filter { item -> item.variants.any(tokens::containsPhrase) && item.excludes.none(tokens::containsPhrase) }
            .map { it.key }
            .toSet()
        return PantryLine(trimmed, name, items, tokens)
    }

    fun index(recipes: List<Pair<RecipeEntity, List<String>>>): List<PantryRecipe> =
        recipes.map { (r, lines) -> PantryRecipe(r, lines.mapNotNull(::analyze)) }

    /** Het lijst-item dat precies dit woord (of deze woordgroep) is, bv. "Kipfilet" → kip. */
    fun catalogItemFor(text: String): PantryItem? {
        val t = pantryTokens(text)
        return PantryCatalog.firstOrNull { t in it.variants }
    }

    /** Eigen woorden matchen op het begin van het laatste woord: "halloumi" vindt "halloumiblokjes". */
    private fun PantryLine.isCovered(selected: Set<String>, custom: List<List<String>>): Boolean =
        items.any { it in selected } || custom.any { tokens.containsPhrase(it, prefixLast = true) }

    /** Heb je [line] in huis, met deze keuze uit "In huis"? (Voor de boodschappenlijst.) */
    fun covers(line: PantryLine, selected: Set<String>, custom: List<String>): Boolean =
        line.isCovered(selected, custom.map(::pantryTokens).filter { it.isNotEmpty() })

    /**
     * De beste recepten bij wat je in huis hebt: minstens één treffer, hoogste aandeel eerst, dan meeste treffers.
     */
    fun match(recipes: List<PantryRecipe>, selected: Set<String>, custom: List<String>, limit: Int = MAX_RESULTS): List<PantryMatch> {
        val customTokens = custom.map(::pantryTokens).filter { it.isNotEmpty() }
        if (selected.isEmpty() && customTokens.isEmpty()) return emptyList()
        return recipes.asSequence()
            .filter { it.lines.isNotEmpty() }
            .map { pr ->
                val have = pr.lines.map { it.isCovered(selected, customTokens) }
                PantryMatch(pr.recipe, have, pr.lines.filterIndexed { i, _ -> !have[i] }.map { it.name })
            }
            .filter { it.haveCount > 0 }
            .sortedWith(compareByDescending<PantryMatch> { it.score }.thenByDescending { it.haveCount }.thenBy { it.recipe.title })
            .take(limit)
            .toList()
    }

    /** Lijst-items die in je recepten voorkomen, de meest gebruikte (in de meeste recepten) eerst. */
    fun usedItems(recipes: List<PantryRecipe>): List<PantryItem> {
        val counts = recipes.flatMap { pr -> pr.lines.flatMap { it.items }.distinct() }.groupingBy { it }.eachCount()
        return PantryCatalog.filter { it.key in counts }.sortedByDescending { counts.getValue(it.key) }
    }
}
