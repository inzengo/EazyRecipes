package nl.eazyrecipes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import nl.eazyrecipes.R
import nl.eazyrecipes.data.CollectionSummary
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.dashedBorder
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import nl.eazyrecipes.ui.theme.LocalCategories

/** Wat de bibliotheek met collecties kan doen. */
data class CollectionActions(
    val open: (String) -> Unit = {},
    val create: (String) -> Unit = {},
    val rename: (String, String) -> Unit = { _, _ -> },
    val delete: (String) -> Unit = {},
    val share: (String) -> Unit = {},
)

/** Inhoud van de tab "Collecties": kaarten in twee kolommen plus "Nieuwe collectie". */
@Composable
fun CollectionsGrid(collections: List<CollectionSummary>, actions: CollectionActions) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }

    // `null` = de kaart "Nieuwe collectie" achteraan.
    val cells: List<CollectionSummary?> = collections + null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c ->
                    if (c == null) NewCollectionCard({ creating = true }, Modifier.weight(1f))
                    else CollectionCard(c, { actions.open(c.id) }, { menuFor = c.id }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (creating) {
        NameDialog("Nieuwe collectie", "", "Maken", onConfirm = { actions.create(it); creating = false }, onDismiss = { creating = false })
    }
    menuFor?.let { id ->
        val c = collections.firstOrNull { it.id == id }
        if (c == null) menuFor = null else CollectionMenu(
            c,
            onRename = { menuFor = null; renaming = id },
            onShare = { menuFor = null; actions.share(id) },
            onDelete = { menuFor = null; actions.delete(id) },
            onDismiss = { menuFor = null },
        )
    }
    renaming?.let { id ->
        val c = collections.firstOrNull { it.id == id }
        if (c == null) renaming = null else NameDialog(
            "Collectie hernoemen", c.name, "Opslaan",
            onConfirm = { actions.rename(id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun CollectionCard(c: CollectionSummary, onOpen: () -> Unit, onMenu: () -> Unit, modifier: Modifier) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(26.dp), color = Eazy.Paper, shadowElevation = 1.dp, modifier = modifier) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box {
                Mosaic(c.recipes.take(3))
                Surface(
                    onClick = onMenu, shape = CircleShape, color = Eazy.Paper.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp),
                ) { Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ph_dots, 16.dp, tint = Eazy.Ink, contentDescription = "Opties voor ${c.name}") } }
            }
            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.padding(horizontal = 6.dp))
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (c.recipes.size == 1) "1 recept" else "${c.recipes.size} recepten", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
                // Stipjes: welke soorten erin zitten.
                val cats = LocalCategories.current
                c.recipes.mapNotNull { r -> cats[r.categoryId] }.distinct().take(5).forEach { cat ->
                    Box(Modifier.size(10.dp).clip(CircleShape).background(cat.context.vivid))
                }
            }
        }
    }
}

/** Eén grote foto links, twee kleine rechts (zoals de mockup). Lege plekken grijs. */
@Composable
private fun Mosaic(recipes: List<RecipeEntity>) {
    @Composable
    fun Tile(r: RecipeEntity?, m: Modifier) {
        if (r == null) Box(m.background(Eazy.Placeholder))
        else RecipePhoto(r.imagePath, r.imageUrl, r.context, m, iconSize = 28.dp)
    }
    Row(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(20.dp)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tile(recipes.getOrNull(0), Modifier.weight(1f).fillMaxSize())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tile(recipes.getOrNull(1), Modifier.weight(1f).fillMaxWidth())
            Tile(recipes.getOrNull(2), Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun NewCollectionCard(onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(26.dp), color = Color.Transparent, modifier = modifier.heightIn(min = 196.dp).dashedBorder(Color(0xFFC9C9C4), 26.dp)) {
        Column(Modifier.heightIn(min = 196.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Eazy.Ink), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_plus, 22.dp, tint = Eazy.Paper)
            }
            Text("Nieuwe collectie", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionMenu(c: CollectionSummary, onRename: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            MenuRow(R.drawable.ph_pencil, Eazy.Brand, "Hernoemen", null, onRename)
            if (c.recipes.isNotEmpty()) MenuRow(R.drawable.ph_share, Color(0xFF47E3BC), "Delen als bestand", "Met alle ${c.recipes.size} recepten; de ontvanger opent het in EazyRecipes", onShare)
            MenuRow(R.drawable.ph_folder, Color(0xFFFF5A4A), "Collectie verwijderen", "De recepten zelf blijven in je bibliotheek") { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("\"${c.name}\" verwijderen?") },
            text = { Text("Alleen de collectie verdwijnt; de recepten blijven in je bibliotheek.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Verwijderen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren", color = Eazy.Ink) } },
            containerColor = Eazy.Paper,
        )
    }
}

@Composable
fun MenuRow(icon: Int, color: Color, label: String, note: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Eazy.Grey) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(color), contentAlignment = Alignment.Center) { Ico(icon, 22.dp, tint = Eazy.Ink) }
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted) }
            }
        }
    }
}

/** Dialoog om een (nieuwe) naam in te voeren. */
@Composable
fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Bijv. Doordeweeks") },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel, color = if (name.isNotBlank()) Eazy.Ink else Eazy.Subtle) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren", color = Eazy.Ink) } },
        containerColor = Eazy.Paper,
    )
}
