package nl.eazyrecipes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import nl.eazyrecipes.importer.ParsedRecipe
import java.net.URI
import java.util.UUID

/** Alle schrijfacties lopen hierlangs, zodat timestamps en de zoekindex altijd kloppen. */
class RecipeRepository(
    private val db: EazyDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.recipeDao()

    fun observeLibrary(): Flow<List<RecipeEntity>> = dao.observeLibrary()

    suspend fun findBySourceUrl(url: String): RecipeEntity? = dao.bySourceUrl(url)

    /** Slaat een geïmporteerd (en evt. in het voorbeeldscherm bewerkt) recept op. @return het nieuwe id. */
    suspend fun save(parsed: ParsedRecipe, imagePath: String? = null): String = db.withTransaction {
        val now = clock()
        val id = newId()
        dao.insertRecipe(
            RecipeEntity(
                id = id,
                title = parsed.title,
                description = parsed.description,
                sourceUrl = parsed.sourceUrl,
                sourceSite = parsed.sourceUrl?.let(::siteOf),
                imageUrl = parsed.imageUrl,
                imagePath = imagePath,
                servings = parsed.servings,
                yieldText = parsed.yieldText,
                prepMinutes = parsed.prepMinutes,
                cookMinutes = parsed.cookMinutes,
                totalMinutes = parsed.totalMinutes,
                category = parsed.category,
                cuisine = parsed.cuisine,
                mainIngredient = parsed.mainIngredient,
                createdAt = now,
                updatedAt = now,
            )
        )
        dao.insertIngredients(parsed.ingredients.mapIndexed { i, raw ->
            IngredientEntity(newId(), id, i, raw, now, now)
        })
        dao.insertSteps(parsed.steps.mapIndexed { i, s ->
            StepEntity(newId(), id, i, s.section, s.text, now, now)
        })
        val tagIds = parsed.tags.map { name ->
            val key = name.lowercase()
            dao.insertTag(TagEntity(newId(), name, key, now, now))
            checkNotNull(dao.tagByKey(key)).id
        }.distinct()
        dao.insertRecipeTags(tagIds.map { RecipeTagEntity(id, it, now, now) })
        dao.insertFts(
            RecipeFtsEntity(
                recipeId = id,
                title = parsed.title,
                ingredients = parsed.ingredients.joinToString("\n"),
                tags = parsed.tags.joinToString("\n"),
            )
        )
        id
    }

    suspend fun get(id: String): RecipeDetails? {
        val d = dao.details(id) ?: return null
        return RecipeDetails(
            recipe = d.recipe,
            ingredients = d.ingredients.filter { it.deletedAt == null }.sortedBy { it.position },
            steps = d.steps.filter { it.deletedAt == null }.sortedBy { it.position },
            tags = dao.tagNames(id),
        )
    }

    /** Zoekt op titel, ingrediënten en tags. Elk woord moet voorkomen; woorden matchen op begin ("kip" → "kipfilet"). */
    suspend fun search(text: String): List<RecipeEntity> {
        val match = ftsMatch(text) ?: return emptyList()
        return dao.search(match)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite, clock())

    suspend fun delete(id: String) = db.withTransaction {
        dao.softDelete(id, clock())
        dao.deleteFts(id)
    }

    companion object {
        /** Gebruikersinvoer → veilige FTS-query: alleen letters/cijfers, elk woord als prefix. */
        internal fun ftsMatch(text: String): String? =
            Regex("""[\p{L}\p{N}]+""").findAll(text.lowercase())
                .map { "${it.value}*" }
                .joinToString(" ")
                .ifEmpty { null }

        internal fun siteOf(url: String): String? =
            runCatching { URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.")
    }
}

data class RecipeDetails(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val steps: List<StepEntity>,
    val tags: List<String>,
)
