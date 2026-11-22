package nl.eazyrecipes.ui.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.eazyrecipes.R
import nl.eazyrecipes.importer.BatchItem
import nl.eazyrecipes.importer.Bookmark
import nl.eazyrecipes.importer.Bookmarks
import nl.eazyrecipes.importer.RecipeImporter
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.ErrorCard
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.siteLabel
import nl.eazyrecipes.ui.theme.Eazy
import java.io.InputStream

// ---------------------------------------------------------------- wachtrij

/** Voortgang van meerdere links. Tik op een opgeslagen recept om het te openen. */
@Composable
fun BatchScreen(
    items: List<BatchItem>,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    val finished = items.count { it.done }
    val failed = items.count { it.status == BatchItem.Status.FAILED }
    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { RoundButton(R.drawable.ic_close, "Sluiten", onDone, background = Eazy.Paper) }
            item {
                Column(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recepten\nophalen", style = MaterialTheme.typography.displaySmall)
                    Text(
                        if (finished == items.size) "Klaar: ${items.count { it.status == BatchItem.Status.SAVED }} toegevoegd" +
                            (if (failed > 0) ", $failed mislukt" else "")
                        else "$finished van ${items.size} klaar. Je kunt de app intussen gewoon gebruiken.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Eazy.Muted,
                    )
                }
            }
            items(items, key = { it.url }) { item -> BatchRow(item, onOpen) }
            if (failed > 0) item { TextButton(onClick = onRetry) { Text("Mislukte opnieuw proberen", color = Eazy.Ink) } }
        }
        StatusBarBackdrop(Eazy.Grey)
        BigPillButton(
            if (finished == items.size) "Klaar" else "Verder op de achtergrond",
            R.drawable.ic_check, Eazy.Brand, onDone,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
}

@Composable
private fun BatchRow(item: BatchItem, onOpen: (String) -> Unit) {
    val openable = item.recipeId != null
    Surface(
        onClick = { item.recipeId?.let(onOpen) },
        enabled = openable,
        shape = RoundedCornerShape(20.dp),
        color = Eazy.Paper,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                when (item.status) {
                    BatchItem.Status.BUSY -> CircularProgressIndicator(Modifier.size(22.dp), color = Eazy.Ink, strokeWidth = 2.5.dp)
                    BatchItem.Status.WAITING -> Box(Modifier.size(28.dp).clip(CircleShape).border(2.dp, Eazy.Dash, CircleShape))
                    BatchItem.Status.SAVED -> Box(Modifier.size(28.dp).clip(CircleShape).background(Eazy.Brand), contentAlignment = Alignment.Center) { Ico(R.drawable.ic_check, 15.dp, tint = Eazy.Ink) }
                    BatchItem.Status.EXISTS -> Box(Modifier.size(28.dp).clip(CircleShape).background(Eazy.Grey), contentAlignment = Alignment.Center) { Ico(R.drawable.ph_bookmark, 15.dp, tint = Eazy.Ink) }
                    BatchItem.Status.FAILED -> Box(Modifier.size(28.dp).clip(CircleShape).background(Eazy.Ink), contentAlignment = Alignment.Center) { Ico(R.drawable.ic_close, 13.dp, tint = Eazy.Paper) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.title ?: siteLabel(item.url) ?: item.url, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = when (item.status) {
                    BatchItem.Status.WAITING -> "Wacht"
                    BatchItem.Status.BUSY -> "Bezig…"
                    BatchItem.Status.SAVED -> siteLabel(item.url) ?: "Toegevoegd"
                    BatchItem.Status.EXISTS -> "Stond al in je bibliotheek"
                    BatchItem.Status.FAILED -> item.message ?: "Mislukt"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (openable) Ico(R.drawable.ic_chevron_right, 16.dp, tint = Eazy.Muted)
        }
    }
}

// ---------------------------------------------------------------- bladwijzers

sealed interface BookmarksState {
    data object Loading : BookmarksState
    data class Loaded(val bookmarks: List<Bookmark>) : BookmarksState
    data class Failed(val message: String) : BookmarksState
}

class BookmarksViewModel(open: () -> InputStream?) : ViewModel() {
    private val _state = MutableStateFlow<BookmarksState>(BookmarksState.Loading)
    val state: StateFlow<BookmarksState> = _state

    /** Gekozen links. In de ViewModel (niet in saved state): duizenden links passen niet in een Android-bundle. */
    var selected by mutableStateOf(emptySet<String>())
        private set

    fun toggle(url: String) { selected = if (url in selected) selected - url else selected + url }
    fun setAll(urls: Collection<String>, on: Boolean) { selected = if (on) selected + urls else selected - urls.toSet() }

    init {
        viewModelScope.launch {
            _state.value = try {
                val html = withContext(Dispatchers.IO) {
                    open()?.use { input ->
                        // Bladwijzerbestanden zijn hooguit een paar MB; meer is waarschijnlijk iets anders.
                        val bytes = input.readNBytesCompat(10 * 1024 * 1024 + 1)
                        if (bytes.size > 10 * 1024 * 1024) null else String(bytes, Charsets.UTF_8)
                    }
                } ?: throw IllegalArgumentException()
                val list = withContext(Dispatchers.Default) { Bookmarks.parse(html) }
                if (list.isEmpty()) BookmarksState.Failed("Geen links gevonden. Kies het HTML-bestand dat je browser bij \"Bladwijzers exporteren\" maakt.")
                else BookmarksState.Loaded(list)
            } catch (e: Exception) {
                BookmarksState.Failed("Dit bestand kon niet worden gelezen. Kies het HTML-bestand van je browser.")
            }
        }
    }
}

private fun InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** Bladwijzers kiezen: per map, met zoeken; links die je al hebt staan uit. */
@Composable
fun BookmarksScreen(
    state: BookmarksState,
    savedUrls: Set<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSetAll: (Collection<String>, Boolean) -> Unit,
    onImport: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Filteren en groeperen alleen opnieuw als de lijst of de zoektekst verandert (niet bij elke tik).
    val groups = androidx.compose.runtime.remember(state, query) {
        (state as? BookmarksState.Loaded)?.bookmarks.orEmpty().filter {
            query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) || it.folder.contains(query, true)
        }.groupBy { it.folder }
    }
    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { RoundButton(R.drawable.ic_close, "Sluiten", onClose, background = Eazy.Paper) }
            item { Text("Bladwijzers", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp)) }
            when (state) {
                BookmarksState.Loading -> item { CircularProgressIndicator(color = Eazy.Ink) }
                is BookmarksState.Failed -> item { ErrorCard("Kan dit bestand niet lezen", state.message) }
                is BookmarksState.Loaded -> {
                    item {
                        Text(
                            "Kies de receptlinks. Pagina's zonder leesbaar recept slaan we over; die zie je daarna als \"mislukt\".",
                            style = MaterialTheme.typography.bodyMedium, color = Eazy.Muted,
                        )
                    }
                    item { SearchRow(query) { query = it } }
                    groups.forEach { (folder, marks) ->
                        val choosable = marks.filter { cleanKey(it.url) !in savedUrls }
                        val allOn = choosable.isNotEmpty() && choosable.all { it.url in selected }
                        item(key = "folder:$folder") {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(folder.ifEmpty { "Zonder map" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (choosable.isNotEmpty()) TextButton(onClick = { onSetAll(choosable.map { it.url }, !allOn) }) {
                                    Text(if (allOn) "Niets" else "Alles", color = Eazy.Ink)
                                }
                            }
                        }
                        items(marks, key = { "bm:" + it.url }) { b ->
                            val have = cleanKey(b.url) in savedUrls
                            val on = b.url in selected
                            Surface(
                                onClick = { onToggle(b.url) },
                                enabled = !have,
                                shape = RoundedCornerShape(18.dp),
                                color = Eazy.Paper,
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape).background(if (on) Eazy.Ink else Eazy.Paper)
                                            .border(2.dp, if (on) Eazy.Ink else Eazy.Dash, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { if (on) Ico(R.drawable.ic_check, 14.dp, tint = Eazy.Paper) }
                                    Column(Modifier.weight(1f)) {
                                        Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (have) Eazy.Subtle else Eazy.Ink)
                                        Text(if (have) "Staat al in je bibliotheek" else siteLabel(b.url) ?: b.url, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        StatusBarBackdrop(Eazy.Grey)
        if (state is BookmarksState.Loaded) BigPillButton(
            if (selected.size == 1) "1 recept ophalen" else "${selected.size} recepten ophalen",
            R.drawable.ph_sparkle, Eazy.Brand, { onImport(selected.toList()) },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            enabled = selected.isNotEmpty(),
        )
    }
}

/** Zelfde vorm als opgeslagen bronlinks (tracking-parameters eraf), om "staat al" te herkennen. */
private fun cleanKey(url: String): String = RecipeImporter.cleanUrl(url) ?: url

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(Eazy.Paper).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico(R.drawable.ph_search, 18.dp, tint = Eazy.Muted)
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Eazy.Ink), cursorBrush = SolidColor(Eazy.Ink),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text("Zoek in titel, site of map…", style = MaterialTheme.typography.bodyLarge, color = Eazy.Subtle); inner() } },
        )
    }
}
