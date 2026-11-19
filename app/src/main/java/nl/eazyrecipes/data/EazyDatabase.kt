package nl.eazyrecipes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        TagEntity::class,
        RecipeTagEntity::class,
        CollectionEntity::class,
        CollectionRecipeEntity::class,
        RecipeFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EazyDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile private var instance: EazyDatabase? = null

        fun get(context: Context): EazyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, EazyDatabase::class.java, "eazyrecipes.db")
                .build()
                .also { instance = it }
        }
    }
}
