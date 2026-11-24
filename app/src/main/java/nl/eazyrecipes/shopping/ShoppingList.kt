package nl.eazyrecipes.shopping

import nl.eazyrecipes.recipe.IngredientParts
import nl.eazyrecipes.recipe.formatQuantity
import nl.eazyrecipes.ui.pantry.PantryMatcher
import nl.eazyrecipes.ui.pantry.pantryTokens
import java.util.Locale
import kotlin.math.ceil

/** Een recept op de lijst, met het aantal porties waarvoor je inkoopt. */
data class ShoppingRecipe(
    val id: String,
    val title: String,
    /** Porties volgens het recept; `null` = onbekend, dan wordt er niet omgerekend. */
    val baseServings: Int?,
    /** Porties waarvoor je inkoopt; `null` = zoals in het recept. */
    val servings: Int?,
    val lines: List<String>,
) {
    val factor: Double
        get() = if (baseServings != null && baseServings > 0 && servings != null && servings > 0) servings.toDouble() / baseServings else 1.0
}

/** Iets wat je zelf op de lijst zet ("wc-papier", "2 pakken melk"). */
data class ExtraItem(val id: String, val text: String, val checked: Boolean)

/** Waar een regel op de lijst vandaan komt: recept + de regel zoals hij in het recept staat (omgerekend). */
data class LineSource(val recipeId: String, val recipeTitle: String, val text: String)

enum class HaveReason {
    /** Zout, peper, olie, water: dat heeft bijna iedereen. */
    BASIS,
    /** Aangetikt bij "In huis". */
    IN_HUIS,
}

data class ShoppingLine(
    /** Blijft hetzelfde zolang de lijst niet verandert; daarmee wordt afvinken onthouden. Eigen items: `x:<id>`. */
    val key: String,
    val name: String,
    /** "500 g", "3", "300 g + 2 el", of leeg. */
    val amount: String,
    val aisle: Aisle,
    val sources: List<LineSource>,
    val checked: Boolean,
    /** Niet `null` = staat onder "Heb je al". */
    val have: HaveReason? = null,
    /** Eigen item: het id in de database. */
    val extraId: String? = null,
) {
    val text: String get() = if (amount.isEmpty()) name else "$amount $name"
}

data class ShoppingSection(val aisle: Aisle, val lines: List<ShoppingLine>)

data class ShoppingListState(
    /** Nog te halen, per afdeling. */
    val sections: List<ShoppingSection>,
    /** Afgevinkt (in je mandje). */
    val done: List<ShoppingLine>,
    /** Heb je waarschijnlijk al (basis of "In huis"); niet afgevinkt. */
    val have: List<ShoppingLine>,
    val recipes: List<ShoppingRecipe>,
) {
    val toBuy: Int get() = sections.sumOf { it.lines.size }
    val isEmpty: Boolean get() = sections.isEmpty() && done.isEmpty() && have.isEmpty()
}

/**
 * Maakt van recepten (met porties) en eigen items één lijst.
 * Gelijke ingrediënten worden samengevoegd ("3 uien" + "1 ui" = 4 uien) en per soort eenheid opgeteld
 * (gr/g/kg, ml/dl/l, el, tl, stuks). Wat niet op te tellen is, komt erachter ("300 g + 2 blikjes").
 */
object ShoppingList {

    fun build(
        recipes: List<ShoppingRecipe>,
        extras: List<ExtraItem> = emptyList(),
        checked: Set<String> = emptySet(),
        pantryKeys: Set<String> = emptySet(),
        pantryCustom: List<String> = emptyList(),
    ): ShoppingListState {
        val entries = recipes.flatMap { r -> r.lines.mapNotNull { entry(r, it, pantryKeys, pantryCustom) } }
        val groups = group(entries)
        val lines = groups.map(::merge).map { it.copy(checked = it.key in checked) } +
            extras.filter { it.text.isNotBlank() }.map(::extraLine)

        val byName = compareBy<ShoppingLine> { it.name.lowercase(Locale.ROOT) }
        val open = lines.filter { !it.checked && it.have == null }
        return ShoppingListState(
            sections = open.groupBy { it.aisle }.toSortedMap().map { (a, l) -> ShoppingSection(a, l.sortedWith(byName)) },
            done = lines.filter { it.checked }.sortedWith(compareBy<ShoppingLine> { it.aisle }.then(byName)),
            have = lines.filter { !it.checked && it.have != null }.sortedWith(byName),
            recipes = recipes,
        )
    }

    /** De lijst als tekst om te delen (alleen wat je nog moet halen). */
    fun asText(state: ShoppingListState): String = buildString {
        appendLine("Boodschappen")
        state.sections.forEach { s ->
            appendLine()
            appendLine(s.aisle.label)
            s.lines.forEach { appendLine("• ${it.text}") }
        }
        if (state.recipes.isNotEmpty()) {
            appendLine()
            appendLine("Voor: " + state.recipes.joinToString(", ") { it.title })
        }
        appendLine()
        append("Gedeeld met EazyRecipes")
    }

    // --- één regel uit een recept ---

    private data class Entry(
        val name: String,
        /** Genormaliseerde woorden van [name]; het laatste woord mag in meervoud staan. */
        val words: List<String>,
        val amount: Amount?,
        val aisle: Aisle,
        val source: LineSource,
        val have: HaveReason?,
    )

    private fun entry(r: ShoppingRecipe, raw: String, pantryKeys: Set<String>, pantryCustom: List<String>): Entry? {
        // Kopjes ("Voor de saus:") zijn geen ingrediënt.
        if (raw.isBlank() || raw.trim().endsWith(':')) return null
        // "Peterselie naar smaak" en "koriander (optioneel)" moet je wél halen; "In huis" telt ze niet mee, de lijst wel.
        val line = raw.replace(optional, " ").replace(Regex("""\s+"""), " ").trim().trimEnd(',').trim()
        if (line.isEmpty()) return null
        val parts = IngredientParts.parse(line)
        val name = shoppingName(PantryMatcher.ingredientName(parts.name))
        val words = pantryTokens(name)
        if (words.isEmpty()) return null
        val analyzed = PantryMatcher.analyze(line)
        val have = when {
            analyzed == null -> HaveReason.BASIS
            PantryMatcher.covers(analyzed, pantryKeys, pantryCustom) -> HaveReason.IN_HUIS
            else -> null
        }
        val shown = if (parts.low != null) listOf(parts.amount(r.factor), parts.name).filter { it.isNotEmpty() }.joinToString(" ") else line
        return Entry(
            name = name,
            words = words,
            amount = parts.low?.let { Amount.of(it * r.factor, parts.high?.times(r.factor), parts.unit) },
            aisle = Aisles.of(name, analyzed?.items.orEmpty()),
            source = LineSource(r.id, r.title, shown),
            have = have,
        )
    }

    // --- samenvoegen ---

    /**
     * Groepeert regels met dezelfde naam, waarbij het laatste woord in enkel- of meervoud mag staan
     * ("ui"/"uien", "tomaat"/"tomaten", "wortel"/"wortels"/"wortelen"). Volgorde: eerste voorkomen.
     */
    private fun group(entries: List<Entry>): List<List<Entry>> {
        val names = entries.map { it.words.joinToString(" ") }.distinct()
        val parent = names.associateWith { it }.toMutableMap()
        fun find(n: String): String { var x = n; while (parent.getValue(x) != x) x = parent.getValue(x); return x }
        val byName = names.toSet()
        for (n in names) {
            for (c in singularCandidates(n)) {
                if (c != n && c in byName) {
                    val a = find(n); val b = find(c)
                    if (a != b) parent[maxOf(a, b)] = minOf(a, b)
                }
            }
        }
        return entries.groupBy { find(it.words.joinToString(" ")) }.values.toList()
    }

    /** Mogelijke enkelvouden van een (Nederlandse) woordgroep; alleen het laatste woord verandert. */
    internal fun singularCandidates(phrase: String): Set<String> {
        val head = phrase.substringBeforeLast(' ', "")
        val w = phrase.substringAfterLast(' ')
        val forms = mutableSetOf(w)
        if (w.length > 3 && w.endsWith("s")) forms += w.dropLast(1) // wortels, paprikas, gambas
        if (w.length > 4 && w.endsWith("eren")) forms += w.dropLast(4) // eieren
        if (w.length > 3 && w.endsWith("en")) {
            val stem = w.dropLast(2) // uien, citroenen, wortelen
            forms += stem
            // tomaten → tomaat, bonen → boon: één klinker tussen medeklinkers wordt dubbel.
            if (stem.length >= 3) {
                val (a, v, c) = Triple(stem[stem.length - 3], stem[stem.length - 2], stem.last())
                if (v in vowels && a !in vowels && c !in vowels) forms += stem.dropLast(1) + v + c
            }
            // kippendijen → kippendij is al gedekt; bessen → bes: dubbele medeklinker wordt enkel.
            if (stem.length >= 2 && stem.last() == stem[stem.length - 2] && stem.last() !in vowels) forms += stem.dropLast(1)
        }
        return forms.map { if (head.isEmpty()) it else "$head $it" }.toSet()
    }

    private const val vowels = "aeiou"

    /** Hoe het eruitziet of klaargemaakt wordt; voor de winkel maakt dat niet uit ("60 g koude boter" = boter). */
    private val stateWords = setOf(
        "koude", "koud", "zachte", "zacht", "gesmolten", "verse", "vers", "gehakte", "fijngehakte", "grofgehakte", "fijngesneden",
        "gesneden", "kleine", "klein", "grote", "groot", "middelgrote", "rijpe", "flinke", "ruime", "lauwe", "lauwwarme", "ontdooide",
    )

    /** "boter of margarine" → "boter", "koude boter" → "boter"; blijft er niets over, dan de naam zoals hij was. */
    internal fun shoppingName(name: String): String {
        val first = name.trim().trimEnd('.', ' ').split(Regex("""\s+of\s+""", RegexOption.IGNORE_CASE)).first().trim()
        val words = first.split(Regex("""\s+""")).toMutableList()
        while (words.size > 1 && words.first().lowercase(Locale.ROOT) in stateWords) words.removeAt(0)
        return words.joinToString(" ").ifEmpty { name.trim() }
    }

    private val optional = Regex("""(?i)[(\[]?\s*(?:optioneel|naar\s+smaak)\s*[)\]]?:?""")

    private fun merge(group: List<Entry>): ShoppingLine {
        val first = group.first()
        val total = Amount.sum(group.mapNotNull { it.amount })
        // Bij stuks: meer dan één = de langste (meervouds)vorm, anders de kortste: "4 uien", "1 ui".
        val names = group.map { it.name }.distinct()
        val pieces = total.firstOrNull { it.kind == Amount.Kind.COUNT }
        val name = when {
            names.size == 1 || pieces == null -> first.name
            ceil(pieces.high ?: pieces.low) > 1.0 -> names.maxBy { it.length }
            else -> names.minBy { it.length }
        }
        val key = "i:" + group.map { it.words.joinToString(" ") }.min()
        return ShoppingLine(
            key = key,
            name = name,
            amount = total.joinToString(" + ") { it.format() },
            aisle = first.aisle,
            sources = group.map { it.source },
            checked = false,
            have = if (group.all { it.have != null }) first.have else null,
        )
    }

    private fun extraLine(x: ExtraItem): ShoppingLine {
        val parts = IngredientParts.parse(x.text.trim())
        val name = if (parts.low != null) parts.name else x.text.trim()
        return ShoppingLine(
            key = "x:${x.id}",
            name = name,
            amount = if (parts.low != null) parts.amountText else "",
            aisle = Aisles.of(name, PantryMatcher.analyze(name)?.items.orEmpty()),
            sources = emptyList(),
            checked = x.checked,
            extraId = x.id,
        )
    }
}

/** Een hoeveelheid in een basiseenheid: gram, milliliter, eetlepel, theelepel, stuks of een eigen eenheid ("blikje"). */
internal data class Amount(val kind: Kind, val low: Double, val high: Double?, val unit: String) {
    enum class Kind { MASS, VOLUME, SPOON, TEASPOON, COUNT, OTHER }

    fun format(): String = when (kind) {
        Kind.MASS -> metric(low, high, 1000.0, "g", "kg")
        Kind.VOLUME -> metric(low, high, 1000.0, "ml", "l")
        Kind.SPOON -> range(low, high, null) + " el"
        Kind.TEASPOON -> range(low, high, null) + " tl"
        // Stuks koop je heel: ½ citroen = 1 citroen, 1½ komkommer = 2.
        Kind.COUNT -> range(ceil(low - 1e-9), high?.let { ceil(it - 1e-9) }, null)
        Kind.OTHER -> range(low, high, null) + " " + unitForm(unit, plural = (high ?: low) > 1.0)
    }

    /** Vanaf 1000 g/ml in kg/l met hoogstens één decimaal ("1,1 kg"), daaronder in g/ml (op 5 afgerond vanaf 100). */
    private fun metric(low: Double, high: Double?, step: Double, small: String, big: String): String {
        if (low < step) return range(low, high, small) + " $small"
        fun dec(v: Double): String {
            val r = Math.round(v / step * 10) / 10.0
            return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
        }
        return dec(low) + (high?.takeIf { dec(it) != dec(low) }?.let { "-" + dec(it) } ?: "") + " $big"
    }

    private fun range(low: Double, high: Double?, unit: String?): String =
        formatQuantity(low, unit) + (high?.takeIf { it > low + 1e-9 }?.let { "-" + formatQuantity(it, unit) } ?: "")

    companion object {
        private val mass = mapOf("g" to 1.0, "gr" to 1.0, "gram" to 1.0, "grams" to 1.0, "kg" to 1000.0, "kilo" to 1000.0, "mg" to 0.001)
        private val volume = mapOf("ml" to 1.0, "milliliter" to 1.0, "cl" to 10.0, "dl" to 100.0, "l" to 1000.0, "liter" to 1000.0)
        private val spoon = setOf("el", "eetlepel", "eetlepels", "lepel", "lepels")
        private val teaspoon = setOf("tl", "theelepel", "theelepels")
        private val count = setOf("stuk", "stuks")

        /** Enkelvoud → meervoud van de overige eenheden; ook om ze samen te voegen ("blikje" = "blikjes"). */
        private val forms = listOf(
            "teentje" to "teentjes", "teen" to "tenen", "blik" to "blikken", "blikje" to "blikjes", "pak" to "pakken",
            "pakje" to "pakjes", "zak" to "zakken", "zakje" to "zakjes", "bos" to "bossen", "bosje" to "bosjes", "takje" to "takjes",
            "plak" to "plakken", "plakje" to "plakjes", "snufje" to "snufjes", "snuf" to "snufjes", "scheut" to "scheuten",
            "scheutje" to "scheutjes", "handje" to "handjes", "handvol" to "handvol", "kop" to "koppen", "kopje" to "kopjes",
            "cup" to "cups", "pot" to "potten", "potje" to "potjes", "fles" to "flessen", "flesje" to "flesjes", "schep" to "scheppen",
            "schepje" to "schepjes", "stengel" to "stengels", "mespunt" to "mespunten", "mespuntje" to "mespuntjes",
            "bakje" to "bakjes", "stukje" to "stukjes",
        )
        private val singularOf: Map<String, String> = forms.flatMap { (s, p) -> listOf(s to s, p to s) }.toMap()

        fun unitForm(singular: String, plural: Boolean): String =
            if (plural) forms.firstOrNull { it.first == singular }?.second ?: singular else singular

        fun of(low: Double, high: Double?, unit: String?): Amount {
            val u = unit?.lowercase(Locale.ROOT)
            return when {
                u == null || u in count -> Amount(Kind.COUNT, low, high, "")
                u in mass -> mass.getValue(u).let { Amount(Kind.MASS, low * it, high?.times(it), "g") }
                u in volume -> volume.getValue(u).let { Amount(Kind.VOLUME, low * it, high?.times(it), "ml") }
                u in spoon -> Amount(Kind.SPOON, low, high, "el")
                u in teaspoon -> Amount(Kind.TEASPOON, low, high, "tl")
                else -> Amount(Kind.OTHER, low, high, singularOf[u] ?: u)
            }
        }

        /** Telt op per soort (en per eigen eenheid). Bij een bereik telt de bovengrens van elk deel (of zijn enige getal). */
        fun sum(amounts: List<Amount>): List<Amount> =
            amounts.groupBy { it.kind to it.unit }.toSortedMap(compareBy({ it.first }, { it.second })).map { (k, list) ->
                val low = list.sumOf { it.low }
                val high = if (list.any { it.high != null }) list.sumOf { it.high ?: it.low } else null
                Amount(k.first, low, high, k.second)
            }
    }
}
