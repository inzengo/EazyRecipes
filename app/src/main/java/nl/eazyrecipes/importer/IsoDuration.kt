package nl.eazyrecipes.importer

private val isoDuration = Regex(
    """^P(?:(\d+(?:[.,]\d+)?)D)?(?:T(?:(\d+(?:[.,]\d+)?)H)?(?:(\d+(?:[.,]\d+)?)M)?(?:(\d+(?:[.,]\d+)?)S)?)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * ISO-8601-duur (`PT1H30M`, `PT0H30M`, `P0DT45M`) → minuten (afgerond).
 * Geeft `null` bij lege of onleesbare waarden, en ook bij 0: "0 minuten" betekent op receptsites "onbekend".
 */
fun parseIsoDurationMinutes(value: String?): Int? {
    val s = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val m = isoDuration.matchEntire(s) ?: return null
    if (m.groupValues.drop(1).all { it.isEmpty() }) return null
    fun g(i: Int) = m.groupValues[i].replace(',', '.').toDoubleOrNull() ?: 0.0
    val minutes = g(1) * 24 * 60 + g(2) * 60 + g(3) + g(4) / 60
    return Math.round(minutes).toInt().takeIf { it > 0 }
}
