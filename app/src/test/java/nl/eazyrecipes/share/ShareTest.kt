package nl.eazyrecipes.share

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.data.SeedCategories
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.importer.ParsedStep
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShareTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dbs = mutableListOf<EazyDatabase>()
    private fun newApp(): Triple<RecipeRepository, ImageStore, Bundles> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java)
            .addCallback(SeedCategories).allowMainThreadQueries().build().also { dbs += it }
        val repo = RecipeRepository(db)
        val images = ImageStore(tmp.newFolder())
        return Triple(repo, images, Bundles(repo, images))
    }

    @After fun tearDown() = dbs.forEach { it.close() }

    private val paella = ParsedRecipe(
        title = "Paella",
        sourceUrl = "https://example.nl/paella",
        servings = 4,
        totalMinutes = 60,
        categoryIds = listOf("vis", "vlees"),
        tags = listOf("Weekend", "Spaans"),
        ingredients = listOf("300 g paellarijst", "200 g gamba's", "100 g chorizo"),
        steps = listOf(ParsedStep("Fruit de chorizo.", "Bereiding"), ParsedStep("Voeg rijst en bouillon toe en laat 20 minuten garen.", "Bereiding")),
    )

    private fun photo(): File = tmp.newFile("foto.png").also { f ->
        f.outputStream().use { Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `back-up en terug in een lege app - alles komt mee, tweede keer niets dubbel`() = runBlocking {
        val (repo, images, bundles) = newApp()
        val soep = repo.createCategory("Soep", "aqua", "bowl-steam")
        val id = repo.save(paella, imagePath = photo().path)
        val linzen = repo.save(ParsedRecipe(title = "Linzensoep", categoryIds = listOf(soep), ingredients = listOf("200 g linzen")))
        repo.setNotes(id, "Minder zout")
        repo.setFavorite(id, true)
        val weekend = repo.observeLabels().first().first { it.name == "Weekend" }.id
        repo.setLabelColor(weekend, "geel")
        val col = repo.createCollection("Feest")
        repo.setInCollection(col, id, true)

        val file = ByteArrayOutputStream().also { bundles.writeBackup(it) }.toByteArray()

        val (repo2, _, bundles2) = newApp()
        val preview = bundles2.peek { ByteArrayInputStream(file) }
        assertEquals(EazyFile.Kind.BACKUP, preview.kind)
        assertEquals(2, preview.recipes.size)

        val result = bundles2.import { ByteArrayInputStream(file) }
        assertEquals(ImportResult(added = 2, alreadyThere = 0, collection = null), result)

        val p = repo2.get(id)!! // zelfde id als het origineel
        assertEquals("Paella", p.recipe.title)
        assertEquals(listOf("vis", "vlees"), p.categoryIds)
        assertEquals(setOf("Weekend", "Spaans"), p.labels.map { it.name }.toSet())
        assertEquals("geel", p.labels.first { it.name == "Weekend" }.color)
        assertEquals("Minder zout", p.recipe.notes)
        assertTrue(p.recipe.favorite)
        assertEquals(paella.ingredients, p.ingredients.map { it.raw })
        assertEquals(paella.steps.map { it.text }, p.steps.map { it.text })
        assertNotNull("foto komt mee", p.recipe.imagePath?.let(::File)?.takeIf { it.isFile })
        // De eigen categorie "Soep" is aangemaakt met haar kleur en icoon.
        val soep2 = repo2.observeCategories().first().single { it.name == "Soep" }
        assertEquals("aqua" to "bowl-steam", soep2.color to soep2.icon)
        assertEquals(listOf(soep2.id), repo2.get(linzen)!!.categoryIds)
        assertEquals(listOf("Paella"), repo2.observeCollections().first().single { it.name == "Feest" }.recipes.map { it.title })

        // Nog een keer importeren: niets dubbel.
        assertEquals(2, bundles2.alreadyThere(preview).size)
        assertEquals(ImportResult(0, 2, null), bundles2.import { ByteArrayInputStream(file) })
        assertEquals(2, repo2.observeLibrary().first().size)
    }

    @Test
    fun `gedeeld recept met dezelfde bronlink telt als al aanwezig`() = runBlocking {
        val (repo, _, bundles) = newApp()
        val id = repo.save(paella)
        val file = ByteArrayOutputStream().also { bundles.writeRecipe(id, it) }.toByteArray()
        val (repo2, _, bundles2) = newApp()
        repo2.save(paella) // zelf al geïmporteerd van dezelfde site, ander id
        assertEquals(ImportResult(0, 1, null), bundles2.import { ByteArrayInputStream(file) })
    }

    @Test
    fun `kapotte of vreemde bestanden geven een duidelijke fout`() {
        fun fails(bytes: ByteArray, expected: String) {
            try {
                EazyFile.read(ByteArrayInputStream(bytes))
                fail("geen fout voor: $expected")
            } catch (e: EazyFile.InvalidFile) {
                assertTrue(e.message!!, e.message!!.contains(expected))
            }
        }
        fun zip(json: String) = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry(EazyFile.JSON_ENTRY)); it.write(json.toByteArray()); it.closeEntry() }
        }.toByteArray()

        fails("geen zip".toByteArray(), "geen EazyRecipes")
        fails(zip("""{"format":"iets-anders"}"""), "geen EazyRecipes")
        fails(zip("""{"format":"eazyrecipes","version":99,"recipes":[]}"""), "nieuwere versie")
        fails(zip("""{"format":"eazyrecipes","version":1,"recipes":[{"geen":"titel"}]}"""), "beschadigd")
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }.toByteArray()

    @Test
    fun `vijandige bestanden - diep genest, reuzentekst, rare id en link, te grote foto`() = runBlocking {
        // Diep geneste JSON: nette fout, geen stack overflow.
        val deep = """{"format":"eazyrecipes","version":1,"recipes":[],"x":""" + "[".repeat(100_000) + "]".repeat(100_000) + "}"
        try { EazyFile.read(ByteArrayInputStream(zipOf(EazyFile.JSON_ENTRY to deep.toByteArray()))); fail("geen fout") } catch (e: EazyFile.InvalidFile) { }

        // Reuzentitel wordt ingekort, een id met "/" wordt vervangen, een tel:-link valt weg.
        val json = """{"format":"eazyrecipes","version":1,"kind":"recipe","recipes":[
            {"id":"a/b","title":"${"x".repeat(3_000_000)}","sourceUrl":"tel:0612345678","imageUrl":"https://ok.nl/f.jpg","createdAt":9223372036854775807}]}"""
        val b = EazyFile.read(ByteArrayInputStream(zipOf(EazyFile.JSON_ENTRY to json.toByteArray()))).first
        with(b.recipes.single()) {
            assertEquals(300, title.length)
            assertTrue(id, Regex("[A-Za-z0-9-]{1,64}").matches(id))
            assertEquals(null, sourceUrl)
            assertEquals("https://ok.nl/f.jpg", imageUrl)
        }
        // En importeren: de datum uit de toekomst wordt begrensd.
        val (repo, images, bundles) = newApp()
        val file = zipOf(EazyFile.JSON_ENTRY to json.toByteArray())
        bundles.import { ByteArrayInputStream(file) }
        assertTrue(repo.observeLibrary().first().single().createdAt <= System.currentTimeMillis())

        // Te grote foto: fout, en er blijft niets achter in de fotomap (ook geen .part).
        val withImage = """{"format":"eazyrecipes","version":1,"recipes":[{"id":"r-1","title":"Met foto","image":"images/r-1.jpg"}]}"""
        val big = zipOf(EazyFile.JSON_ENTRY to withImage.toByteArray(), "images/r-1.jpg" to ByteArray(16 * 1024 * 1024))
        val (_, images2, bundles2) = newApp()
        val dir = images2.javaClass.getDeclaredField("dir").apply { isAccessible = true }.get(images2) as File
        try { bundles2.import { ByteArrayInputStream(big) }; fail("geen fout") } catch (e: EazyFile.InvalidFile) { }
        assertEquals(emptyList<String>(), dir.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `recept als tekst`() = runBlocking {
        val (repo, _, _) = newApp()
        val d = repo.get(repo.save(paella))!!
        val text = RecipeText.format(d, servings = 2, factor = 0.5)
        assertEquals(
            """
            Paella
            1 uur · 2 porties

            Ingrediënten
            • 150 g paellarijst
            • 100 g gamba's
            • 50 g chorizo

            Bereiding
            1. Fruit de chorizo.
            2. Voeg rijst en bouillon toe en laat 20 minuten garen.

            Bron: https://example.nl/paella

            Gedeeld met EazyRecipes
            """.trimIndent(),
            text,
        )
        assertEquals("Kip kerrie", Sharing.safeName("Kip / kerrie"))
    }
}
