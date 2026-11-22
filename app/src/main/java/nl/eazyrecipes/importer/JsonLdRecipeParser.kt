package nl.eazyrecipes.importer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import nl.eazyrecipes.data.DefaultCategories

/**
 * Haalt een schema.org `Recipe` uit de JSON-LD van een HTML-pagina.
 *
 * Kan omgaan met: meerdere `<script type="application/ld+json">`-blokken, `@graph`, arrays op topniveau,
 * `@type` als lijst, `HowToSection`/`HowToStep`, instructies als losse tekst, en afbeeldingen als string,
 * lijst of `ImageObject`.
 */
object JsonLdRecipeParser {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { isLenient = true; ignoreUnknownKeys = true; allowTrailingComma = true }
    private val blockEnd = Regex("""(?i)<br\s*/?>|</p>|</li>|</div>""")

    /** @return het recept, of `null` als de pagina geen (bruikbare) `Recipe`-JSON-LD heeft. */
    fun parse(html: String, pageUrl: String? = null): ParsedRecipe? {
        val doc = Jsoup.parse(html, pageUrl.orEmpty())
        val recipe = doc.select("script[type*=ld+json]")
            .asSequence()
            .mapNotNull { parseJson(it.data()) }
            .flatMap { findRecipes(it) }
            .firstOrNull()
            ?: return null
        return toParsed(recipe, pageUrl)
    }

    private fun parseJson(raw: String): JsonElement? {
        val text = raw.trim().removePrefix("<!--").removeSuffix("-->").trim()
        if (text.isEmpty()) return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull()
            // Sommige sites zetten rauwe regeleinden/tabs in strings; dat is ongeldige JSON.
            ?: runCatching { json.parseToJsonElement(text.replace(Regex("[\\u0000-\\u001F]"), " ")) }.getOrNull()
    }

    private fun findRecipes(element: JsonElement): Sequence<JsonObject> = sequence {
        when (element) {
            is JsonArray -> element.forEach { yieldAll(findRecipes(it)) }
            is JsonObject -> {
                if (element.types().any { it.equals("Recipe", ignoreCase = true) }) yield(element)
                element["@graph"]?.let { yieldAll(findRecipes(it)) }
                element["mainEntity"]?.let { yieldAll(findRecipes(it)) }
            }
            else -> Unit
        }
    }

    private fun toParsed(r: JsonObject, pageUrl: String?): ParsedRecipe? {
        val title = r.text("name") ?: r.text("headline") ?: return null
        val yields = r["recipeYield"].strings()
        val keywords = r["keywords"].strings().flatMap { it.split(',') }.map { it.cleanText() }.filter { it.isNotEmpty() }
        val category = r["recipeCategory"].strings().firstOrNull()?.cleanText()
        val cuisine = r["recipeCuisine"].strings().firstOrNull()?.cleanText()
        val ingredients = r["recipeIngredient"].ifAbsent(r["ingredients"]).strings()
            .map { it.cleanText() }.filter { it.isNotEmpty() }
        val steps = parseInstructions(r["recipeInstructions"], section = null)
        val prep = parseIsoDurationMinutes(r.text("prepTime"))
        val cook = parseIsoDurationMinutes(r.text("cookTime"))
        val total = parseIsoDurationMinutes(r.text("totalTime"))
            ?: if (prep != null || cook != null) (prep ?: 0) + (cook ?: 0) else null

        val tags = (r["recipeCategory"].strings() + r["recipeCuisine"].strings() + keywords)
            .map { it.cleanText() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        return ParsedRecipe(
            title = title,
            description = r.text("description"),
            sourceUrl = r.text("url")?.takeIf { pageUrl == null } ?: pageUrl,
            imageUrl = imageUrl(r["image"]) ?: imageUrl(r["thumbnailUrl"]),
            servings = yields.firstNotNullOfOrNull { firstInt(it) },
            yieldText = yields.maxByOrNull { it.length }?.cleanText(),
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            category = category,
            cuisine = cuisine,
            categoryId = DefaultCategories.idFor(
                MainIngredientGuesser.guess(
                    category = category,
                    diets = r["suitableForDiet"].strings(),
                    keywords = keywords,
                    ingredients = ingredients,
                )
            ),
            tags = tags,
            ingredients = ingredients,
            steps = steps,
        )
    }

    private fun parseInstructions(element: JsonElement?, section: String?): List<ParsedStep> = when (element) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> element.contentOrNull.orEmpty()
            .let { if ('<' in it) Jsoup.parse(it.replace(blockEnd, "\n")).wholeText() else it } // soms HTML met <p>/<li>
            .split('\n')
            .map { it.cleanText() }
            .filter { it.isNotEmpty() }
            .map { ParsedStep(it, section) }
        is JsonArray -> element.flatMap { parseInstructions(it, section) }
        is JsonObject -> {
            val types = element.types()
            when {
                types.any { it.equals("HowToSection", true) } || (element["itemListElement"] != null && "HowToStep" !in types) ->
                    parseInstructions(element["itemListElement"], element.text("name") ?: section)
                else -> {
                    val text = element.text("text") ?: element.text("name") ?: element.text("description")
                    if (text == null) emptyList() else listOf(ParsedStep(text, section))
                }
            }
        }
    }

    private fun imageUrl(element: JsonElement?): String? = when (element) {
        null, JsonNull -> null
        is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> element.firstNotNullOfOrNull { imageUrl(it) }
        is JsonObject -> imageUrl(element["url"]) ?: imageUrl(element["contentUrl"])
    }

    private fun JsonObject.types(): List<String> = this["@type"].strings()

    /** Tekstwaarde, ontdaan van HTML-entiteiten/tags en overbodige witruimte. */
    private fun JsonObject.text(key: String): String? =
        this[key].strings().firstOrNull()?.cleanText()?.takeIf { it.isNotEmpty() }

    /** String, getal of lijst daarvan → lijst van strings. Objecten worden overgeslagen (behalve `name`). */
    private fun JsonElement?.strings(): List<String> = when (this) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> flatMap { it.strings() }
        is JsonObject -> (this["name"] ?: this["@value"]).strings()
    }

    private fun JsonElement?.ifAbsent(other: JsonElement?): JsonElement? = if (this == null || this == JsonNull) other else this

    private fun firstInt(s: String): Int? = Regex("""\d+""").find(s)?.value?.toIntOrNull()?.takeIf { it > 0 }

    private fun String.cleanText(): String {
        val unescaped = if ('&' in this || '<' in this) Parser.unescapeEntities(Jsoup.parse(this).text(), false) else this
        return unescaped.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
    }
}
