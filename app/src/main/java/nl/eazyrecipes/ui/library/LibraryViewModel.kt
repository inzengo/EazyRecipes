package nl.eazyrecipes.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.eazyrecipes.data.CollectionSummary
import nl.eazyrecipes.data.LabelSummary
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.ui.theme.isQuick
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/**
 * De categorie-tegels: "Alles" (`null`), de categorieën van de gebruiker (hun id) en "Snel" ([QUICK]: ≤ 30 min,
 * los van de soort). [QUICK] botst niet met een categorie: standaardcategorieën hebben vaste namen zonder "snel",
 * eigen categorieën een UUID.
 */
const val QUICK = "snel"

/** [categoriesOf]: per recept al zijn soorten (een recept kan in meerdere); zonder kaart alleen de hoofdsoort. */
fun categoryMatches(category: String?, r: RecipeEntity, categoriesOf: Map<String, Set<String>> = emptyMap()): Boolean = when (category) {
    null -> true
    QUICK -> isQuick(r.totalMinutes)
    else -> r.categoryId == category || category in categoriesOf[r.id].orEmpty()
}

enum class LibraryTab { RECIPES, FAVORITES, COLLECTIONS }

enum class SortOrder(val label: String) { RECENT("Nieuwste"), QUICK("Snelste eerst"), AZ("A–Z") }

data class LibraryFilter(
    val query: String = "",
    /** `null` = alles, [QUICK], of een categorie-id. */
    val category: String? = null,
    val tab: LibraryTab = LibraryTab.RECIPES,
    /** Alleen recepten met een bekende tijd ≤ dit aantal minuten. */
    val maxMinutes: Int? = null,
    val sources: Set<String> = emptySet(),
    val sort: SortOrder = SortOrder.RECENT,
    /** Geopende collectie: alleen haar recepten. */
    val collectionId: String? = null,
    /** Label-ids: alleen recepten met álle gekozen labels. */
    val labels: Set<String> = emptySet(),
) {
    /** Aantal filters uit de filter-sheet (voor het bolletje op de filterknop). */
    val sheetCount: Int get() = (if (maxMinutes != null) 1 else 0) + (if (sources.isNotEmpty()) 1 else 0) +
        (if (sort != SortOrder.RECENT) 1 else 0) + (if (labels.isNotEmpty()) 1 else 0)

    /** Niets gekozen: dan tonen we een uitgelicht recept. */
    val isPlain: Boolean get() = query.isBlank() && category == null && tab == LibraryTab.RECIPES && sheetCount == 0 && collectionId == null
}

data class LibraryUiState(
    val total: Int,
    val shown: List<RecipeEntity>,
    val featured: RecipeEntity?,
    val filter: LibraryFilter,
    val availableSources: List<String>,
    /** Links die al in de bibliotheek staan (voor "link plakken"). */
    val savedUrls: Set<String> = emptySet(),
    val collections: List<CollectionSummary> = emptyList(),
    /** Recepten zonder bekende tijd: die vallen weg bij een tijdfilter (dat melden we in de sheet). */
    val unknownTimeCount: Int = 0,
    /** Zichtbare labels met recepten, meest gebruikt eerst (voor het filter). */
    val labels: List<LabelSummary> = emptyList(),
    /** Per recept al zijn categorieën (voor de tweede sticker op een kaart). */
    val categoriesOf: Map<String, Set<String>> = emptyMap(),
) {
    /** De collectie die nu open staat (of `null`). */
    val openCollection: CollectionSummary? get() = filter.collectionId?.let { id -> collections.firstOrNull { it.id == id } }

    /** Aantal getoonde recepten, inclusief het uitgelichte. */
    val count: Int get() = shown.size + (if (featured != null) 1 else 0)
}

private val dutch: Collator = Collator.getInstance(Locale("nl", "NL")).apply { strength = Collator.PRIMARY }

/** Pure filterlogica (getest in LibraryFilterTest). [ftsIds] = uitkomst van zoeken, of `null` als er niet gezocht wordt. */
fun applyFilter(
    all: List<RecipeEntity>,
    ftsIds: Set<String>?,
    f: LibraryFilter,
    inCollection: Set<String>? = null,
    /** Per recept zijn label-ids. */
    labelsOf: Map<String, Set<String>> = emptyMap(),
    /** Per recept zijn categorie-ids. */
    categoriesOf: Map<String, Set<String>> = emptyMap(),
): List<RecipeEntity> {
    val filtered = all.filter { r ->
        (inCollection == null || r.id in inCollection) &&
        (f.tab != LibraryTab.FAVORITES || r.favorite) &&
            categoryMatches(f.category, r, categoriesOf) &&
            (ftsIds == null || r.id in ftsIds) &&
            (f.maxMinutes == null || (r.totalMinutes != null && r.totalMinutes <= f.maxMinutes)) &&
            (f.sources.isEmpty() || r.sourceSite in f.sources) &&
            (f.labels.isEmpty() || labelsOf[r.id].orEmpty().containsAll(f.labels))
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val repository: RecipeRepository,
    private val today: () -> Long = { LocalDate.now().toEpochDay() },
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter())
    private val library = repository.observeLibrary()

    /**
     * De tekst in het zoekveld, als Compose-state: synchroon, zonder een frame vertraging. Het veld leest hieruit en
     * níet uit [state]; die komt pas terug na de zoekopdracht, en dan bouwt een volgende letter voort op een oude waarde
     * (letters verdwijnen, de cursor springt).
     */
    var query by mutableStateOf("")
        private set

    /** Zoeken via de FTS-index; opnieuw als de query (na een korte pauze in het typen) of de bibliotheek verandert. */
    private val ftsIds = combine(
        filter.map { it.query.trim() }.distinctUntilChanged().debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        library,
    ) { q, _ -> q }
        .mapLatest { q -> if (q.isBlank()) null else repository.search(q).map { it.id }.toSet() }

    private val collections = repository.observeCollections()

    /** Categorieën en labels samen (combine kan hooguit vijf bronnen). */
    private data class Meta(
        val categoryIds: Set<String>,
        val labels: List<LabelSummary>,
        val labelsOf: Map<String, Set<String>>,
        val categoriesOf: Map<String, Set<String>>,
    )

    private val meta = combine(
        repository.observeCategories(), repository.observeLabels(), repository.observeRecipeLabels(), repository.observeRecipeCategories(),
    ) { cats, labels, of, catsOf ->
        Meta(cats.map { it.id }.toSet(), labels.filter { !it.tag.hidden && it.recipes > 0 }, of, catsOf)
    }

    val state: StateFlow<LibraryUiState?> = combine(library, filter, ftsIds, collections, meta) { all, raw, ids, cols, m ->
        // Een net verwijderde categorie of label als filter: dan dat filter negeren.
        val f = raw.copy(
            category = raw.category?.takeIf { it == QUICK || it in m.categoryIds },
            labels = raw.labels.filter { id -> m.labels.any { it.id == id } }.toSet(),
        )
        val featured = featuredFor(all, f, today())
        // Een collectie die net verwijderd is, toont niets; de UI wist dan het filter.
        val inCollection = f.collectionId?.let { id -> cols.firstOrNull { it.id == id }?.recipes?.map { it.id }?.toSet() ?: emptySet() }
        LibraryUiState(
            total = all.size,
            shown = applyFilter(all, ids, f, inCollection, m.labelsOf, m.categoriesOf).filter { it.id != featured?.id },
            categoriesOf = m.categoriesOf,
            featured = featured,
            filter = f,
            availableSources = all.mapNotNull { it.sourceSite }.distinct().sortedWith(dutch),
            savedUrls = all.mapNotNull { it.sourceUrl }.toSet(),
            collections = cols,
            unknownTimeCount = all.count { it.totalMinutes == null },
            labels = m.labels,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (LibraryFilter) -> LibraryFilter) {
        filter.update(transform)
        query = filter.value.query
    }

    fun onQueryChange(q: String) {
        query = q
        filter.update { it.copy(query = q) }
    }

    fun openCollection(id: String) = filter.update { it.copy(collectionId = id, tab = LibraryTab.RECIPES) }

    fun createCollection(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCollection(name) }
    }

    fun renameCollection(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameCollection(id, name) }
    }

    fun deleteCollection(id: String) {
        filter.update { if (it.collectionId == id) it.copy(collectionId = null) else it }
        viewModelScope.launch { repository.deleteCollection(id) }
    }

    fun toggleFavorite(r: RecipeEntity) {
        viewModelScope.launch { repository.setFavorite(r.id, !r.favorite) }
    }
}

private const val SEARCH_DEBOUNCE_MS = 150L
