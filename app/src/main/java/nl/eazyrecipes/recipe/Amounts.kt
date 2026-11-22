package nl.eazyrecipes.recipe

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Een ingrediëntregel opgesplitst in hoeveelheid, eenheid en naam: "200 gr kipfilet" → 200 · "gr" · "kipfilet".
 * Alleen een hoeveelheid vooraan telt; "kipfilet (ca. 200 g)" heeft er dus geen. Regels zonder hoeveelheid
 * blijven bij omrekenen precies zoals ze waren.
 */
data class IngredientParts(
    /** `null` = geen hoeveelheid herkend. */
    val low: Double?,
    /** Bovengrens bij een bereik ("2-3 tomaten"). */
    val high: Double? = null,
    /** Eenheid zoals geschreven ("gr", "el", "teentjes"), of `null`. */
    val unit: String? = null,
    val name: String,
    /** Het hoeveelheid-deel zoals het in de bron stond ("1½ el"), voor weergave zonder omrekenen. */
    val amountText: String = "",
) {
    /**
     * Hoeveelheid + eenheid, vermenigvuldigd met [factor]. Bij factor 1 wordt niets afgerond (112 g blijft 112 g),
     * alleen netjes geschreven ("0.5" → "½").
     */
    fun amount(factor: Double): String {
        if (low == null) return ""
        val exact = abs(factor - 1.0) < 1e-9
        val n = formatQuantity(low * factor, unit, round = !exact) + (high?.let { "-" + formatQuantity(it * factor, unit, round = !exact) } ?: "")
        return if (unit != null) "$n $unit" else n
    }

    companion object {
        private val glyphs = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)
        private const val G = "[½¼¾⅓⅔⅛]"
        private const val NUM = """(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?\s*$G?|$G)"""

        /** Eenheden die we herkennen achter een getal; langste eerst, zodat "eetlepels" niet als "e…" eindigt. */
        private val units = listOf(
            "gram", "grams", "gr", "g", "kilo", "kg", "mg", "milliliter", "ml", "cl", "dl", "liter", "l",
            "eetlepels", "eetlepel", "el", "theelepels", "theelepel", "tl", "lepels", "lepel",
            "kopjes", "kopje", "kop", "cups", "cup", "stuks", "stuk", "stukjes", "stukje",
            "blikjes", "blikje", "blikken", "blik", "pakjes", "pakje", "pakken", "pak", "zakjes", "zakje", "zak",
            "teentjes", "teentje", "tenen", "teen", "snufjes", "snufje", "snuf", "scheutjes", "scheutje", "scheut",
            "handjes", "handje", "handvol", "bosjes", "bosje", "bos", "takjes", "takje", "plakjes", "plakje", "plakken", "plak",
            "mespuntje", "mespunt", "bakjes", "bakje", "potjes", "potje", "pot", "flesje", "fles", "schepjes", "schepje", "schep",
            "stengels", "stengel",
        ).sortedByDescending { it.length }

        private val pattern = Regex(
            """^\s*($NUM)(?:\s*(?:-|–|tot|à|a|of)\s*($NUM))?(?:\s*(${units.joinToString("|")})(?![\p{L}]))?\.?\s*(.*)$""",
            RegexOption.IGNORE_CASE,
        )

        fun parse(raw: String): IngredientParts {
            // Sommige sites schrijven breuken met een breukstreep (1⁄2) in plaats van een slash.
            val line = raw.trim().replace('⁄', '/').replace('∕', '/')
            val m = pattern.matchEntire(line) ?: return IngredientParts(null, name = line)
            val (a, b, unit, rest) = m.destructured
            val low = number(a) ?: return IngredientParts(null, name = line)
            val name = rest.trim()
            // Alleen een getal ("4") zonder naam: geen ingrediënt, laat staan.
            if (name.isEmpty()) return IngredientParts(null, name = line)
            val amountText = line.substring(0, line.length - rest.length).trim()
            return IngredientParts(low, b.takeIf { it.isNotEmpty() }?.let(::number), unit.ifEmpty { null }, name, amountText)
        }

        /** "1 1/2", "1/2", "1,5", "1½", "½" → getal. */
        internal fun number(text: String): Double? {
            val t = text.replace(Regex("""\s+"""), " ").trim()
            if (t.isEmpty()) return null
            val glyph = glyphs[t.last()]
            if (glyph != null) {
                val whole = t.dropLast(1).trim()
                return glyph + (if (whole.isEmpty()) 0.0 else whole.replace(',', '.').toDoubleOrNull() ?: return null)
            }
            Regex("""(\d+) (\d+)/(\d+)""").matchEntire(t)?.let { m ->
                val (w, n, d) = m.destructured
                return if (d.toInt() == 0) null else w.toDouble() + n.toDouble() / d.toDouble()
            }
            Regex("""(\d+)/(\d+)""").matchEntire(t)?.let { m ->
                val (n, d) = m.destructured
                return if (d.toInt() == 0) null else n.toDouble() / d.toDouble()
            }
            // "1.000" en "1.500" zijn duizendtallen (Nederlands), geen 1,0 en 1,5.
            if (Regex("""\d{1,3}(\.\d{3})+""").matches(t)) return t.replace(".", "").toDoubleOrNull()
            return t.replace(',', '.').toDoubleOrNull()
        }
    }
}

private val gramLike = setOf("g", "gr", "gram", "grams", "ml", "milliliter")

private val niceFractions = listOf(0.0 to "", 0.125 to "⅛", 0.25 to "¼", 1.0 / 3 to "⅓", 0.5 to "½", 2.0 / 3 to "⅔", 0.75 to "¾", 1.0 to "")

/**
 * Leesbaar getal voor in de keuken: grammen afgerond op 5 (vanaf 100), boven de 10 hele getallen,
 * daaronder breuken (1½, ¾) of één decimaal met komma. Met [round] = false alleen netjes geschreven, niet afgerond.
 */
fun formatQuantity(value: Double, unit: String? = null, round: Boolean = true): String {
    if (value <= 0.0) return "0"
    val u = unit?.lowercase(Locale.ROOT)
    if (round && u in gramLike && value >= 100) return ((value / 5).roundToInt() * 5).toString()
    if (round && value >= 10) return value.roundToInt().toString()
    val whole = floor(value).toInt()
    val frac = value - whole
    val (f, glyph) = niceFractions.minBy { abs(it.first - frac) }
    val w = if (f == 1.0) whole + 1 else whole
    if (abs(f - frac) <= (if (round) 0.04 else 0.01) && !(w == 0 && glyph.isEmpty())) {
        return if (glyph.isEmpty()) w.toString() else if (w == 0) glyph else "$w$glyph"
    }
    // Heel kleine hoeveelheden (0,05) houden twee decimalen, anders worden ze "0".
    val scale = if (value < 0.1) 100 else 10
    val rounded = (value * scale).roundToInt().toDouble() / scale
    return if (rounded == floor(rounded)) rounded.toInt().toString() else rounded.toString().replace('.', ',')
}
