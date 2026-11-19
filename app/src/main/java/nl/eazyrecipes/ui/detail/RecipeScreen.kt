package nl.eazyrecipes.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.eazyrecipes.R
import nl.eazyrecipes.data.RecipeDetails
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.importer.ImageStore
import nl.eazyrecipes.ui.components.Chip
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatTile
import nl.eazyrecipes.ui.components.TopRounded
import nl.eazyrecipes.ui.formatMinutes
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import androidx.lifecycle.compose.collectAsStateWithLifecycle

sealed interface DetailState {
    data object Loading : DetailState
    data class Found(val details: RecipeDetails) : DetailState
    data object Gone : DetailState
}

class RecipeViewModel(
    private val id: String,
    private val repository: RecipeRepository,
    private val images: ImageStore,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    init { reload() }

    private fun reload() = viewModelScope.launch {
        _state.value = repository.get(id)?.let { DetailState.Found(it) } ?: DetailState.Gone
    }

    private val current: RecipeDetails? get() = (_state.value as? DetailState.Found)?.details

    fun toggleFavorite() {
        val d = current ?: return
        viewModelScope.launch { repository.setFavorite(id, !d.recipe.favorite); reload() }
    }

    fun delete(onDone: () -> Unit) {
        val d = current ?: return
        viewModelScope.launch {
            repository.delete(id)
            images.delete(d.recipe.imagePath)
            onDone()
        }
    }
}

@Composable
fun RecipeScreen(vm: RecipeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is DetailState.Found -> Detail(s.details, vm, onBack)
        DetailState.Gone -> Column(Modifier.fillMaxSize().background(Eazy.Grey).statusBarsPadding().padding(16.dp)) {
            RoundButton(R.drawable.ic_back, "Terug", onBack, background = Eazy.Paper)
            Text("Dit recept bestaat niet meer.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
        }
        DetailState.Loading -> Box(Modifier.fillMaxSize().background(Eazy.Paper))
    }
}

@Composable
private fun Detail(d: RecipeDetails, vm: RecipeViewModel, onBack: () -> Unit) {
    val r = d.recipe
    val ctx = r.mainIngredient.context
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Eazy.Paper).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            RecipePhoto(r.imagePath, r.imageUrl, ctx, Modifier.fillMaxSize(), iconSize = 160.dp)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundButton(R.drawable.ic_back, "Terug", onBack)
                Box(Modifier.weight(1f))
                RoundButton(
                    R.drawable.ph_heart, if (r.favorite) "Uit favorieten" else "Favoriet", vm::toggleFavorite,
                    background = if (r.favorite) Eazy.Brand else Eazy.Paper.copy(alpha = 0.9f),
                )
            }
        }
        Column(
            Modifier.offset(y = (-36).dp).background(Eazy.Paper, TopRounded).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 40.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(ctx.label, ctx.icon, ctx.vivid)
                r.sourceSite?.let { site ->
                    Chip(site, R.drawable.ic_link, Eazy.Grey, contentColor = Eazy.Muted, onClick = {
                        r.sourceUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    })
                }
            }
            Text(r.title, style = MaterialTheme.typography.headlineMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(R.drawable.ph_clock, "totaal", ctx, Modifier.weight(1f)) {
                    Text(formatMinutes(r.totalMinutes) ?: "–", style = MaterialTheme.typography.titleLarge)
                }
                StatTile(R.drawable.ph_users, "porties", ctx, Modifier.weight(1f)) {
                    Text(r.servings?.toString() ?: "–", style = MaterialTheme.typography.titleLarge)
                }
                StatTile(R.drawable.ph_basket, "ingrediënten", ctx, Modifier.weight(1f)) {
                    Text("${d.ingredients.size}", style = MaterialTheme.typography.titleLarge)
                }
            }

            if (d.ingredients.isNotEmpty()) Column {
                Text("Ingrediënten", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 6.dp))
                d.ingredients.forEach {
                    Text(it.raw, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 14.dp))
                    HorizontalDivider(color = Eazy.Line)
                }
            }

            if (d.steps.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bereiding", style = MaterialTheme.typography.headlineSmall)
                val multipleSections = d.steps.mapNotNull { it.sectionName }.distinct().size > 1
                d.steps.forEachIndexed { i, step ->
                    if (multipleSections && step.sectionName != null && step.sectionName != d.steps.getOrNull(i - 1)?.sectionName) {
                        Text(step.sectionName, style = MaterialTheme.typography.titleMedium, color = Eazy.Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().background(Eazy.Grey, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.headlineSmall, color = ctx.deep, modifier = Modifier.width(24.dp))
                        Text(step.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (d.tags.isNotEmpty()) {
                Text(d.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Eazy.Subtle)
            }

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Recept verwijderen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Recept verwijderen?") },
            text = { Text("\"${r.title}\" verdwijnt uit je bibliotheek.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren", color = Eazy.Ink) } },
            containerColor = Eazy.Paper,
        )
    }
}
