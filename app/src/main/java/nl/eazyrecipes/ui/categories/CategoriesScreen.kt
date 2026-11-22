package nl.eazyrecipes.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.eazyrecipes.R
import nl.eazyrecipes.data.CategoryEntity
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.DashedAddButton
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.theme.CategoryIcons
import nl.eazyrecipes.ui.theme.CategoryPalette
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.categoryIcon
import nl.eazyrecipes.ui.theme.paletteColor

data class CategoryRow(val category: CategoryEntity, val recipes: Int)

class CategoriesViewModel(private val repository: RecipeRepository) : ViewModel() {
    val rows: StateFlow<List<CategoryRow>?> =
        combine(repository.observeCategories(), repository.observeLibrary()) { cats, recipes ->
            val counts = recipes.groupingBy { it.categoryId }.eachCount()
            cats.map { CategoryRow(it, counts[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: String?, name: String, color: String, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (id == null) repository.createCategory(name, color, icon) else repository.updateCategory(id, name, color, icon)
        }
    }

    fun delete(id: String) = viewModelScope.launch { repository.deleteCategory(id) }
    fun move(id: String, delta: Int) = viewModelScope.launch { repository.moveCategory(id, delta) }
}

/** Wat er in het bewerk-venster staat; `id == null` = nieuwe categorie. */
private data class Draft(val id: String?, val name: String, val color: String, val icon: String, val recipes: Int = 0)

private val DraftSaver = listSaver<Draft?, Any?>(
    save = { d -> if (d == null) emptyList() else listOf(d.id, d.name, d.color, d.icon, d.recipes) },
    restore = { l -> if (l.isEmpty()) null else Draft(l[0] as String?, l[1] as String, l[2] as String, l[3] as String, l[4] as Int) },
)

@Composable
fun CategoriesScreen(vm: CategoriesViewModel, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    CategoriesContent(rows, onBack, vm::save, { vm.delete(it) }, { id, d -> vm.move(id, d) })
}

@Composable
fun CategoriesContent(
    rows: List<CategoryRow>?,
    onBack: () -> Unit,
    onSave: (id: String?, name: String, color: String, icon: String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var editing by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<Draft?>(null) }
    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundButton(R.drawable.ic_back, "Terug", onBack, background = Eazy.Paper)
            Text("Categorieën", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Naam, kleur en icoon kies je zelf. De volgorde is die van de tegels in je bibliotheek.",
                style = MaterialTheme.typography.bodyMedium,
                color = Eazy.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            rows?.forEachIndexed { i, row ->
                CategoryRowView(
                    row,
                    canUp = i > 0,
                    canDown = i < rows.lastIndex,
                    onClick = { editing = Draft(row.category.id, row.category.name, row.category.color, row.category.icon, row.recipes) },
                    onMove = { d -> onMove(row.category.id, d) },
                )
            }
            DashedAddButton("Nieuwe categorie", {
                val used = rows.orEmpty().map { it.category.color }.toSet()
                editing = Draft(null, "", (CategoryPalette.firstOrNull { it.key !in used } ?: CategoryPalette.first()).key, "bowl-food")
            })
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(10.dp)).background(Eazy.Snel.vivid), contentAlignment = Alignment.Center) {
                    Ico(R.drawable.ph_lightning, 16.dp, tint = Eazy.Ink)
                }
                Text("\"Snel\" is automatisch: alle recepten van 30 minuten of korter.", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
            }
        }
        StatusBarBackdrop(Eazy.Grey)
    }

    editing?.let { d ->
        EditSheet(
            draft = d,
            takenNames = rows.orEmpty().filter { it.category.id != d.id }.map { it.category.name.trim().lowercase() }.toSet(),
            onSave = { name, color, icon -> onSave(d.id, name, color, icon); editing = null },
            onDelete = d.id?.let { id -> { onDelete(id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryRowView(row: CategoryRow, canUp: Boolean, canDown: Boolean, onClick: () -> Unit, onMove: (Int) -> Unit) {
    val c = row.category
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Eazy.Paper) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(paletteColor(c.color).vivid), contentAlignment = Alignment.Center) {
                Ico(categoryIcon(c.icon), 26.dp, tint = Eazy.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(if (row.recipes == 1) "1 recept" else "${row.recipes} recepten", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
            }
            MoveButton(R.drawable.ic_chevron_down, "Naar voren", canUp, flip = true) { onMove(-1) }
            MoveButton(R.drawable.ic_chevron_down, "Naar achteren", canDown) { onMove(1) }
        }
    }
}

@Composable
private fun MoveButton(icon: Int, label: String, enabled: Boolean, flip: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = Eazy.Grey, contentColor = if (enabled) Eazy.Ink else Eazy.Dash, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = if (flip) Modifier.rotate(180f) else Modifier) {
            Ico(icon, 16.dp, contentDescription = label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(draft: Draft, takenNames: Set<String>, onSave: (String, String, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by rememberSaveable(draft.id) { mutableStateOf(draft.name) }
    var color by rememberSaveable(draft.id) { mutableStateOf(draft.color) }
    var icon by rememberSaveable(draft.id) { mutableStateOf(draft.icon) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val p = paletteColor(color)
    val duplicate = name.trim().lowercase() in takenNames
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Voorbeeld van de tegel zoals in de bibliotheek.
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(p.vivid), contentAlignment = Alignment.Center) {
                    Ico(categoryIcon(icon), 32.dp, tint = Eazy.Ink)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    label = { Text("Naam") },
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text("Die naam bestaat al") }) else null,
                    placeholder = { Text("Bijv. Soep") },
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink, focusedLabelColor = Eazy.Ink),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Kleur", style = MaterialTheme.typography.labelMedium, color = Eazy.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryPalette.forEach { pc ->
                    val on = pc.key == color
                    Surface(
                        onClick = { color = pc.key },
                        shape = CircleShape,
                        color = pc.vivid,
                        modifier = Modifier.size(44.dp).border(3.dp, if (on) Eazy.Ink else Color.Transparent, CircleShape),
                    ) {
                        Box(contentAlignment = Alignment.Center) { if (on) Ico(R.drawable.ic_check, 18.dp, tint = Eazy.Ink, contentDescription = "${pc.name}, gekozen") }
                    }
                }
            }

            Text("Icoon", style = MaterialTheme.typography.labelMedium, color = Eazy.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryIcons.forEach { ci ->
                    val on = ci.key == icon
                    Surface(
                        onClick = { icon = ci.key },
                        shape = RoundedCornerShape(16.dp),
                        color = if (on) p.vivid else Eazy.Grey,
                        contentColor = Eazy.Ink,
                        modifier = Modifier.size(48.dp).border(2.dp, if (on) Eazy.Ink else Color.Transparent, RoundedCornerShape(16.dp)),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Ico(ci.res, 24.dp) }
                    }
                }
            }

            BigPillButton(
                text = if (draft.id == null) "Categorie maken" else "Opslaan",
                icon = R.drawable.ic_check,
                accent = p.vivid,
                onClick = { onSave(name.trim(), color, icon) },
                enabled = name.isNotBlank() && !duplicate,
            )
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Categorie verwijderen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("\"${draft.name}\" verwijderen?") },
            text = {
                Text(
                    when (draft.recipes) {
                        0 -> "Er staan geen recepten in deze categorie."
                        1 -> "1 recept blijft bewaard, maar heeft daarna geen soort meer."
                        else -> "${draft.recipes} recepten blijven bewaard, maar hebben daarna geen soort meer."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren", color = Eazy.Ink) } },
            containerColor = Eazy.Paper,
        )
    }
}
