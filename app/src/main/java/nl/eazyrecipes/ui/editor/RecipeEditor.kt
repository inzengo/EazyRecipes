package nl.eazyrecipes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.eazyrecipes.R
import nl.eazyrecipes.data.CategoryEntity
import nl.eazyrecipes.importer.ParsedRecipe
import nl.eazyrecipes.importer.ParsedStep
import nl.eazyrecipes.ui.components.BigPillButton
import nl.eazyrecipes.ui.components.Chip
import nl.eazyrecipes.ui.components.DashedAddButton
import nl.eazyrecipes.ui.components.Ico
import nl.eazyrecipes.ui.components.PhotoStatusBar
import nl.eazyrecipes.ui.components.PhotoTopScrim
import nl.eazyrecipes.ui.components.RecipePhoto
import nl.eazyrecipes.ui.components.RoundButton
import nl.eazyrecipes.ui.components.StatTile
import nl.eazyrecipes.ui.components.TopRounded
import nl.eazyrecipes.ui.siteLabel
import nl.eazyrecipes.ui.theme.ContextColor
import nl.eazyrecipes.ui.theme.Eazy
import nl.eazyrecipes.ui.theme.context
import nl.eazyrecipes.ui.theme.LocalCategories
import nl.eazyrecipes.ui.theme.accent

/**
 * Bewerkbaar recept: gebruikt door het importscherm (voorbeeld vóór opslaan) en door "Bewerken" op het detailscherm.
 * Alle wijzigingen gaan via [onEdit], zodat de ViewModel de bron van waarheid blijft.
 */
@Composable
fun RecipeEditor(
    draft: ParsedRecipe,
    imagePath: String?,
    onEdit: ((ParsedRecipe) -> ParsedRecipe) -> Unit,
    onClose: () -> Unit,
    closeLabel: String,
    saveLabel: String,
    saving: Boolean,
    canSave: Boolean,
    saveError: String?,
    onSave: () -> Unit,
    banner: @Composable () -> Unit = {},
) {
    val d = draft
    val categories = LocalCategories.current
    // Alleen soorten die (nog) bestaan: een gok kan op een verwijderde standaardsoort vallen.
    val ids = d.categoryIds.filter { id -> categories.list.any { it.id == id } }
    val ctx = categories.context(ids.firstOrNull())
    val scroll = rememberScrollState()
    var pickKind by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Eazy.Paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // --- foto ---
            Box(Modifier.fillMaxWidth().height(PHOTO_HEIGHT)) {
                RecipePhoto(imagePath, d.imageUrl, ctx, Modifier.fillMaxSize(), iconSize = 160.dp)
                PhotoTopScrim()
                RoundButton(R.drawable.ic_close, closeLabel, onClose, Modifier.statusBarsPadding().padding(16.dp))
            }
            // --- witte kaart over de foto ---
            Column(
                Modifier.offset(y = (-36).dp).background(Eazy.Paper, TopRounded).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                banner()

                // Soort = een gok van de importer; één tik om te corrigeren, verder geen aandacht.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ctx == Eazy.Alles) Chip("Kies soort", ctx.icon, Eazy.Grey, onClick = { pickKind = true }, trailing = R.drawable.ic_chevron_down)
                    else Chip(
                        ctx.label + (if (ids.size > 1) " +${ids.size - 1}" else ""),
                        ctx.icon, ctx.vivid, onClick = { pickKind = true }, trailing = R.drawable.ic_chevron_down,
                    )
                    siteLabel(d.sourceUrl)?.let { Chip(it, R.drawable.ic_link, Eazy.Grey, contentColor = Eazy.Muted) }
                }

                EditableText(
                    value = d.title,
                    onChange = { t -> onEdit { it.copy(title = t) } },
                    style = MaterialTheme.typography.headlineMedium,
                    placeholder = "Naam van het recept",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(R.drawable.ph_clock, "minuten", ctx, Modifier.weight(1f)) {
                        NumberField(d.totalMinutes) { n -> onEdit { it.copy(totalMinutes = n) } }
                    }
                    StatTile(R.drawable.ph_users, "porties", ctx, Modifier.weight(1f)) {
                        NumberField(d.servings) { n -> onEdit { it.copy(servings = n) } }
                    }
                    StatTile(R.drawable.ph_basket, "ingrediënten", ctx, Modifier.weight(1f)) {
                        Text("${d.ingredients.count { it.isNotBlank() }}", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Section("Ingrediënten") {
                    d.ingredients.forEachIndexed { i, line ->
                        EditableRow(
                            value = line,
                            onChange = { t -> onEdit { it.copy(ingredients = it.ingredients.replaceAt(i, t)) } },
                            onRemove = { onEdit { it.copy(ingredients = it.ingredients.without(i)) } },
                            placeholder = "bv. 200 gr kipfilet",
                        )
                    }
                    DashedAddButton("Ingrediënt toevoegen", { onEdit { it.copy(ingredients = it.ingredients + "") } })
                }

                Section("Bereiding") {
                    d.steps.forEachIndexed { i, step ->
                        val prev = d.steps.getOrNull(i - 1)?.section
                        if (step.section != null && step.section != prev && d.steps.mapNotNull { it.section }.distinct().size > 1) {
                            Text(step.section, style = MaterialTheme.typography.titleMedium, color = Eazy.Muted, modifier = Modifier.padding(top = 6.dp))
                        }
                        StepEditor(
                            number = i + 1,
                            step = step,
                            ctx = ctx,
                            onChange = { t -> onEdit { it.copy(steps = it.steps.replaceAt(i, it.steps[i].copy(text = t))) } },
                            onRemove = { onEdit { it.copy(steps = it.steps.without(i)) } },
                        )
                    }
                    DashedAddButton("Stap toevoegen", {
                        onEdit { it.copy(steps = it.steps + ParsedStep("", it.steps.lastOrNull()?.section)) }
                    })
                }
            }
        }

        // --- vaste knop onderaan ---
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            saveError?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(CircleShape).background(Eazy.Paper).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            BigPillButton(
                text = if (saving) "Opslaan…" else saveLabel,
                icon = R.drawable.ic_check,
                accent = ctx.accent,
                onClick = onSave,
                enabled = canSave,
            )
        }

        PhotoStatusBar(scroll, PHOTO_HEIGHT)
    }

    if (pickKind) {
        KindSheet(
            categories = categories.list,
            selected = ids,
            // Op het actuele concept rekenen (niet op een vastgelegde lijst): twee snelle tikken gaan dan niet verloren.
            onToggle = { id -> onEdit { r -> r.copy(categoryIds = r.categoryIds.let { if (id in it) it - id else it + id }) } },
            onPrimary = { id -> onEdit { r -> r.copy(categoryIds = listOf(id) + (r.categoryIds - id)) } },
            onDismiss = { pickKind = false },
        )
    }
}

private val PHOTO_HEIGHT = 300.dp

/**
 * Soorten kiezen: meerdere mogen (paella = vis én vlees). De bovenste gekozen is de hoofdsoort en bepaalt de kleur;
 * "Kleur" bij een andere gekozen soort maakt die de hoofdsoort.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSheet(
    categories: List<CategoryEntity>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onPrimary: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Eazy.Paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text("Soort recept", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Kies er één of meer; het recept staat dan onder elke tegel. De soort met \"Kleur\" bepaalt de kleur.",
                style = MaterialTheme.typography.bodyMedium,
                color = Eazy.Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            categories.forEach { cat ->
                val c = cat.context
                val on = cat.id in selected
                val primary = selected.firstOrNull() == cat.id
                Surface(
                    onClick = { onToggle(cat.id) },
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.vivid), contentAlignment = Alignment.Center) {
                            Ico(c.icon, 22.dp, tint = Eazy.Ink)
                        }
                        Text(c.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        when {
                            primary -> Chip("Kleur", null, c.vivid)
                            on -> Chip("Kleur", null, Eazy.Grey, contentColor = Eazy.Muted, onClick = { onPrimary(cat.id) })
                        }
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(if (on) Eazy.Ink else Eazy.Paper)
                                .border(2.dp, if (on) Eazy.Ink else Eazy.Dash, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Ico(R.drawable.ic_check, 15.dp, tint = Eazy.Paper, contentDescription = "Gekozen") }
                    }
                }
            }
            BigPillButton("Klaar", R.drawable.ic_check, (selected.firstOrNull()?.let { id -> categories.firstOrNull { it.id == id }?.context?.vivid }) ?: Eazy.Brand, onDismiss, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun EditableText(
    value: String,
    onChange: (String) -> Unit,
    style: TextStyle,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style.copy(color = Eazy.Ink),
        cursorBrush = SolidColor(Eazy.Ink),
        keyboardOptions = keyboard,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = Eazy.Subtle)
                inner()
            }
        },
    )
}

@Composable
private fun NumberField(value: Int?, onChange: (Int?) -> Unit) {
    EditableText(
        value = value?.toString().orEmpty(),
        onChange = { t -> onChange(t.filter(Char::isDigit).take(4).toIntOrNull()) },
        style = MaterialTheme.typography.titleLarge,
        placeholder = "–",
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun EditableRow(value: String, onChange: (String) -> Unit, onRemove: () -> Unit, placeholder: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Eazy.Grey).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditableText(value, onChange, MaterialTheme.typography.bodyLarge, placeholder, Modifier.weight(1f).padding(vertical = 10.dp))
        RemoveButton(onRemove)
    }
}

@Composable
private fun StepEditor(number: Int, step: ParsedStep, ctx: ContextColor, onChange: (String) -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Eazy.Grey).padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$number", style = MaterialTheme.typography.headlineSmall, color = ctx.deep, modifier = Modifier.width(24.dp))
        EditableText(step.text, onChange, MaterialTheme.typography.bodyMedium, "Beschrijf deze stap", Modifier.weight(1f))
        RemoveButton(onRemove)
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent, contentColor = Eazy.Subtle, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Ico(R.drawable.ic_close, 16.dp, contentDescription = "Verwijderen") }
    }
}

private fun <T> List<T>.replaceAt(i: Int, value: T): List<T> = toMutableList().also { it[i] = value }
private fun <T> List<T>.without(i: Int): List<T> = toMutableList().also { it.removeAt(i) }
