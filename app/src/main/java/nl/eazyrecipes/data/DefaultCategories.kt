package nl.eazyrecipes.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * De vijf soorten waarmee de app begint. Hun id is vast (niet een UUID), zodat de gok bij importeren
 * ([MainIngredient]) er een categorie van kan maken: VLEES → "vlees". Hernoemen of andere kleur kan gewoon;
 * verwijder je er een, dan krijgen nieuwe imports met die gok geen soort.
 */
object DefaultCategories {

    data class Default(val id: String, val name: String, val color: String, val icon: String)

    val all = listOf(
        Default("vlees", "Vlees", "rood", "cow"),
        Default("vis", "Vis", "blauw", "fish"),
        Default("vega", "Vega", "groen", "leaf"),
        Default("vegan", "Vegan", "mint", "plant"),
        Default("zoet", "Zoet", "roze", "cake"),
    )

    fun idFor(main: MainIngredient?): String? = main?.name?.lowercase()

    /** Als entiteiten, bv. voor previews en screenshot-tests. */
    fun entities(now: Long = 0): List<CategoryEntity> =
        all.mapIndexed { i, d -> CategoryEntity(d.id, d.name, d.color, d.icon, i, now, now) }

    /** Rechtstreeks in SQL: gebruikt door de migratie v1 → v2 en bij een nieuwe database (Room-DAO's bestaan dan nog niet). */
    internal fun insert(db: SupportSQLiteDatabase, now: Long) {
        all.forEachIndexed { i, d ->
            db.execSQL(
                "INSERT OR IGNORE INTO `category` (`id`, `name`, `color`, `icon`, `position`, `createdAt`, `updatedAt`, `deletedAt`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, NULL)",
                arrayOf<Any>(d.id, d.name, d.color, d.icon, i, now, now),
            )
        }
    }
}
