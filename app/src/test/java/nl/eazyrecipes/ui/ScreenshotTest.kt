package nl.eazyrecipes.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import nl.eazyrecipes.data.CollectionEntity
import nl.eazyrecipes.data.CollectionSummary
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.MainIngredient
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.FetchResult
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.importer.JsonLdRecipeParser
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.ui.detail.DetailState
import nl.eazyrecipes.ui.detail.RecipeScreen
import nl.eazyrecipes.ui.detail.RecipeViewModel
import nl.eazyrecipes.ui.importflow.ImportScreen
import nl.eazyrecipes.ui.importflow.ImportUiState
import nl.eazyrecipes.ui.importflow.ImportViewModel
import nl.eazyrecipes.ui.home.HomeScreen
import nl.eazyrecipes.ui.library.Category
import nl.eazyrecipes.ui.library.LibraryFilter
import nl.eazyrecipes.ui.library.LibraryScreen
import nl.eazyrecipes.ui.library.LibraryTab
import nl.eazyrecipes.ui.library.LibraryUiState
import nl.eazyrecipes.ui.library.LibraryViewModel
import nl.eazyrecipes.ui.library.SortOrder
import nl.eazyrecipes.ui.library.applyFilter
import nl.eazyrecipes.ui.library.featuredFor
import nl.eazyrecipes.ui.theme.EazyTheme
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException

/**
 * Tekent de schermen als PNG naar de map `app/build/screenshots`, om het design te controleren zonder telefoon of emulator.
 * Foto's laden hier bewust niet (Coil zonder netwerk), dus je ziet de foto-loze variant: een vlak in de contextkleur.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: EazyDatabase
    private lateinit var repo: RecipeRepository
    private val noNetwork = OkHttpClient.Builder().addInterceptor { throw IOException("geen netwerk") }.build()
    private val url = "https://www.leukerecepten.nl/recepten/kip-kerrie/"
    private val html by lazy { checkNotNull(javaClass.getResource("/pages/leukerecepten-kip-kerrie.html")).readText() }

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    @Before fun setUp() {
        // Coil zou anders zelf foto's van internet halen: tests moeten offline en voorspelbaar zijn.
        coil3.SingletonImageLoader.setUnsafe { ctx ->
            coil3.ImageLoader.Builder(ctx).components {
                add(coil3.intercept.Interceptor { chain ->
                    if (chain.request.data is String) coil3.request.ErrorResult(null, chain.request, IOException("geen netwerk in test"))
                    else chain.proceed()
                })
            }.build()
        }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RecipeRepository(db)
    }

    @After fun tearDown() = db.close()

    /**
     * Room levert resultaten af via de main-looper van Robolectric; die draait niet vanzelf door.
     * Daarom pompen we hem zelf tot [done], in echte tijd.
     */
    private fun pumpUntil(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "$what niet klaar binnen 30 s" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(5)
        }
    }

    /** @param ready wacht hierop ná setContent (pas dan draait de main-looper van Robolectric door). */
    private fun shot(name: String, ready: () -> Boolean = { true }, state: () -> Any? = { null }, content: @Composable () -> Unit) {
        compose.setContent { EazyTheme(content) }
        runCatching { compose.waitUntil(30_000, ready) }.onFailure { throw AssertionError("$name niet klaar: ${state()}", it) }
        compose.waitForIdle()
        // Zelf tekenen: captureToImage() wacht onder Robolectric eindeloos op een hertekening.
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun recipe(title: String, m: MainIngredient, minutes: Int?, fav: Boolean = false, site: String? = null) = RecipeEntity(
        id = title, title = title, mainIngredient = m, totalMinutes = minutes, favorite = fav, sourceSite = site, createdAt = 0, updatedAt = 0,
    )

    private fun importVm(fetch: FetchResult) =
        ImportViewModel(url, RecipeImporter({ fetch }, null), repo, ImageStore(tmp.newFolder(), noNetwork))

    private fun libraryState(recipes: List<RecipeEntity>, f: LibraryFilter = LibraryFilter()) = LibraryUiState(
        total = recipes.size,
        shown = applyFilter(recipes, null, f).let { list -> featuredFor(recipes, f, 0)?.let { ft -> list - ft } ?: list },
        featured = featuredFor(recipes, f, 0),
        filter = f,
        availableSources = listOf("eefkooktzo.nl", "leukerecepten.nl"),
    )

    private val sample = listOf(
        recipe("Kip kerrie: romig en vers gemaakt", MainIngredient.VLEES, 30, fav = true, site = "leukerecepten.nl"),
        recipe("Appelkruimeltaart", MainIngredient.ZOET, 100, site = "leukerecepten.nl"),
        recipe("Zalm uit de oven met citroen", MainIngredient.VIS, 25, site = "eefkooktzo.nl"),
        recipe("Champignon bourguignon", MainIngredient.VEGA, 60, site = "leukerecepten.nl"),
        recipe("Linzensoep", MainIngredient.VEGAN, 45, site = "eefkooktzo.nl"),
        recipe("Griekse aardappelstoof", MainIngredient.VEGA, 50, site = "leukerecepten.nl"),
    ).mapIndexed { i, r -> r.copy(createdAt = i.toLong()) }

    @Test fun library_empty() = shot("01-library-empty") {
        LibraryScreen(libraryState(emptyList()), {}, {}, {})
    }

    @Test fun library_filled() = shot("02-library") {
        LibraryScreen(libraryState(sample), {}, {}, {})
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun library_category_with_filters() = shot("02b-library-vega-filters") {
        LibraryScreen(libraryState(sample, LibraryFilter(category = Category.VEGA, maxMinutes = 60, sort = SortOrder.QUICK)), {}, {}, {})
    }

    private val sampleCollections by lazy {
        listOf(
            CollectionSummary(CollectionEntity("c1", "Doordeweeks", 0, 0), sample.take(3)),
            CollectionSummary(CollectionEntity("c2", "Feestjes", 0, 0), listOf(sample[1])),
            CollectionSummary(CollectionEntity("c3", "Nog proberen", 0, 0), emptyList()),
        )
    }

    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun library_collections_tab() = shot("02d-collections") {
        LibraryScreen(libraryState(sample, LibraryFilter(tab = LibraryTab.COLLECTIONS)).copy(collections = sampleCollections), {}, {}, {})
    }

    @Test fun library_open_collection() = shot("02e-collection-open") {
        val f = LibraryFilter(collectionId = "c1")
        LibraryScreen(
            LibraryUiState(3, applyFilter(sample, null, f, sample.take(3).map { it.id }.toSet()), null, f, emptyList(), collections = sampleCollections),
            {}, {}, {},
        )
    }

    @Test fun home_with_nav() {
        runBlocking { listOf("Soep", "Taart", "Stoof", "Salade").forEach { repo.save(nl.eazyrecipes.importer.ParsedRecipe(title = it)) } }
        val vm = LibraryViewModel(repo) { 0 }
        shot("02c-home-nav", { vm.state.value != null }) {
            HomeScreen(vm, {}, {}, pantry = {})
        }
    }

    private val pantryActions = nl.eazyrecipes.ui.pantry.PantryActions({}, {}, {}, {}, {})

    private val pantryRecipes by lazy {
        nl.eazyrecipes.ui.pantry.PantryMatcher.index(
            listOf(
                recipe("Shakshuka met feta", MainIngredient.VEGA, 25) to listOf("4 eieren", "1 blik tomatenblokjes", "1 ui", "1 rode paprika", "2 tenen knoflook", "75 g feta", "zout en peper"),
                recipe("Kip kerrie: romig en vers gemaakt", MainIngredient.VLEES, 30) to listOf("150 gr rijst", "200 gr kipfilet", "1 ui", "2 tl kerriepoeder", "125 ml crème fraîche", "1 rode paprika"),
                recipe("Zalm uit de oven met citroen", MainIngredient.VIS, 25) to listOf("2 zalmfilets", "1 citroen", "300 g krieltjes", "2 el olijfolie"),
                recipe("Linzensoep", MainIngredient.VEGAN, 45) to listOf("200 g rode linzen", "1 ui", "2 wortels", "1 blik tomatenblokjes", "1 l groentebouillon", "1 tl komijn"),
                recipe("Appelkruimeltaart", MainIngredient.ZOET, 100) to listOf("4 appels", "200 g bloem", "150 g boter", "100 g suiker", "1 ei"),
            )
        )
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun pantry_with_matches() = shot("08-pantry") {
        val sel = nl.eazyrecipes.ui.pantry.PantrySelection(setOf("ui", "knoflook", "eieren", "tomaat", "paprika"), listOf("halloumi"))
        nl.eazyrecipes.ui.pantry.PantryScreen(nl.eazyrecipes.ui.pantry.pantryState(pantryRecipes, sel), pantryActions)
    }

    @Test fun pantry_nothing_chosen() = shot("08b-pantry-empty") {
        nl.eazyrecipes.ui.pantry.PantryScreen(nl.eazyrecipes.ui.pantry.pantryState(pantryRecipes, nl.eazyrecipes.ui.pantry.PantrySelection()), pantryActions)
    }

    @Test fun import_loading() = shot("03-import-loading") {
        // Nooit klaar, zodat de laad-toestand blijft staan.
        val vm = ImportViewModel(url, RecipeImporter({ kotlinx.coroutines.awaitCancellation() }, null), repo, ImageStore(tmp.newFolder(), noNetwork))
        ImportScreen(url, vm, {}, {}, {})
    }

    @Test fun import_failed() {
        val vm = importVm(FetchResult.HttpError(404))
        pumpUntil("fout") { vm.state.value is ImportUiState.Failed }
        shot("04-import-failed", { vm.state.value is ImportUiState.Failed }) { ImportScreen(url, vm, {}, {}, {}) }
    }

    @Test fun import_preview() {
        val vm = importVm(FetchResult.Page(html, url))
        pumpUntil("voorbeeld") { vm.state.value is ImportUiState.Editing }
        shot("05-import-preview", { vm.state.value is ImportUiState.Editing }) { ImportScreen(url, vm, {}, {}, {}) }
    }

    @Config(qualifiers = "w393dp-h2600dp-xxhdpi")
    @Test fun import_preview_full() {
        val vm = importVm(FetchResult.Page(html, url))
        pumpUntil("voorbeeld") { vm.state.value is ImportUiState.Editing }
        shot("06-import-preview-full", { vm.state.value is ImportUiState.Editing }, { vm.state.value }) { ImportScreen(url, vm, {}, {}, {}) }
    }

    @Config(qualifiers = "w393dp-h2200dp-xxhdpi")
    @Test fun detail_full() {
        val id = runBlocking { repo.save(checkNotNull(JsonLdRecipeParser.parse(html, url))) }
        val vm = RecipeViewModel(id, repo, ImageStore(tmp.newFolder(), noNetwork))
        pumpUntil("detail") { vm.state.value is DetailState.Found }
        shot("07-detail-full", { vm.state.value is DetailState.Found }) { RecipeScreen(vm, {}, {}) }
    }
}
