package nl.eazyrecipes.ui.library

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.ParsedRecipe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModelTest {

    private lateinit var db: EazyDatabase
    private lateinit var repo: RecipeRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java).allowMainThreadQueries().build()
        repo = RecipeRepository(db)
        runBlocking {
            repo.save(ParsedRecipe(title = "Kip kerrie", ingredients = listOf("200 gr kipfilet")))
            repo.save(ParsedRecipe(title = "Linzensoep", ingredients = listOf("rode linzen")))
        }
    }

    @After fun tearDown() = db.close()

    /** Room en de debounce lopen via de main-looper van Robolectric: zelf doorpompen tot [done]. */
    private fun pumpUntil(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "$what niet binnen 10 s" }
            // De klok van de main-looper is virtueel: zet hem vooruit, anders verloopt de zoek-debounce nooit.
            ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
    }

    @Test
    fun `snel typen - het veld volgt elke letter direct, de resultaten volgen daarna`() {
        val vm = LibraryViewModel(repo) { 0 }
        vm.viewModelScope.launch { vm.state.collect {} }
        pumpUntil("bibliotheek") { vm.state.value?.total == 2 }

        "kipf".forEachIndexed { i, _ ->
            vm.onQueryChange("kipf".take(i + 1))
            assertEquals("kipf".take(i + 1), vm.query) // zonder te wachten op de zoekopdracht
        }
        pumpUntil("zoekresultaat") { vm.state.value?.let { it.filter.query == "kipf" && it.shown.map { r -> r.title } == listOf("Kip kerrie") } == true }

        // Wissen via een chip of "Wis alles" leegt ook het veld.
        vm.update { LibraryFilter(tab = it.tab) }
        assertEquals("", vm.query)
        pumpUntil("alles terug") { vm.state.value?.shown?.size == 2 }
    }
}
