package nl.eazyrecipes.share

import nl.eazyrecipes.importer.ParsedStep
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Een `.eazy`-bestand: een ZIP met `eazy.json` (recepten, categorieën, labels, collecties) en de foto's in `images/`.
 * Hetzelfde formaat voor één recept, een collectie en een volledige back-up; [Bundle.kind] zegt welke.
 * Versie 1. Nieuwe velden mogen erbij (lezers negeren onbekende velden); een hogere `version` weigeren we.
 */
object EazyFile {
    const val FORMAT = "eazyrecipes"
    const val VERSION = 1
    const val JSON_ENTRY = "eazy.json"
    const val EXTENSION = "eazy"

    /** Grenzen tegen kapotte of kwaadaardige bestanden (zip-bom, reuzentekst, eindeloos geneste JSON). */
    internal const val MAX_JSON = 20L * 1024 * 1024
    private const val MAX_IMAGE = 15L * 1024 * 1024
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 25_000
    private const val MAX_DEPTH = 32
    private const val MAX_RECIPES = 20_000
    private const val MAX_META = 2_000 // categorieën, labels, collecties
    private const val MAX_LINES = 500 // ingrediënten of stappen per recept
    private const val MAX_TITLE = 300
    private const val MAX_TEXT = 5_000 // beschrijving, ingrediëntregel, stap
    private const val MAX_NOTES = 20_000

    /** Ids die we overnemen komen in routes en bestandsnamen terecht: alleen veilige tekens. */
    private val safeId = Regex("""[A-Za-z0-9-]{1,64}""")

    enum class Kind { RECIPE, COLLECTION, BACKUP }

    data class Category(val id: String, val name: String, val color: String, val icon: String)
    data class Label(val id: String, val name: String, val color: String?, val hidden: Boolean)
    data class Collection(val id: String, val name: String, val recipeIds: List<String>)
    data class Recipe(
        val id: String,
        val title: String,
        val description: String? = null,
        val sourceUrl: String? = null,
        val imageUrl: String? = null,
        /** Pad in de ZIP (`images/<id>.jpg`), of `null`. */
        val image: String? = null,
        val servings: Int? = null,
        val yieldText: String? = null,
        val prepMinutes: Int? = null,
        val cookMinutes: Int? = null,
        val totalMinutes: Int? = null,
        val categoryIds: List<String> = emptyList(),
        val labelIds: List<String> = emptyList(),
        val notes: String? = null,
        val favorite: Boolean = false,
        val createdAt: Long? = null,
        val ingredients: List<String> = emptyList(),
        val steps: List<ParsedStep> = emptyList(),
    )

    data class Bundle(
        val kind: Kind,
        val exportedAt: Long,
        val recipes: List<Recipe>,
        val categories: List<Category> = emptyList(),
        val labels: List<Label> = emptyList(),
        val collections: List<Collection> = emptyList(),
    )

    class InvalidFile(message: String) : IOException(message)

    // --- schrijven ---

    /** @param images ZIP-pad → lokaal bestand (alleen bestaande bestanden worden meegenomen). */
    fun write(bundle: Bundle, images: Map<String, File>, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            val json = toJson(bundle).toString().toByteArray(Charsets.UTF_8)
            if (json.size > MAX_JSON) throw InvalidFile("Te veel recepten voor één bestand.")
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json)
            zip.closeEntry()
            images.forEach { (path, file) ->
                if (!file.isFile) return@forEach
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // --- lezen ---

    /**
     * Leest een `.eazy`. Foto's gaan naar [imageSink] (ZIP-pad + inhoud → lokaal pad, of `null` als hij hem niet wil);
     * zonder sink worden ze overgeslagen (alleen een voorbeeld tonen). Elke entry wordt helemaal gelezen en geteld,
     * ook als we hem niet gebruiken, zodat een zip-bom op de totaalgrens stuit. [checkCancelled] per entry: stopt
     * het werk als het scherm sluit.
     * @return de inhoud plus ZIP-pad → lokaal pad van de opgeslagen foto's.
     */
    fun read(
        input: InputStream,
        imageSink: ((name: String, data: InputStream) -> String?)? = null,
        checkCancelled: () -> Unit = {},
    ): Pair<Bundle, Map<String, String>> {
        var bundle: Bundle? = null
        val images = mutableMapOf<String, String>()
        var total = 0L
        var entries = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                checkCancelled()
                val e = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw InvalidFile("Te veel bestanden in het archief.")
                val counted = Limited(zip, if (e.name == JSON_ENTRY) MAX_JSON else MAX_IMAGE)
                when {
                    e.isDirectory -> Unit
                    e.name == JSON_ENTRY && bundle == null -> bundle = parse(counted.readBytes())
                    e.name.startsWith("images/") && imageSink != null && e.name !in images ->
                        imageSink(e.name, counted)?.let { images[e.name] = it }
                }
                counted.drain()
                total += counted.count
                if (total > MAX_TOTAL) throw InvalidFile("Het bestand is te groot.")
                zip.closeEntry()
            }
        }
        return (bundle ?: throw InvalidFile("Dit is geen EazyRecipes-bestand.")) to images
    }

    /** JSON → [Bundle], met een diepte-controle vóór het parsen: org.json parseert recursief (stack overflow). */
    private fun parse(bytes: ByteArray): Bundle {
        val text = String(bytes, Charsets.UTF_8)
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in text) {
            when {
                escaped -> escaped = false
                inString && ch == '\\' -> escaped = true
                ch == '"' -> inString = !inString
                !inString && (ch == '{' || ch == '[') -> if (++depth > MAX_DEPTH) throw InvalidFile("Het bestand is beschadigd.")
                !inString && (ch == '}' || ch == ']') -> depth--
            }
        }
        return try {
            fromJson(JSONObject(text))
        } catch (e: InvalidFile) {
            throw e
        } catch (e: Exception) {
            throw InvalidFile("Het bestand is beschadigd.")
        } catch (e: StackOverflowError) {
            throw InvalidFile("Het bestand is beschadigd.")
        } catch (e: OutOfMemoryError) {
            throw InvalidFile("Het bestand is te groot.")
        }
    }

    /** Leest hooguit [max] bytes; meer = fout (een te grote of kapotte entry). Sluit de onderliggende ZIP niet. */
    private class Limited(input: InputStream, private val max: Long) : FilterInputStream(input) {
        var count = 0L
            private set
        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        private fun bump(n: Long) {
            count += n
            if (count > max) throw InvalidFile("Een onderdeel van het bestand is te groot.")
        }
        /** De rest van de entry lezen (en tellen) zonder hem te bewaren. */
        fun drain() {
            val buf = ByteArray(16 * 1024)
            while (read(buf, 0, buf.size) >= 0) Unit
        }
        override fun close() = Unit
    }

    // --- JSON ---

    private fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
    private fun JSONObject.int(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null
    private fun JSONObject.long(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    internal fun toJson(b: Bundle): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("kind", b.kind.name.lowercase())
        .put("exportedAt", b.exportedAt)
        .put("categories", JSONArray(b.categories.map {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("icon", it.icon)
        }))
        .put("labels", JSONArray(b.labels.map {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color ?: JSONObject.NULL).put("hidden", it.hidden)
        }))
        .put("collections", JSONArray(b.collections.map {
            JSONObject().put("id", it.id).put("name", it.name).put("recipeIds", JSONArray(it.recipeIds))
        }))
        .put("recipes", JSONArray(b.recipes.map { r ->
            JSONObject().put("id", r.id).put("title", r.title)
                .put("description", r.description ?: JSONObject.NULL)
                .put("sourceUrl", r.sourceUrl ?: JSONObject.NULL)
                .put("imageUrl", r.imageUrl ?: JSONObject.NULL)
                .put("image", r.image ?: JSONObject.NULL)
                .put("servings", r.servings ?: JSONObject.NULL)
                .put("yieldText", r.yieldText ?: JSONObject.NULL)
                .put("prepMinutes", r.prepMinutes ?: JSONObject.NULL)
                .put("cookMinutes", r.cookMinutes ?: JSONObject.NULL)
                .put("totalMinutes", r.totalMinutes ?: JSONObject.NULL)
                .put("categoryIds", JSONArray(r.categoryIds))
                .put("labelIds", JSONArray(r.labelIds))
                .put("notes", r.notes ?: JSONObject.NULL)
                .put("favorite", r.favorite)
                .put("createdAt", r.createdAt ?: JSONObject.NULL)
                .put("ingredients", JSONArray(r.ingredients))
                .put("steps", JSONArray(r.steps.map { s -> JSONObject().put("text", s.text).put("section", s.section ?: JSONObject.NULL) }))
        }))

    private fun String.cap(max: Int) = if (length > max) take(max) else this
    private fun <T> List<T>.cap(max: Int) = if (size > max) take(max) else this
    private fun id(raw: String): String = raw.takeIf { safeId.matches(it) } ?: java.util.UUID.randomUUID().toString()
    /** Alleen gewone weblinks: een `tel:`- of app-link uit een vreemd bestand opent niets ongewensts. */
    private fun web(url: String?): String? = url?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }?.cap(2_000)

    internal fun fromJson(o: JSONObject): Bundle {
        if (o.optString("format") != FORMAT) throw InvalidFile("Dit is geen EazyRecipes-bestand.")
        if (o.optInt("version", 0) > VERSION) throw InvalidFile("Dit bestand komt uit een nieuwere versie van EazyRecipes. Werk de app bij.")
        return runCatching {
            Bundle(
                kind = runCatching { Kind.valueOf(o.getString("kind").uppercase()) }.getOrDefault(Kind.RECIPE),
                exportedAt = o.optLong("exportedAt"),
                categories = o.optJSONArray("categories").objects().cap(MAX_META).map {
                    Category(it.getString("id").cap(64), it.getString("name").cap(40), it.optString("color", "rood").cap(32), it.optString("icon", "bowl-food").cap(32))
                },
                labels = o.optJSONArray("labels").objects().cap(MAX_META).map {
                    Label(it.getString("id").cap(64), it.getString("name").cap(60), it.str("color")?.cap(32), it.optBoolean("hidden"))
                },
                collections = o.optJSONArray("collections").objects().cap(MAX_META).map {
                    Collection(it.getString("id").cap(64), it.getString("name").cap(60), it.optJSONArray("recipeIds").strings().cap(MAX_RECIPES))
                },
                recipes = o.optJSONArray("recipes").objects().cap(MAX_RECIPES).map { r ->
                    Recipe(
                        id = id(r.getString("id")),
                        title = r.getString("title").cap(MAX_TITLE).ifBlank { "Recept" },
                        description = r.str("description")?.cap(MAX_TEXT),
                        sourceUrl = web(r.str("sourceUrl")),
                        imageUrl = web(r.str("imageUrl")),
                        image = r.str("image")?.cap(200),
                        servings = r.int("servings")?.takeIf { it in 1..999 },
                        yieldText = r.str("yieldText")?.cap(100),
                        prepMinutes = r.int("prepMinutes")?.takeIf { it in 0..100_000 },
                        cookMinutes = r.int("cookMinutes")?.takeIf { it in 0..100_000 },
                        totalMinutes = r.int("totalMinutes")?.takeIf { it in 0..100_000 },
                        categoryIds = r.optJSONArray("categoryIds").strings().cap(50).map { it.cap(64) },
                        labelIds = r.optJSONArray("labelIds").strings().cap(100).map { it.cap(64) },
                        notes = r.str("notes")?.cap(MAX_NOTES),
                        favorite = r.optBoolean("favorite"),
                        createdAt = r.long("createdAt"),
                        ingredients = r.optJSONArray("ingredients").strings().cap(MAX_LINES).map { it.cap(MAX_TEXT) },
                        steps = r.optJSONArray("steps").objects().cap(MAX_LINES).map { s -> ParsedStep(s.getString("text").cap(MAX_TEXT), s.str("section")?.cap(200)) },
                    )
                }.distinctBy { it.id },
            )
        }.getOrElse { if (it is InvalidFile) throw it else throw InvalidFile("Het bestand is beschadigd.") }
    }
}
