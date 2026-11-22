package nl.eazyrecipes.ui.pantry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.eazyrecipes.R
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.StatusBarBackdrop
import nl.eazyrecipes.ui.home.ColorHero
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context

/** Zoveel chips staan er standaard; de rest achter "+ N meer". */
private const val CHIPS_COLLAPSED = 18

private val DotOff = Color(0xFFE3E3DF)

class PantryActions(
    val toggle: (String) -> Unit,
    val addCustom: (String) -> Unit,
    val removeCustom: (String) -> Unit,
    val clear: () -> Unit,
    val open: (String) -> Unit,
)

@Composable
fun PantryScreen(vm: PantryViewModel, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    PantryScreen(state, PantryActions(vm::toggle, vm::addCustom, vm::removeCustom, vm::clear, onOpen))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryScreen(state: PantryUiState?, actions: PantryActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ColorHero(Eazy.Brand, R.drawable.ph_basket, "Wat heb je\nin huis?", "Tik aan wat je hebt, wij zoeken wat je ermee kunt maken.")
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state == null) return@Column
                if (state.libraryEmpty) {
                    Notice("Bewaar eerst een paar recepten. Dan zie je hier hun ingrediënten om aan te tikken.")
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "In huis · ${state.selectedCount} gekozen",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = Eazy.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.selectedCount > 0) {
                        Text(
                            "Wissen",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                            modifier = Modifier.clip(CircleShape).clickableLabel(actions.clear),
                        )
                    }
                }
                val hidden = state.chips.drop(CHIPS_COLLAPSED).count { !it.selected }
                val visible = if (expanded || hidden == 0) state.chips else state.chips.take(CHIPS_COLLAPSED) + state.chips.drop(CHIPS_COLLAPSED).filter { it.selected }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { c -> ToggleChip(c.label, c.selected) { actions.toggle(c.key) } }
                    state.custom.forEach { w -> ToggleChip(w, selected = true) { actions.removeCustom(w) } }
                    if (hidden > 0 && !expanded) MoreChip("+ $hidden meer") { expanded = true }
                }
                CustomField(actions.addCustom)

                Text("Hiermee kun je aan de slag", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp))
                when {
                    state.selectedCount == 0 -> Notice("Kies een paar ingrediënten om te beginnen.")
                    state.matches.isEmpty() -> Notice("Geen recept gevonden met wat je hebt gekozen. Tik er nog een paar aan.")
                    else -> state.matches.forEach { m -> MatchRow(m) { actions.open(m.recipe.id) } }
                }
            }
        }
        StatusBarBackdrop(Eazy.Brand)
    }
}

private fun Modifier.clickableLabel(onClick: () -> Unit) =
    clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)

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

/** Zwart met ✓ = gekozen, wit met + = niet gekozen (zoals de mockup). */
@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Eazy.Ink else Eazy.Paper,
        contentColor = if (selected) Eazy.Paper else Eazy.Ink,
        shadowElevation = 1.dp,
    ) {
        Row(
            Modifier.height(42.dp).padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Ico(if (selected) R.drawable.ic_check else R.drawable.ic_plus, 16.dp)
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun MoreChip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Eazy.Grey, contentColor = Eazy.Ink, modifier = Modifier.border(1.5.dp, Eazy.Dash, CircleShape)) {
        Box(Modifier.height(42.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun CustomField(onAdd: (String) -> Unit) {
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
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Iets anders, bv. halloumi", style = MaterialTheme.typography.bodyLarge, color = Eazy.Subtle)
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
private fun MatchRow(m: PantryMatch, onClick: () -> Unit) {
    val ctx = m.recipe.mainIngredient.context
    val scoreColor = if (m.recipe.mainIngredient == null) Eazy.Brand else ctx.vivid
    Surface(onClick = onClick, shape = RoundedCornerShape(26.dp), color = Eazy.Paper, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecipePhoto(m.recipe.imagePath, m.recipe.imageUrl, ctx, Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)), iconSize = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    m.have.forEach { has ->
                        Box(Modifier.weight(1f, fill = false).width(16.dp).height(6.dp).clip(CircleShape).background(if (has) Eazy.Ink else DotOff))
                    }
                }
                Text(
                    if (m.missing.isEmpty()) "Je hebt alles in huis!" else "Nog nodig: " + m.missing.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = Eazy.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(54.dp).clip(CircleShape).background(scoreColor), contentAlignment = Alignment.Center) {
                Text(
                    "${m.haveCount}/${m.total}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (m.total >= 10) 15.sp else 17.sp),
                    color = Eazy.Ink,
                )
            }
        }
    }
}
