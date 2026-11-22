package nl.eazyrecipes.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java).addCallback(nl.eazyrecipes.data.SeedCategories)
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
        categoryId = "vlees",
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
        assertEquals("vlees", d.recipe.categoryId)
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
    fun `categorieen - maken, wijzigen, volgorde, verwijderen maakt recepten soortloos`() = runTest {
        assertEquals(listOf("vlees", "vis", "vega", "vegan", "zoet"), repo.observeCategories().first().map { it.id })
        val soep = repo.createCategory("  Soep ", "aqua", "bowl-steam")
        with(repo.observeCategories().first().last()) { assertEquals(soep, id); assertEquals("Soep", name); assertEquals(5, position) }

        val id = repo.save(kipKerrie.copy(categoryId = soep))
        assertEquals(soep, repo.get(id)!!.recipe.categoryId)
        assertNull("onbekende categorie wordt geen soort", repo.get(repo.save(kipKerrie.copy(categoryId = "bestaat-niet")))!!.recipe.categoryId)

        repo.updateCategory(soep, "Soepen", "oranje", "cooking-pot")
        with(repo.observeCategories().first().first { it.id == soep }) { assertEquals("Soepen", name); assertEquals("oranje", color); assertEquals("cooking-pot", icon) }

        repo.moveCategory(soep, -1)
        assertEquals(listOf("vlees", "vis", "vega", "vegan", soep, "zoet"), repo.observeCategories().first().map { it.id })
        repo.moveCategory("vlees", -1) // kan niet verder naar voren
        assertEquals("vlees", repo.observeCategories().first().first().id)

        now = 5_000L
        repo.deleteCategory(soep)
        assertEquals(listOf("vlees", "vis", "vega", "vegan", "zoet"), repo.observeCategories().first().map { it.id })
        with(repo.get(id)!!.recipe) { assertNull(categoryId); assertEquals(5_000L, updatedAt) }

        // Standaardcategorie weg en opnieuw "Vlees" maken: dezelfde (vaste) id komt terug, dus de import-gok werkt weer.
        repo.deleteCategory("vlees")
        assertEquals("vlees", repo.createCategory("vlees", "koraal", "cow"))
        assertEquals("vlees", repo.get(repo.save(kipKerrie.copy(categoryId = "vlees")))!!.recipe.categoryId)
    }

    @Test
    fun `labels - koppelen, hernoemen, samenvoegen, kleur, verbergen, verwijderen`() = runTest {
        val kip = repo.save(kipKerrie) // tags: Kip, Crème fraîche
        val taart = repo.save(appeltaart) // tags: taart, KIP (= Kip)
        fun labels() = runBlocking { repo.observeLabels().first().associate { it.name to it.recipes } }
        assertEquals(mapOf("Kip" to 2, "Crème fraîche" to 1, "taart" to 1), labels())

        // Zelf een label maken en aan een recept hangen.
        val weekend = repo.createLabel("Weekend")
        repo.setRecipeLabel(kip, weekend, true)
        assertEquals(listOf("Crème fraîche", "Kip", "Weekend"), repo.get(kip)!!.labels.map { it.name })
        assertEquals("zoekindex kent het nieuwe label", listOf(kip), repo.search("weekend").map { it.id })
        assertEquals(mapOf(kip to setOf(weekend)), repo.observeRecipeLabels().first().mapValues { (_, v) -> v.filter { it == weekend }.toSet() }.filterValues { it.isNotEmpty() })

        // Hernoemen; zoeken volgt.
        repo.renameLabel(weekend, "Zondag")
        assertEquals(listOf(kip), repo.search("zondag").map { it.id })
        assertEquals(emptyList<String>(), repo.search("weekend").map { it.id })

        // Hernoemen naar een bestaande naam = samenvoegen.
        val taartId = repo.get(taart)!!.labels.first { it.name == "taart" }.id
        repo.renameLabel(taartId, "zondag")
        assertEquals(mapOf("Kip" to 2, "Crème fraîche" to 1, "zondag" to 2), labels())

        // Kleur en verbergen.
        val zondag = repo.observeLabels().first().first { it.name == "zondag" }.id
        repo.setLabelColor(zondag, "geel")
        repo.setLabelHidden(zondag, true)
        with(repo.observeLabels().first().first { it.id == zondag }.tag) { assertEquals("geel", color); assertEquals(true, hidden) }

        // Samenvoegen met de knop, dan verwijderen.
        val creme = repo.observeLabels().first().first { it.name == "Crème fraîche" }.id
        repo.mergeLabels(creme, zondag)
        assertEquals(mapOf("Kip" to 2, "zondag" to 2), labels())
        repo.deleteLabel(zondag)
        assertEquals(mapOf("Kip" to 2), labels())
        assertEquals(listOf("Kip"), repo.get(kip)!!.labels.map { it.name })

        // Ontkoppelen.
        val kipLabel = repo.get(kip)!!.labels.single().id
        repo.setRecipeLabel(kip, kipLabel, false)
        assertEquals(mapOf("Kip" to 1), labels())

        // Een verwijderd label komt terug als een nieuw recept het meebrengt.
        repo.save(ParsedRecipe(title = "Nieuw", tags = listOf("Zondag")))
        assertEquals(1, labels()["Zondag"])
    }

    @Test
    fun `samenvoegen - nieuwe import met de oude naam komt bij het samengevoegde label, kleur blijft`() = runTest {
        repo.save(ParsedRecipe(title = "A", tags = listOf("veggie")))
        repo.save(ParsedRecipe(title = "B", tags = listOf("Vegetarisch")))
        val all = repo.observeLabels().first().associateBy { it.name }
        // Via het bewerk-venster: hernoemen naar een bestaande naam mét een kleur = samenvoegen, kleur op het overblijvende label.
        val survivor = repo.updateLabel(all.getValue("veggie").id, "vegetarisch", "groen", false)
        assertEquals(all.getValue("Vegetarisch").id, survivor)
        with(repo.observeLabels().first().single()) { assertEquals("vegetarisch", name); assertEquals("groen", tag.color); assertEquals(2, recipes) }

        repo.save(ParsedRecipe(title = "C", tags = listOf("Veggie")))
        assertEquals("geen dubbel label terug", listOf("vegetarisch" to 3), repo.observeLabels().first().map { it.name to it.recipes })
        assertEquals(3, repo.search("vegetarisch").size)
        assertEquals("oude naam staat niet meer in de zoekindex", 0, repo.search("veggie").size)
    }

    @Test
    fun `notitie en omslagfoto opslaan werkt updatedAt bij`() = runTest {
        val id = repo.save(kipKerrie)
        now = 2_000L
        repo.setNotes(id, "  Minder kerrie  ")
        assertEquals("Minder kerrie", repo.get(id)!!.recipe.notes)
        repo.setNotes(id, "   ")
        assertNull("leeg = geen notitie", repo.get(id)!!.recipe.notes)
        now = 3_000L
        repo.setImagePath(id, "/images/x.jpg")
        with(repo.get(id)!!.recipe) { assertEquals("/images/x.jpg", imagePath); assertEquals(3_000L, updatedAt) }
    }

    @Test
    fun `recepten met ingredientregels in volgorde, zonder verwijderde`() = runTest {
        val kip = repo.save(kipKerrie)
        now = 2_000L
        val taart = repo.save(appeltaart)
        assertEquals(
            listOf(taart to appeltaart.ingredients, kip to kipKerrie.ingredients),
            repo.observeRecipesWithIngredients().first().map { (r, lines) -> r.id to lines },
        )
        repo.delete(taart)
        assertEquals(listOf(kip), repo.observeRecipesWithIngredients().first().map { it.first.id })
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
                categoryId = "vis",
                ingredients = listOf("300 gr kipdijfilet", "kokosmelk"),
                steps = listOf(ParsedStep("Alles in de pan.")),
            ),
        )

        val d = repo.get(id)!!
        assertEquals("Kip kerrie van mama", d.recipe.title)
        assertEquals("vis", d.recipe.categoryId)
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
    fun `collecties - toevoegen, weghalen, opnieuw, hernoemen, verwijderen`() = runTest {
        val kip = repo.save(kipKerrie)
        val taart = repo.save(appeltaart)
        val week = repo.createCollection("  Doordeweeks ")
        val feest = repo.createCollection("Feest")

        repo.setInCollection(week, kip, true)
        repo.setInCollection(week, taart, true)
        repo.setInCollection(feest, taart, true)
        var cols = repo.observeCollections().first()
        assertEquals(listOf("Doordeweeks", "Feest"), cols.map { it.name })
        assertEquals("volgorde van toevoegen", listOf(kip, taart), cols[0].recipes.map { it.id })
        assertEquals("recept in meerdere collecties", listOf(taart), cols[1].recipes.map { it.id })

        repo.setInCollection(week, kip, false)
        assertEquals(listOf(taart), repo.observeCollections().first()[0].recipes.map { it.id })
        repo.setInCollection(week, kip, true)
        assertEquals("opnieuw toegevoegd komt achteraan", listOf(taart, kip), repo.observeCollections().first()[0].recipes.map { it.id })

        repo.renameCollection(feest, "Verjaardag")
        repo.delete(taart)
        cols = repo.observeCollections().first()
        assertEquals("Verjaardag", cols[1].name)
        assertEquals("verwijderd recept verdwijnt uit collecties", listOf(kip), cols[0].recipes.map { it.id })

        repo.deleteCollection(week)
        assertEquals(listOf("Verjaardag"), repo.observeCollections().first().map { it.name })
        assertTrue("recept zelf blijft bestaan", repo.get(kip) != null)
        val tombstones = db.query("SELECT COUNT(*) FROM collection_recipe WHERE deletedAt IS NOT NULL", null)
        tombstones.use { it.moveToFirst(); assertTrue("soft-delete voor sync", it.getInt(0) >= 1) }
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
