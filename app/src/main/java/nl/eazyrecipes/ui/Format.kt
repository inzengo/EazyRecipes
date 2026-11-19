package nl.eazyrecipes.ui

/** 25 → "25 min", 60 → "1 uur", 100 → "1 u 40", null → null. */
fun formatMinutes(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h uur"
        else -> "$h u $m"
    }
}

/** Host zonder `www.` voor het bron-label, bv. "leukerecepten.nl". */
fun siteLabel(url: String?): String? =
    url?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.lowercase()?.removePrefix("www.")
