package nl.eazyrecipes.ui.library

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.eazyrecipes.R
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.TimeBadge
import nl.eazyrecipes.ui.formatMinutes
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import nl.eazyrecipes.ui.theme.isQuick

@Composable
fun LibraryScreen(recipes: List<RecipeEntity>?, onOpen: (String) -> Unit, onImport: (String) -> Unit) {
    var addOpen by rememberSaveable { mutableStateOf(false) }

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
    }

    if (addOpen) AddLinkSheet(onDismiss = { addOpen = false }, onSubmit = { addOpen = false; onImport(it) })
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
private fun AddLinkSheet(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    // Staat er een link op het klembord? Dan alvast invullen.
    var text by remember {
        mutableStateOf(clipboard.getText()?.text?.let { Regex("""https?://\S+""").find(it)?.value }.orEmpty())
    }
    val valid = RecipeImporter.cleanUrl(text) != null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onSubmit(text.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Eazy.Ink, cursorColor = Eazy.Ink),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            BigPillButton("Recept ophalen", R.drawable.ph_sparkle, Eazy.Brand, onClick = { onSubmit(text.trim()) }, enabled = valid)
        }
    }
}
