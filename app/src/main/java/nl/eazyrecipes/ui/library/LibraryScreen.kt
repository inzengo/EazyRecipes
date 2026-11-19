package nl.eazyrecipes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.eazyrecipes.R
import nl.eazyrecipes.data.RecipeEntity
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.components.TimeBadge
import nl.eazyrecipes.ui.formatMinutes
import nl.eazyrecipes.ui.theme.ContextColor
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import nl.eazyrecipes.ui.theme.isQuick

val Category.color: ContextColor
    get() = when (this) {
        Category.ALLES -> Eazy.Alles
        Category.SNEL -> Eazy.Snel
        else -> main.context
    }

private val Category.heroTitle: String
    get() = when (this) {
        Category.ALLES -> "Wat koken we\nvanavond?"
        Category.ZOET -> "Zoet &\nbakken"
        Category.SNEL -> "Snel\nklaar"
        else -> color.label
    }

/** Kleur van de kop (en de balk achter de klok): wit bij "Alles", anders de contextkleur. */
val LibraryFilter.heroColor: Color get() = if (category == Category.ALLES) Eazy.Paper else category.color.vivid

private fun recipes(n: Int) = if (n == 1) "1 recept" else "$n recepten"

/** Laat een full-line item over de 20 dp marge van het raster heen lopen, tot aan de schermrand. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val px = horizontal.roundToPx()
    val wide = constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px)
    val placeable = measurable.measure(wide)
    layout(constraints.maxWidth, placeable.height) { placeable.place(-px, 0) }
}

/** Hoogte van een kaart: de mockup wisselt per kolom 216/168 af, zodat het raster speels oogt. */
private fun cardHeight(index: Int): Dp {
    val column = index % 2
    val row = index / 2
    val tall = if (column == 0) row % 2 == 0 else row % 2 == 1
    return if (tall) 216.dp else 168.dp
}

@Composable
fun LibraryScreen(
    state: LibraryUiState?,
    onFilter: ((LibraryFilter) -> LibraryFilter) -> Unit,
    onToggleFavorite: (RecipeEntity) -> Unit,
    onOpen: (String) -> Unit,
    collectionActions: CollectionActions = CollectionActions(),
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val f = state?.filter ?: LibraryFilter()

    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "hero") {
                Hero(state, f, onQuery = { q -> onFilter { it.copy(query = q) } }, onOpenFilters = { filtersOpen = true })
            }
            item(span = StaggeredGridItemSpan.FullLine, key = "tiles") {
                CategoryTiles(f.category) { c ->
                    onFilter { it.copy(category = c, tab = if (it.tab == LibraryTab.COLLECTIONS) LibraryTab.RECIPES else it.tab) }
                }
            }
            item(span = StaggeredGridItemSpan.FullLine, key = "tabs") { Tabs(f.tab) { t -> onFilter { it.copy(tab = t) } } }
            if (f.tab != LibraryTab.COLLECTIONS) {
                item(span = StaggeredGridItemSpan.FullLine, key = "chips") { ActiveChips(f, state?.openCollection?.name, onFilter) }
            }

            when {
                state == null -> Unit
                f.tab == LibraryTab.COLLECTIONS -> item(span = StaggeredGridItemSpan.FullLine, key = "collections") {
                    CollectionsGrid(state.collections, collectionActions)
                }
                state.total == 0 -> item(span = StaggeredGridItemSpan.FullLine, key = "welcome") { Welcome() }
                state.shown.isEmpty() && state.featured == null -> item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    if (state.openCollection != null && f.query.isBlank() && f.category == Category.ALLES && f.sheetCount == 0) {
                        Empty("Deze collectie is nog leeg", "Open een recept en tik op het mapje om het hier te zetten.")
                    } else if (f.tab == LibraryTab.FAVORITES && f.query.isBlank() && f.category == Category.ALLES && f.sheetCount == 0) {
                        Empty("Nog geen favorieten", "Tik op het hartje bij een recept, dan staat het hier.")
                    } else {
                        Empty("Niets gevonden", "Probeer een ander woord of wis een filter.")
                    }
                }
                else -> {
                    state.featured?.let { r ->
                        item(span = StaggeredGridItemSpan.FullLine, key = "featured") { Featured(r) { onOpen(r.id) } }
                    }
                    itemsIndexed(state.shown, key = { _, r -> r.id }) { i, r ->
                        RecipeCard(r, cardHeight(i), onClick = { onOpen(r.id) }, onFavorite = { onToggleFavorite(r) })
                    }
                }
            }
        }
        StatusBarBackdrop(f.heroColor)
    }

    if (filtersOpen && state != null) {
        FilterSheet(state, onFilter, onDismiss = { filtersOpen = false })
    }
}

@Composable
private fun Hero(state: LibraryUiState?, f: LibraryFilter, onQuery: (String) -> Unit, onOpenFilters: () -> Unit) {
    val c = f.category.color
    val plainAlles = f.category == Category.ALLES
    val collection = state?.openCollection
    val title = when {
        collection != null -> collection.name
        f.tab == LibraryTab.FAVORITES && plainAlles -> "Jouw\nfavorieten"
        else -> f.category.heroTitle
    }
    val count = state?.count ?: 0
    val sub = when {
        state == null -> ""
        f.tab == LibraryTab.COLLECTIONS -> state.collections.size.let { if (it == 1) "1 collectie" else "$it collecties" }
        state.total == 0 -> "Nog geen recepten"
        collection != null -> "Collectie · ${recipes(count)}"
        f.tab == LibraryTab.FAVORITES -> "${recipes(count)} met een hartje"
        f.isPlain -> "${recipes(state.total)} in je bibliotheek"
        else -> "${recipes(count)} gevonden"
    }
    Box(
        Modifier
            .bleed(20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(f.heroColor)
    ) {
        // Watermerk: groot icoon rechtsboven. matchParentSize: het bepaalt de hoogte van de kop niet mee.
        Box(Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 46.dp, y = (-18).dp).wrapContentSize(unbounded = true)) {
                Ico(c.icon, 240.dp, tint = if (plainAlles) Eazy.Brand.copy(alpha = 0.35f) else Eazy.Ink.copy(alpha = 0.1f))
            }
        }
        Column(
            Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(Eazy.Ink), contentAlignment = Alignment.Center) {
                    Ico(R.drawable.ph_fork_knife, 16.dp, tint = if (plainAlles) Eazy.Brand else c.vivid)
                }
                Text("EazyRecipes", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.displaySmall)
                Text(sub, style = MaterialTheme.typography.labelLarge, color = Eazy.Ink.copy(alpha = 0.78f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(f.query, onQuery, Modifier.weight(1f))
                FilterButton(f.sheetCount, onOpenFilters)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    Row(
        // Rand naast de schaduw: op de witte "Alles"-kop zou het veld anders wegvallen.
        modifier.height(52.dp).shadow(6.dp, CircleShape, spotColor = Eazy.Ink.copy(alpha = 0.25f))
            .border(1.5.dp, Eazy.Line, CircleShape).clip(CircleShape).background(Eazy.Paper)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico(R.drawable.ph_search, 20.dp, tint = Eazy.Muted)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Eazy.Ink),
            cursorBrush = SolidColor(Eazy.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Recept of ingrediënt…", style = MaterialTheme.typography.bodyLarge, color = Eazy.Subtle)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Surface(onClick = { onQuery("") }, shape = CircleShape, color = Eazy.Grey, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_close, 14.dp, contentDescription = "Zoekopdracht wissen") }
            }
        }
    }
}

@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    Box {
        Surface(onClick = onClick, shape = CircleShape, color = Eazy.Ink, contentColor = Eazy.Paper, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_filter, 22.dp, contentDescription = "Filters openen") }
        }
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(22.dp)
                    .border(2.dp, Eazy.Paper, CircleShape).clip(CircleShape).background(Eazy.Brand),
                contentAlignment = Alignment.Center,
            ) { Text("$count", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Eazy.Ink) }
        }
    }
}

@Composable
private fun CategoryTiles(selected: Category, onPick: (Category) -> Unit) {
    Row(
        Modifier.bleed(20.dp).horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Category.entries.forEach { cat ->
            val c = cat.color
            val on = cat == selected
            Surface(onClick = { onPick(cat) }, color = Color.Transparent, shape = RoundedCornerShape(24.dp)) {
                Column(
                    Modifier.width(82.dp).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Gekozen: ring (grijs-zwart) en iets omhoog, zoals de mockup.
                    Box(
                        Modifier
                            .offset(y = if (on) (-3).dp else 0.dp)
                            .size(80.dp)
                            .border(3.dp, if (on) Eazy.Ink else Color.Transparent, RoundedCornerShape(30.dp))
                            .padding(6.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.vivid),
                        contentAlignment = Alignment.Center,
                    ) { Ico(c.icon, 32.dp, tint = if (cat == Category.ALLES) Eazy.Paper else Eazy.Ink) }
                    Text(c.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun Tabs(selected: LibraryTab, onPick: (LibraryTab) -> Unit) {
    val tabs = listOf(
        Triple(LibraryTab.RECIPES, "Recepten", R.drawable.ph_fork_knife),
        Triple(LibraryTab.FAVORITES, "Favorieten", R.drawable.ph_heart),
        Triple(LibraryTab.COLLECTIONS, "Collecties", R.drawable.ph_folder),
    )
    Row(
        Modifier.padding(vertical = 2.dp).fillMaxWidth().clip(CircleShape).background(Color(0xFFE3E3DF)).padding(4.dp),
    ) {
        tabs.forEach { (tab, label, icon) ->
            val on = tab == selected
            Surface(
                onClick = { onPick(tab) },
                shape = CircleShape,
                color = if (on) Eazy.Ink else Color.Transparent,
                contentColor = if (on) Eazy.Paper else Eazy.Ink,
                modifier = Modifier.weight(1f).height(42.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Ico(icon, 15.dp)
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveChips(f: LibraryFilter, collectionName: String?, onFilter: ((LibraryFilter) -> LibraryFilter) -> Unit) {
    val chips = buildList {
        collectionName?.let { add(it to { onFilter { f -> f.copy(collectionId = null) } }) }
        f.maxMinutes?.let { m -> add("≤ ${formatMinutes(m)}" to { onFilter { it.copy(maxMinutes = null) } }) }
        f.sources.forEach { s -> add(s to { onFilter { it.copy(sources = it.sources - s) } }) }
        if (f.sort != SortOrder.RECENT) add(f.sort.label to { onFilter { it.copy(sort = SortOrder.RECENT) } })
    }
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (label, clear) ->
            Surface(onClick = clear, shape = CircleShape, color = Eazy.Ink, contentColor = Eazy.Paper) {
                Row(Modifier.height(34.dp).padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Ico(R.drawable.ic_close, 14.dp, contentDescription = "Filter wissen")
                }
            }
        }
    }
}

@Composable
private fun Featured(r: RecipeEntity, onClick: () -> Unit) {
    val ctx = r.mainIngredient.context
    Surface(onClick = onClick, shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth().height(236.dp)) {
        Box {
            RecipePhoto(r.imagePath, r.imageUrl, ctx, Modifier.fillMaxSize(), iconSize = 120.dp)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.38f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.74f))))
            Row(
                Modifier.padding(14.dp).height(30.dp).clip(CircleShape).background(Eazy.Brand).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Ico(R.drawable.ph_sparkle, 14.dp, tint = Eazy.Ink)
                Text("Vanavond misschien?", style = MaterialTheme.typography.labelSmall, color = Eazy.Ink)
            }
            if (r.mainIngredient != null) Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(40.dp).clip(RoundedCornerShape(14.dp)).background(ctx.vivid),
                contentAlignment = Alignment.Center,
            ) { Ico(ctx.icon, 22.dp, tint = Eazy.Ink, contentDescription = ctx.label) }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 27.sp), color = Eazy.Paper, maxLines = 2)
                val meta = listOfNotNull(formatMinutes(r.totalMinutes), r.sourceSite).joinToString(" · ")
                if (meta.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ico(R.drawable.ph_clock, 14.dp, tint = Eazy.Paper)
                    Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Eazy.Paper)
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(r: RecipeEntity, height: Dp, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = r.mainIngredient.context
    Box(modifier) {
        Surface(onClick = onClick, color = Color.Transparent) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))) {
                    RecipePhoto(r.imagePath, r.imageUrl, ctx, Modifier.fillMaxSize())
                    // Categorie-sticker: kleur + icoon (kleur is nooit het enige signaal). Zonder soort: geen sticker.
                    if (r.mainIngredient != null) Box(
                        Modifier.padding(8.dp).size(34.dp).clip(RoundedCornerShape(12.dp)).background(ctx.vivid),
                        contentAlignment = Alignment.Center,
                    ) { Ico(ctx.icon, 19.dp, tint = Eazy.Ink, contentDescription = ctx.label) }
                    formatMinutes(r.totalMinutes)?.let {
                        TimeBadge(it, isQuick(r.totalMinutes), Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                    r.sourceSite?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted, maxLines = 1) }
                }
            }
        }
        // Hartje op de kaart: favoriet = lavendel (rood is vlees).
        Surface(
            onClick = onFavorite,
            shape = CircleShape,
            color = if (r.favorite) Eazy.Brand else Eazy.Paper.copy(alpha = 0.92f),
            contentColor = Eazy.Ink,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Ico(
                    if (r.favorite) R.drawable.ph_heart else R.drawable.ic_heart_outline, 18.dp,
                    contentDescription = if (r.favorite) "Uit favorieten halen" else "Aan favorieten toevoegen",
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Eazy.Paper).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Eazy.Brand), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ph_books, 26.dp, tint = Eazy.Ink)
        }
        Text("Je bibliotheek is nog leeg", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Zie je een lekker recept in je browser? Tik op Delen → EazyRecipes. Of tik op + en plak een link.",
            style = MaterialTheme.typography.bodyMedium,
            color = Eazy.Muted,
        )
    }
}

@Composable
private fun Empty(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Eazy.Paper).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted)
    }
}

// --- filter-sheet ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: LibraryUiState, onFilter: ((LibraryFilter) -> LibraryFilter) -> Unit, onDismiss: () -> Unit) {
    val f = state.filter
    val count = state.count
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp), modifier = Modifier.weight(1f))
                TextButton(onClick = { onFilter { it.copy(maxMinutes = null, sources = emptySet(), sort = SortOrder.RECENT) } }) {
                    Text("Wis alles", style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline), color = Eazy.Ink)
                }
            }
            Group("Bereidingstijd") {
                listOf(null to "Alles", 20 to "≤ 20 min", 30 to "≤ 30 min", 60 to "≤ 1 uur").forEach { (m, label) ->
                    Pill(label, f.maxMinutes == m) { onFilter { it.copy(maxMinutes = m) } }
                }
            }
            if (state.availableSources.isNotEmpty()) Group("Bron") {
                state.availableSources.forEach { src ->
                    val on = src in f.sources
                    Pill(src, on) { onFilter { it.copy(sources = if (on) it.sources - src else it.sources + src) } }
                }
            }
            Group("Sorteren") {
                SortOrder.entries.forEach { s -> Pill(s.label, f.sort == s) { onFilter { it.copy(sort = s) } } }
            }
            Surface(onClick = onDismiss, shape = CircleShape, color = Eazy.Ink, contentColor = Eazy.Paper, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Toon ${recipes(count)}", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Eazy.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (on) Eazy.Ink else Eazy.Grey, contentColor = if (on) Eazy.Paper else Eazy.Ink) {
        Box(Modifier.height(42.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
