package nl.eazyrecipes.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.importer.ParsedStep
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeRepositoryTest {

    private lateinit var db: EazyDatabase
    private lateinit var repo: RecipeRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RecipeRepository(db, clock = { now })
    }

    @After
    fun tearDown() = db.close()

    private val kipKerrie = ParsedRecipe(
        title = "Kip kerrie",
        sourceUrl = "https://www.leukerecepten.nl/recepten/kip-kerrie/",
        totalMinutes = 30,
        mainIngredient = MainIngredient.VLEES,
        tags = listOf("Kip", "Crème fraîche"),
        ingredients = listOf("150 gr rijst", "200 gr kipfilet", "125 ml crème fraîche"),
        steps = listOf(ParsedStep("Kook de rijst.", "Bereiding"), ParsedStep("Bak de kip.", "Bereiding")),
    )
    private val appeltaart = ParsedRecipe(
        title = "Appelkruimeltaart",
        tags = listOf("taart", "KIP"), // "KIP" test hoofdletter-ongevoelige tags
        ingredients = listOf("4 appels", "kaneel"),
    )

    @Test
    fun `opslaan en teruglezen in volgorde, met timestamps en bron`() = runTest {
        val id = repo.save(kipKerrie)
        val d = requireNotNull(repo.get(id))

        assertEquals("Kip kerrie", d.recipe.title)
        assertEquals("leukerecepten.nl", d.recipe.sourceSite)
        assertEquals(MainIngredient.VLEES, d.recipe.mainIngredient)
        assertEquals(1_000L, d.recipe.createdAt)
        assertEquals(1_000L, d.recipe.updatedAt)
        assertNull(d.recipe.deletedAt)
        assertEquals(kipKerrie.ingredients, d.ingredients.map { it.raw })
        assertEquals(listOf("Kook de rijst.", "Bak de kip."), d.steps.map { it.text })
        assertEquals("Bereiding", d.steps.first().sectionName)
        assertEquals(listOf("Crème fraîche", "Kip"), d.tags)
        assertEquals(36, id.length) // UUID
    }

    @Test
    fun `tags worden gedeeld, ongeacht hoofdletters`() = runTest {
        val a = repo.save(kipKerrie)
        val b = repo.save(appeltaart)
        assertEquals(listOf("Kip", "taart"), repo.get(b)!!.tags)
        assertTrue("Kip" in repo.get(a)!!.tags)
    }

    @Test
    fun `zoeken op titel, ingredient, tag, prefix en zonder accenten`() = runTest {
        val kip = repo.save(kipKerrie)
        val taart = repo.save(appeltaart)

        assertEquals(listOf(kip), repo.search("kerrie").map { it.id })
        assertEquals("prefix: kipf → kipfilet", listOf(kip), repo.search("kipf").map { it.id })
        assertEquals("zonder accent", listOf(kip), repo.search("creme").map { it.id })
        assertEquals("op ingrediënt", listOf(taart), repo.search("kaneel").map { it.id })
        assertEquals("alle woorden moeten passen", listOf(kip), repo.search("rijst kip").map { it.id })
        assertTrue(repo.search("lasagne").isEmpty())
        assertTrue("rare tekens breken de query niet", repo.search("\"*(-)").isEmpty())
        assertEquals("op tag", setOf(kip, taart), repo.search("kip").map { it.id }.toSet())
    }

    @Test
    fun `verwijderen is soft-delete en haalt het recept uit bibliotheek en zoeken`() = runTest {
        val id = repo.save(kipKerrie)
        now = 2_000L
        repo.delete(id)

        assertTrue(repo.observeLibrary().first().isEmpty())
        assertTrue(repo.search("kerrie").isEmpty())
        assertNull(repo.get(id))
        assertNull(repo.findBySourceUrl(kipKerrie.sourceUrl!!))
        val row = db.query("SELECT deletedAt, updatedAt FROM recipe WHERE id = ?", arrayOf(id))
        row.use { assertTrue(it.moveToFirst()); assertEquals(2_000L, it.getLong(0)); assertEquals(2_000L, it.getLong(1)) }
    }

    @Test
    fun `bewerken vervangt inhoud en zoekindex, houdt tags, favoriet en bron`() = runTest {
        val id = repo.save(kipKerrie)
        repo.setFavorite(id, true)
        now = 5_000L

        val draft = repo.get(id)!!.toDraft()
        assertEquals(kipKerrie.ingredients, draft.ingredients)
        repo.update(
            id,
            draft.copy(
                title = "Kip kerrie van mama",
                mainIngredient = MainIngredient.VIS,
                ingredients = listOf("300 gr kipdijfilet", "kokosmelk"),
                steps = listOf(ParsedStep("Alles in de pan.")),
            ),
        )

        val d = repo.get(id)!!
        assertEquals("Kip kerrie van mama", d.recipe.title)
        assertEquals(MainIngredient.VIS, d.recipe.mainIngredient)
        assertEquals(listOf("300 gr kipdijfilet", "kokosmelk"), d.ingredients.map { it.raw })
        assertEquals(listOf("Alles in de pan."), d.steps.map { it.text })
        assertEquals(5_000L, d.recipe.updatedAt)
        assertEquals(1_000L, d.recipe.createdAt)
        assertTrue(d.recipe.favorite)
        assertEquals(kipKerrie.sourceUrl, d.recipe.sourceUrl)
        assertEquals(listOf("Crème fraîche", "Kip"), d.tags)

        assertEquals("nieuw ingrediënt vindbaar", listOf(id), repo.search("kokosmelk").map { it.id })
        assertTrue("oud ingrediënt niet meer", repo.search("rijst").isEmpty())
        assertEquals("tags blijven vindbaar", listOf(id), repo.search("creme").map { it.id })
        assertEquals("één rij in de zoekindex", 1, repo.search("mama").size)
    }

    @Test
    fun `favoriet zetten werkt updatedAt bij, bibliotheek nieuwste eerst`() = runTest {
        val a = repo.save(kipKerrie)
        now = 1_500L
        val b = repo.save(appeltaart)
        now = 3_000L
        repo.setFavorite(a, true)

        val lib = repo.observeLibrary().first()
        assertEquals(listOf(b, a), lib.map { it.id })
        assertTrue(lib.last().favorite)
        assertEquals(3_000L, lib.last().updatedAt)
        assertEquals(a, repo.findBySourceUrl(kipKerrie.sourceUrl!!)?.id)
    }
}
