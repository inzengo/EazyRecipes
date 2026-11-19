package nl.eazyrecipes.ui.library

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.eazyrecipes.R
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.components.TimeBadge
import nl.eazyrecipes.ui.formatMinutes
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import nl.eazyrecipes.ui.theme.isQuick

@Composable
fun LibraryScreen(recipes: List<RecipeEntity>?, onOpen: (String) -> Unit, onImport: (String) -> Unit) {
    var addOpen by rememberSaveable { mutableStateOf(false) }
    // Wat er in "link plakken" stond toen het venster (per ongeluk) dicht ging; leeg na een import.
    var draft by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) { Header(count = recipes?.size) }
            if (recipes != null && recipes.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) { EmptyState() }
            }
            itemsIndexed(recipes.orEmpty(), key = { _, r -> r.id }) { i, r ->
                RecipeCard(r, tall = i % 3 == 0, onClick = { onOpen(r.id) })
            }
        }

        // Zwevende +-knop (de volledige navigatiepil komt in fase 3).
        Surface(
            onClick = { addOpen = true },
            shape = CircleShape,
            color = Eazy.Ink,
            contentColor = Eazy.Paper,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_plus, 26.dp, contentDescription = "Recept toevoegen") }
        }

        StatusBarBackdrop(Eazy.Grey)
    }

    if (addOpen) {
        AddLinkSheet(
            draft = draft,
            savedUrls = remember(recipes) { recipes.orEmpty().mapNotNull { it.sourceUrl }.toSet() },
            onDismiss = { text -> draft = text; addOpen = false },
            onSubmit = { url -> draft = ""; addOpen = false; onImport(url) },
        )
    }
}

internal fun linkIn(text: String): String? = Regex("""https?://\S+""").find(text)?.value

/**
 * Wat staat er al in het veld als "link plakken" opent?
 * 1. tekst van de vorige keer, als het venster zonder importeren dicht ging;
 * 2. anders een link van het klembord, tenzij dat recept al in de bibliotheek staat;
 * 3. anders niets.
 */
internal fun initialLinkText(draft: String, clipboard: String?, savedUrls: Set<String>): String {
    if (draft.isNotBlank()) return draft
    val link = clipboard?.let(::linkIn) ?: return ""
    val clean = RecipeImporter.cleanUrl(link) ?: return ""
    return if (clean in savedUrls) "" else link
}

@Composable
private fun Header(count: Int?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(R.drawable.ph_fork_knife, 16.dp)
            Text("EazyRecipes", style = MaterialTheme.typography.titleMedium)
        }
        Text("Wat koken we\nvanavond?", style = MaterialTheme.typography.displaySmall)
        if (count != null && count > 0) {
            Text(if (count == 1) "1 recept" else "$count recepten", style = MaterialTheme.typography.labelLarge, color = Eazy.Muted)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Eazy.Paper).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Eazy.Brand), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ph_books, 26.dp, tint = Eazy.Ink)
        }
        Text("Je bibliotheek is nog leeg", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Zie je een lekker recept in je browser? Tik op Delen → EazyRecipes. Of tik hieronder op + en plak een link.",
            style = MaterialTheme.typography.bodyMedium,
            color = Eazy.Muted,
        )
    }
}

@Composable
private fun RecipeCard(r: RecipeEntity, tall: Boolean, onClick: () -> Unit) {
    val ctx = r.mainIngredient.context
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                RecipePhoto(
                    r.imagePath, r.imageUrl, ctx,
                    Modifier.fillMaxWidth().aspectRatio(if (tall) 0.78f else 1f).clip(MaterialTheme.shapes.large),
                )
                // Categorie-sticker: kleur + icoon (kleur is nooit het enige signaal).
                Box(
                    Modifier.padding(10.dp).size(36.dp).clip(CircleShape).background(ctx.vivid),
                    contentAlignment = Alignment.Center,
                ) { Ico(ctx.icon, 19.dp, tint = Eazy.Ink, contentDescription = ctx.label) }
                if (r.favorite) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Eazy.Brand),
                        contentAlignment = Alignment.Center,
                    ) { Ico(R.drawable.ph_heart, 18.dp, tint = Eazy.Ink, contentDescription = "Favoriet") }
                }
                formatMinutes(r.totalMinutes)?.let {
                    TimeBadge(it, isQuick(r.totalMinutes), Modifier.align(Alignment.BottomStart).padding(10.dp))
                }
            }
            Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkSheet(draft: String, savedUrls: Set<String>, onDismiss: (String) -> Unit, onSubmit: (String) -> Unit) {
    val context = LocalContext.current
    // Wat we automatisch invulden; dat bewaren we niet als "jouw tekst" bij per ongeluk sluiten.
    var prefilled by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(draft) }
    val valid = RecipeImporter.cleanUrl(text) != null
    val focus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = { onDismiss(if (text == prefilled) "" else text) }, containerColor = Eazy.Paper) {
        // Android geeft het klembord pas vrij als ons venster focus heeft: lees het (opnieuw) zodra dat zo is.
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused) {
            if (windowFocused && text.isEmpty()) {
                initialLinkText("", readClipboard(context), savedUrls).takeIf { it.isNotEmpty() }?.let { text = it; prefilled = it }
            }
            if (windowFocused) focus.requestFocus()
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Recept toevoegen", style = MaterialTheme.typography.headlineSmall)
            Text("Plak de link van een receptpagina.", style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://…") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = { Ico(R.drawable.ic_link, 20.dp) },
                trailingIcon = {
                    // Vangnet als automatisch invullen niet lukt: één tik, en dan mag Android het klembord altijd lezen.
                    if (text.isEmpty()) TextButton(onClick = {
                        readClipboard(context)?.let { clip -> text = linkIn(clip) ?: clip.trim() }
                    }) { Text("Plakken", style = MaterialTheme.typography.labelLarge, color = Eazy.Ink) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onSubmit(text.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            BigPillButton("Recept ophalen", R.drawable.ph_sparkle, Eazy.Brand, onClick = { onSubmit(text.trim()) }, enabled = valid)
        }
    }
}

/** Klembord als tekst; `coerceToText` vangt ook links die als URI (niet als tekst) gekopieerd zijn. */
private fun readClipboard(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
}.getOrNull()
