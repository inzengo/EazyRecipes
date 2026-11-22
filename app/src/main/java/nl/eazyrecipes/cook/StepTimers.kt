package nl.eazyrecipes.cook

import java.util.Locale

/** Een tijd die in een stap genoemd wordt ("10 minuten", "1,5 uur", "5-7 min"). */
data class StepTimer(
    val seconds: Int,
    /** Zoals hij op de knop staat: "10 min", "1 uur 15 min". */
    val label: String,
)

/**
 * Vindt tijden in een bereidingsstap. Bij een bereik ("8-10 minuten") nemen we de ondergrens: dan kijk je op tijd
 * of het klaar is. "1 uur en 15 minuten" wordt één timer. Hooguit [MAX] timers per stap, dubbele één keer.
 */
object StepTimers {
    const val MAX = 3

    private val words = mapOf(
        "een" to 1.0, "één" to 1.0, "twee" to 2.0, "drie" to 3.0, "vier" to 4.0, "vijf" to 5.0, "zes" to 6.0, "zeven" to 7.0,
        "acht" to 8.0, "negen" to 9.0, "tien" to 10.0, "elf" to 11.0, "twaalf" to 12.0, "vijftien" to 15.0, "twintig" to 20.0,
        "dertig" to 30.0, "veertig" to 40.0, "anderhalf" to 1.5, "anderhalve" to 1.5, "half" to 0.5, "halve" to 0.5,
    )

    /** Getal: "10", "1,5", "1/2", "1½", "½", of een woord ("tien", "een half"). Langste woorden eerst. */
    private val NUM = """\d+\s*/\s*\d+|\d+(?:[.,]\d+)?\s*½|½|\d+(?:[.,]\d+)?|een\s+half|""" +
        words.keys.sortedByDescending { it.length }.joinToString("|")

    /** Afkortingen ("min", "m", "u", "s") alleen na een cijfer: "een min of meer" is geen timer. */
    private const val UNIT_FULL = """uur|uren|kwartier|minuten|minuutjes|minuut|seconden"""
    private const val UNIT_SHORT = """min|sec|m|u|s"""

    private val single = Regex(
        """(?<![\p{L}\d/])($NUM)(?:\s*(?:-|–|tot|à|a|of)\s*(\d+(?:[.,]\d+)?))?\s*($UNIT_FULL|$UNIT_SHORT)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    /** "1 uur en 15 minuten", "1 uur 15 min" → samen. */
    private val hoursAndMinutes = Regex(
        """(?<![\p{L}\d/])($NUM)\s*(?:uur|u)\s*(?:en\s*)?(\d+)\s*(?:minuten|minuut|min)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String): List<StepTimer> {
        val found = mutableListOf<Pair<Int, Int>>() // positie → seconden
        val taken = mutableListOf<IntRange>()
        hoursAndMinutes.findAll(text).forEach { m ->
            val h = amount(m.groupValues[1]) ?: return@forEach
            found += m.range.first to (h * 3600 + m.groupValues[2].toInt() * 60).toInt()
            taken += m.range
        }
        single.findAll(text).forEach { m ->
            if (taken.any { m.range.first in it }) return@forEach
            val unit = m.groupValues[3].lowercase(Locale.ROOT)
            val num = m.groupValues[1]
            if (!num.first().isDigit() && num.first() != '½' && unit.matches(Regex(UNIT_SHORT))) return@forEach
            val n = amount(num) ?: return@forEach
            val perUnit = when {
                unit == "kwartier" -> 900
                unit.startsWith("u") -> 3600
                unit.startsWith("m") -> 60
                else -> 1
            }
            val seconds = (n * perUnit).toInt()
            // Onzinnig voor een kooktimer: korter dan 10 seconden of langer dan een dag.
            if (seconds in 10..86_400) found += m.range.first to seconds
        }
        return found.sortedBy { it.first }.map { it.second }.distinct().take(MAX).map { StepTimer(it, label(it)) }
    }

    private fun amount(text: String): Double? {
        val t = text.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
        if (t == "een half") return 0.5
        words[t]?.let { return it }
        Regex("""(\d+) ?/ ?(\d+)""").matchEntire(t)?.let { m ->
            val d = m.groupValues[2].toDouble()
            return if (d == 0.0) null else m.groupValues[1].toDouble() / d
        }
        if (t.endsWith("½")) return 0.5 + (t.dropLast(1).trim().ifEmpty { "0" }.replace(',', '.').toDoubleOrNull() ?: return null)
        return t.replace(',', '.').toDoubleOrNull()
    }

    fun label(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return listOfNotNull(
            if (h > 0) "$h uur" else null,
            if (m > 0) "$m min" else null,
            if (s > 0) "$s sec" else null,
        ).joinToString(" ")
    }
}

/** "4:05" of "1:02:05" voor een aftellende timer. */
fun formatCountdown(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
