package nl.eazyrecipes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    /**
     * Slaat een geïmporteerd (en evt. in het voorbeeldscherm bewerkt) recept op. @return het nieuwe id.
     * [keepId]/[createdAt]: bij het terugzetten van een back-up of het openen van een `.eazy` blijven die van het origineel
     * (een id dat hier al bestaat, ook verwijderd, krijgt een nieuw).
     */
    suspend fun save(
        parsed: ParsedRecipe,
        imagePath: String? = null,
        keepId: String? = null,
        createdAt: Long? = null,
        notes: String? = null,
        favorite: Boolean = false,
    ): String = db.withTransaction {
        val now = clock()
        val id = keepId?.takeIf { dao.recipe(it) == null } ?: newId()
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
                categoryId = null, // zie setCategories hieronder
                createdAt = createdAt ?: now,
                notes = notes?.trim()?.ifEmpty { null },
                favorite = favorite,
                updatedAt = now,
            )
        )
        insertChildren(id, parsed, now)
        setCategories(id, parsed.categoryIds, now)
        val tagIds = parsed.tags.map { name -> labelId(name, now) }.distinct()
        dao.insertRecipeTags(tagIds.map { RecipeTagEntity(id, it, now, now) })
        dao.insertFts(
            RecipeFtsEntity(
                recipeId = id,
                title = parsed.title,
                ingredients = parsed.ingredients.joinToString("\n"),
                tags = dao.tagNames(id).joinToString("\n"),
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
                updatedAt = now,
            )
        )
        setCategories(id, edited.categoryIds, now)
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
            labels = dao.labelsOf(id),
            categoryIds = dao.categoriesOf(id).let { all -> listOfNotNull(d.recipe.categoryId).filter { it in all } + all.filter { it != d.recipe.categoryId } },
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

    // --- labels ---

    /**
     * Bestaand label met deze naam (ongeacht hoofdletters), of een nieuw.
     * Was het samengevoegd, dan het label waarin het opging; was het gewoon verwijderd, dan komt het terug.
     * [unhide]: de gebruiker typt het zelf in, dan moet hij het ook zien.
     */
    private suspend fun labelId(name: String, now: Long, unhide: Boolean = false): String {
        val key = name.trim().lowercase()
        dao.insertTag(TagEntity(newId(), name.trim(), key, now, now))
        var tag = checkNotNull(dao.tagByKey(key))
        repeat(10) { // alias volgen (a → b → c), met een grens tegen kringen
            val next = tag.takeIf { it.deletedAt != null }?.mergedInto?.let { dao.tag(it) } ?: return@repeat
            tag = next
        }
        when {
            // Terug na verwijderen: met de schrijfwijze van nu, als het echt dít label is (niet het label waarin het opging).
            tag.deletedAt != null -> dao.updateTag(
                tag.copy(name = if (tag.nameKey == key) name.trim() else tag.name, deletedAt = null, mergedInto = null, hidden = false, updatedAt = now)
            )
            unhide && tag.hidden -> dao.updateTag(tag.copy(hidden = false, updatedAt = now))
        }
        return tag.id
    }

    private suspend fun activeTag(id: String): TagEntity? = dao.tag(id)?.takeIf { it.deletedAt == null }

    /** Labels met hun aantal recepten, meest gebruikt eerst (ook verborgen; filter zelf op [TagEntity.hidden]). */
    fun observeLabels(): Flow<List<LabelSummary>> = dao.observeLabels()

    /** Per recept de ids van zijn labels. */
    fun observeRecipeLabels(): Flow<Map<String, Set<String>>> =
        dao.observeRecipeTags().map { rows -> rows.groupBy({ it.recipeId }, { it.tagId }).mapValues { it.value.toSet() } }

    /** Nieuw label (of een bestaand met dezelfde naam, weer zichtbaar). @return het id. */
    suspend fun createLabel(name: String): String = db.withTransaction { labelId(name, clock(), unhide = true) }

    /** Label aan of uit voor een recept. */
    suspend fun setRecipeLabel(recipeId: String, tagId: String, on: Boolean): Unit = db.withTransaction {
        if (on && activeTag(tagId) == null) return@withTransaction
        val now = clock()
        if (dao.setRecipeTag(recipeId, tagId, deletedAt = if (on) null else now, now = now) == 0 && on) {
            dao.insertRecipeTags(listOf(RecipeTagEntity(recipeId, tagId, now, now)))
        }
        dao.recipe(recipeId)?.takeIf { it.deletedAt == null }?.let { dao.updateRecipe(it.copy(updatedAt = now)) }
        dao.refreshFtsTags(listOf(tagId))
    }

    /**
     * Naam, kleur en verbergen in één keer (het bewerk-venster). Bestaat de nieuwe naam al als ander label, dan worden
     * ze samengevoegd en krijgt het overblijvende label de gekozen kleur en zichtbaarheid. @return het overblijvende id.
     */
    suspend fun updateLabel(id: String, name: String, color: String?, hidden: Boolean): String = db.withTransaction {
        val now = clock()
        val tag = activeTag(id) ?: return@withTransaction id
        val key = name.trim().lowercase().ifEmpty { tag.nameKey }
        val other = dao.tagByKey(key)?.takeIf { it.id != id }
        val survivor = when {
            other == null -> tag.copy(name = name.trim().ifEmpty { tag.name }, nameKey = key)
            other.deletedAt != null -> {
                // De naam is van een eerder verwijderd label: dat maakt plaats (de sleutel is uniek, ook voor verwijderde).
                dao.updateTag(other.copy(nameKey = "${other.nameKey}#${other.id}", updatedAt = now))
                tag.copy(name = name.trim(), nameKey = key)
            }
            else -> {
                mergeInto(id, other.id, now)
                other.copy(name = name.trim())
            }
        }
        dao.updateTag(survivor.copy(color = color, hidden = hidden, updatedAt = now))
        dao.refreshFtsTags(listOf(id, survivor.id))
        survivor.id
    }

    suspend fun renameLabel(id: String, name: String) {
        val tag = activeTag(id) ?: return
        updateLabel(id, name, tag.color, tag.hidden)
    }

    /** Voegt [fromId] samen met [intoId]: recepten krijgen [intoId], [fromId] verdwijnt (en verwijst voortaan naar [intoId]). */
    suspend fun mergeLabels(fromId: String, intoId: String): Unit = db.withTransaction {
        if (fromId == intoId || activeTag(intoId) == null || activeTag(fromId) == null) return@withTransaction
        mergeInto(fromId, intoId, clock())
        dao.refreshFtsTags(listOf(fromId, intoId))
    }

    private suspend fun mergeInto(fromId: String, intoId: String, now: Long) {
        dao.touchRecipesWithTag(fromId, now)
        dao.copyTagLinks(fromId, intoId, now)
        dao.reviveTagLinks(fromId, intoId, now)
        dao.softDeleteRecipeTagsOf(fromId, now)
        dao.tag(fromId)?.let { dao.updateTag(it.copy(deletedAt = now, mergedInto = intoId, updatedAt = now)) }
    }

    suspend fun setLabelColor(id: String, color: String?) {
        val tag = activeTag(id) ?: return
        dao.updateTag(tag.copy(color = color, updatedAt = clock()))
    }

    suspend fun setLabelHidden(id: String, hidden: Boolean) {
        val tag = activeTag(id) ?: return
        dao.updateTag(tag.copy(hidden = hidden, updatedAt = clock()))
    }

    /** Label weg bij alle recepten (de recepten blijven). Een latere import met deze naam maakt het weer aan. */
    suspend fun deleteLabel(id: String): Unit = db.withTransaction {
        val now = clock()
        dao.touchRecipesWithTag(id, now)
        dao.softDeleteRecipeTagsOf(id, now)
        dao.tag(id)?.let { dao.updateTag(it.copy(deletedAt = now, mergedInto = null, updatedAt = now)) }
        dao.refreshFtsTags(listOf(id))
    }

    // --- categorieën ---

    /**
     * Zet de categorieën van een recept: [ids] in volgorde, de eerste (bestaande) wordt de hoofdsoort.
     * Onbekende of verwijderde categorieën vallen weg.
     */
    private suspend fun setCategories(recipeId: String, ids: List<String>, now: Long) {
        val active = ids.distinct().filter { dao.category(it) != null }
        dao.insertRecipeCategories(active.map { RecipeCategoryEntity(recipeId, it, now, now) }) // bestaande: genegeerd
        active.forEach { dao.reviveRecipeCategory(recipeId, it, now) }
        dao.removeOtherCategories(recipeId, active.ifEmpty { listOf("") }, now)
        dao.recipe(recipeId)?.let { r -> if (r.categoryId != active.firstOrNull()) dao.updateRecipe(r.copy(categoryId = active.firstOrNull(), updatedAt = now)) }
    }

    /** Per recept de ids van zijn categorieën (voor het filter: een recept staat onder elke tegel van zijn soorten). */
    fun observeRecipeCategories(): Flow<Map<String, Set<String>>> =
        dao.observeRecipeCategories().map { rows -> rows.groupBy({ it.recipeId }, { it.categoryId }).mapValues { it.value.toSet() } }

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

    /** Verwijdert de categorie; haar recepten blijven, met hun andere soort als hoofdsoort (of zonder soort). */
    suspend fun deleteCategory(id: String) = db.withTransaction {
        val now = clock()
        dao.touchRecipesInCategory(id, now)
        dao.softDeleteCategory(id, now)
        dao.softDeleteCategoryLinks(id, now)
        dao.reassignPrimary(id, now)
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
    /** Namen van alle labels (ook verborgen), voor zoeken en de editor. */
    val tags: List<String>,
    /** Labels als entiteit (kleur, verborgen), voor het receptscherm. */
    val labels: List<TagEntity> = emptyList(),
    /** Alle categorieën, hoofdsoort eerst. */
    val categoryIds: List<String> = listOfNotNull(recipe.categoryId),
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
        categoryIds = categoryIds,
        tags = tags,
        ingredients = ingredients.map { it.raw },
        steps = steps.map { ParsedStep(it.text, it.sectionName) },
    )
}
