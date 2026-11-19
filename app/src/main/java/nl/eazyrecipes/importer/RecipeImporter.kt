package nl.eazyrecipes.importer

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface ImportResult {
    data class Success(val recipe: ParsedRecipe, val usedBrowser: Boolean) : ImportResult

    /** De pagina laadde wel, maar bevat geen recept-JSON-LD. Later: OCR/handmatig/AI als alternatief. */
    data class NoRecipe(val url: String) : ImportResult

    data class Failed(val reason: Reason, val httpCode: Int? = null, val cause: Throwable? = null) : ImportResult

    enum class Reason { INVALID_URL, NETWORK, BLOCKED, HTTP }
}

/**
 * URL → [ParsedRecipe]. Eerst HTTP. Lukt dat niet (blokkade, of HTML zonder recept, bv. door JavaScript
 * opgebouwd), dan via de [browser] (een onzichtbare WebView die door Cloudflare-controles komt).
 */
class RecipeImporter(
    private val http: PageFetcher,
    private val browser: PageFetcher?,
) {
    suspend fun import(rawUrl: String): ImportResult {
        val url = cleanUrl(rawUrl) ?: return ImportResult.Failed(ImportResult.Reason.INVALID_URL)

        val first = http.fetch(url)
        if (first is FetchResult.Page) parse(first)?.let { return ImportResult.Success(it, usedBrowser = false) }

        val tryBrowser = when (first) {
            is FetchResult.Page -> true
            is FetchResult.HttpError -> first.code in blockedCodes
            is FetchResult.Failed -> false // geen netwerk: de WebView gaat ook niet helpen
        }
        if (tryBrowser && browser != null) {
            when (val second = browser.fetch(url)) {
                is FetchResult.Page -> return parse(second)?.let { ImportResult.Success(it, usedBrowser = true) }
                    ?: ImportResult.NoRecipe(url)
                is FetchResult.HttpError -> return failure(second)
                is FetchResult.Failed -> Unit // val terug op wat HTTP opleverde
            }
        }
        return when (first) {
            is FetchResult.Page -> ImportResult.NoRecipe(url)
            is FetchResult.HttpError -> failure(first)
            is FetchResult.Failed -> ImportResult.Failed(ImportResult.Reason.NETWORK, cause = first.cause)
        }
    }

    private fun parse(page: FetchResult.Page): ParsedRecipe? =
        JsonLdRecipeParser.parse(page.html, cleanUrl(page.finalUrl) ?: page.finalUrl)

    private fun failure(e: FetchResult.HttpError) = ImportResult.Failed(
        if (e.code in blockedCodes) ImportResult.Reason.BLOCKED else ImportResult.Reason.HTTP,
        httpCode = e.code,
    )

    companion object {
        private val blockedCodes = setOf(401, 403, 429, 503)

        private val trackingParams = Regex("""^(utm_.*|fbclid|gclid|dclid|msclkid|mc_cid|mc_eid|igshid|si|ref|_ga)$""", RegexOption.IGNORE_CASE)

        /** Alleen http(s). Haalt trackingparameters en het #fragment weg, zodat dezelfde link hetzelfde recept is. */
        fun cleanUrl(raw: String): String? {
            val url = raw.trim().toHttpUrlOrNull() ?: return null
            val b = url.newBuilder().fragment(null)
            url.queryParameterNames.filter { trackingParams.matches(it) }.forEach { b.removeAllQueryParameters(it) }
            return b.build().toString()
        }
    }
}
