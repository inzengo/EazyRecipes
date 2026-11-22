package nl.eazyrecipes.ui.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.importer.ImportResult
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.importer.PhotoRecipeParser
import nl.eazyrecipes.importer.PhotoUnreadable
import nl.eazyrecipes.importer.RecipeImporter

sealed interface ImportUiState {
    data class Loading(val viaBrowser: Boolean = false, val photoCount: Int = 0) : ImportUiState
    data class Failed(val title: String, val message: String, val canRetry: Boolean) : ImportUiState
    data class Editing(
        val draft: ParsedRecipe,
        /** Id van een eerder opgeslagen recept met dezelfde link. */
        val duplicateOf: String? = null,
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : ImportUiState {
        val canSave: Boolean get() = draft.title.isNotBlank() && !saving
    }
    data class Saved(val id: String) : ImportUiState
}

class ImportViewModel(
    private val url: String,
    private val importer: RecipeImporter,
    private val repository: RecipeRepository,
    private val images: ImageStore,
    /** Foto's of screenshots om de tekst van te lezen, in plaats van een link. */
    private val photos: List<Uri> = emptyList(),
    private val readPhotos: suspend (List<Uri>) -> String = { "" },
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** Zonder link: zelf invoeren (leeg concept). */
    init {
        when {
            photos.isNotEmpty() -> load()
            url.isBlank() -> _state.value = ImportUiState.Editing(ParsedRecipe(title = ""))
            else -> load()
        }
    }

    fun retry() = load()

    /** Geen leesbaar recept op de pagina: dan zelf invullen, met de link als bron. */
    fun manual() {
        _state.value = ImportUiState.Editing(ParsedRecipe(title = "", sourceUrl = RecipeImporter.cleanUrl(url)))
    }

    private fun load() {
        if (photos.isNotEmpty()) return loadPhotos()
        _state.value = ImportUiState.Loading()
        viewModelScope.launch {
            val result = importer.import(url) { _state.value = ImportUiState.Loading(viaBrowser = true) }
            _state.value = when (result) {
                is ImportResult.Success -> ImportUiState.Editing(
                    draft = result.recipe,
                    duplicateOf = result.recipe.sourceUrl?.let { repository.findBySourceUrl(it)?.id },
                )
                else -> failureFor(result)
            }
        }
    }

    private fun loadPhotos() {
        _state.value = ImportUiState.Loading(photoCount = photos.size)
        viewModelScope.launch {
            _state.value = try {
                PhotoRecipeParser.parse(readPhotos(photos))?.let { ImportUiState.Editing(it) } ?: ImportUiState.Failed(
                    "Geen tekst gevonden",
                    "We konden op deze foto geen tekst lezen. Een scherpe foto, recht van boven en met goed licht, werkt het best. " +
                        "Je kunt het recept ook zelf invoeren.",
                    canRetry = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportUiState.Failed(
                    "Foto niet te lezen",
                    if (e is PhotoUnreadable) "Deze foto kon niet worden geopend. Kies hem nog eens, of een andere foto."
                    else "Het lezen van de tekst is mislukt. Probeer het nog eens.",
                    canRetry = e !is PhotoUnreadable,
                )
            }
        }
    }

    /** Elke bewerking in het voorbeeldscherm gaat hierlangs. */
    fun edit(transform: (ParsedRecipe) -> ParsedRecipe) = _state.update { s ->
        if (s is ImportUiState.Editing && !s.saving) s.copy(draft = transform(s.draft), saveError = null) else s
    }

    fun save() {
        val s = _state.value as? ImportUiState.Editing ?: return
        if (!s.canSave) return
        val recipe = s.draft.cleaned()
        _state.value = s.copy(saving = true, saveError = null)
        viewModelScope.launch {
            runCatching {
                val image = recipe.imageUrl?.let { images.download(it) } // mislukt? Dan alleen de bron-URL.
                repository.save(recipe, image?.path)
            }.onSuccess { id ->
                _state.value = ImportUiState.Saved(id)
            }.onFailure {
                _state.value = s.copy(saving = false, saveError = "Opslaan is mislukt. Probeer het nog eens.")
            }
        }
    }

    companion object {
        fun failureFor(result: ImportResult): ImportUiState.Failed = when (result) {
            is ImportResult.NoRecipe -> ImportUiState.Failed(
                "Geen recept gevonden",
                "Deze pagina laadt wel, maar we vinden er geen recept op. Sommige sites zetten hun recepten niet in een " +
                    "leesbaar formaat. Je kunt het wel zelf invoeren; de link bewaren we als bron.",
                canRetry = false,
            )
            is ImportResult.Failed -> when (result.reason) {
                ImportResult.Reason.INVALID_URL -> ImportUiState.Failed(
                    "Dit is geen link", "Plak of deel een link die begint met http:// of https://.", canRetry = false,
                )
                ImportResult.Reason.NETWORK -> ImportUiState.Failed(
                    "Geen verbinding", "We konden de site niet bereiken. Controleer je internet en probeer het opnieuw.", canRetry = true,
                )
                ImportResult.Reason.BLOCKED -> ImportUiState.Failed(
                    "De site laat ons niet binnen",
                    "Ook via de browser lukte het niet (fout ${result.httpCode}). Soms helpt het om het later nog eens te proberen.",
                    canRetry = true,
                )
                ImportResult.Reason.HTTP -> ImportUiState.Failed(
                    "Pagina niet gevonden",
                    "De site gaf een fout (${result.httpCode}). Klopt de link nog?",
                    canRetry = result.httpCode?.let { it >= 500 } ?: true,
                )
            }
            is ImportResult.Success -> error("Success is geen fout")
        }
    }
}

/** Lege regels weg en witruimte bijgesneden: zo slaan we op wat de gebruiker ziet. */
internal fun ParsedRecipe.cleaned(): ParsedRecipe = copy(
    title = title.trim(),
    description = description?.trim()?.ifEmpty { null },
    ingredients = ingredients.map { it.trim() }.filter { it.isNotEmpty() },
    steps = steps.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() },
)
