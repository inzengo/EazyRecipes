package nl.eazyrecipes.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.eazyrecipes.BuildConfig
import nl.eazyrecipes.R
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.library.AddLinkSheet
import nl.eazyrecipes.ui.library.Category
import nl.eazyrecipes.ui.library.CollectionActions
import nl.eazyrecipes.ui.library.LibraryScreen
import nl.eazyrecipes.ui.library.LibraryViewModel
import nl.eazyrecipes.ui.library.color
import nl.eazyrecipes.ui.theme.Eazy

enum class HomeTab(val label: String, @DrawableRes val icon: Int) {
    LIBRARY("Recepten", R.drawable.ph_books),
    PANTRY("In huis", R.drawable.ph_basket),
    DISCOVER("Ontdek", R.drawable.ph_compass),
    MORE("Meer", R.drawable.ph_dots),
}

/** De vier hoofdschermen met de zwevende navigatie en de +-knop. */
@Composable
fun HomeScreen(
    library: LibraryViewModel,
    onOpen: (String) -> Unit,
    onImport: (String) -> Unit,
    pantry: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.LIBRARY) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    // Wat er in "link plakken" stond toen het venster (per ongeluk) dicht ging; leeg na een import.
    var draft by rememberSaveable { mutableStateOf("") }
    val state by library.state.collectAsStateWithLifecycle()

    BackHandler(enabled = tab != HomeTab.LIBRARY) { tab = HomeTab.LIBRARY }

    // De navigatie en +-knop kleuren mee met de gekozen categorie, zoals de kop.
    val category = state?.filter?.category ?: Category.ALLES
    val accent = if (tab == HomeTab.LIBRARY && category != Category.ALLES) category.color.vivid else Eazy.Brand

    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        when (tab) {
            HomeTab.LIBRARY -> LibraryScreen(
                state, library::update, library::toggleFavorite, onOpen,
                CollectionActions(library::openCollection, library::createCollection, library::renameCollection, library::deleteCollection),
            )
            HomeTab.PANTRY -> pantry()
            HomeTab.DISCOVER -> DiscoverScreen()
            HomeTab.MORE -> MoreScreen()
        }
        BottomNav(tab, accent, onSelect = { tab = it }, onAdd = { addOpen = true }, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (addOpen) {
        AddLinkSheet(
            draft = draft,
            savedUrls = state?.savedUrls.orEmpty(),
            onDismiss = { text -> draft = text; addOpen = false },
            onSubmit = { url -> draft = ""; addOpen = false; onImport(url) },
        )
    }
}

@Composable
private fun BottomNav(selected: HomeTab, accent: Color, onSelect: (HomeTab) -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).height(64.dp).shadow(16.dp, CircleShape).clip(CircleShape).background(Eazy.Ink).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTab.entries.forEach { t ->
                val on = t == selected
                Surface(
                    onClick = { onSelect(t) },
                    shape = CircleShape,
                    color = if (on) accent else Color.Transparent,
                    contentColor = if (on) Eazy.Ink else Color(0xFFA6A6A6),
                    modifier = Modifier.height(52.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = if (on) 14.dp else 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Ico(t.icon, 22.dp, contentDescription = if (on) null else t.label)
                        if (on) Text(t.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
            }
        }
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = accent,
            contentColor = Eazy.Ink,
            modifier = Modifier.size(64.dp).shadow(16.dp, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_plus, 28.dp, contentDescription = "Recept toevoegen") }
        }
    }
}

/** Gekleurde kop met watermerk-icoon (In huis, Ontdek). */
@Composable
fun ColorHero(color: Color, @DrawableRes icon: Int, title: String, sub: String, badge: String? = null) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(color)
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-8).dp).wrapContentSize(unbounded = true)) {
                Ico(icon, 210.dp, tint = Eazy.Ink.copy(alpha = 0.1f))
            }
        }
        Column(
            Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            badge?.let {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = Eazy.Paper,
                    modifier = Modifier.clip(CircleShape).background(Eazy.Ink).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(title, style = MaterialTheme.typography.displaySmall)
            Text(sub, style = MaterialTheme.typography.labelLarge, color = Eazy.Ink.copy(alpha = 0.8f))
        }
    }
}

private val DiscoverBlue = Color(0xFF4D9DFF)

@Composable
private fun DiscoverScreen() {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ColorHero(DiscoverBlue, R.drawable.ph_compass, "Ontdek\nnieuwe recepten", "Zoek op je favoriete sites en bewaar met één tik.", badge = "Latere fase")
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SiteRow("L", Color(0xFFFFC738), "leukerecepten.nl", "Werkt al via Delen of plakken")
                SiteRow("E", Color(0xFFFF8AD4), "eefkooktzo.nl", "Werkt al via Delen of plakken")
                Text(
                    "Straks kun je hier zoeken in deze sites zonder de browser te openen. Tot die tijd: deel een recept vanuit je browser naar EazyRecipes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Eazy.Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        StatusBarBackdrop(DiscoverBlue)
    }
}

@Composable
private fun SiteRow(letter: String, color: Color, name: String, note: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Eazy.Paper).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(letter, style = MaterialTheme.typography.headlineSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
        }
    }
}

@Composable
private fun MoreScreen() {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Meer", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 12.dp))
            MoreRow(R.drawable.ph_folder, Eazy.Snel.vivid, "Back-up maken en terugzetten", "Komt in fase 4")
            MoreRow(R.drawable.ph_share, Color(0xFF47E3BC), "Recepten delen", "Komt in fase 4")
            MoreRow(R.drawable.ph_chef_hat, Eazy.Brand, "Over EazyRecipes", "Versie ${BuildConfig.VERSION_NAME}")
        }
        StatusBarBackdrop(Eazy.Grey)
    }
}

@Composable
private fun MoreRow(@DrawableRes icon: Int, color: Color, label: String, note: String) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(22.dp)).background(Eazy.Paper).padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(color), contentAlignment = Alignment.Center) {
            Ico(icon, 22.dp, tint = Eazy.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
        }
    }
}
