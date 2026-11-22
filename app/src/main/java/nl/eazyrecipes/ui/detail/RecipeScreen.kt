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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import nl.eazyrecipes.ui.components.PhotoStatusBar
import nl.eazyrecipes.ui.components.PhotoTopScrim
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatTile
import nl.eazyrecipes.ui.components.TopRounded
import nl.eazyrecipes.ui.formatMinutes
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import nl.eazyrecipes.data.CollectionSummary
import nl.eazyrecipes.ui.components.DashedAddButton
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.library.NameDialog
import android.content.ContentResolver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.eazyrecipes.recipe.IngredientParts
import nl.eazyrecipes.ui.components.dashedBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import java.io.File

private const val NOTE_SAVE_DELAY_MS = 600L

sealed interface DetailState {
    data object Loading : DetailState
    data class Found(val details: RecipeDetails) : DetailState
    data object Gone : DetailState
}

class RecipeViewModel(
    private val id: String,
    private val repository: RecipeRepository,
    private val images: ImageStore,
    /** Voor het opslaan van de notitie: moet doorlopen als dit scherm (en zijn ViewModel) net verdwijnt. */
    private val saveScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    /** Gekozen aantal porties (alleen voor deze sessie); `null` als het recept geen porties noemt. */
    var servings by mutableStateOf<Int?>(null)
        private set
    private var baseServings: Int? = null

    /** Afgevinkte ingrediënten (index), alleen voor deze sessie. */
    var checked by mutableStateOf(emptySet<Int>())
        private set

    var note by mutableStateOf("")
        private set
    private var noteDirty = false
    private var noteJob: Job? = null

    var coverBusy by mutableStateOf(false)
        private set
    /** Eenmalige melding voor een toast (bv. "foto kon niet worden geladen"). */
    var message by mutableStateOf<String?>(null)

    private var loaded = false

    init { reload() }

    /** Ook na terugkomen van "Bewerken". Porties en notitie die je hier aanpast, blijven staan. */
    fun reload() = viewModelScope.launch {
        val d = repository.get(id)
        if (d != null) {
            if (!loaded) note = d.recipe.notes.orEmpty()
            if (!loaded || d.recipe.servings != baseServings) {
                baseServings = d.recipe.servings
                servings = d.recipe.servings
            }
            // Vinkjes horen bij regels: na een bewerking van de ingrediënten opnieuw beginnen.
            if (!loaded || d.ingredients.map { it.raw } != current?.ingredients?.map { it.raw }) checked = emptySet()
            loaded = true
        }
        _state.value = d?.let { DetailState.Found(it) } ?: DetailState.Gone
    }

    /** Factor voor de hoeveelheden: gekozen porties / porties in het recept. */
    val factor: Double get() {
        val base = baseServings ?: return 1.0
        return (servings ?: base).toDouble() / base
    }

    fun changeServings(delta: Int) {
        val s = servings ?: return
        servings = (s + delta).coerceIn(1, 99)
    }

    fun toggleChecked(index: Int) {
        checked = if (index in checked) checked - index else checked + index
    }

    fun onNoteChange(text: String) {
        note = text
        noteDirty = true
        noteJob?.cancel()
        noteJob = viewModelScope.launch { delay(NOTE_SAVE_DELAY_MS); flushNote() }
    }

    /** Sla een nog niet opgeslagen notitie meteen op (bij weggaan van het scherm). */
    fun flushNote() {
        if (!noteDirty) return
        noteDirty = false
        noteJob?.cancel()
        val text = note
        // Mutex: twee snelle opslagen (debounce + terug) mogen niet in omgekeerde volgorde in de database landen.
        saveScope.launch { noteLock.withLock { repository.setNotes(id, text) } }
    }

    private val noteLock = Mutex()

    override fun onCleared() = flushNote()

    fun setCover(resolver: ContentResolver, uri: Uri) =
        replaceCover("Deze foto kon niet worden geladen.") { images.saveFromUri(resolver, uri) }

    /** De foto van de website opnieuw ophalen (na een eigen foto). */
    fun restoreSourceCover() {
        val url = current?.recipe?.imageUrl ?: return
        replaceCover("De foto van de website kon niet worden opgehaald. Ben je online?") { images.download(url) }
    }

    private fun replaceCover(failMessage: String, load: suspend () -> File?) {
        if (coverBusy) return
        coverBusy = true
        val old = current?.recipe?.imagePath
        // In saveScope: ga je terug terwijl de foto laadt, dan wordt hij toch gekoppeld (of opgeruimd), niet half achtergelaten.
        saveScope.launch {
            try {
                val file = load()
                if (file == null) {
                    message = failMessage
                } else {
                    repository.setImagePath(id, file.path)
                    images.delete(old)
                    viewModelScope.launch { reload() }
                }
            } finally {
                coverBusy = false
            }
        }
    }

    private val current: RecipeDetails? get() = (_state.value as? DetailState.Found)?.details

    /** Alle collecties; of dit recept erin zit, volgt uit `recipes`. */
    val collections: StateFlow<List<CollectionSummary>> =
        repository.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setInCollection(collectionId: String, member: Boolean) {
        viewModelScope.launch { repository.setInCollection(collectionId, id, member) }
    }

    fun createCollectionWithRecipe(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.setInCollection(repository.createCollection(name), id, true) }
    }

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
fun RecipeScreen(vm: RecipeViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Terug van het bewerkscherm: opnieuw laden (dit scherm verlaat de compositie zolang je bewerkt).
    LaunchedEffect(Unit) { vm.reload() }
    when (val s = state) {
        is DetailState.Found -> Detail(s.details, vm, onBack = { vm.flushNote(); onBack() }, onEdit = { vm.flushNote(); onEdit() })
        DetailState.Gone -> Column(Modifier.fillMaxSize().background(Eazy.Grey).statusBarsPadding().padding(16.dp)) {
            RoundButton(R.drawable.ic_back, "Terug", onBack, background = Eazy.Paper)
            Text("Dit recept bestaat niet meer.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
        }
        DetailState.Loading -> Box(Modifier.fillMaxSize().background(Eazy.Paper))
    }
}

private val PHOTO_HEIGHT = 320.dp

@Composable
private fun Detail(d: RecipeDetails, vm: RecipeViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val r = d.recipe
    val ctx = r.mainIngredient.context
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickCollections by rememberSaveable { mutableStateOf(false) }
    var coverOptions by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setCover(context.contentResolver, it) }
    }
    val choosePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    vm.message?.let { msg ->
        LaunchedEffect(msg) { Toast.makeText(context, msg, Toast.LENGTH_LONG).show(); vm.message = null }
    }
    val parts = remember(d.ingredients) { d.ingredients.map { IngredientParts.parse(it.raw) } }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(Eazy.Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(PHOTO_HEIGHT)) {
                RecipePhoto(r.imagePath, r.imageUrl, ctx, Modifier.fillMaxSize(), iconSize = 160.dp)
                PhotoTopScrim()
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoundButton(R.drawable.ic_back, "Terug", onBack)
                    Box(Modifier.weight(1f))
                    RoundButton(R.drawable.ph_folder, "In collectie zetten", { pickCollections = true })
                    RoundButton(R.drawable.ph_pencil, "Bewerken", onEdit)
                    RoundButton(
                        R.drawable.ph_heart, if (r.favorite) "Uit favorieten" else "Favoriet", vm::toggleFavorite,
                        background = if (r.favorite) Eazy.Brand else Eazy.Paper.copy(alpha = 0.9f),
                    )
                }
                // Omslag wijzigen: rechtsonder op de foto, boven de witte kaart (die 36 dp over de foto valt).
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 52.dp)) {
                    if (vm.coverBusy) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Eazy.Paper.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Eazy.Ink, strokeWidth = 2.5.dp)
                        }
                    } else {
                        RoundButton(R.drawable.ph_camera, "Omslagfoto wijzigen", { if (r.imageUrl != null) coverOptions = true else choosePhoto() })
                    }
                }
            }
            Column(
                Modifier.offset(y = (-36).dp).background(Eazy.Paper, TopRounded).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 40.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        Text(vm.servings?.toString() ?: "–", style = MaterialTheme.typography.titleLarge)
                    }
                    StatTile(R.drawable.ph_basket, "ingrediënten", ctx, Modifier.weight(1f)) {
                        Text("${d.ingredients.size}", style = MaterialTheme.typography.titleLarge)
                    }
                }

                vm.servings?.let { n ->
                    ServingsBar(n, r.servings, ctx.vivid, onChange = vm::changeServings)
                }

                if (d.ingredients.isNotEmpty()) Column {
                    Text("Ingrediënten", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 6.dp))
                    val amounts = parts.map { it.amount(vm.factor) }
                    // Eén kolombreedte voor alle hoeveelheden (de langste), zodat de namen netjes onder elkaar staan.
                    val measurer = rememberTextMeasurer()
                    val amountStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold)
                    val density = LocalDensity.current
                    val amountWidth = remember(amounts, amountStyle) {
                        val px = amounts.filter { it.isNotEmpty() }.maxOfOrNull { measurer.measure(it, amountStyle).size.width } ?: 0
                        with(density) { px.toDp() }.coerceIn(48.dp, 120.dp)
                    }
                    parts.forEachIndexed { i, p ->
                        IngredientRow(amounts[i], p.name, amountWidth.takeIf { amounts.any(String::isNotEmpty) }, checked = i in vm.checked) { vm.toggleChecked(i) }
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

                NoteField(vm.note, vm::onNoteChange)

                if (d.tags.isNotEmpty()) {
                    Text(d.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Eazy.Subtle)
                }

                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Recept verwijderen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        PhotoStatusBar(scroll, PHOTO_HEIGHT)
    }

    if (coverOptions) {
        CoverSheet(
            onPick = { coverOptions = false; choosePhoto() },
            onRestore = { coverOptions = false; vm.restoreSourceCover() },
            onDismiss = { coverOptions = false },
        )
    }

    if (pickCollections) {
        val collections by vm.collections.collectAsStateWithLifecycle()
        CollectionsSheet(
            recipeId = r.id,
            collections = collections,
            onToggle = vm::setInCollection,
            onCreate = vm::createCollectionWithRecipe,
            onDismiss = { pickCollections = false },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionsSheet(
    recipeId: String,
    collections: List<CollectionSummary>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("In collectie zetten", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Een recept kan in meerdere collecties staan.",
                style = MaterialTheme.typography.bodyMedium,
                color = Eazy.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            collections.forEach { c ->
                val member = c.recipes.any { it.id == recipeId }
                Surface(onClick = { onToggle(c.id, !member) }, shape = RoundedCornerShape(22.dp), color = Eazy.Grey) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(if (member) Eazy.Ink else Eazy.Paper)
                                .border(2.dp, if (member) Eazy.Ink else Eazy.Dash, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (member) Ico(R.drawable.ic_check, 15.dp, tint = Eazy.Paper) }
                        Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${c.recipes.size}", style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
                    }
                }
            }
            DashedAddButton("Nieuwe collectie", { creating = true })
        }
    }
    if (creating) {
        NameDialog("Nieuwe collectie", "", "Maken en toevoegen", onConfirm = { onCreate(it); creating = false }, onDismiss = { creating = false })
    }
}

/** Porties-stepper in de receptkleur (zoals de mockup). */
@Composable
private fun ServingsBar(servings: Int, original: Int?, color: Color, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color).padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Porties", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
            Text(
                if (servings == original) "Zoals in het recept" else "Recept is voor $original · omgerekend",
                style = MaterialTheme.typography.labelSmall,
                color = Eazy.Ink.copy(alpha = 0.75f),
            )
        }
        RoundButton(R.drawable.ic_minus, "Minder porties", { onChange(-1) }, background = Eazy.Paper)
        Text("$servings", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        RoundButton(R.drawable.ic_plus, "Meer porties", { onChange(1) }, background = Eazy.Ink, contentColor = Eazy.Paper)
    }
}

/** Ingrediënt om af te vinken: rondje, hoeveelheid vet (omgerekend), naam. */
@Composable
private fun IngredientRow(amount: String, name: String, amountWidth: Dp?, checked: Boolean, onToggle: () -> Unit) {
    val color = if (checked) Eazy.Subtle else Eazy.Ink
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (checked) Eazy.Ink else Eazy.Paper)
                    .border(2.dp, if (checked) Eazy.Ink else Eazy.Dash, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (checked) Ico(R.drawable.ic_check, 15.dp, tint = Eazy.Paper) }
            if (amountWidth != null) {
                Text(
                    amount,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = color,
                    modifier = Modifier.width(amountWidth),
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (checked) TextDecoration.LineThrough else null),
                color = color,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = Eazy.Line)
    }
}

@Composable
private fun NoteField(note: String, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().dashedBorder(Eazy.Dash, 22.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(R.drawable.ph_pencil, 18.dp, tint = Eazy.Muted)
            Text("Mijn notitie", style = MaterialTheme.typography.labelLarge, color = Eazy.Muted)
        }
        BasicTextField(
            value = note,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Eazy.Ink),
            cursorBrush = SolidColor(Eazy.Ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            decorationBox = { inner ->
                Box {
                    if (note.isEmpty()) Text("Bijv. volgende keer minder suiker", style = MaterialTheme.typography.bodyMedium, color = Eazy.Subtle)
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSheet(onPick: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Omslagfoto", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            SheetOption(R.drawable.ph_camera, "Kies een foto", "Uit je galerij", onPick)
            SheetOption(R.drawable.ic_link, "Foto van de website", "Zet de originele foto terug", onRestore)
        }
    }
}

@Composable
private fun SheetOption(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Eazy.Grey) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Ico(icon, 22.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Eazy.Muted)
            }
        }
    }
}
