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

    /** Elk niet-verwijderd recept met zijn ingrediëntregels in volgorde, nieuwste recept eerst. */
    fun observeRecipesWithIngredients(): Flow<List<Pair<RecipeEntity, List<String>>>> =
        combine(dao.observeLibrary(), dao.observeIngredients()) { recipes, ingredients ->
            val linesOf = ingredients.groupBy({ it.recipeId }, { it.raw })
            recipes.map { it to linesOf[it.id].orEmpty() }
        }

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
                categoryId = activeCategory(parsed.categoryId),
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
                categoryId = activeCategory(edited.categoryId),
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

    /** Eigen notitie; leeg = geen notitie. */
    suspend fun setNotes(id: String, notes: String) = dao.setNotes(id, notes.trim().ifEmpty { null }, clock())

    /** Nieuwe lokale omslagfoto. Het oude bestand opruimen is aan de aanroeper (ImageStore). */
    suspend fun setImagePath(id: String, path: String?) = dao.setImagePath(id, path, clock())

    suspend fun delete(id: String) = db.withTransaction {
        dao.softDelete(id, clock())
        dao.deleteFts(id)
    }

    // --- categorieën ---

    /** Alleen een bestaande (niet-verwijderde) categorie; anders geen soort. */
    private suspend fun activeCategory(id: String?): String? = id?.let { dao.category(it)?.id }

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()

    /**
     * Nieuwe categorie achteraan. @return het id.
     * Heet hij als een verwijderde standaardcategorie ("Vlees"), dan komt díe terug: dan blijft de gok bij importeren
     * (vlees/vis/…) er recepten in zetten.
     */
    suspend fun createCategory(name: String, color: String, icon: String): String = db.withTransaction {
        val now = clock()
        val position = (dao.categories().maxOfOrNull { it.position } ?: -1) + 1
        val default = DefaultCategories.all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        val old = default?.let { dao.categoryIncludingDeleted(it.id) }?.takeIf { it.deletedAt != null }
        if (old != null) {
            dao.updateCategory(old.copy(name = name.trim(), color = color, icon = icon, position = position, deletedAt = null, updatedAt = now))
            return@withTransaction old.id
        }
        val id = newId()
        dao.insertCategory(CategoryEntity(id, name.trim(), color, icon, position, now, now))
        id
    }

    suspend fun updateCategory(id: String, name: String, color: String, icon: String) {
        val old = dao.category(id) ?: return
        dao.updateCategory(old.copy(name = name.trim(), color = color, icon = icon, updatedAt = clock()))
    }

    /** Verwijdert de categorie; haar recepten blijven bestaan, zonder soort. */
    suspend fun deleteCategory(id: String) = db.withTransaction {
        val now = clock()
        dao.softDeleteCategory(id, now)
        dao.clearCategory(id, now)
    }

    /** Eén plek naar voren (-1) of naar achteren (+1) in de volgorde. */
    suspend fun moveCategory(id: String, delta: Int) = db.withTransaction {
        val list = dao.categories().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@withTransaction
        list.add(to, list.removeAt(from))
        val now = clock()
        list.forEachIndexed { i, c -> if (c.position != i) dao.setCategoryPosition(c.id, i, now) }
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
        categoryId = recipe.categoryId,
        tags = tags,
        ingredients = ingredients.map { it.raw },
        steps = steps.map { ParsedStep(it.text, it.sectionName) },
    )
}
