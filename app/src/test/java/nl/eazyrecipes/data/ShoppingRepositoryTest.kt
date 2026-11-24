package nl.eazyrecipes.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.shopping.ShoppingList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingRepositoryTest {

    private lateinit var db: EazyDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var shopping: ShoppingRepository
    private var now = 1_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java)
            .addCallback(SeedCategories).allowMainThreadQueries().build()
        recipes = RecipeRepository(db, clock = { now })
        shopping = ShoppingRepository(db, clock = { now++ })
    }

    @After fun tearDown() = db.close()

    private suspend fun list() = ShoppingList.build(
        shopping.observeListRecipes(recipes).first(), shopping.observeItems().first(), shopping.observeChecked().first(),
    )

    @Test
    fun `recepten op de lijst, porties, afvinken en leegmaken`() = runBlocking {
        val kip = recipes.save(ParsedRecipe(title = "Kip kerrie", servings = 2, ingredients = listOf("2 uien", "200 g kipfilet")))
        val soep = recipes.save(ParsedRecipe(title = "Soep", servings = 4, ingredients = listOf("1 ui")))
        shopping.addRecipe(kip, 4)
        shopping.addRecipe(soep, null)
        assertEquals(listOf("Kip kerrie", "Soep"), list().recipes.map { it.title })
        // Porties 2 → 4: dubbel; plus de ui uit de soep.
        assertEquals("5", list().sections.flatMap { it.lines }.single { it.name == "uien" }.amount)

        // Porties aanpassen houdt de plek in de volgorde.
        shopping.addRecipe(kip, 2)
        assertEquals(listOf("Kip kerrie", "Soep"), list().recipes.map { it.title })
        assertEquals("3", list().sections.flatMap { it.lines }.single { it.name == "uien" }.amount)

        // Afvinken, en weer open.
        val uien = list().sections.flatMap { it.lines }.single { it.name == "uien" }
        shopping.setChecked(uien.checkKey, true)
        assertEquals(listOf("uien"), list().done.map { it.name })
        shopping.setChecked(uien.checkKey, false)
        assertEquals(emptyList<String>(), list().done.map { it.name })

        // Eigen items.
        val wc = shopping.addItem("  wc-papier ")!!
        assertEquals(null, shopping.addItem("   "))
        shopping.setItemChecked(wc, true)
        assertEquals(listOf("wc-papier"), list().done.map { it.name })
        shopping.removeItem(wc)
        assertTrue(list().done.isEmpty())

        // Een verwijderd recept verdwijnt van de lijst.
        recipes.delete(soep)
        assertEquals(listOf("Kip kerrie"), list().recipes.map { it.title })

        // Eraf en er weer op: achteraan.
        val taart = recipes.save(ParsedRecipe(title = "Taart", ingredients = listOf("4 appels")))
        shopping.addRecipe(taart, null)
        shopping.removeRecipe(kip)
        shopping.addRecipe(kip, null)
        assertEquals(listOf("Taart", "Kip kerrie"), list().recipes.map { it.title })

        shopping.addItem("melk")
        shopping.setChecked("i:appels|4", true)
        shopping.clear()
        assertTrue(list().isEmpty)
        assertTrue(shopping.observeChecked().first().isEmpty())
    }
}
