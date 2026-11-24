package nl.eazyrecipes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    /** Recepten op de lijst (die zelf niet verwijderd zijn), in de volgorde waarin je ze toevoegde. */
    @Query(
        "SELECT s.* FROM shopping_recipe s JOIN recipe r ON r.id = s.recipeId " +
            "WHERE s.deletedAt IS NULL AND r.deletedAt IS NULL ORDER BY s.createdAt, s.recipeId"
    )
    fun observeRecipes(): Flow<List<ShoppingRecipeEntity>>

    @Query("SELECT * FROM shopping_recipe WHERE recipeId = :recipeId")
    suspend fun recipe(recipeId: String): ShoppingRecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecipe(row: ShoppingRecipeEntity)

    @Query("UPDATE shopping_recipe SET deletedAt = :now, updatedAt = :now WHERE recipeId = :recipeId AND deletedAt IS NULL")
    suspend fun removeRecipe(recipeId: String, now: Long)

    @Query("SELECT * FROM shopping_item WHERE deletedAt IS NULL ORDER BY createdAt, id")
    fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Insert
    suspend fun insertItem(row: ShoppingItemEntity)

    @Query("UPDATE shopping_item SET checked = :checked, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun setItemChecked(id: String, checked: Boolean, now: Long)

    @Query("UPDATE shopping_item SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun removeItem(id: String, now: Long)

    @Query("SELECT `key` FROM shopping_check WHERE deletedAt IS NULL")
    fun observeChecked(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCheck(row: ShoppingCheckEntity)

    @Query("UPDATE shopping_check SET deletedAt = :now, updatedAt = :now WHERE `key` = :key AND deletedAt IS NULL")
    suspend fun removeCheck(key: String, now: Long)

    @Query("UPDATE shopping_recipe SET deletedAt = :now, updatedAt = :now WHERE deletedAt IS NULL")
    suspend fun removeAllRecipes(now: Long)

    @Query("UPDATE shopping_item SET deletedAt = :now, updatedAt = :now WHERE deletedAt IS NULL")
    suspend fun removeAllItems(now: Long)

    @Query("UPDATE shopping_check SET deletedAt = :now, updatedAt = :now WHERE deletedAt IS NULL")
    suspend fun removeAllChecks(now: Long)
}
