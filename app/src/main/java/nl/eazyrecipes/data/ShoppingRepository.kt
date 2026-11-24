package nl.eazyrecipes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import nl.eazyrecipes.shopping.ExtraItem
import nl.eazyrecipes.shopping.ShoppingRecipe
import java.util.UUID

/** De boodschappenlijst: recepten (met porties), eigen items en wat er is afgevinkt. */
class ShoppingRepository(
    private val db: EazyDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.shoppingDao()

    fun observeRecipes(): Flow<List<ShoppingRecipeEntity>> = dao.observeRecipes()

    /** Recepten op de lijst met hun ingrediëntregels, klaar voor [nl.eazyrecipes.shopping.ShoppingList.build]. */
    fun observeListRecipes(recipes: RecipeRepository): Flow<List<ShoppingRecipe>> =
        combine(dao.observeRecipes(), recipes.observeRecipesWithIngredients()) { rows, library ->
            val byId = library.associateBy { it.first.id }
            rows.mapNotNull { row ->
                val (r, lines) = byId[row.recipeId] ?: return@mapNotNull null
                ShoppingRecipe(r.id, r.title, r.servings, row.servings, lines)
            }
        }

    fun observeItems(): Flow<List<ExtraItem>> = dao.observeItems().map { rows -> rows.map { ExtraItem(it.id, it.text, it.checked) } }

    fun observeChecked(): Flow<Set<String>> = dao.observeChecked().map { it.toSet() }

    /** Zet een recept op de lijst, of past zijn porties aan als het er al op staat. */
    suspend fun addRecipe(recipeId: String, servings: Int?) = db.withTransaction {
        val now = clock()
        val old = dao.recipe(recipeId)
        // Stond hij er al op: plek in de volgorde houden. Was hij eraf gehaald: opnieuw achteraan.
        val createdAt = old?.takeIf { it.deletedAt == null }?.createdAt ?: now
        dao.putRecipe(ShoppingRecipeEntity(recipeId, servings, createdAt, now))
    }

    suspend fun removeRecipe(recipeId: String) = dao.removeRecipe(recipeId, clock())

    /** @return het id, of `null` bij lege tekst. */
    suspend fun addItem(text: String): String? {
        val t = text.trim().replace(Regex("""\s+"""), " ").take(200)
        if (t.isEmpty()) return null
        val now = clock()
        val id = newId()
        dao.insertItem(ShoppingItemEntity(id, t, false, now, now))
        return id
    }

    suspend fun setItemChecked(id: String, checked: Boolean) = dao.setItemChecked(id, checked, clock())

    suspend fun removeItem(id: String) = dao.removeItem(id, clock())

    /** Een regel uit de recepten afvinken (of weer open zetten); [checkKey] = `ShoppingLine.checkKey`. */
    suspend fun setChecked(checkKey: String, checked: Boolean) {
        val now = clock()
        if (checked) dao.putCheck(ShoppingCheckEntity(checkKey, now, now)) else dao.removeCheck(checkKey, now)
    }

    /** Lijst leeg: recepten, eigen items en vinkjes. */
    suspend fun clear() = db.withTransaction {
        val now = clock()
        dao.removeAllRecipes(now)
        dao.removeAllItems(now)
        dao.removeAllChecks(now)
    }
}
