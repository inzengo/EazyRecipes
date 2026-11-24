package nl.eazyrecipes.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.eazyrecipes.R
import nl.eazyrecipes.data.RecipeRepository
import nl.eazyrecipes.data.ShoppingRepository
import nl.eazyrecipes.share.Sharing
import nl.eazyrecipes.shopping.HaveReason
import nl.eazyrecipes.shopping.ShoppingLine
import nl.eazyrecipes.shopping.ShoppingList
import nl.eazyrecipes.shopping.ShoppingListState
import nl.eazyrecipes.shopping.ShoppingRecipe
import nl.eazyrecipes.ui.components.Chip
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.home.ColorHero
import nl.eazyrecipes.ui.pantry.PantryStore
import nl.eazyrecipes.ui.theme.Eazy

/** Geel, zoals "Snel": de boodschappenlijst is het snelle, praktische deel van de keuken. */
private val ShoppingYellow get() = Eazy.Snel.vivid

class ShoppingViewModel(
    private val shopping: ShoppingRepository,
    recipes: RecipeRepository,
    pantry: PantryStore,
) : ViewModel() {

    val state: StateFlow<ShoppingListState?> = combine(
        shopping.observeListRecipes(recipes), shopping.observeItems(), shopping.observeChecked(), pantry.selection,
    ) { list, extras, checked, sel -> ShoppingList.build(list, extras, checked, sel.keys, sel.custom) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(line: ShoppingLine) {
        viewModelScope.launch {
            val extra = line.extraId
            if (extra != null) shopping.setItemChecked(extra, !line.checked) else shopping.setChecked(line.checkKey, !line.checked)
        }
    }

    fun addItem(text: String) { viewModelScope.launch { shopping.addItem(text) } }
    fun removeItem(id: String) { viewModelScope.launch { shopping.removeItem(id) } }
    fun setServings(recipeId: String, servings: Int?) { viewModelScope.launch { shopping.addRecipe(recipeId, servings) } }
    fun removeRecipe(recipeId: String) { viewModelScope.launch { shopping.removeRecipe(recipeId) } }
    fun clear() { viewModelScope.launch { shopping.clear() } }
}

class ShoppingActions(
    val toggle: (ShoppingLine) -> Unit,
    val addItem: (String) -> Unit,
    val removeItem: (String) -> Unit,
    val setServings: (String, Int?) -> Unit,
    val removeRecipe: (String) -> Unit,
    val clear: () -> Unit,
    val openRecipe: (String) -> Unit,
    val share: (ShoppingListState) -> Unit,
)

@Composable
fun ShoppingScreen(vm: ShoppingViewModel, onOpenRecipe: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ShoppingScreen(
        state,
        ShoppingActions(
            vm::toggle, vm::addItem, vm::removeItem, vm::setServings, vm::removeRecipe, vm::clear, onOpenRecipe,
            share = { s -> Sharing.shareText(context, "Boodschappen", ShoppingList.asText(s)) },
        ),
    )
}

@Composable
fun ShoppingScreen(
    state: ShoppingListState?,
    actions: ShoppingActions,
    /** Voor de screenshot-test: "Heb je al" en "In je mandje" open. */
    expanded: Boolean = false,
) {
    var showDone by rememberSaveable { mutableStateOf(expanded) }
    var showHave by rememberSaveable { mutableStateOf(expanded) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var recipeSheet by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val sub = when {
                state == null || state.isEmpty -> "Zet recepten op je lijst; wij tellen alles bij elkaar op."
                state.toBuy == 0 -> "Alles gehaald!"
                else -> "${state.toBuy} te halen" + if (state.recipes.isNotEmpty()) " · ${state.recipes.size} ${if (state.recipes.size == 1) "recept" else "recepten"}" else ""
            }
            ColorHero(ShoppingYellow, R.drawable.ph_list, "Boodschappen", sub)
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state == null) return@Column
                if (state.recipes.isNotEmpty()) {
                    // Eén rij om opzij te schuiven: zo blijft de lijst zelf bovenaan, ook met veel recepten.
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.recipes.forEach { r ->
                            val n = r.servings ?: r.baseServings
                            Chip(r.title.take(28) + (n?.let { " · $it p." } ?: ""), R.drawable.ph_bowl_food, Eazy.Paper, onClick = { recipeSheet = r.id }, trailing = R.drawable.ic_chevron_down)
                        }
                    }
                }
                AddField(actions.addItem)
                if (state.isEmpty) {
                    Notice("Je lijst is leeg. Open een recept en tik bij de ingrediënten op \"Op de lijst\", of typ hierboven zelf iets.")
                    return@Column
                }
                if (state.toBuy == 0) Notice("Alles staat in je mandje.")
                state.sections.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitle(s.aisle.label)
                        s.lines.forEach { LineRow(it, actions) }
                    }
                }
                if (state.have.isNotEmpty()) {
                    Fold("Heb je waarschijnlijk al", state.have.size, showHave) { showHave = !showHave }
                    if (showHave) state.have.forEach { LineRow(it, actions, note = if (it.have == HaveReason.IN_HUIS) "staat bij In huis" else "basis") }
                }
                if (state.done.isNotEmpty()) {
                    Fold("In je mandje", state.done.size, showDone) { showDone = !showDone }
                    if (showDone) state.done.forEach { LineRow(it, actions) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    if (state.toBuy > 0) Chip("Delen", R.drawable.ph_share, Eazy.Paper, onClick = { actions.share(state) })
                    Chip("Lijst leegmaken", R.drawable.ic_close, Eazy.Paper, onClick = { confirmClear = true })
                }
            }
        }
        StatusBarBackdrop(ShoppingYellow)
    }

    state?.recipes?.firstOrNull { it.id == recipeSheet }?.let { r ->
        RecipeSheet(r, actions, onDismiss = { recipeSheet = null })
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Lijst leegmaken?") },
            text = { Text("De recepten, je eigen items en alle vinkjes gaan van de lijst. De recepten zelf blijven bewaard.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; actions.clear() }) { Text("Leegmaken", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuleren", color = Eazy.Ink) } },
            containerColor = Eazy.Paper,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = Eazy.Muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun Fold(title: String, count: Int, open: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, modifier = Modifier.padding(top = 6.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$title ($count)", style = MaterialTheme.typography.labelLarge)
            Ico(if (open) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right, 16.dp, tint = Eazy.Muted)
        }
    }
}

/** Eén regel: rondje om af te vinken, hoeveelheid vet, naam, en waar hij vandaan komt. */
@Composable
private fun LineRow(line: ShoppingLine, actions: ShoppingActions, note: String? = null) {
    Surface(onClick = { actions.toggle(line) }, shape = RoundedCornerShape(20.dp), color = Eazy.Paper, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(if (line.checked) Eazy.Ink else Eazy.Paper)
                    .border(2.dp, if (line.checked) Eazy.Ink else Eazy.Dash, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (line.checked) Ico(R.drawable.ic_check, 14.dp, tint = Eazy.Paper) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val done = line.checked
                Text(
                    buildAnnotatedString {
                        if (line.amount.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(line.amount + " ") }
                        append(line.name)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                    color = if (done) Eazy.Muted else Eazy.Ink,
                )
                val from = listOfNotNull(note) + line.sources.map { it.recipeTitle }.distinct()
                if (from.isNotEmpty()) {
                    Text(from.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Eazy.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            line.extraId?.let { id ->
                Surface(onClick = { actions.removeItem(id) }, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_close, 16.dp, tint = Eazy.Muted, contentDescription = "Weghalen") }
                }
            }
        }
    }
}

@Composable
private fun AddField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { onAdd(text); text = "" }
    Row(
        Modifier.fillMaxWidth().height(52.dp).border(1.5.dp, Eazy.Line, CircleShape).clip(CircleShape).background(Eazy.Paper)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Eazy.Ink),
            cursorBrush = SolidColor(Eazy.Ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) submit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Iets toevoegen, bv. wc-papier", style = MaterialTheme.typography.bodyLarge, color = Eazy.Subtle)
                    inner()
                }
            },
        )
        if (text.isNotBlank()) {
            Surface(onClick = submit, shape = CircleShape, color = Eazy.Ink, contentColor = Eazy.Paper, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_plus, 18.dp, contentDescription = "Toevoegen") }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Eazy.Muted,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Eazy.Paper).padding(24.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Recept op de lijst: porties aanpassen, openen of van de lijst halen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeSheet(r: ShoppingRecipe, actions: ShoppingActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(r.title, style = MaterialTheme.typography.headlineSmall)
            val n = r.servings ?: r.baseServings
            if (n != null && r.baseServings != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(ShoppingYellow).padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Porties", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                        Text(if (n == r.baseServings) "Zoals in het recept" else "Recept is voor ${r.baseServings}", style = MaterialTheme.typography.labelSmall, color = Eazy.Ink.copy(alpha = 0.75f))
                    }
                    StepButton(R.drawable.ic_minus, "Minder", enabled = n > 1) { actions.setServings(r.id, n - 1) }
                    Text("$n", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp))
                    StepButton(R.drawable.ic_plus, "Meer", enabled = n < 99) { actions.setServings(r.id, n + 1) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Recept openen", R.drawable.ph_bowl_food, Eazy.Grey, onClick = { onDismiss(); actions.openRecipe(r.id) })
                Chip("Van de lijst", R.drawable.ic_close, Eazy.Grey, onClick = { onDismiss(); actions.removeRecipe(r.id) })
            }
        }
    }
}

@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = Eazy.Paper.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Ico(icon, 18.dp, contentDescription = description) }
    }
}
