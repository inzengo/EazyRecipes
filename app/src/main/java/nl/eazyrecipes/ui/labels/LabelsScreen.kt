package nl.eazyrecipes.ui.labels

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.eazyrecipes.R
import nl.eazyrecipes.data.LabelSummary
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.LabelChip
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.theme.CategoryPalette
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.paletteColor

class LabelsViewModel(private val repository: RecipeRepository) : ViewModel() {
    val labels: StateFlow<List<LabelSummary>?> =
        repository.observeLabels().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: String, name: String, color: String?, hidden: Boolean) = viewModelScope.launch {
        repository.updateLabel(id, name, color, hidden)
    }

    fun merge(from: String, into: String) = viewModelScope.launch { repository.mergeLabels(from, into) }
    fun delete(id: String) = viewModelScope.launch { repository.deleteLabel(id) }
}

class LabelActions(
    val save: (id: String, name: String, color: String?, hidden: Boolean) -> Unit = { _, _, _, _ -> },
    val merge: (from: String, into: String) -> Unit = { _, _ -> },
    val delete: (String) -> Unit = {},
    val back: () -> Unit = {},
)

@Composable
fun LabelsScreen(vm: LabelsViewModel, onBack: () -> Unit) {
    val labels by vm.labels.collectAsStateWithLifecycle()
    LabelsContent(labels, LabelActions({ id, n, c, h -> vm.save(id, n, c, h) }, { f, i -> vm.merge(f, i) }, { vm.delete(it) }, onBack))
}

@Composable
fun LabelsContent(labels: List<LabelSummary>?, actions: LabelActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundButton(R.drawable.ic_back, "Terug", actions.back, background = Eazy.Paper)
            Text("Labels", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Komen mee van websites of maak je zelf op een recept. Geef ze een kleur, voeg dubbele samen, of verberg wat je niet nodig hebt.",
                style = MaterialTheme.typography.bodyMedium,
                color = Eazy.Muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (labels != null && labels.size > 8) SearchBox(query) { query = it }
            when {
                labels == null -> Unit
                labels.isEmpty() -> Text(
                    "Nog geen labels. Ze komen vanzelf mee bij het importeren, of voeg ze toe op een recept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Eazy.Muted,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Eazy.Paper).padding(24.dp),
                )
                else -> labels.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }.forEach { l ->
                    Surface(onClick = { editing = l.id }, shape = RoundedCornerShape(22.dp), color = Eazy.Paper) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LabelChip(l.name, l.tag.color)
                            Box(Modifier.weight(1f))
                            if (l.tag.hidden) Ico(R.drawable.ph_eye_slash, 18.dp, tint = Eazy.Muted, contentDescription = "Verborgen")
                            Text(if (l.recipes == 1) "1 recept" else "${l.recipes} recepten", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
                            Ico(R.drawable.ic_chevron_right, 16.dp, tint = Eazy.Muted)
                        }
                    }
                }
            }
        }
        StatusBarBackdrop(Eazy.Grey)
    }

    val current = editing?.let { id -> labels?.firstOrNull { it.id == id } }
    // Label verdwenen (samengevoegd/verwijderd): venster dicht, en niet vanzelf weer open als het terugkomt.
    LaunchedEffect(current, labels) { if (editing != null && labels != null && current == null) editing = null }
    if (current != null) {
        EditLabelSheet(
            label = current,
            others = labels.orEmpty().filter { it.id != current.id },
            actions = actions,
            onDone = { editing = null },
        )
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(Eazy.Paper).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico(R.drawable.ph_search, 18.dp, tint = Eazy.Muted)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Eazy.Ink),
            cursorBrush = SolidColor(Eazy.Ink),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Zoek een label…", style = MaterialTheme.typography.bodyLarge, color = Eazy.Subtle)
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditLabelSheet(label: LabelSummary, others: List<LabelSummary>, actions: LabelActions, onDone: () -> Unit) {
    var name by rememberSaveable(label.id) { mutableStateOf(label.name) }
    var color by rememberSaveable(label.id) { mutableStateOf(label.tag.color) }
    var hidden by rememberSaveable(label.id) { mutableStateOf(label.tag.hidden) }
    var merging by rememberSaveable(label.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(label.id) { mutableStateOf(false) }
    val mergeTarget = others.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDone, containerColor = Eazy.Paper, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabelChip(name.ifBlank { label.name }, color)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                singleLine = true,
                label = { Text("Naam") },
                supportingText = mergeTarget?.let { { Text("Bestaat al${if (it.tag.hidden) " (verborgen)" else ""}: opslaan voegt ze samen.") } },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink, focusedLabelColor = Eazy.Ink),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Kleur", style = MaterialTheme.typography.labelMedium, color = Eazy.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatch(Eazy.Grey, color == null, "Geen kleur") { color = null }
                CategoryPalette.forEach { pc -> Swatch(pc.vivid, pc.key == color, pc.name) { color = pc.key } }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Verbergen", style = MaterialTheme.typography.titleMedium)
                    Text("Niet op recepten en in het filter; zoeken vindt het nog wel.", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
                }
                Switch(
                    checked = hidden,
                    onCheckedChange = { hidden = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Eazy.Ink, checkedThumbColor = Eazy.Paper),
                )
            }

            BigPillButton(
                text = if (mergeTarget != null) "Samenvoegen" else "Opslaan",
                icon = R.drawable.ic_check,
                accent = color?.let { paletteColor(it).vivid } ?: Eazy.Brand,
                onClick = { actions.save(label.id, name, color, hidden); onDone() },
                enabled = name.isNotBlank(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (others.isNotEmpty()) TextButton(onClick = { merging = true }) { Text("Samenvoegen met…", color = Eazy.Ink) }
                TextButton(onClick = { confirmDelete = true }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("\"${label.name}\" samenvoegen met") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    others.forEach { o ->
                        Surface(onClick = { merging = false; actions.merge(label.id, o.id); onDone() }, shape = RoundedCornerShape(16.dp), color = Eazy.Grey) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                LabelChip(o.name, o.tag.color)
                                Box(Modifier.weight(1f))
                                if (o.tag.hidden) Ico(R.drawable.ph_eye_slash, 16.dp, tint = Eazy.Muted, contentDescription = "Verborgen")
                                Text("${o.recipes}", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text("Annuleren", color = Eazy.Ink) } },
            containerColor = Eazy.Paper,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("\"${label.name}\" verwijderen?") },
            text = { Text("Het label verdwijnt van ${if (label.recipes == 1) "1 recept" else "${label.recipes} recepten"}. De recepten zelf blijven bewaard.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete(label.id); onDone() }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren", color = Eazy.Ink) } },
            containerColor = Eazy.Paper,
        )
    }
}

@Composable
private fun Swatch(color: Color, on: Boolean, name: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = color, modifier = Modifier.size(44.dp).border(3.dp, if (on) Eazy.Ink else Color.Transparent, CircleShape)) {
        Box(contentAlignment = Alignment.Center) { if (on) Ico(R.drawable.ic_check, 18.dp, tint = Eazy.Ink, contentDescription = "$name, gekozen") }
    }
}
