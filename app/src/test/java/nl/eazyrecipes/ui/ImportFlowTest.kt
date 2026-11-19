package nl.eazyrecipes.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.MainIngredient
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.FetchResult
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.importer.ImportResult
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.importer.ParsedStep
import nl.eazyrecipes.importer.PageFetcher
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.ui.importflow.ImportUiState
import nl.eazyrecipes.ui.importflow.ImportViewModel
import nl.eazyrecipes.ui.importflow.cleaned
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FormatTest {
    @Test fun minutes() {
        assertEquals("25 min", formatMinutes(25))
        assertEquals("1 uur", formatMinutes(60))
        assertEquals("1 u 40", formatMinutes(100))
        assertNull(formatMinutes(null))
        assertNull(formatMinutes(0))
    }

    @Test fun site() {
        assertEquals("leukerecepten.nl", siteLabel("https://www.leukerecepten.nl/recepten/x/"))
        assertNull(siteLabel(null))
    }

    @Test fun `cleaned haalt lege regels en witruimte weg`() {
        val r = ParsedRecipe(
            title = "  Soep ",
            description = "  ",
            ingredients = listOf(" ui ", "", "   "),
            steps = listOf(ParsedStep(" Snijd. "), ParsedStep("")),
        ).cleaned()
        assertEquals("Soep", r.title)
        assertNull(r.description)
        assertEquals(listOf("ui"), r.ingredients)
        assertEquals(listOf(ParsedStep("Snijd.")), r.steps)
    }

    @Test fun `elke fout heeft een titel en uitleg`() {
        val all = listOf(ImportResult.NoRecipe("u")) +
            ImportResult.Reason.entries.map { ImportResult.Failed(it, httpCode = 403) }
        all.forEach {
            val f = ImportViewModel.failureFor(it)
            assertTrue(f.title.isNotBlank() && f.message.isNotBlank())
        }
        assertFalse("404 opnieuw proberen heeft geen zin", ImportViewModel.failureFor(ImportResult.Failed(ImportResult.Reason.HTTP, 404)).canRetry)
        assertTrue(ImportViewModel.failureFor(ImportResult.Failed(ImportResult.Reason.NETWORK)).canRetry)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: EazyDatabase
    private lateinit var repo: RecipeRepository

    private val url = "https://www.leukerecepten.nl/recepten/kip-kerrie/"
    private val html = checkNotNull(javaClass.getResource("/pages/leukerecepten-kip-kerrie.html")).readText()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RecipeRepository(db)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun vm(fetcher: PageFetcher) = ImportViewModel(
        url,
        RecipeImporter(fetcher, browser = null),
        repo,
        // Geen netwerk in tests: deze client faalt meteen, dus het recept wordt zonder lokale foto opgeslagen.
        ImageStore(tmp.newFolder(), okhttp3.OkHttpClient.Builder().addInterceptor { throw java.io.IOException("geen netwerk in test") }.build()),
    )

    /** Room werkt op eigen threads: wacht tot de state het verwachte type heeft. */
    private suspend inline fun <reified T : ImportUiState> ImportViewModel.await(): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first { it is T } as T } }

    @Test fun `ophalen, bewerken en opslaan`() = runTest {
        val vm = vm { FetchResult.Page(html, url) }
        val editing = vm.await<ImportUiState.Editing>()
        assertEquals("Kip kerrie: romig en vers gemaakt", editing.draft.title)
        assertNull(editing.duplicateOf)

        vm.edit { it.copy(title = "Kip kerrie van mama", mainIngredient = MainIngredient.VIS, ingredients = it.ingredients + "  ") }
        vm.save()

        val id = vm.await<ImportUiState.Saved>().id
        val saved = repo.get(id)!!
        assertEquals("Kip kerrie van mama", saved.recipe.title)
        assertEquals(MainIngredient.VIS, saved.recipe.mainIngredient)
        assertEquals("lege regel niet opgeslagen", 13, saved.ingredients.size)
        assertEquals(3, saved.steps.size)
        assertNull("foto-download mislukt → alleen bron-URL", saved.recipe.imagePath)
        assertTrue(saved.recipe.imageUrl!!.endsWith("kip-kerrie-recept.jpg"))
    }

    @Test fun `zelfde link nog eens importeren toont dat hij al bestaat`() = runTest {
        vm { FetchResult.Page(html, url) }.apply { await<ImportUiState.Editing>(); save(); await<ImportUiState.Saved>() }
        val existing = repo.findBySourceUrl(url)!!.id
        val again = vm { FetchResult.Page(html, url) }.await<ImportUiState.Editing>()
        assertEquals(existing, again.duplicateOf)
    }

    @Test fun `lege titel kan niet worden opgeslagen`() = runTest {
        val vm = vm { FetchResult.Page(html, url) }
        vm.await<ImportUiState.Editing>()
        vm.edit { it.copy(title = "   ") }
        vm.save()
        assertFalse((vm.state.value as ImportUiState.Editing).canSave)
    }

    @Test fun `fout en opnieuw proberen`() = runTest {
        var calls = 0
        val vm = vm { if (calls++ == 0) FetchResult.Failed(java.io.IOException()) else FetchResult.Page(html, url) }
        val failed = vm.await<ImportUiState.Failed>()
        assertEquals("Geen verbinding", failed.title)
        vm.retry()
        vm.await<ImportUiState.Editing>()
    }
}
