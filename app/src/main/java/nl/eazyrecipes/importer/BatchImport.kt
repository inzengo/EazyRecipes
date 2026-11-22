package nl.eazyrecipes.importer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.ui.importflow.ImportViewModel
import nl.eazyrecipes.ui.importflow.cleaned

data class BatchItem(
    val url: String,
    val status: Status = Status.WAITING,
    val recipeId: String? = null,
    val title: String? = null,
    val message: String? = null,
) {
    enum class Status { WAITING, BUSY, SAVED, EXISTS, FAILED }
    val done: Boolean get() = status != Status.WAITING && status != Status.BUSY
}

/**
 * Wachtrij voor meerdere links (geplakte lijst of bladwijzers): één voor één ophalen en meteen opslaan, zonder
 * voorbeeldscherm (aanpassen kan daarna). Loopt in [scope] (de app), dus door als je het scherm verlaat.
 * Eén tegelijk: sommige sites (Cloudflare) gaan via een onzichtbare browser, en die is er maar één.
 */
class BatchImport(
    private val importer: RecipeImporter,
    private val repository: RecipeRepository,
    private val images: ImageStore,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<BatchItem>>(emptyList())
    val items: StateFlow<List<BatchItem>> = _items

    /**
     * Elke toevoeging start een werker; de mutex zorgt dat er één tegelijk werkt. Een werker die net "niets meer"
     * zag, kan zo nooit een zojuist toegevoegde link laten liggen: de volgende werker pakt hem op.
     */
    private val running = Mutex()
    private fun kick() { scope.launch { running.withLock { run() } } }

    /**
     * Voegt links toe (opgeschoond, zonder dubbele) en start het ophalen. Een link die al klaar in de lijst staat
     * (bv. mislukt, of het recept is intussen verwijderd) gaat opnieuw in de rij. @return hoeveel er in de rij kwamen.
     */
    fun enqueue(urls: List<String>): Int {
        val clean = urls.mapNotNull(RecipeImporter::cleanUrl).distinct()
        var added = 0
        _items.update { list ->
            val pending = list.filter { !it.done }.map { it.url }.toSet()
            val fresh = clean.filter { it !in pending }
            added = fresh.size
            list.filterNot { it.url in fresh } + fresh.map { BatchItem(it) }
        }
        kick()
        return added
    }

    /** Klaar (opgeslagen, al aanwezig, mislukt) uit de lijst halen. */
    fun clearFinished() = _items.update { list -> list.filterNot { it.done } }

    /** Mislukte opnieuw proberen. */
    fun retryFailed() {
        _items.update { list -> list.map { if (it.status == BatchItem.Status.FAILED) BatchItem(it.url) else it } }
        kick()
    }

    private suspend fun run() {
        while (true) {
            val next = _items.value.firstOrNull { it.status == BatchItem.Status.WAITING } ?: return
            set(next.url) { it.copy(status = BatchItem.Status.BUSY) }
            val result = runCatching { process(next.url) }.getOrElse {
                next.copy(status = BatchItem.Status.FAILED, message = "Er ging iets mis; probeer het opnieuw.")
            }
            set(next.url) { result }
        }
    }

    private suspend fun process(url: String): BatchItem {
        repository.findBySourceUrl(url)?.let { return BatchItem(url, BatchItem.Status.EXISTS, it.id, it.title) }
        return when (val r = importer.import(url) {}) {
            is ImportResult.Success -> {
                val recipe = r.recipe.cleaned()
                // Kan pas na het ophalen blijken (doorverwijzing naar een link die we al hebben).
                recipe.sourceUrl?.let { repository.findBySourceUrl(it) }?.let { return BatchItem(url, BatchItem.Status.EXISTS, it.id, it.title) }
                val image = recipe.imageUrl?.let { images.download(it) }
                BatchItem(url, BatchItem.Status.SAVED, repository.save(recipe, image?.path), recipe.title)
            }
            else -> BatchItem(url, BatchItem.Status.FAILED, message = ImportViewModel.failureFor(r).title)
        }
    }

    private fun set(url: String, transform: (BatchItem) -> BatchItem) =
        _items.update { list -> list.map { if (it.url == url) transform(it) else it } }
}
