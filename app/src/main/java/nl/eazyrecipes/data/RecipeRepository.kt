package nl.eazyrecipes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.importer.ParsedStep
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
        insertChildren(id, parsed, now)
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

    /**
     * Slaat een bewerkt recept op. Ingrediënten en stappen worden in zijn geheel vervangen: het recept is de
     * eenheid van sync (zijn `updatedAt` verandert), dus losse rijen hoeven geen eigen geschiedenis te hebben.
     * Tags, notitie, favoriet en bron blijven zoals ze waren.
     */
    suspend fun update(id: String, edited: ParsedRecipe): Unit = db.withTransaction {
        val old = dao.recipe(id)?.takeIf { it.deletedAt == null } ?: error("Recept $id bestaat niet")
        val now = clock()
        dao.updateRecipe(
            old.copy(
                title = edited.title,
                description = edited.description,
                servings = edited.servings,
                prepMinutes = edited.prepMinutes,
                cookMinutes = edited.cookMinutes,
                totalMinutes = edited.totalMinutes,
                mainIngredient = edited.mainIngredient,
                updatedAt = now,
            )
        )
        dao.deleteIngredients(id)
        dao.deleteSteps(id)
        insertChildren(id, edited, now)
        dao.deleteFts(id)
        dao.insertFts(
            RecipeFtsEntity(
                recipeId = id,
                title = edited.title,
                ingredients = edited.ingredients.joinToString("\n"),
                tags = dao.tagNames(id).joinToString("\n"),
            )
        )
    }

    private suspend fun insertChildren(id: String, r: ParsedRecipe, now: Long) {
        dao.insertIngredients(r.ingredients.mapIndexed { i, raw -> IngredientEntity(newId(), id, i, raw, now, now) })
        dao.insertSteps(r.steps.mapIndexed { i, s -> StepEntity(newId(), id, i, s.section, s.text, now, now) })
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

    // --- collecties ---

    /** Alle collecties met hun (niet-verwijderde) recepten, in de volgorde waarin ze zijn toegevoegd. */
    fun observeCollections(): Flow<List<CollectionSummary>> =
        combine(dao.observeCollections(), dao.observeMemberships(), dao.observeLibrary()) { cols, members, recipes ->
            val byId = recipes.associateBy { it.id }
            val membersOf = members.groupBy { it.collectionId }
            cols.map { c -> CollectionSummary(c, membersOf[c.id].orEmpty().mapNotNull { byId[it.recipeId] }) }
        }

    suspend fun createCollection(name: String): String {
        val now = clock()
        val id = newId()
        dao.insertCollection(CollectionEntity(id, name.trim(), now, now))
        return id
    }

    suspend fun renameCollection(id: String, name: String) = dao.renameCollection(id, name.trim(), clock())

    suspend fun deleteCollection(id: String) = db.withTransaction {
        val now = clock()
        dao.softDeleteMemberships(id, now)
        dao.softDeleteCollection(id, now)
    }

    /** Zet een recept in of uit een collectie. Opnieuw toevoegen zet het achteraan. */
    suspend fun setInCollection(collectionId: String, recipeId: String, member: Boolean): Unit = db.withTransaction {
        val now = clock()
        val position = dao.nextPosition(collectionId)
        val updated = dao.updateMembership(collectionId, recipeId, deletedAt = if (member) null else now, position = position, now = now)
        if (updated == 0 && member) dao.insertMembership(CollectionRecipeEntity(collectionId, recipeId, position, now, now))
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

data class CollectionSummary(val collection: CollectionEntity, val recipes: List<RecipeEntity>) {
    val id: String get() = collection.id
    val name: String get() = collection.name
}

data class RecipeDetails(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val steps: List<StepEntity>,
    val tags: List<String>,
) {
    /** Als bewerkbaar concept voor de editor. */
    fun toDraft(): ParsedRecipe = ParsedRecipe(
        title = recipe.title,
        description = recipe.description,
        sourceUrl = recipe.sourceUrl,
        imageUrl = recipe.imageUrl,
        servings = recipe.servings,
        yieldText = recipe.yieldText,
        prepMinutes = recipe.prepMinutes,
        cookMinutes = recipe.cookMinutes,
        totalMinutes = recipe.totalMinutes,
        category = recipe.category,
        cuisine = recipe.cuisine,
        mainIngredient = recipe.mainIngredient,
        tags = tags,
        ingredients = ingredients.map { it.raw },
        steps = steps.map { ParsedStep(it.text, it.sectionName) },
    )
}
