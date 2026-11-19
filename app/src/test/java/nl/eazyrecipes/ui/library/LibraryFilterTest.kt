package nl.eazyrecipes.ui.library

import nl.eazyrecipes.data.MainIngredient
import nl.eazyrecipes.data.RecipeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryFilterTest {

    private fun r(
        id: String, m: MainIngredient?, min: Int?, created: Long, fav: Boolean = false, site: String? = "leukerecepten.nl",
    ) = RecipeEntity(id = id, title = id, mainIngredient = m, totalMinutes = min, favorite = fav, sourceSite = site, createdAt = created, updatedAt = created)

    private val kip = r("Kip kerrie", MainIngredient.VLEES, 30, 1, fav = true)
    private val taart = r("Appeltaart", MainIngredient.ZOET, 100, 2, site = "eefkooktzo.nl")
    private val zalm = r("Zalm", MainIngredient.VIS, 25, 3)
    private val soep = r("Linzensoep", MainIngredient.VEGAN, null, 4, fav = true)
    private val pasta = r("Pasta pesto", MainIngredient.VEGA, 15, 5, site = null)
    private val all = listOf(kip, taart, zalm, soep, pasta)

    private fun ids(f: LibraryFilter, fts: Set<String>? = null) = applyFilter(all, fts, f).map { it.id }

    @Test fun `standaard - nieuwste eerst`() =
        assertEquals(listOf("Pasta pesto", "Linzensoep", "Zalm", "Appeltaart", "Kip kerrie"), ids(LibraryFilter()))

    @Test fun categorieen() {
        assertEquals(listOf("Kip kerrie"), ids(LibraryFilter(category = Category.VLEES)))
        assertEquals("vegan telt ook als vega", listOf("Pasta pesto", "Linzensoep"), ids(LibraryFilter(category = Category.VEGA)))
        assertEquals(listOf("Linzensoep"), ids(LibraryFilter(category = Category.VEGAN)))
        assertEquals("snel = ≤ 30 min, ongeacht soort", listOf("Pasta pesto", "Zalm", "Kip kerrie"), ids(LibraryFilter(category = Category.SNEL)))
    }

    @Test fun `favorieten, tijd, bron, zoeken`() {
        assertEquals(listOf("Linzensoep", "Kip kerrie"), ids(LibraryFilter(tab = LibraryTab.FAVORITES)))
        assertEquals("onbekende tijd valt af", listOf("Pasta pesto"), ids(LibraryFilter(maxMinutes = 20)))
        assertEquals(listOf("Appeltaart"), ids(LibraryFilter(sources = setOf("eefkooktzo.nl"))))
        assertEquals(listOf("Zalm"), ids(LibraryFilter(), fts = setOf("Zalm")))
        assertEquals("alles tegelijk (EN)", listOf("Kip kerrie"), ids(LibraryFilter(tab = LibraryTab.FAVORITES, category = Category.SNEL)))
    }

    @Test fun sorteren() {
        assertEquals("snelste eerst, onbekend achteraan", listOf("Pasta pesto", "Zalm", "Kip kerrie", "Appeltaart", "Linzensoep"), ids(LibraryFilter(sort = SortOrder.QUICK)))
        assertEquals(listOf("Appeltaart", "Kip kerrie", "Linzensoep", "Pasta pesto", "Zalm"), ids(LibraryFilter(sort = SortOrder.AZ)))
    }

    @Test fun `aantal filters voor het bolletje`() {
        assertEquals(0, LibraryFilter(query = "kip", category = Category.VIS).sheetCount)
        assertEquals(3, LibraryFilter(maxMinutes = 30, sources = setOf("a", "b"), sort = SortOrder.AZ).sheetCount)
    }

    @Test fun `uitgelicht - stabiel per dag, alleen zonder filters en vanaf 4 recepten`() {
        val day1 = featuredFor(all, LibraryFilter(), epochDay = 20_000)
        assertEquals(day1, featuredFor(all.reversed(), LibraryFilter(), epochDay = 20_000))
        assertEquals("volgende dag het volgende recept", 5, (0L until 5L).map { featuredFor(all, LibraryFilter(), 20_000 + it) }.toSet().size)
        assertNull(featuredFor(all, LibraryFilter(query = "kip"), 20_000))
        assertNull(featuredFor(all, LibraryFilter(tab = LibraryTab.FAVORITES), 20_000))
        assertNull(featuredFor(all.take(3), LibraryFilter(), 20_000))
    }
}
