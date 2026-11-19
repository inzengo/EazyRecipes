package nl.eazyrecipes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.ui.importflow.cleaned
import nl.eazyrecipes.ui.theme.Eazy

sealed interface EditUiState {
    data object Loading : EditUiState
    data class Editing(
        val draft: ParsedRecipe,
        val imagePath: String?,
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : EditUiState {
        val canSave: Boolean get() = draft.title.isNotBlank() && !saving
    }
    data object Saved : EditUiState
    data object Gone : EditUiState
}

class EditRecipeViewModel(private val id: String, private val repository: RecipeRepository) : ViewModel() {
    private val _state = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.get(id)?.let { EditUiState.Editing(it.toDraft(), it.recipe.imagePath) } ?: EditUiState.Gone
        }
    }

    fun edit(transform: (ParsedRecipe) -> ParsedRecipe) = _state.update { s ->
        if (s is EditUiState.Editing && !s.saving) s.copy(draft = transform(s.draft), saveError = null) else s
    }

    fun save() {
        val s = _state.value as? EditUiState.Editing ?: return
        if (!s.canSave) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            runCatching { repository.update(id, s.draft.cleaned()) }
                .onSuccess { _state.value = EditUiState.Saved }
                .onFailure { _state.value = s.copy(saving = false, saveError = "Opslaan is mislukt. Probeer het nog eens.") }
        }
    }
}

@Composable
fun EditRecipeScreen(vm: EditRecipeViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is EditUiState.Editing -> RecipeEditor(
            draft = s.draft,
            imagePath = s.imagePath,
            onEdit = vm::edit,
            onClose = onDone,
            closeLabel = "Niet opslaan",
            saveLabel = "Wijzigingen opslaan",
            saving = s.saving,
            canSave = s.canSave,
            saveError = s.saveError,
            onSave = vm::save,
        )
        EditUiState.Saved, EditUiState.Gone -> LaunchedEffect(s) { onDone() }
        EditUiState.Loading -> Box(Modifier.fillMaxSize().background(Eazy.Paper))
    }
}
