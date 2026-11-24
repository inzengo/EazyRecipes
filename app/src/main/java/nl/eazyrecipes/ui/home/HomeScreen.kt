package nl.eazyrecipes.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.eazyrecipes.container
import nl.eazyrecipes.share.Sharing
import androidx.compose.foundation.clickable
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
import nl.eazyrecipes.ui.library.CollectionActions
import nl.eazyrecipes.ui.library.LibraryScreen
import nl.eazyrecipes.ui.library.LibraryViewModel
import nl.eazyrecipes.ui.library.tileContext
import nl.eazyrecipes.ui.theme.Eazy

enum class HomeTab(val label: String, @DrawableRes val icon: Int) {
    LIBRARY("Recepten", R.drawable.ph_books),
    PANTRY("In huis", R.drawable.ph_basket),
    // "Boodschappen" is te lang: dan valt "Meer" van de balk (zie screenshot 02d). Het scherm zelf heet wel zo.
    SHOPPING("Lijst", R.drawable.ph_list),
    MORE("Meer", R.drawable.ph_dots),
}

/** De vier hoofdschermen met de zwevende navigatie en de +-knop. */
@Composable
fun HomeScreen(
    library: LibraryViewModel,
    onOpen: (String) -> Unit,
    onImport: (String) -> Unit,
    pantry: @Composable () -> Unit,
    shopping: @Composable () -> Unit = {},
    /** Aantal dat je nog moet halen: als getal op het boodschappen-icoon. */
    toBuy: Int = 0,
    initialTab: HomeTab = HomeTab.LIBRARY,
    onOpenCategories: () -> Unit = {},
    onOpenLabels: () -> Unit = {},
    onOpenFile: (android.net.Uri) -> Unit = {},
    onImportMany: (List<String>) -> Unit = {},
    onOpenBookmarks: (android.net.Uri) -> Unit = {},
    onImportPhotos: (List<android.net.Uri>) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    // Wat er in "link plakken" stond toen het venster (per ongeluk) dicht ging; leeg na een import.
    var draft by rememberSaveable { mutableStateOf("") }
    val state by library.state.collectAsStateWithLifecycle()
    // Hier en niet in het venster: dat is al dicht als de bestandskiezer terugkomt.
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onOpenFile) }
    val openBookmarks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onOpenBookmarks) }
    // Een recept kan over meer screenshots lopen: tot 5 tegelijk, in de volgorde waarin je ze kiest.
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) onImportPhotos(uris)
    }
    // De camera-app schrijft naar een bestand van ons; onthouden welk, ook als Android de app intussen afsluit.
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget?.let(android.net.Uri::parse)
        cameraTarget = null
        if (ok && target != null) onImportPhotos(listOf(target))
    }

    BackHandler(enabled = tab != HomeTab.LIBRARY) { tab = HomeTab.LIBRARY }

    // De navigatie en +-knop kleuren mee met de gekozen categorie, zoals de kop.
    val tile = tileContext(state?.filter?.category)
    val accent = when {
        tab == HomeTab.LIBRARY && tile != Eazy.Alles -> tile.vivid
        tab == HomeTab.SHOPPING -> Eazy.Snel.vivid
        else -> Eazy.Brand
    }

    Box(Modifier.fillMaxSize().background(Eazy.Grey)) {
        when (tab) {
            HomeTab.LIBRARY -> LibraryScreen(
                state, library::update, library::toggleFavorite, onOpen,
                CollectionActions(
                    library::openCollection, library::createCollection, library::renameCollection, library::deleteCollection,
                    share = { id ->
                        val name = state?.collections?.firstOrNull { it.id == id }?.name ?: "Collectie"
                        scope.launch { Sharing.shareFile(context, name) { out -> context.container.bundles.writeCollection(id, out) } }
                    },
                ),
                query = library.query,
                onQuery = library::onQueryChange,
            )
            HomeTab.PANTRY -> pantry()
            HomeTab.SHOPPING -> shopping()
            HomeTab.MORE -> MoreScreen(onOpenCategories, onOpenLabels, onOpenFile)
        }
        BottomNav(tab, accent, toBuy, onSelect = { tab = it }, onAdd = { addOpen = true }, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (addOpen) {
        AddLinkSheet(
            draft = draft,
            savedUrls = state?.savedUrls.orEmpty(),
            onDismiss = { text -> draft = text; addOpen = false },
            onSubmit = { url -> draft = ""; addOpen = false; onImport(url) },
            onSubmitMany = { urls -> draft = ""; addOpen = false; onImportMany(urls) },
            onManual = { draft = ""; addOpen = false; onImport("") },
            onBookmarks = { addOpen = false; openBookmarks.launch(arrayOf("text/html", "*/*")) },
            onEazyFile = { addOpen = false; openFile.launch(arrayOf("*/*")) },
            onPickPhotos = {
                addOpen = false
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                addOpen = false
                val uri = newCameraUri(context)
                cameraTarget = uri.toString()
                // Geen camera-app (kan op sommige tablets)? Dan gebeurt er niets in plaats van een crash.
                try { takePhoto.launch(uri) } catch (e: android.content.ActivityNotFoundException) { cameraTarget = null }
            },
        )
    }
}

/** Leeg bestand voor de camera-app, in onze cache; oude camerafoto's ruimen we dan meteen op. */
private fun newCameraUri(context: android.content.Context): android.net.Uri {
    val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = java.io.File(dir, "foto-${System.currentTimeMillis()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

@Composable
private fun BottomNav(selected: HomeTab, accent: Color, toBuy: Int, onSelect: (HomeTab) -> Unit, onAdd: () -> Unit, modifier: Modifier) {
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
                        val count = if (t == HomeTab.SHOPPING && toBuy > 0) toBuy else null
                        Box {
                            Ico(t.icon, 22.dp, contentDescription = if (on) null else t.label + (count?.let { ", $it te halen" } ?: ""))
                            // Hoeveel je nog moet halen, klein op het icoon (alleen als de knop niet actief is; dan staat het in de kop).
                            if (count != null && !on) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-7).dp).clip(CircleShape).background(Eazy.Snel.vivid)
                                        .padding(horizontal = 5.dp),
                                ) {
                                    Text(if (count > 99) "99+" else "$count", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Eazy.Ink)
                                }
                            }
                        }
                        if (on) Text(t.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
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

/** Gekleurde kop met watermerk-icoon (In huis, Boodschappen). */
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

@Composable
private fun MoreScreen(onOpenCategories: () -> Unit, onOpenLabels: () -> Unit, onOpenFile: (android.net.Uri) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // Back-up: jij kiest waar het bestand komt (Downloads, Google Drive, …).
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        val app = context.applicationContext
        // In de app-scope: wissel je van tab, dan loopt de back-up gewoon door.
        context.container.appScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { app.container.bundles.writeBackup(it) } ?: throw java.io.IOException("Kan het bestand niet schrijven.")
                }
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Geen half bestand laten staan dat op een goede back-up lijkt.
                runCatching { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) }
                false
            }
            withContext(Dispatchers.Main) {
                busy = false
                Toast.makeText(app, if (ok) "Back-up gemaakt" else "Back-up maken is mislukt", Toast.LENGTH_LONG).show()
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onOpenFile) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Meer", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 12.dp))
            MoreRow(R.drawable.ph_bowl_food, Color(0xFFFF8A3D), "Categorieën", "Namen, kleuren en iconen", onOpenCategories)
            MoreRow(R.drawable.ph_tag, Color(0xFF3FD0F0), "Labels", "Kleuren, samenvoegen, verbergen", onOpenLabels)
            MoreRow(R.drawable.ph_folder, Eazy.Snel.vivid, "Back-up maken", if (busy) "Bezig…" else "Alle recepten, foto's, categorieën en labels in één bestand") {
                if (!busy) createBackup.launch(Sharing.backupName())
            }
            MoreRow(R.drawable.ph_share, Color(0xFF47E3BC), "Back-up terugzetten", "Of een gedeeld .eazy-bestand openen") {
                openFile.launch(arrayOf("*/*"))
            }
            MoreRow(R.drawable.ph_chef_hat, Eazy.Brand, "Over EazyRecipes", "Versie ${BuildConfig.VERSION_NAME}")
        }
        StatusBarBackdrop(Eazy.Grey)
    }
}

@Composable
private fun MoreRow(@DrawableRes icon: Int, color: Color, label: String, note: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(22.dp)).background(Eazy.Paper)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(start = 10.dp, end = 16.dp),
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
        if (onClick != null) Ico(R.drawable.ic_chevron_right, 18.dp, tint = Eazy.Muted)
    }
}
