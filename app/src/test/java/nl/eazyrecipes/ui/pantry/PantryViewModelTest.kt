package nl.eazyrecipes.ui.pantry

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import nl.eazyrecipes.data.EazyDatabase
import nl.eazyrecipes.data.RecipeRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PantryViewModelTest {

    private lateinit var db: EazyDatabase
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pantry-test", Context.MODE_PRIVATE)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EazyDatabase::class.java).allowMainThreadQueries().build()
        prefs.edit().clear().commit()
    }

    @After fun tearDown() = db.close()

    private fun vm() = PantryViewModel(RecipeRepository(db), prefs)

    private fun selection(vm: PantryViewModel): PantrySelection = vm.selection.value

    @Test
    fun `keuze blijft bewaard, eigen woord uit de lijst wordt een chip, dubbele tellen een keer`() {
        val a = vm()
        a.toggle("ui")
        a.addCustom("  Kipfilet ")
        a.addCustom("Halloumi")
        a.addCustom("halloumi")
        a.addCustom("   ")
        assertEquals(PantrySelection(setOf("ui", "kip"), listOf("Halloumi")), selection(a))

        // Na "herstart": zelfde keuze uit SharedPreferences.
        assertEquals(PantrySelection(setOf("ui", "kip"), listOf("Halloumi")), selection(vm()))
    }

    @Test
    fun `onbekende bewaarde sleutels vallen weg`() {
        prefs.edit().putStringSet("selected", setOf("ui", "bestaat-niet")).commit()
        assertEquals(setOf("ui"), selection(vm()).keys)
    }
}
