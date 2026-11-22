package nl.eazyrecipes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    // --- schrijven ---

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    suspend fun insertSteps(items: List<StepEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tag WHERE nameKey = :nameKey LIMIT 1")
    suspend fun tagByKey(nameKey: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeTags(items: List<RecipeTagEntity>)

    @Insert
    suspend fun insertFts(row: RecipeFtsEntity)

    @Query("DELETE FROM recipe_fts WHERE recipeId = :recipeId")
    suspend fun deleteFts(recipeId: String)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: String)

    @Query("DELETE FROM step WHERE recipeId = :recipeId")
    suspend fun deleteSteps(recipeId: String)

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun recipe(id: String): RecipeEntity?

    @Query("UPDATE recipe SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE recipe SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE recipe SET notes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun setNotes(id: String, notes: String?, now: Long)

    @Query("UPDATE recipe SET imagePath = :imagePath, updatedAt = :now WHERE id = :id")
    suspend fun setImagePath(id: String, imagePath: String?, now: Long)

    // --- categorieën ---

    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY position, createdAt")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY position, createdAt")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id AND deletedAt IS NULL")
    suspend fun category(id: String): CategoryEntity?

    /** Ook verwijderde: om een standaardcategorie terug te zetten. */
    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun categoryIncludingDeleted(id: String): CategoryEntity?

    @Insert
    suspend fun insertCategory(c: CategoryEntity)

    @Update
    suspend fun updateCategory(c: CategoryEntity)

    @Query("UPDATE category SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setCategoryPosition(id: String, position: Int, now: Long)

    @Query("UPDATE category SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteCategory(id: String, now: Long)

    /**
     * Na het verwijderen van categorie [id]: recepten met die hoofdsoort krijgen hun volgende (nog bestaande) soort,
     * of geen. Hun updatedAt verandert mee (sync).
     */
    @Query(
        """
        UPDATE recipe SET updatedAt = :now, categoryId = (
            SELECT rc.categoryId FROM recipe_category rc JOIN category c ON c.id = rc.categoryId
            WHERE rc.recipeId = recipe.id AND rc.deletedAt IS NULL AND c.deletedAt IS NULL
            ORDER BY c.position, c.createdAt LIMIT 1
        )
        WHERE categoryId = :id
        """
    )
    suspend fun reassignPrimary(id: String, now: Long)

    @Query(
        """
        UPDATE recipe SET updatedAt = :now WHERE deletedAt IS NULL
        AND id IN (SELECT recipeId FROM recipe_category WHERE categoryId = :id AND deletedAt IS NULL)
        """
    )
    suspend fun touchRecipesInCategory(id: String, now: Long)

    @Query("UPDATE recipe_category SET deletedAt = :now, updatedAt = :now WHERE categoryId = :id AND deletedAt IS NULL")
    suspend fun softDeleteCategoryLinks(id: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeCategories(items: List<RecipeCategoryEntity>)

    /** Een eerder weggehaalde koppeling weer aanzetten (een actieve blijft onaangeroerd: geen onnodige sync). */
    @Query("UPDATE recipe_category SET deletedAt = NULL, updatedAt = :now WHERE recipeId = :recipeId AND categoryId = :categoryId AND deletedAt IS NOT NULL")
    suspend fun reviveRecipeCategory(recipeId: String, categoryId: String, now: Long)

    @Query("UPDATE recipe_category SET deletedAt = :now, updatedAt = :now WHERE recipeId = :recipeId AND deletedAt IS NULL AND categoryId NOT IN (:keep)")
    suspend fun removeOtherCategories(recipeId: String, keep: List<String>, now: Long)

    /** Actieve categorieën van een recept, in tegelvolgorde. */
    @Query(
        """
        SELECT rc.categoryId FROM recipe_category rc JOIN category c ON c.id = rc.categoryId
        WHERE rc.recipeId = :recipeId AND rc.deletedAt IS NULL AND c.deletedAt IS NULL
        ORDER BY c.position, c.createdAt
        """
    )
    suspend fun categoriesOf(recipeId: String): List<String>

    @Query(
        """
        SELECT rc.* FROM recipe_category rc
        JOIN category c ON c.id = rc.categoryId
        JOIN recipe r ON r.id = rc.recipeId
        WHERE rc.deletedAt IS NULL AND c.deletedAt IS NULL AND r.deletedAt IS NULL
        """
    )
    fun observeRecipeCategories(): Flow<List<RecipeCategoryEntity>>

    // --- collecties ---

    @Insert
    suspend fun insertCollection(c: CollectionEntity)

    @Query("UPDATE collection SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renameCollection(id: String, name: String, now: Long)

    @Query("UPDATE collection SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteCollection(id: String, now: Long)

    @Query("UPDATE collection_recipe SET deletedAt = :now, updatedAt = :now WHERE collectionId = :id AND deletedAt IS NULL")
    suspend fun softDeleteMemberships(id: String, now: Long)

    @Insert
    suspend fun insertMembership(m: CollectionRecipeEntity)

    /** @return aantal bijgewerkte rijen (0 = bestond nog niet). */
    @Query("UPDATE collection_recipe SET deletedAt = :deletedAt, updatedAt = :now, position = :position WHERE collectionId = :collectionId AND recipeId = :recipeId")
    suspend fun updateMembership(collectionId: String, recipeId: String, deletedAt: Long?, position: Int, now: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM collection_recipe WHERE collectionId = :collectionId")
    suspend fun nextPosition(collectionId: String): Int

    @Query("SELECT * FROM collection WHERE deletedAt IS NULL ORDER BY createdAt")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_recipe WHERE deletedAt IS NULL ORDER BY position")
    fun observeMemberships(): Flow<List<CollectionRecipeEntity>>

    // --- lezen (altijd zonder soft-deleted rijen) ---

    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeLibrary(): Flow<List<RecipeEntity>>

    /** Alle ingrediëntregels van niet-verwijderde recepten (voor "In huis"). */
    @Query(
        """
        SELECT i.* FROM ingredient i JOIN recipe r ON r.id = i.recipeId
        WHERE r.deletedAt IS NULL AND i.deletedAt IS NULL
        ORDER BY i.recipeId, i.position
        """
    )
    fun observeIngredients(): Flow<List<IngredientEntity>>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id AND deletedAt IS NULL")
    suspend fun details(id: String): RecipeWithDetails?

    @Query("SELECT * FROM recipe WHERE sourceUrl = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun bySourceUrl(url: String): RecipeEntity?

    @Query(
        """
        SELECT r.* FROM recipe r
        JOIN recipe_fts f ON f.recipeId = r.id
        WHERE recipe_fts MATCH :match AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC
        """
    )
    suspend fun search(match: String): List<RecipeEntity>

    // --- labels ---

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun tag(id: String): TagEntity?

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query(
        """
        SELECT t.*, COUNT(r.id) AS recipes FROM tag t
        LEFT JOIN recipe_tag rt ON rt.tagId = t.id AND rt.deletedAt IS NULL
        LEFT JOIN recipe r ON r.id = rt.recipeId AND r.deletedAt IS NULL
        WHERE t.deletedAt IS NULL
        GROUP BY t.id
        ORDER BY recipes DESC, t.nameKey
        """
    )
    fun observeLabels(): Flow<List<LabelSummary>>

    /** Alle koppelingen recept ↔ label (actief), voor het label-filter in de bibliotheek. */
    @Query(
        """
        SELECT rt.* FROM recipe_tag rt JOIN tag t ON t.id = rt.tagId
        WHERE rt.deletedAt IS NULL AND t.deletedAt IS NULL
        """
    )
    fun observeRecipeTags(): Flow<List<RecipeTagEntity>>

    @Query(
        """
        SELECT t.* FROM tag t JOIN recipe_tag rt ON rt.tagId = t.id
        WHERE rt.recipeId = :recipeId AND rt.deletedAt IS NULL AND t.deletedAt IS NULL
        ORDER BY t.nameKey
        """
    )
    suspend fun labelsOf(recipeId: String): List<TagEntity>

    @Query("SELECT recipeId FROM recipe_tag WHERE tagId = :tagId AND deletedAt IS NULL")
    suspend fun recipeIdsWithTag(tagId: String): List<String>

    /** @return aantal bijgewerkte rijen (0 = koppeling bestond nog niet). */
    @Query("UPDATE recipe_tag SET deletedAt = :deletedAt, updatedAt = :now WHERE recipeId = :recipeId AND tagId = :tagId")
    suspend fun setRecipeTag(recipeId: String, tagId: String, deletedAt: Long?, now: Long): Int

    @Query("UPDATE recipe_tag SET deletedAt = :now, updatedAt = :now WHERE tagId = :tagId AND deletedAt IS NULL")
    suspend fun softDeleteRecipeTagsOf(tagId: String, now: Long)

    /** Recepten met (nog actief) dit label als gewijzigd markeren (sync). */
    @Query(
        """
        UPDATE recipe SET updatedAt = :now WHERE deletedAt IS NULL
        AND id IN (SELECT recipeId FROM recipe_tag WHERE tagId = :tagId AND deletedAt IS NULL)
        """
    )
    suspend fun touchRecipesWithTag(tagId: String, now: Long)

    /** Samenvoegen, stap 1: elk recept met [from] krijgt ook [into] (nieuwe koppeling). */
    @Query(
        """
        INSERT OR IGNORE INTO recipe_tag (recipeId, tagId, createdAt, updatedAt, deletedAt)
        SELECT recipeId, :into, :now, :now, NULL FROM recipe_tag WHERE tagId = :from AND deletedAt IS NULL
        """
    )
    suspend fun copyTagLinks(from: String, into: String, now: Long)

    /** Samenvoegen, stap 2: een eerder verwijderde koppeling met [into] weer aanzetten. */
    @Query(
        """
        UPDATE recipe_tag SET deletedAt = NULL, updatedAt = :now WHERE tagId = :into AND deletedAt IS NOT NULL
        AND recipeId IN (SELECT recipeId FROM recipe_tag WHERE tagId = :from AND deletedAt IS NULL)
        """
    )
    suspend fun reviveTagLinks(from: String, into: String, now: Long)

    /** De labelnamen in de zoekindex bijwerken voor alle recepten die (ooit) een van deze labels hadden. */
    @Query(
        """
        UPDATE recipe_fts SET tags = COALESCE((
            SELECT group_concat(t.name, char(10)) FROM recipe_tag rt JOIN tag t ON t.id = rt.tagId
            WHERE rt.recipeId = recipe_fts.recipeId AND rt.deletedAt IS NULL AND t.deletedAt IS NULL
        ), '')
        WHERE recipeId IN (SELECT recipeId FROM recipe_tag WHERE tagId IN (:tagIds))
        """
    )
    suspend fun refreshFtsTags(tagIds: List<String>)

    @Query(
        """
        SELECT t.name FROM tag t JOIN recipe_tag rt ON rt.tagId = t.id
        WHERE rt.recipeId = :recipeId AND rt.deletedAt IS NULL AND t.deletedAt IS NULL
        ORDER BY t.name
        """
    )
    suspend fun tagNames(recipeId: String): List<String>
}
