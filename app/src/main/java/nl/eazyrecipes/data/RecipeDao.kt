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

    // --- lezen (altijd zonder soft-deleted rijen) ---

    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeLibrary(): Flow<List<RecipeEntity>>

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

    @Query(
        """
        SELECT t.name FROM tag t JOIN recipe_tag rt ON rt.tagId = t.id
        WHERE rt.recipeId = :recipeId AND rt.deletedAt IS NULL AND t.deletedAt IS NULL
        ORDER BY t.name
        """
    )
    suspend fun tagNames(recipeId: String): List<String>
}
