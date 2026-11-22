package nl.eazyrecipes.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import nl.eazyrecipes.data.CategoryEntity
import nl.eazyrecipes.ui.theme.CategoryIcons
import nl.eazyrecipes.ui.theme.CategoryPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.eazyrecipes.data.RecipeDetails
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.importer.ParsedRecipe
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Uitkomst van een import, voor de melding. */
data class ImportResult(val added: Int, val alreadyThere: Int, val collection: String?)

/** Een recept/collectie/back-up naar een `.eazy` schrijven, en zo'n bestand weer inlezen. */
class Bundles(
    private val repository: RecipeRepository,
    private val images: ImageStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // --- exporteren ---

    suspend fun writeRecipe(id: String, out: OutputStream) = write(EazyFile.Kind.RECIPE, listOf(id), null, out)

    suspend fun writeCollection(collectionId: String, out: OutputStream) {
        val c = repository.observeCollections().first().firstOrNull { it.id == collectionId }
            ?: throw java.io.IOException("Deze collectie bestaat niet meer.")
        write(EazyFile.Kind.COLLECTION, c.recipes.map { it.id }, EazyFile.Collection(c.id, c.name, c.recipes.map { it.id }), out)
    }

    /** Alles: recepten, categorieën (ook zonder recepten), labels, collecties. */
    suspend fun writeBackup(out: OutputStream) {
        val ids = repository.observeLibrary().first().map { it.id }
        val collections = repository.observeCollections().first().map { c -> EazyFile.Collection(c.id, c.name, c.recipes.map { it.id }) }
        write(EazyFile.Kind.BACKUP, ids, null, out, allCollections = collections, allCategories = true)
    }

    private suspend fun write(
        kind: EazyFile.Kind,
        ids: List<String>,
        collection: EazyFile.Collection?,
        out: OutputStream,
        allCollections: List<EazyFile.Collection> = listOfNotNull(collection),
        allCategories: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val details = ids.mapNotNull { repository.get(it) }
        val categories = repository.observeCategories().first()
        val usedCategories = details.flatMap { it.categoryIds }.toSet()
        val labels = details.flatMap { it.labels }.distinctBy { it.id }
        val imageFiles = mutableMapOf<String, File>()
        val recipes = details.map { d ->
            val file = d.recipe.imagePath?.let(::File)?.takeIf { it.isFile }
            val entry = file?.let { "images/${d.recipe.id}.${it.extension.ifEmpty { "jpg" }}" }
            if (entry != null) imageFiles[entry] = file
            d.toExport(entry)
        }
        val bundle = EazyFile.Bundle(
            kind = kind,
            exportedAt = clock(),
            recipes = recipes,
            categories = categories.filter { allCategories || it.id in usedCategories }.map { EazyFile.Category(it.id, it.name, it.color, it.icon) },
            labels = labels.map { EazyFile.Label(it.id, it.name, it.color, it.hidden) },
            collections = allCollections,
        )
        EazyFile.write(bundle, imageFiles, out)
    }

    private fun RecipeDetails.toExport(image: String?) = EazyFile.Recipe(
        id = recipe.id,
        title = recipe.title,
        description = recipe.description,
        sourceUrl = recipe.sourceUrl,
        imageUrl = recipe.imageUrl,
        image = image,
        servings = recipe.servings,
        yieldText = recipe.yieldText,
        prepMinutes = recipe.prepMinutes,
        cookMinutes = recipe.cookMinutes,
        totalMinutes = recipe.totalMinutes,
        categoryIds = categoryIds,
        labelIds = labels.map { it.id },
        notes = recipe.notes,
        favorite = recipe.favorite,
        createdAt = recipe.createdAt,
        ingredients = ingredients.map { it.raw },
        steps = steps.map { nl.eazyrecipes.importer.ParsedStep(it.text, it.sectionName) },
    )

    // --- importeren ---

    /** Alleen de inhoud, zonder foto's: voor het voorbeeld vóór importeren. */
    suspend fun peek(open: () -> InputStream): EazyFile.Bundle = withContext(Dispatchers.IO) {
        open().use { EazyFile.read(it).first }
    }

    /** Hoeveel recepten uit [bundle] staan er al (zelfde id of zelfde bronlink)? */
    suspend fun alreadyThere(bundle: EazyFile.Bundle): Set<String> =
        bundle.recipes.filter { existingId(it) != null }.map { it.id }.toSet()

    private suspend fun existingId(r: EazyFile.Recipe): String? =
        repository.get(r.id)?.recipe?.id ?: r.sourceUrl?.let { repository.findBySourceUrl(it)?.id }

    /**
     * Voegt de recepten toe die er nog niet zijn. Categorieën worden op id of naam gekoppeld (of aangemaakt met hun
     * kleur/icoon), labels op naam. Bij een collectie of back-up komen de collecties erbij (op naam samengevoegd).
     * Het bestand wordt twee keer gelezen: eerst de inhoud, dan alleen de foto's van recepten die er écht bij komen.
     * Gaat er iets mis, dan worden opgeslagen maar ongebruikte foto's opgeruimd.
     */
    suspend fun import(open: () -> InputStream): ImportResult = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        val cancelled = { job?.ensureActive(); Unit }
        val bundle = open().use { EazyFile.read(it, checkCancelled = cancelled).first }

        val localIdOf = mutableMapOf<String, String>()
        val toAdd = bundle.recipes.filter { r -> existingId(r)?.also { localIdOf[r.id] = it } == null }
        val wanted = toAdd.mapNotNull { it.image }.toSet()

        val written = mutableListOf<String>()
        val used = mutableSetOf<String>()
        try {
            val savedImages = if (wanted.isEmpty()) emptyMap() else open().use { input ->
                EazyFile.read(input, { name, data -> if (name in wanted) images.saveStream(data)?.path?.also { written += it } else null }, cancelled).second
            }

            // Categorieën: alleen die van recepten die erbij komen; bestaande (zelfde id of naam) of nieuw.
            val needed = toAdd.flatMap { it.categoryIds }.toSet()
            val local = repository.observeCategories().first().toMutableList()
            val categoryMap = bundle.categories.filter { it.id in needed }.associate { c ->
                c.id to (local.firstOrNull { it.id == c.id }?.id
                    ?: local.firstOrNull { it.name.equals(c.name, ignoreCase = true) }?.id
                    ?: run {
                        val color = c.color.takeIf { k -> CategoryPalette.any { it.key == k } } ?: CategoryPalette.first().key
                        val icon = c.icon.takeIf { k -> CategoryIcons.any { it.key == k } } ?: "bowl-food"
                        repository.createCategory(c.name, color, icon).also { newId ->
                            local += CategoryEntity(newId, c.name, color, icon, 0, 0, 0)
                        }
                    })
            }
            val labelsById = bundle.labels.associateBy { it.id }
            val now = clock()

            for (r in toAdd) {
                cancelled()
                val imagePath = r.image?.let { savedImages[it] }?.also { used += it }
                localIdOf[r.id] = repository.save(
                    ParsedRecipe(
                        title = r.title,
                        description = r.description,
                        sourceUrl = r.sourceUrl,
                        imageUrl = r.imageUrl,
                        servings = r.servings,
                        yieldText = r.yieldText,
                        prepMinutes = r.prepMinutes,
                        cookMinutes = r.cookMinutes,
                        totalMinutes = r.totalMinutes,
                        categoryIds = r.categoryIds.mapNotNull { categoryMap[it] },
                        tags = r.labelIds.mapNotNull { labelsById[it]?.name },
                        ingredients = r.ingredients,
                        steps = r.steps,
                    ),
                    imagePath = imagePath,
                    keepId = r.id,
                    createdAt = r.createdAt?.coerceIn(0, now),
                    notes = r.notes,
                    favorite = r.favorite,
                )
            }

            // Labelkleuren alleen invullen waar het label hier nog geen kleur heeft.
            val localLabels = repository.observeLabels().first()
            bundle.labels.forEach { l ->
                val mine = localLabels.firstOrNull { it.name.equals(l.name, ignoreCase = true) } ?: return@forEach
                val color = l.color?.takeIf { k -> CategoryPalette.any { it.key == k } }
                if (mine.tag.color == null && color != null) repository.setLabelColor(mine.id, color)
            }

            // Collecties: op naam samenvoegen.
            val localCollections = repository.observeCollections().first().associate { it.name.lowercase() to it.id }.toMutableMap()
            bundle.collections.forEach { c ->
                val target = localCollections.getOrPut(c.name.lowercase()) { repository.createCollection(c.name) }
                c.recipeIds.mapNotNull { localIdOf[it] }.forEach { repository.setInCollection(target, it, true) }
            }

            ImportResult(toAdd.size, bundle.recipes.size - toAdd.size, bundle.collections.singleOrNull()?.name.takeIf { bundle.kind == EazyFile.Kind.COLLECTION })
        } finally {
            // Foto's die geen recept kregen (fout, afgebroken, of het recept bestond al): weg.
            written.filter { it !in used }.forEach(images::delete)
        }
    }
}
