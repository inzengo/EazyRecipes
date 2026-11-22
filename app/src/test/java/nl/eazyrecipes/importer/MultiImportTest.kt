package nl.eazyrecipes.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.data.SeedCategories
import nl.eazyrecipes.ui.importflow.ImportUiState
import nl.eazyrecipes.ui.importflow.ImportViewModel
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java)
        .addCallback(SeedCategories).allowMainThreadQueries().build()
    private val repo = RecipeRepository(db)
    private val html by lazy { checkNotNull(javaClass.getResource("/pages/leukerecepten-kip-kerrie.html")).readText() }
    private val noNetwork = OkHttpClient.Builder().addInterceptor { throw IOException("geen netwerk") }.build()

    @After fun tearDown() = db.close()

    @Test
    fun `bladwijzers - mappen, alleen weblinks, geen dubbele`() {
        val file = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
              <DT><H3>Eten</H3>
              <DL><p>
                <DT><A HREF="https://www.leukerecepten.nl/recepten/kip-kerrie/">Kip kerrie</A>
                <DT><H3>Pasta</H3>
                <DL><p>
                  <DT><A HREF="https://eefkooktzo.nl/pasta-pesto/">Pasta pesto</A>
                  <DT><A HREF="https://www.leukerecepten.nl/recepten/kip-kerrie/">Kip kerrie (dubbel)</A>
                </DL><p>
              </DL><p>
              <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
              <DT><A HREF="https://nos.nl/">Nieuws</A>
            </DL><p>
        """.trimIndent()
        val marks = Bookmarks.parse(file)
        assertEquals(
            listOf(
                Bookmark("Kip kerrie", "https://www.leukerecepten.nl/recepten/kip-kerrie/", "Eten"),
                Bookmark("Pasta pesto", "https://eefkooktzo.nl/pasta-pesto/", "Eten › Pasta"),
                Bookmark("Nieuws", "https://nos.nl/", ""),
            ),
            marks,
        )
    }

    @Test
    fun `wachtrij - ophalen en opslaan, al aanwezig, mislukt, dubbele links een keer`() = runBlocking {
        val good = "https://www.leukerecepten.nl/recepten/kip-kerrie/"
        val missing = "https://example.nl/bestaat-niet"
        val importer = RecipeImporter({ url -> if (url.startsWith(good)) FetchResult.Page(html, good) else FetchResult.HttpError(404) }, null)
        val batch = BatchImport(importer, repo, ImageStore(tmp.newFolder(), noNetwork), CoroutineScope(SupervisorJob() + Dispatchers.IO))

        assertEquals(2, batch.enqueue(listOf(good, "$good?utm_source=x", missing)))
        withTimeout(20_000) { while (!batch.items.value.all { it.done }) delay(20) }
        with(batch.items.value) {
            assertEquals(BatchItem.Status.SAVED, first { it.url == good }.status)
            assertEquals(BatchItem.Status.FAILED, first { it.url == missing }.status)
        }
        assertEquals(1, repo.observeLibrary().first().size)

        // Nog eens dezelfde link: staat er al, niet dubbel opgeslagen.
        batch.clearFinished()
        batch.enqueue(listOf(good))
        withTimeout(20_000) { while (!batch.items.value.all { it.done }) delay(20) }
        assertEquals(BatchItem.Status.EXISTS, batch.items.value.single().status)
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test
    fun `zelf invoeren - zonder link een leeg concept, na een mislukte link met de link als bron`() {
        val importer = RecipeImporter({ FetchResult.HttpError(404) }, null)
        val manual = ImportViewModel("", importer, repo, ImageStore(tmp.newFolder(), noNetwork))
        with(manual.state.value as ImportUiState.Editing) { assertEquals("", draft.title); assertNull(draft.sourceUrl) }

        val failed = ImportViewModel("https://example.nl/geen-recept?utm_medium=x", importer, repo, ImageStore(tmp.newFolder(), noNetwork))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        runBlocking { withTimeout(10_000) { while (failed.state.value !is ImportUiState.Failed) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); delay(10) } } }
        failed.manual()
        assertEquals("https://example.nl/geen-recept", (failed.state.value as ImportUiState.Editing).draft.sourceUrl)
    }
}
