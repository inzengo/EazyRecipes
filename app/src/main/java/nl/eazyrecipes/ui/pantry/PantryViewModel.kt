package nl.eazyrecipes.ui.pantry

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import nl.eazyrecipes.data.RecipeRepository

data class PantryChip(val key: String, val label: String, val selected: Boolean)

data class PantryUiState(
    /** Lijst-items uit je recepten (meest gebruikt eerst), plus gekozen items die er (nu) niet meer in staan. */
    val chips: List<PantryChip>,
    /** Eigen woorden ("iets anders"), in de volgorde waarin je ze toevoegde. */
    val custom: List<String>,
    val matches: List<PantryMatch>,
    val libraryEmpty: Boolean,
) {
    val selectedCount: Int get() = chips.count { it.selected } + custom.size
}

data class PantrySelection(val keys: Set<String> = emptySet(), val custom: List<String> = emptyList())

/** Zet chips en treffers bij elkaar; puur, zodat de screenshot-test hem ook gebruikt. */
fun pantryState(recipes: List<PantryRecipe>, sel: PantrySelection): PantryUiState {
    val used = PantryMatcher.usedItems(recipes)
    val usedKeys = used.map { it.key }.toSet()
    val extra = PantryCatalog.filter { it.key in sel.keys && it.key !in usedKeys }
    return PantryUiState(
        chips = (used + extra).map { PantryChip(it.key, it.label, it.key in sel.keys) },
        custom = sel.custom,
        matches = PantryMatcher.match(recipes, sel.keys, sel.custom),
        libraryEmpty = recipes.isEmpty(),
    )
}

/** Wat je hebt aangetikt blijft bewaard (SharedPreferences), ook na het afsluiten van de app. */
class PantryViewModel(repository: RecipeRepository, private val prefs: SharedPreferences) : ViewModel() {

    private val _selection = MutableStateFlow(
        PantrySelection(
            // Alleen sleutels die (nog) in de lijst staan; anders tellen ze onzichtbaar mee.
            keys = prefs.getStringSet(KEYS, null).orEmpty().filter { k -> PantryCatalog.any { it.key == k } }.toSet(),
            custom = prefs.getString(CUSTOM, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
        )
    )

    /** Wat er nu gekozen is (alleen-lezen, voor tests). */
    internal val selection: StateFlow<PantrySelection> get() = _selection

    /** Regels analyseren is het zware werk: alleen opnieuw als de bibliotheek verandert, niet bij elke tik. */
    private val index = repository.observeRecipesWithIngredients()
        .map(PantryMatcher::index)

    val state: StateFlow<PantryUiState?> = combine(index, _selection, ::pantryState)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(key: String) = change { s -> s.copy(keys = if (key in s.keys) s.keys - key else s.keys + key) }

    /**
     * Voegt een eigen woord toe. Staat het woord in de lijst ("kip", "Kipfilet"), dan wordt dat item gekozen:
     * als eigen woord zou "kip" op het begin van "kippenbouillon" matchen. Dubbele (ongeacht hoofdletters/accenten) tellen één keer.
     */
    fun addCustom(text: String) {
        val word = text.trim().replace(Regex("""\s+"""), " ")
        if (pantryTokens(word).isEmpty()) return
        val item = PantryMatcher.catalogItemFor(word)
        change { s ->
            when {
                item != null -> s.copy(keys = s.keys + item.key)
                s.custom.any { pantryTokens(it) == pantryTokens(word) } -> s
                else -> s.copy(custom = s.custom + word)
            }
        }
    }

    fun removeCustom(word: String) = change { s -> s.copy(custom = s.custom - word) }

    fun clear() = change { PantrySelection() }

    private fun change(transform: (PantrySelection) -> PantrySelection) {
        _selection.update(transform)
        val s = _selection.value
        prefs.edit().putStringSet(KEYS, s.keys).putString(CUSTOM, s.custom.joinToString("\n")).apply()
    }

    private companion object {
        const val KEYS = "selected"
        const val CUSTOM = "custom"
    }
}
