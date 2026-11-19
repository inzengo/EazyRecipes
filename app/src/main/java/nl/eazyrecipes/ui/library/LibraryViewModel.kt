package nl.eazyrecipes.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.eazyrecipes.data.MainIngredient
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.ui.theme.isQuick
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/** De categorie-tegels: "Alles", de vijf soorten, en "Snel" (≤ 30 min, los van de soort). */
enum class Category(val main: MainIngredient? = null) {
    ALLES, VLEES(MainIngredient.VLEES), VIS(MainIngredient.VIS), VEGA(MainIngredient.VEGA),
    VEGAN(MainIngredient.VEGAN), ZOET(MainIngredient.ZOET), SNEL;

    fun matches(r: RecipeEntity): Boolean = when (this) {
        ALLES -> true
        SNEL -> isQuick(r.totalMinutes)
        VEGA -> r.mainIngredient == MainIngredient.VEGA || r.mainIngredient == MainIngredient.VEGAN // vegan is ook vega
        else -> r.mainIngredient == main
    }
}

enum class LibraryTab { RECIPES, FAVORITES, COLLECTIONS }

enum class SortOrder(val label: String) { RECENT("Nieuwste"), QUICK("Snelste eerst"), AZ("A–Z") }

data class LibraryFilter(
    val query: String = "",
    val category: Category = Category.ALLES,
    val tab: LibraryTab = LibraryTab.RECIPES,
    /** Alleen recepten met een bekende tijd ≤ dit aantal minuten. */
    val maxMinutes: Int? = null,
    val sources: Set<String> = emptySet(),
    val sort: SortOrder = SortOrder.RECENT,
) {
    /** Aantal filters uit de filter-sheet (voor het bolletje op de filterknop). */
    val sheetCount: Int get() = (if (maxMinutes != null) 1 else 0) + (if (sources.isNotEmpty()) 1 else 0) + (if (sort != SortOrder.RECENT) 1 else 0)

    /** Niets gekozen: dan tonen we een uitgelicht recept. */
    val isPlain: Boolean get() = query.isBlank() && category == Category.ALLES && tab == LibraryTab.RECIPES && sheetCount == 0
}

data class LibraryUiState(
    val total: Int,
    val shown: List<RecipeEntity>,
    val featured: RecipeEntity?,
    val filter: LibraryFilter,
    val availableSources: List<String>,
    /** Links die al in de bibliotheek staan (voor "link plakken"). */
    val savedUrls: Set<String> = emptySet(),
) {
    /** Aantal getoonde recepten, inclusief het uitgelichte. */
    val count: Int get() = shown.size + (if (featured != null) 1 else 0)
}

private val dutch: Collator = Collator.getInstance(Locale("nl", "NL")).apply { strength = Collator.PRIMARY }

/** Pure filterlogica (getest in LibraryFilterTest). [ftsIds] = uitkomst van zoeken, of `null` als er niet gezocht wordt. */
fun applyFilter(all: List<RecipeEntity>, ftsIds: Set<String>?, f: LibraryFilter): List<RecipeEntity> {
    val filtered = all.filter { r ->
        (f.tab != LibraryTab.FAVORITES || r.favorite) &&
            f.category.matches(r) &&
            (ftsIds == null || r.id in ftsIds) &&
            (f.maxMinutes == null || (r.totalMinutes != null && r.totalMinutes <= f.maxMinutes)) &&
            (f.sources.isEmpty() || r.sourceSite in f.sources)
    }
    return when (f.sort) {
        SortOrder.RECENT -> filtered.sortedByDescending { it.createdAt }
        SortOrder.QUICK -> filtered.sortedWith(compareBy(nullsLast()) { it.totalMinutes })
        SortOrder.AZ -> filtered.sortedWith { a, b -> dutch.compare(a.title, b.title) }
    }
}

/** Elke dag een ander recept bovenaan, maar dezelfde zolang de dag duurt. Pas vanaf 4 recepten (anders dubbelt het). */
fun featuredFor(all: List<RecipeEntity>, f: LibraryFilter, epochDay: Long): RecipeEntity? {
    if (!f.isPlain || all.size < 4) return null
    val stable = all.sortedBy { it.createdAt }
    return stable[Math.floorMod(epochDay, stable.size.toLong()).toInt()]
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: RecipeRepository,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter())
    private val library = repository.observeLibrary()

    /** Zoeken via de FTS-index; opnieuw als de query of de bibliotheek verandert. */
    private val ftsIds = combine(filter.map { it.query.trim() }.distinctUntilChanged(), library) { q, _ -> q }
        .mapLatest { q -> if (q.isBlank()) null else repository.search(q).map { it.id }.toSet() }

    val state: StateFlow<LibraryUiState?> = combine(library, filter, ftsIds) { all, f, ids ->
        val featured = featuredFor(all, f, today())
        LibraryUiState(
            total = all.size,
            shown = applyFilter(all, ids, f).filter { it.id != featured?.id },
            featured = featured,
            filter = f,
            availableSources = all.mapNotNull { it.sourceSite }.distinct().sortedWith(dutch),
            savedUrls = all.mapNotNull { it.sourceUrl }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (LibraryFilter) -> LibraryFilter) = filter.update(transform)

    fun toggleFavorite(r: RecipeEntity) {
        viewModelScope.launch { repository.setFavorite(r.id, !r.favorite) }
    }
}
