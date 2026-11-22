package nl.eazyrecipes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        CategoryEntity::class,
        RecipeCategoryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class EazyDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile private var instance: EazyDatabase? = null

        fun get(context: Context): EazyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, EazyDatabase::class.java, "eazyrecipes.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SeedCategories)
                .build()
                .also { instance = it }
        }
    }
}

/** Nieuwe database: meteen de standaardcategorieën erin. */
object SeedCategories : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = DefaultCategories.insert(db, System.currentTimeMillis())
}

/**
 * v1 → v2: eigen categorieën. Maakt de tabel, zet de vijf standaardsoorten erin en geeft elk recept de categorie
 * die bij zijn oude hoofdingrediënt hoort (VLEES → "vlees", …). Recepten zonder soort blijven zonder.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                "`icon` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))"
        )
        db.execSQL("ALTER TABLE `recipe` ADD COLUMN `categoryId` TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_categoryId` ON `recipe` (`categoryId`)")
        DefaultCategories.insert(db, System.currentTimeMillis())
        db.execSQL("UPDATE `recipe` SET `categoryId` = LOWER(`mainIngredient`) WHERE `mainIngredient` IS NOT NULL")
    }
}

/** v2 → v3: labels krijgen een kleur, kunnen verborgen worden en onthouden waarin ze zijn samengevoegd. Bestaande tags blijven zichtbaar en grijs. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tag` ADD COLUMN `color` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `tag` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `tag` ADD COLUMN `mergedInto` TEXT DEFAULT NULL")
    }
}

/** v3 → v4: een recept kan in meerdere categorieën. Elk recept krijgt zijn huidige soort als (enige) koppeling. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_category` (`recipeId` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`recipeId`, `categoryId`), " +
                "FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_category_categoryId` ON `recipe_category` (`categoryId`)")
        db.execSQL(
            "INSERT OR IGNORE INTO `recipe_category` (`recipeId`, `categoryId`, `createdAt`, `updatedAt`, `deletedAt`) " +
                "SELECT `id`, `categoryId`, `updatedAt`, `updatedAt`, NULL FROM `recipe` WHERE `categoryId` IS NOT NULL"
        )
    }
}
